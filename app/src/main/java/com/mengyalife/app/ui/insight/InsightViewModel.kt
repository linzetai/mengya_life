package com.mengyalife.app.ui.insight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mengyalife.app.MengyaApplication
import com.mengyalife.app.data.insight.InsightGenerationResult
import com.mengyalife.app.data.insight.InsightService
import com.mengyalife.app.domain.insight.InsightReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InsightUiState(
    val loading: Boolean = false,
    val report: InsightReport? = null,
    val currentVersion: String? = null,
    val error: String? = null,
) {
    val hasReport: Boolean get() = report != null && report.dataVersion == currentVersion
}

class InsightViewModel(
    private val service: InsightService,
) : ViewModel() {
    private val _state = MutableStateFlow(InsightUiState())
    val state: StateFlow<InsightUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val input = service.currentInput()
            val report = service.loadCurrent()
            _state.update {
                it.copy(currentVersion = input.dataVersion, report = report, error = null)
            }
        }
    }

    fun generate() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = service.generate(force = true)
            when (result) {
                is InsightGenerationResult.Ready -> _state.update {
                    it.copy(loading = false, report = result.report, error = null)
                }
                is InsightGenerationResult.Failed -> _state.update {
                    it.copy(loading = false, report = result.report, error = result.message)
                }
            }
            val input = service.currentInput()
            _state.update { it.copy(currentVersion = input.dataVersion) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MengyaApplication
                InsightViewModel(app.container.insightService)
            }
        }
    }
}
