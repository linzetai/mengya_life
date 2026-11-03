package com.mengyalife.app.ui.advisor

import com.mengyalife.app.domain.advisor.AdvisorConsentRequest
import com.mengyalife.app.domain.advisor.AdvisorGoalChoice
import com.mengyalife.app.domain.advisor.AdvisorGoalContext
import com.mengyalife.app.domain.advisor.AdvisorMessage
import com.mengyalife.app.domain.advisor.AiConfigState

/** 顾问页只读状态。 */
data class AdvisorUiState(
    val messages: List<AdvisorMessage> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val config: AiConfigState = AiConfigState(),
    val configured: Boolean = false,
    val goalChoices: List<AdvisorGoalChoice> = emptyList(),
    val contextGoal: AdvisorGoalChoice? = null,
    val contextPayload: AdvisorGoalContext? = null,
    val goalPickerOpen: Boolean = false,
    val consent: AdvisorConsentRequest? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
) {
    val canSend: Boolean
        get() = !sending && input.isNotBlank() && configured
}

/** 设置页 AI 配置状态。密钥明文永远不进入这个状态。 */
data class AiSettingsUiState(
    val endpoint: String = "",
    val model: String = "",
    val keyInput: String = "",
    val hasKey: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val testing: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val allowFakeEngine: Boolean = false,
    val useFakeEngine: Boolean = false,
) {
    val configured: Boolean
        get() = endpoint.isNotBlank() && model.isNotBlank() && hasKey
}

sealed interface AdvisorAction {
    data class InputChange(val value: String) : AdvisorAction
    data object Send : AdvisorAction
    data class Retry(val messageId: String) : AdvisorAction
    data object CancelRequest : AdvisorAction
    data object OpenGoalPicker : AdvisorAction
    data object DismissGoalPicker : AdvisorAction
    data class SelectGoal(val goal: AdvisorGoalChoice) : AdvisorAction
    data object ClearContext : AdvisorAction
    data object ConfirmConsent : AdvisorAction
    data object CancelConsent : AdvisorAction
    data object DismissError : AdvisorAction
    data object DismissInfo : AdvisorAction
}

sealed interface AiSettingsAction {
    data class EndpointChange(val value: String) : AiSettingsAction
    data class ModelChange(val value: String) : AiSettingsAction
    data class KeyChange(val value: String) : AiSettingsAction
    data object Save : AiSettingsAction
    data object TestConnection : AiSettingsAction
    data object DeleteKey : AiSettingsAction
    data object ConsumeMessage : AiSettingsAction
    data class SetFakeEngine(val enabled: Boolean) : AiSettingsAction
}
