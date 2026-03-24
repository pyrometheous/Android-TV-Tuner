package dev.tvtuner.tuner.network.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.tvtuner.tuner.core.TunerBackend
import dev.tvtuner.tuner.network.HdhrTunerBackend
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkTunerModule {

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindHdhrBackend(impl: HdhrTunerBackend): TunerBackend
}
