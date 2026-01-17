package com.podcapture.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        private val CAPTURE_WINDOW_SECONDS = intPreferencesKey("capture_window_seconds")
        private val SKIP_INTERVAL_SECONDS = intPreferencesKey("skip_interval_seconds")
        private val OBSIDIAN_VAULT_URI = stringPreferencesKey("obsidian_vault_uri")
        private val OBSIDIAN_DEFAULT_TAGS = stringPreferencesKey("obsidian_default_tags")
        private val API_CALL_COUNT = intPreferencesKey("api_call_count")
        private val API_CALL_COUNT_DATE = stringPreferencesKey("api_call_count_date")
        const val DEFAULT_CAPTURE_WINDOW = 30
        const val DEFAULT_SKIP_INTERVAL = 10
        const val DEFAULT_OBSIDIAN_TAGS = "inbox/, resources/references/podcasts"
    }

    val captureWindowSeconds: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[CAPTURE_WINDOW_SECONDS] ?: DEFAULT_CAPTURE_WINDOW
        }

    val skipIntervalSeconds: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[SKIP_INTERVAL_SECONDS] ?: DEFAULT_SKIP_INTERVAL
        }

    val obsidianVaultUri: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[OBSIDIAN_VAULT_URI] ?: ""
        }

    val obsidianDefaultTags: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[OBSIDIAN_DEFAULT_TAGS] ?: DEFAULT_OBSIDIAN_TAGS
        }

    val apiCallCount: Flow<Int> = context.dataStore.data
        .map { preferences ->
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(java.util.Date())
            val storedDate = preferences[API_CALL_COUNT_DATE] ?: ""
            if (storedDate == today) {
                preferences[API_CALL_COUNT] ?: 0
            } else {
                0 // Reset count for new day
            }
        }

    val apiCallCountDate: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[API_CALL_COUNT_DATE] ?: ""
        }

    suspend fun incrementApiCallCount() {
        context.dataStore.edit { preferences ->
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(java.util.Date())
            val storedDate = preferences[API_CALL_COUNT_DATE] ?: ""

            if (storedDate == today) {
                preferences[API_CALL_COUNT] = (preferences[API_CALL_COUNT] ?: 0) + 1
            } else {
                // New day, reset counter
                preferences[API_CALL_COUNT] = 1
                preferences[API_CALL_COUNT_DATE] = today
            }
        }
    }

    suspend fun resetApiCallCount() {
        context.dataStore.edit { preferences ->
            preferences[API_CALL_COUNT] = 0
            preferences[API_CALL_COUNT_DATE] = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(java.util.Date())
        }
    }

    suspend fun setCaptureWindowSeconds(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[CAPTURE_WINDOW_SECONDS] = seconds
        }
    }

    suspend fun setSkipIntervalSeconds(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[SKIP_INTERVAL_SECONDS] = seconds
        }
    }

    suspend fun setObsidianVaultUri(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[OBSIDIAN_VAULT_URI] = uri
        }
    }

    suspend fun setObsidianDefaultTags(tags: String) {
        context.dataStore.edit { preferences ->
            preferences[OBSIDIAN_DEFAULT_TAGS] = tags
        }
    }
}
