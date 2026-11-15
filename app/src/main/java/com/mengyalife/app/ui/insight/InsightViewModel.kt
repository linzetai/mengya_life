package com.mengyalife.app.ui.insight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mengyalife.app.MengyaApplication
import com.mengyalife.app.data.advisor.AdvisorConfigRepository
import com.mengyalife.app.data.consent.AiConsentRepository
import com.mengyalife.app.data.insight.InsightGenerationResult
import com.mengyalife.app.data.insight.InsightService
import com.mengyalife.app.domain.insight.InsightReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
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
    private val consentRepository: AiConsentRepository,
    configRepository: AdvisorConfigRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(InsightUiState())
    val state: StateFlow<InsightUiState> = _state.asStateFlow()
    private var generationJob: Job? = null
    private var evidenceConsentEpoch: Long? = null

    init {
        viewModelScope.launch {
            combine(consentRepository.state, configRepository.state) { _, _ -> Unit }
                .drop(1)
                .collect {
                    if (generationJob?.isActive == true) {
                        generationJob?.cancel()
                        _state.update { it.copy(loading = false, error = "授权或模型配置已变化，本次洞察已取消") }
                    }
                    // Never keep historical evidence across a consent/configuration change.
                    _state.update { it.copy(report = null) }
                    refresh()
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val consent = consentRepository.current()
                val input = service.currentInput()
                val report = service.loadCurrent()
                val staleReason = if (report == null) service.staleReportReason() else null
                val currentConsent = consentRepository.current()
                // Keep only already-viewed evidence under the exact same authorization.
                // It remains historical, never actionable; a consent epoch change clears it.
                val sameConsent = currentConsent.granted && currentConsent == consent
                _state.update {
                    val evidence = historicalInsightEvidence(it.report, evidenceConsentEpoch, consent, currentConsent)
                    it.copy(
                        currentVersion = input.dataVersion,
                        report = if (sameConsent) report ?: evidence else null,
                        error = staleReason,
                        loading = false,
                    )
                }
                evidenceConsentEpoch = if (sameConsent) consent.epoch else null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 数据库/读取异常不能把界面永久卡在 loading
                _state.update {
                    it.copy(loading = false, report = null, error = "读取洞察数据失败：${e.message ?: "未知错误"}")
                }
            }
        }
    }

    fun generate() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        generationJob = viewModelScope.launch {
            val result = try {
                service.generate(force = true)
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(loading = false, error = "授权或模型配置已变化，本次洞察已取消") }
                throw cancelled
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "洞察生成失败：${e.message ?: "未知错误"}") }
                return@launch
            }
            when (result) {
                is InsightGenerationResult.Ready -> _state.update {
                    it.copy(loading = false, report = result.report, error = null)
                }
                is InsightGenerationResult.Failed -> _state.update {
                    it.copy(loading = false, report = result.report, error = result.message)
                }
            }
            val input = service.currentInput()
            evidenceConsentEpoch = consentRepository.current().epoch
            _state.update { it.copy(currentVersion = input.dataVersion) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MengyaApplication
                InsightViewModel(
                    app.container.insightService,
                    app.container.aiConsentRepository,
                    app.container.advisorConfigRepository,
                )
            }
        }
    }
}
