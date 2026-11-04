package com.mengyalife.app.data.weekly

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.mengyalife.app.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.weeklyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "${BuildConfig.PREFS_NAME}_weekly",
)

class WeeklyReviewPreferences(private val context: Context) {
    private val enabledKey = booleanPreferencesKey("weekly_review_enabled")
    private val notificationAskedKey = booleanPreferencesKey("notification_permission_asked")
    private val aiEnabledKey = booleanPreferencesKey("weekly_ai_enabled")

    val enabled: Flow<Boolean> = context.weeklyDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[enabledKey] ?: false }

    suspend fun current(): Boolean = enabled.first()

    val notificationAsked: Flow<Boolean> = context.weeklyDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[notificationAskedKey] ?: false }

    val aiEnabled: Flow<Boolean> = context.weeklyDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[aiEnabledKey] ?: false }

    suspend fun setAiEnabled(value: Boolean) {
        context.weeklyDataStore.edit { it[aiEnabledKey] = value }
    }

    suspend fun markNotificationAsked() {
        context.weeklyDataStore.edit { it[notificationAskedKey] = true }
    }

    suspend fun setEnabled(value: Boolean) {
        context.weeklyDataStore.edit { it[enabledKey] = value }
    }
}
