package dev.tvtuner.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("tvtuner_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.dataStore

    val isOnboardingComplete: Flow<Boolean> = store.data
        .map { it[Keys.ONBOARDING_COMPLETE] ?: false }

    val recordingStoragePath: Flow<String?> = store.data
        .map { it[Keys.RECORDING_STORAGE_PATH] }

    val lastWatchedChannelId: Flow<Long?> = store.data
        .map { it[Keys.LAST_WATCHED_CHANNEL_ID]?.let { v -> if (v == -1L) null else v } }

    val selectedTunerBackend: Flow<String> = store.data
        .map { it[Keys.SELECTED_TUNER_BACKEND] ?: TunerBackend.AUTO }

    val networkTunerUrl: Flow<String?> = store.data
        .map { it[Keys.NETWORK_TUNER_URL] }

    suspend fun setOnboardingComplete(complete: Boolean) {
        store.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setRecordingStoragePath(path: String) {
        store.edit { it[Keys.RECORDING_STORAGE_PATH] = path }
    }

    suspend fun setLastWatchedChannelId(id: Long?) {
        store.edit { it[Keys.LAST_WATCHED_CHANNEL_ID] = id ?: -1L }
    }

    suspend fun setSelectedTunerBackend(backend: String) {
        store.edit { it[Keys.SELECTED_TUNER_BACKEND] = backend }
    }

    suspend fun setNetworkTunerUrl(url: String?) {
        store.edit {
            if (url != null) it[Keys.NETWORK_TUNER_URL] = url
            else it.remove(Keys.NETWORK_TUNER_URL)
        }
    }

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val RECORDING_STORAGE_PATH = stringPreferencesKey("recording_storage_path")
        val LAST_WATCHED_CHANNEL_ID = longPreferencesKey("last_watched_channel_id")
        val SELECTED_TUNER_BACKEND = stringPreferencesKey("selected_tuner_backend")
        val NETWORK_TUNER_URL = stringPreferencesKey("network_tuner_url")
    }

    object TunerBackend {
        const val AUTO = "AUTO"
        const val USB_MYGICA = "USB_MYGICA"
        const val NETWORK_HDHR = "NETWORK_HDHR"
        const val FAKE = "FAKE"
    }
}
