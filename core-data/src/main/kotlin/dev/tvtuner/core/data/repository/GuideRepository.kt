package dev.tvtuner.core.data.repository

import dev.tvtuner.core.data.db.dao.GuideDao
import dev.tvtuner.core.data.db.entity.GuideEntryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GuideRepository @Inject constructor(
    private val guideDao: GuideDao,
) {
    fun observeUpcomingForChannel(channelId: Long): Flow<List<GuideEntryEntity>> =
        guideDao.observeUpcomingForChannel(channelId, System.currentTimeMillis())

    fun observeGuideWindow(
        channelIds: List<Long>,
        fromMs: Long,
        toMs: Long,
    ): Flow<List<GuideEntryEntity>> = guideDao.observeGuideWindow(channelIds, fromMs, toMs)

    suspend fun getCurrentProgram(channelId: Long): GuideEntryEntity? =
        guideDao.getCurrentProgram(channelId, System.currentTimeMillis())

    suspend fun insertEntries(entries: List<GuideEntryEntity>) =
        guideDao.insertAll(entries)

    suspend fun clearForChannel(channelId: Long) =
        guideDao.deleteForChannel(channelId)

    suspend fun pruneExpired() =
        guideDao.deleteExpired(System.currentTimeMillis())
}
