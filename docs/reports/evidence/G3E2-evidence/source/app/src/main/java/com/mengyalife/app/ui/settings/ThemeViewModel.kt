package com.mengyalife.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mengyalife.app.MengyaApplication
import com.mengyalife.app.data.preferences.ThemePreferences
import com.mengyalife.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 主题状态持有者。
 *
 * [themeMode] 初始为 `null`（表示「还没从磁盘读出来」），
 * 界面在此期间先按「跟随系统」渲染，避免启动时闪一下错误的配色。
 */
class ThemeViewModel(
    private val preferences: ThemePreferences,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode?> = preferences.themeMode
        .map<ThemeMode, ThemeMode?> { it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferences.setThemeMode(mode)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MengyaApplication
                ThemeViewModel(app.container.themePreferences)
            }
        }
    }
}
