package dev.tvtuner.core.data.repository

import dev.tvtuner.core.data.db.dao.ChannelDao
import dev.tvtuner.core.data.db.entity.ChannelEntity
import dev.tvtuner.core.data.db.entity.GuideStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepository @Inject constructor(
    private val channelDao: ChannelDao,
) {
    fun observeVisibleChannels(): Flow<List<ChannelEntity>> =
        channelDao.observeVisibleChannels()

    fun observeAllChannels(): Flow<List<ChannelEntity>> =
        channelDao.observeAllChannels()

    fun observeFavorites(): Flow<List<ChannelEntity>> =
        channelDao.observeFavorites()

    suspend fun getById(id: Long): ChannelEntity? = channelDao.getById(id)

    suspend fun replaceAll(channels: List<ChannelEntity>) {
        channelDao.deleteAll()
        channelDao.insertAll(channels)
    }

    suspend fun upsert(channel: ChannelEntity): Long = channelDao.insert(channel)

    suspend fun setFavorite(id: Long, isFavorite: Boolean) =
        channelDao.setFavorite(id, isFavorite)

    suspend fun setHidden(id: Long, isHidden: Boolean) =
        channelDao.setHidden(id, isHidden)

    suspend fun setUserName(id: Long, name: String?) =
        channelDao.setUserName(id, name)

    suspend fun markGuideRefreshed(id: Long, status: String = GuideStatus.PARTIAL) =
        channelDao.updateGuideStatus(id, status, System.currentTimeMillis())

    suspend fun count(): Int = channelDao.count()

    suspend fun getAll(): List<ChannelEntity> = channelDao.getAll()
}
