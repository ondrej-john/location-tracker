package cz.ojohn.locationtracker.di

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import cz.ojohn.locationtracker.screen.find.FindDeviceViewModel
import cz.ojohn.locationtracker.screen.main.MainViewModel
import cz.ojohn.locationtracker.screen.sms.SmsViewModel
import cz.ojohn.locationtracker.screen.tracking.TrackingViewModel
import cz.ojohn.locationtracker.viewmodel.ViewModelFactory
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap

/**
 * Module providing ViewModels
 */
@Module
abstract class ViewModelsModule {

    @Binds
    @IntoMap
    @ViewModelKey(MainViewModel::class)
    abstract fun bindMainViewModel(viewModel: MainViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(TrackingViewModel::class)
    abstract fun bindTrackingViewModel(viewModel: TrackingViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(SmsViewModel::class)
    abstract fun bindSmsViewModel(viewModel: SmsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(FindDeviceViewModel::class)
    abstract fun bindFindDeviceViewModel(viewModel: FindDeviceViewModel): ViewModel

    @Binds
    abstract fun bindViewModelFactory(viewModelFactory: ViewModelFactory): ViewModelProvider.Factory
}
