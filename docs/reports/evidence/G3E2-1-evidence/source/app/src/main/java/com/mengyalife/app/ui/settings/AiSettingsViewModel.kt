package com.mengyalife.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mengyalife.app.BuildConfig
import com.mengyalife.app.MengyaApplication
import com.mengyalife.app.data.advisor.AdvisorConfigRepository
import com.mengyalife.app.data.advisor.AdvisorEngine
import com.mengyalife.app.data.consent.AiConsentRepository
import com.mengyalife.app.domain.advisor.AdvisorRuntimeConfig
import com.mengyalife.app.domain.advisor.AdvisorRules
import com.mengyalife.app.ui.advisor.AiSettingsAction
import com.mengyalife.app.ui.advisor.AiSettingsUiState
import com.mengyalife.app.domain.advisor.ConsentCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 设置页「AI 服务」的 ViewModel。
 *
 * - 密钥只单向进入加密存储；UI 状态永远不含明文；
 * - 「测试连接」可以用当前输入 + 已保存密钥，但**不会**顺手保存；
 * - 删除密钥会同时清掉 Keystore 条目。
 */
class AiSettingsViewModel(
    private val configRepository: AdvisorConfigRepository,
    private val consentRepository: AiConsentRepository,
    private val networkEngine: AdvisorEngine,
    private val fakeEngine: AdvisorEngine?,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AiSettingsUiState(allowFakeEngine = BuildConfig.QA_BUILD),
    )
    val state: StateFlow<AiSettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            configRepository.state.collect { config ->
                _state.update {
                    it.copy(
                        endpoint = if (it.saving) it.endpoint else config.endpoint,
                        model = if (it.saving) it.model else config.model,
                        hasKey = config.hasKey,
                        loading = false,
                        useFakeEngine = config.useFakeEngine,
                        allowFakeEngine = BuildConfig.QA_BUILD,
                        consentValid = it.consent.isValidFor(config.endpoint) &&
                            config.endpoint.isNotBlank() && config.model.isNotBlank() &&
                            config.hasKey,
                    )
                }
            }
        }
        viewModelScope.launch {
            consentRepository.state.collect { consent ->
                _state.update {
                    it.copy(
                        consent = consent,
                        consentValid = consent.isValidFor(it.endpoint) &&
                            it.endpoint.isNotBlank() && it.model.isNotBlank() && it.hasKey,
                    )
                }
            }
        }
    }

    fun onAction(action: AiSettingsAction) {
        when (action) {
            is AiSettingsAction.EndpointChange -> _state.update {
                it.copy(endpoint = action.value, error = null, message = null)
            }
            is AiSettingsAction.ModelChange -> _state.update {
                it.copy(model = action.value, error = null, message = null)
            }
            is AiSettingsAction.KeyChange -> _state.update {
                it.copy(keyInput = action.value, error = null, message = null)
            }
            AiSettingsAction.Save -> save()
            AiSettingsAction.TestConnection -> testConnection()
            AiSettingsAction.DeleteKey -> deleteKey()
            AiSettingsAction.ConsumeMessage -> _state.update {
                it.copy(message = null, error = null)
            }
            is AiSettingsAction.SetFakeEngine -> setFakeEngine(action.enabled)
            is AiSettingsAction.ToggleCategory -> toggleCategory(action.category, action.enabled)
            AiSettingsAction.WithdrawConsent -> withdrawConsent()
        }
    }

    private fun save() {
        val state = _state.value
        if (state.saving) return
        _state.update { it.copy(saving = true, error = null, message = null) }
        viewModelScope.launch {
            val result = configRepository.save(
                endpoint = state.endpoint,
                model = state.model,
                apiKey = state.keyInput.takeIf { it.isNotBlank() },
            )
            _state.update {
                if (result.ok) {
                    it.copy(
                        saving = false,
                        keyInput = "",
                        hasKey = true,
                        message = "配置已保存（密钥已加密存储，不会显示原文）。",
                    )
                } else {
                    it.copy(saving = false, error = result.error ?: "保存失败")
                }
            }
        }
    }

    private fun testConnection() {
        val state = _state.value
        if (state.testing || state.saving) return
        AdvisorRules.validateEndpoint(state.endpoint)?.let {
            _state.update { s -> s.copy(error = it, message = null) }
            return
        }
        AdvisorRules.validateModel(state.model)?.let {
            _state.update { s -> s.copy(error = it, message = null) }
            return
        }
        _state.update { it.copy(testing = true, error = null, message = "正在测试连接…") }
        viewModelScope.launch {
            val existing = configRepository.runtimeConfig()
            val key = state.keyInput.trim().takeIf { it.isNotEmpty() } ?: existing?.apiKey
            if (key.isNullOrBlank()) {
                _state.update {
                    it.copy(testing = false, error = "请先填写并保存 API Key", message = null)
                }
                return@launch
            }
            val runtime = AdvisorRuntimeConfig(
                endpoint = state.endpoint.trim(),
                model = state.model.trim(),
                apiKey = key,
            )
            val engine = if (state.useFakeEngine && fakeEngine != null) fakeEngine else networkEngine
            val result = engine.testConnection(runtime)
            _state.update {
                it.copy(
                    testing = false,
                    message = if (result.success) result.message else null,
                    error = if (result.success) null else result.message,
                )
            }
        }
    }

    private fun deleteKey() {
        if (_state.value.saving) return
        viewModelScope.launch {
            configRepository.deleteKey()
            _state.update {
                it.copy(
                    hasKey = false,
                    keyInput = "",
                    message = "API Key 已删除，旧密钥无法再被使用。",
                    error = null,
                )
            }
        }
    }

    private fun toggleCategory(category: ConsentCategory, enabled: Boolean) {
        if (!_state.value.consent.granted) {
            _state.update { it.copy(error = "尚未授权；请先在顾问页完成首次授权。") }
            return
        }
        viewModelScope.launch {
            consentRepository.setCategory(category, enabled)
            _state.update {
                it.copy(
                    message = if (enabled) "已开启「${category.label}」" else "已关闭「${category.label}」",
                )
            }
        }
    }

    private fun withdrawConsent() {
        viewModelScope.launch {
            consentRepository.withdraw()
            _state.update {
                it.copy(
                    message = "已撤回授权；未来请求不会再自动使用个人数据，本地数据未删除。",
                    error = null,
                )
            }
        }
    }

    private fun setFakeEngine(enabled: Boolean) {
        if (!BuildConfig.QA_BUILD) return
        viewModelScope.launch {
            configRepository.setUseFakeEngine(enabled)
            _state.update { it.copy(useFakeEngine = enabled) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MengyaApplication
                AiSettingsViewModel(
                    configRepository = app.container.advisorConfigRepository,
                    consentRepository = app.container.aiConsentRepository,
                    networkEngine = app.container.networkAdvisorEngine,
                    fakeEngine = app.container.fakeAdvisorEngine,
                )
            }
        }
    }
}
