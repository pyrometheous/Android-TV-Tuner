package dev.tvtuner.tuner.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.tvtuner.tuner.core.TunerBackend
import dev.tvtuner.tuner.core.TunerManager
import dev.tvtuner.tuner.fake.FakeTunerBackend
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TunerCoreModule {

    @Provides
    @Singleton
    fun provideTunerManager(
        backends: @JvmSuppressWildcards Set<TunerBackend>,
    ): TunerManager = TunerManager(backends)

    @Provides
    @IntoSet
    @Singleton
    fun provideFakeTunerBackend(impl: FakeTunerBackend): TunerBackend = impl
}
