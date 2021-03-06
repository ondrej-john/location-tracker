package cz.ojohn.locationtracker.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationProvider
import android.os.Bundle
import cz.ojohn.locationtracker.data.LocationEntry
import cz.ojohn.locationtracker.data.TrackingRadius
import cz.ojohn.locationtracker.data.UserPreferences
import cz.ojohn.locationtracker.util.locationManager
import cz.ojohn.locationtracker.util.startForegroundServiceCompat
import cz.ojohn.locationtracker.util.toLocationEntry
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject

/**
 * Class that is responsible for performing location tracking
 */
class LocationTracker(private val appContext: Context,
                      private val userPreferences: UserPreferences,
                      private val locationController: LocationController) : LocationListener {

    companion object {
        private const val LAST_KNOWN_TIME_THRESHOLD = 120 * 1000
    }

    private var listenerCount: Int = 0

    private val locationSubject: PublishSubject<LocationEntry> = PublishSubject.create()
    private val statusSubject: BehaviorSubject<TrackingStatus> = BehaviorSubject.createDefault(TrackingStatus.DISABLED)

    override fun onLocationChanged(location: Location) {
        locationSubject.onNext(location.toLocationEntry())
    }

    override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) {
        if (provider == locationController.locationSource.androidProvider) {
            if (status == LocationProvider.AVAILABLE || status == LocationProvider.TEMPORARILY_UNAVAILABLE) {
                if (statusSubject.value == TrackingStatus.NOT_AVAILABLE) {
                    changeStatus(TrackingStatus.RUNNING)
                }
            } else {
                if (statusSubject.value != TrackingStatus.DISABLED) {
                    changeStatus(TrackingStatus.NOT_AVAILABLE)
                }
            }
        }
    }

    override fun onProviderEnabled(provider: String?) {}

    override fun onProviderDisabled(provider: String?) {
        if (provider == locationController.locationSource.androidProvider && statusSubject.value != TrackingStatus.DISABLED) {
            changeStatus(TrackingStatus.NOT_AVAILABLE)
        }
    }

    fun startTrackingService() {
        if (statusSubject.value != TrackingStatus.RUNNING) {
            appContext.startForegroundServiceCompat(TrackingService.getIntent(appContext))
        }
    }

    fun restartTracking() {
        val lastStatus = TrackingStatus.valueOf(
                userPreferences.getString(UserPreferences.KEY_TRACKING_LAST_STATUS, TrackingStatus.DISABLED.toString()))

        if (lastStatus != TrackingStatus.DISABLED) {
            startTrackingService()
        }
    }

    fun enableTracking(): Boolean {
        val updatesEnabled = enableLocationUpdates()
        if (updatesEnabled) {
            if (locationController.isLocationAllowed()) {
                changeStatus(TrackingStatus.RUNNING)
            } else {
                changeStatus(TrackingStatus.NOT_AVAILABLE)
            }
        } else {
            changeStatus(TrackingStatus.DISABLED)
        }
        return updatesEnabled
    }

    fun disableTracking() {
        if (statusSubject.value != TrackingStatus.DISABLED) {
            disableLocationUpdates()
            appContext.stopService(TrackingService.getIntent(appContext))
            changeStatus(TrackingStatus.DISABLED)
        }
    }

    fun enableLocationUpdates(): Boolean {
        if (listenerCount == 0) {
            try {
                appContext.locationManager.requestLocationUpdates(locationController.locationSource.androidProvider,
                        0, 0f, this)
                listenerCount++
            } catch (ex: SecurityException) {
                return false
            }
        } else {
            listenerCount++
        }
        return true
    }

    fun disableLocationUpdates() {
        if (listenerCount == 1) {
            appContext.locationManager.removeUpdates(this)
        }
        listenerCount--
    }

    @SuppressLint("MissingPermission")
    fun useGpsForLocation(useGps: Boolean) {
        val isGpsEnabled = locationController.locationSource == LocationController.LocationSource.GPS
        locationController.locationSource = if (useGps) LocationController.LocationSource.GPS else LocationController.LocationSource.NETWORK

        if (listenerCount > 0 && isGpsEnabled != useGps) {
            appContext.locationManager.removeUpdates(this)
            appContext.locationManager.requestLocationUpdates(locationController.locationSource.androidProvider,
                    0, 0f, this)
        }
    }

    fun getSettings(): LocationTracker.Settings {
        return userPreferences.getTrackingSettings()
    }

    fun updateSettings(settings: Settings) {
        userPreferences.setTrackingSettings(settings)
    }

    fun getLastKnownLocation(): LocationEntry? {
        try {
            val gpsLastPos = appContext.locationManager.
                    getLastKnownLocation(LocationController.LocationSource.GPS.androidProvider)
                    ?.toLocationEntry()
            val networkLastPos = appContext.locationManager
                    .getLastKnownLocation(LocationController.LocationSource.NETWORK.androidProvider)
                    ?.toLocationEntry()

            if (gpsLastPos == null) {
                return networkLastPos
            } else if (networkLastPos != null && networkLastPos.time - gpsLastPos.time > LAST_KNOWN_TIME_THRESHOLD) {
                return networkLastPos
            }
            return gpsLastPos
        } catch (ex: SecurityException) {
            return null
        }
    }

    fun distanceBetween(startLatitude: Double, startLongitude: Double,
                        endLatitude: Double, endLongitude: Double, accuracy: Float? = null): Float {
        val distance = floatArrayOf(0f, 0f)
        Location.distanceBetween(startLatitude, startLongitude, endLatitude, endLongitude, distance)
        if (getSettings().reduceFalseAlarms && accuracy != null) {
            val reducedDistance = distance[0] - accuracy
            return if (reducedDistance > 0) reducedDistance else distance[0]
        } else {
            return distance[0]
        }
    }

    fun observeLocationUpdates(): Observable<LocationEntry> = locationSubject

    fun observeTrackingStatus(): Observable<TrackingStatus> = statusSubject

    private fun changeStatus(status: TrackingStatus) {
        statusSubject.onNext(status)
        userPreferences.edit()
                .put(UserPreferences.KEY_TRACKING_LAST_STATUS, status.toString())
                .apply()
    }

    enum class TrackingStatus {
        RUNNING, // Location tracking is running
        DISABLED, // Location tracking is disabled
        NOT_AVAILABLE; // Location tracking should be enabled, but it is not available because of permissions or system settings
    }

    data class LocationResponse(val locationEntry: LocationEntry,
                                val locationName: String? = null,
                                val locationSource: String? = null,
                                val batteryStatus: Int? = null,
                                val wifiName: String? = null,
                                val ipAddr: String? = null)

    data class Settings(val latitude: Double,
                        val longitude: Double,
                        val radius: TrackingRadius,
                        val phone: String,
                        val reduceFalseAlarms: Boolean,
                        val lowBatteryNotify: Boolean,
                        val lowBatteryTurnOff: Boolean,
                        val chargerNotify: Boolean)
}
