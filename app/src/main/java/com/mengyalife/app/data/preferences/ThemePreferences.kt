package com.mengyalife.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mengyalife.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** DataStore 落盘位置：`files/datastore/mengya_settings.preferences_pb`。 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mengya_settings",
)

/**
 * 主题偏好。
 *
 * 存的是 [ThemeMode] 的 **枚举名**（如 `DARK`），而不是序号：
 * 以后往枚举里插值也不会把用户的设置读错。
 *
 * 读取失败（例如文件损坏）时回落到「跟随系统」，并把 IOException 吃掉，
 * 不让一个坏掉的偏好文件把整个 App 打崩。
 */
class ThemePreferences(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_mode")

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { preferences ->
            val stored = preferences[themeKey]
            ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[themeKey] = mode.name
        }
    }
}
