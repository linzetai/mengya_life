package com.mengyalife.app.ui.advisor

import com.mengyalife.app.data.advisor.StoredGoalProposal
import com.mengyalife.app.domain.advisor.AdvisorMessage
import com.mengyalife.app.domain.advisor.AiConfigState
import com.mengyalife.app.domain.advisor.AiConsentState
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.advisor.GoalProposalDraft

data class ProposalApplyUiState(
    val proposalId: String,
    val loading: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val createdTitles: List<String> = emptyList(),
    val canUndo: Boolean = false,
    val undone: Boolean = false,
)

/** 顾问页只读状态。 */
data class AdvisorUiState(
    val messages: List<AdvisorMessage> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val config: AiConfigState = AiConfigState(),
    val consent: AiConsentState = AiConsentState(),
    val consentValid: Boolean = false,
    val proposals: Map<String, StoredGoalProposal> = emptyMap(),
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val applyState: ProposalApplyUiState? = null,
) {
    val configured: Boolean get() = config.configured
    val canSend: Boolean
        get() = configured && consentValid && !sending && input.isNotBlank()
}

/** 设置页 AI 配置 + 授权状态。密钥明文永远不进入这个状态。 */
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
    val consent: AiConsentState = AiConsentState(),
    val consentValid: Boolean = false,
) {
    val configured: Boolean
        get() = endpoint.isNotBlank() && model.isNotBlank() && hasKey
}

sealed interface AdvisorAction {
    data class InputChange(val value: String) : AdvisorAction
    data object Send : AdvisorAction
    data class Retry(val messageId: String) : AdvisorAction
    data object CancelRequest : AdvisorAction
    data object GrantConsent : AdvisorAction
    data object DeclineConsent : AdvisorAction
    data object RequestProposal : AdvisorAction
    data class UpdateProposalDraft(
        val proposalId: String,
        val draft: GoalProposalDraft,
        val selected: Set<String>,
    ) : AdvisorAction
    data class ApplyProposal(
        val proposalId: String,
        val draft: GoalProposalDraft,
        val selected: Set<String>,
    ) : AdvisorAction
    data class UndoProposal(val proposalId: String) : AdvisorAction
    data object DismissApplyState : AdvisorAction
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
    data class ToggleCategory(val category: ConsentCategory, val enabled: Boolean) : AiSettingsAction
    data object WithdrawConsent : AiSettingsAction
    data object ConsumeMessage : AiSettingsAction
    data class SetFakeEngine(val enabled: Boolean) : AiSettingsAction
}
