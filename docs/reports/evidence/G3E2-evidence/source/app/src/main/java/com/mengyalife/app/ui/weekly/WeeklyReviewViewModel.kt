package com.mengyalife.app.ui.weekly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mengyalife.app.MengyaApplication
import com.mengyalife.app.data.weekly.WeeklyReviewPreferences
import com.mengyalife.app.data.weekly.WeeklyReviewScheduler
import com.mengyalife.app.data.weekly.WeeklyAiService
import com.mengyalife.app.data.weekly.WeeklyReviewService
import com.mengyalife.app.domain.weekly.WeeklyAiReview
import com.mengyalife.app.domain.weekly.WeeklyReview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WeeklyReviewUiState(
    val loading: Boolean = false,
    val generating: Boolean = false,
    val review: WeeklyReview? = null,
    val aiReview: WeeklyAiReview? = null,
    val error: String? = null,
)

class WeeklyReviewViewModel(
    private val service: WeeklyReviewService,
    private val aiService: WeeklyAiService,
    private val preferences: WeeklyReviewPreferences,
    private val scheduler: WeeklyReviewScheduler,
) : ViewModel() {
    val enabled: StateFlow<Boolean> = preferences.enabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), false,
    )
    val aiEnabled: StateFlow<Boolean> = preferences.aiEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), false,
    )
    val notificationAsked: StateFlow<Boolean> = preferences.notificationAsked.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), false,
    )
    private val _state = MutableStateFlow(WeeklyReviewUiState())
    val state: StateFlow<WeeklyReviewUiState> = _state

    init {
        refresh()
        viewModelScope.launch {
            service.observeLatest().collect { local ->
                _state.update {
                    it.copy(
                        review = local,
                        aiReview = local?.let { r -> aiService.currentFor(r.periodKey) } ?: it.aiReview,
                    )
                }
            }
        }
        viewModelScope.launch {
            aiService.observeLatest().collect { ai ->
                if (ai?.periodKey == _state.value.review?.periodKey) {
                    _state.update { it.copy(aiReview = ai) }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                service.generateIfNeeded()
                val local = service.latest()
                _state.update {
                    it.copy(
                        loading = false,
                        review = local,
                        aiReview = local?.let { r -> aiService.currentFor(r.periodKey) },
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "读取每周回顾失败：${e.message ?: "未知错误"}") }
            }
        }
    }

    fun markNotificationAsked() {
        viewModelScope.launch { preferences.markNotificationAsked() }
    }

    fun setEnabled(value: Boolean) {
        viewModelScope.launch {
            preferences.setEnabled(value)
            if (value) scheduler.schedule() else scheduler.cancel()
        }
    }

    fun setAiEnabled(value: Boolean) {
        viewModelScope.launch {
            if (value && !aiService.canUse()) {
                _state.update { it.copy(error = "请先完成 AI 授权和模型配置，再开启 AI 增强") }
                return@launch
            }
            preferences.setAiEnabled(value)
        }
    }

    fun generateAiNow() {
        viewModelScope.launch {
            _state.update { it.copy(generating = true, error = null) }
            try {
                aiService.generateIfAllowed()
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(generating = false, error = "AI 增强失败：${e.message ?: "未知错误"}") }
            }
        }
    }

    fun generateNow() {
        if (_state.value.generating) return
        viewModelScope.launch {
            _state.update { it.copy(generating = true, error = null) }
            try {
                val review = service.generateIfNeeded(force = true)
                _state.update { it.copy(generating = false, review = review) }
            } catch (e: Exception) {
                _state.update { it.copy(generating = false, error = "生成本地回顾失败：${e.message ?: "未知错误"}") }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MengyaApplication
                WeeklyReviewViewModel(
                    app.container.weeklyReviewService,
                    app.container.weeklyAiService,
                    app.container.weeklyReviewPreferences,
                    app.container.weeklyReviewScheduler,
                )
            }
        }
    }
}
