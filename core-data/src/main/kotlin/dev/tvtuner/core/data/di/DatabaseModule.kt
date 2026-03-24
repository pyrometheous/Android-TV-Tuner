package dev.tvtuner.core.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.tvtuner.core.data.db.TvTunerDatabase
import dev.tvtuner.core.data.db.dao.ChannelDao
import dev.tvtuner.core.data.db.dao.GuideDao
import dev.tvtuner.core.data.db.dao.RecordingDao
import dev.tvtuner.core.data.db.dao.ScheduledRecordingDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TvTunerDatabase =
        Room.databaseBuilder(
            context,
            TvTunerDatabase::class.java,
            TvTunerDatabase.DATABASE_NAME,
        ).build()

    @Provides fun provideChannelDao(db: TvTunerDatabase): ChannelDao = db.channelDao()
    @Provides fun provideGuideDao(db: TvTunerDatabase): GuideDao = db.guideDao()
    @Provides fun provideRecordingDao(db: TvTunerDatabase): RecordingDao = db.recordingDao()
    @Provides fun provideScheduledRecordingDao(db: TvTunerDatabase): ScheduledRecordingDao =
        db.scheduledRecordingDao()
}
