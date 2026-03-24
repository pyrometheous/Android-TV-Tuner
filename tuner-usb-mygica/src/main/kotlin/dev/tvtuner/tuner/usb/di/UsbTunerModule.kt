package dev.tvtuner.tuner.usb.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.tvtuner.tuner.core.TunerBackend
import dev.tvtuner.tuner.usb.MyGicaUsbTunerBackend
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UsbTunerModule {

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindMyGicaBackend(impl: MyGicaUsbTunerBackend): TunerBackend
}
