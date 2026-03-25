package dev.tvtuner.tuner.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.tvtuner.tuner.core.TunerBackend
import dev.tvtuner.tuner.core.TunerManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TunerCoreModule {

    @Provides
    @Singleton
    fun provideTunerManager(
        backends: @JvmSuppressWildcards Set<TunerBackend>,
    ): TunerManager = TunerManager(backends)
}
