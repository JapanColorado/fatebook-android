package dev.russell.fatebook.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // Lazy: MasterKey/Tink initialization takes 100-400ms and this singleton is
    // built during the Hilt graph in Application.onCreate — defer the cost to
    // the first secure read instead of every cold start.
    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "fatebook_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // --- API Key (encrypted) ---

    // Cached after first read: ApiKeyInterceptor reads this on every HTTP
    // request, and each uncached read is an AES-GCM decrypt. The lock keeps a
    // concurrent first read from caching the old key over a racing set() —
    // that stale value would then be served until process death.
    private val apiKeyLock = Any()
    private var cachedApiKey: String? = null
    private var apiKeyLoaded = false

    var apiKey: String?
        get() = synchronized(apiKeyLock) {
            if (!apiKeyLoaded) {
                cachedApiKey = encryptedPrefs.getString(KEY_API_KEY, null)
                apiKeyLoaded = true
            }
            cachedApiKey
        }
        set(value) = synchronized(apiKeyLock) {
            encryptedPrefs.edit().putString(KEY_API_KEY, value).apply()
            cachedApiKey = value
            apiKeyLoaded = true
        }

    val hasApiKey: Boolean
        get() = !apiKey.isNullOrBlank()

    // --- Notification settings (DataStore) ---

    val notificationsEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[NOTIFICATIONS_ENABLED] ?: false }

    val reminderHour: Flow<Int> =
        context.dataStore.data.map { it[REMINDER_HOUR] ?: 9 }

    val reminderMinute: Flow<Int> =
        context.dataStore.data.map { it[REMINDER_MINUTE] ?: 0 }

    val lastPredictionDateEpochMs: Flow<Long> =
        context.dataStore.data.map { it[LAST_PREDICTION_DATE] ?: 0L }

    /** Feed sort order; value is a [dev.russell.fatebook.ui.feed.FeedSort] name. */
    val feedSort: Flow<String> =
        context.dataStore.data.map { it[FEED_SORT] ?: "RESOLVE_BY" }

    /**
     * True once the full question history has been synced into Room.
     * From then on refresh() re-fetches all pages, not just the first —
     * otherwise the next pull-to-refresh would prune the synced history.
     */
    val fullHistorySynced: Flow<Boolean> =
        context.dataStore.data.map { it[FULL_HISTORY_SYNCED] ?: false }

    var displayName: String?
        get() = encryptedPrefs.getString(KEY_DISPLAY_NAME, null)
        set(value) = encryptedPrefs.edit().putString(KEY_DISPLAY_NAME, value).apply()

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setReminderTime(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[REMINDER_HOUR] = hour
            it[REMINDER_MINUTE] = minute
        }
    }

    suspend fun setLastPredictionDate(epochMs: Long) {
        context.dataStore.edit { it[LAST_PREDICTION_DATE] = epochMs }
    }

    suspend fun setFeedSort(sort: String) {
        context.dataStore.edit { it[FEED_SORT] = sort }
    }

    suspend fun setFullHistorySynced(synced: Boolean) {
        context.dataStore.edit { it[FULL_HISTORY_SYNCED] = synced }
    }

    fun clearAll() {
        synchronized(apiKeyLock) {
            encryptedPrefs.edit().clear().apply()
            cachedApiKey = null
            apiKeyLoaded = true
        }
    }

    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_DISPLAY_NAME = "display_name"
        private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        private val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        private val LAST_PREDICTION_DATE = longPreferencesKey("last_prediction_date")
        private val FEED_SORT = stringPreferencesKey("feed_sort")
        private val FULL_HISTORY_SYNCED = booleanPreferencesKey("full_history_synced")
    }
}
