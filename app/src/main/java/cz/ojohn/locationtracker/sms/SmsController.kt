package cz.ojohn.locationtracker.sms

import android.content.Context
import android.telephony.SmsManager
import cz.ojohn.locationtracker.R
import cz.ojohn.locationtracker.data.UserPreferences
import cz.ojohn.locationtracker.location.LocationTracker
import java.text.DateFormat
import java.util.*

/**
 * Class used to handle SMS messages and alarms
 */
class SmsController(private val appContext: Context,
                    private val userPreferences: UserPreferences) {

    companion object {
        const val SMS_KEYWORD = "LT"
        const val SMS_KEYWORD_LOCATION = "LOCATION"

        const val SMS_DATA_DELIMITER = ';'
        const val SMS_WIFI_DELIMITER = ","

        const val FORMAT_GPS = "%.4f N %.4f E"
        const val FORMAT_ACCURACY = "%.1f m"
        const val FORMAT_BATTERY = "%d %"
    }

    private val smsManager: SmsManager
        get() = SmsManager.getDefault()

    val smsSettings: Settings
        get() = userPreferences.getSmsSettings()

    fun sendSmsAlarm(phone: String) {
        sendSms(phone, appContext.getString(R.string.sms_alarm))
    }

    fun sendDeviceLocation(phone: String, locationResponse: LocationTracker.LocationResponse?) {
        if (locationResponse != null) {
            val formatLocationInfoSms = formatLocationInfoSms(locationResponse)
            sendSms(phone, formatLocationInfoSms)
        } else {
            sendSms(phone, appContext.getString(R.string.sms_response_error_no_location))
        }
    }

    fun processIncomingSms(sender: String, sms: String): SmsAction {
        var input = sms.trim()
        if (input.startsWith(SMS_KEYWORD, true)) {
            input = input.substring(SMS_KEYWORD.length, input.length).trim()
            if (input.contains(SMS_KEYWORD_LOCATION, true)) {
                return SmsAction.SendLocation(sender)
            }
            return SmsAction.None()
        } else {
            return SmsAction.None()
        }
    }

    private fun formatLocationInfoSms(locationResponse: LocationTracker.LocationResponse): String {
        val location = locationResponse.locationEntry
        val settings = smsSettings
        val stringBuilder = StringBuilder()
        if (settings.sendGps) {
            stringBuilder.append(FORMAT_GPS.format(location.lat, location.lon))
        }
        if (settings.sendLocationName) {
            val locationName = locationResponse.locationName
                    ?: appContext.getString(R.string.sms_response_name_unknown)
            stringBuilder.append(SMS_DATA_DELIMITER).append(locationName)
        }
        if (settings.sendLocationTime) {
            val time = DateFormat.getDateTimeInstance().format(Date(location.time))
            stringBuilder.append(SMS_DATA_DELIMITER).append(time)
        }
        if (settings.sendLocationAccuracy) {
            val accuracy = if (location.accuracy != null) FORMAT_ACCURACY.format(location.accuracy)
            else appContext.getString(R.string.sms_response_accuracy_unknown)

            stringBuilder.append(SMS_DATA_DELIMITER).append(accuracy)
        }
        if (settings.sendLocationSource) {
            val source = locationResponse.locationSource
                    ?: appContext.getString(R.string.sms_response_src_unknown)
            stringBuilder.append(SMS_DATA_DELIMITER).append(source)
        }
        if (settings.sendBattery) {
            val battery = if (locationResponse.batteryStatus != null)
                FORMAT_BATTERY.format(locationResponse.batteryStatus)
            else appContext.getString(R.string.sms_response_battery_unknown)

            stringBuilder.append(SMS_DATA_DELIMITER).append(battery)
        }
        if (settings.sendWiFi) {
            val wifi = locationResponse.wifiName
                    ?: appContext.getString(R.string.sms_response_wifi_unknown)
            stringBuilder.append(SMS_DATA_DELIMITER).append(wifi)
        }
        if (settings.sendWiFiNearby) {
            stringBuilder.append(SMS_DATA_DELIMITER)
            if (locationResponse.wifiNearby != null) {
                if (locationResponse.wifiNearby.isNotEmpty()) {
                    for (i in 0..locationResponse.wifiNearby.size) {
                        stringBuilder.append(locationResponse.wifiNearby[i])
                        if (i != locationResponse.wifiNearby.size - 1) {
                            stringBuilder.append(SMS_WIFI_DELIMITER)
                        }
                    }
                } else {
                    stringBuilder.append(appContext.getString(R.string.sms_response_wifi_no_nearby))
                }
            } else {
                stringBuilder.append(appContext.getString(R.string.sms_response_wifi_nearby_unknown))
            }
        }
        return stringBuilder.toString()
    }

    private fun sendSms(phone: String, text: String) {
        val textDivided = smsManager.divideMessage(text)
        if (textDivided.size > 1) {
            smsManager.sendMultipartTextMessage(phone, null, textDivided, null, null)
        } else {
            smsManager.sendTextMessage(phone, null, text, null, null)
        }
    }

    data class Settings(val sendGps: Boolean,
                        val sendLocationName: Boolean,
                        val sendLocationTime: Boolean,
                        val sendLocationAccuracy: Boolean,
                        val sendLocationSource: Boolean,
                        val sendBattery: Boolean,
                        val sendWiFi: Boolean,
                        val sendWiFiNearby: Boolean)

    sealed class SmsAction {
        class None : SmsAction()
        class SendLocation(val phone: String) : SmsAction()
    }
}
