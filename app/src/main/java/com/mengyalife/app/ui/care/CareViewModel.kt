package com.mengyalife.app.ui.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mengyalife.app.MengyaApplication
import com.mengyalife.app.data.advisor.AdvisorConfigRepository
import com.mengyalife.app.data.advisor.AdvisorDataRepository
import com.mengyalife.app.data.advisor.AdvisorEngine
import com.mengyalife.app.data.advisor.ConversationRepository
import com.mengyalife.app.data.care.CareRepository
import com.mengyalife.app.data.consent.AiConsentRepository
import com.mengyalife.app.data.repository.SmallPlanRepository
import com.mengyalife.app.domain.advisor.AdvisorChatResult
import com.mengyalife.app.domain.advisor.AdvisorChatMessage
import com.mengyalife.app.domain.advisor.AdvisorRules
import com.mengyalife.app.domain.advisor.AdvisorSource
import com.mengyalife.app.domain.advisor.AdvisorSourceType
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.care.CareAction
import com.mengyalife.app.domain.care.CareCandidate
import com.mengyalife.app.domain.care.CareOpportunityFactory
import com.mengyalife.app.domain.care.CareParser
import com.mengyalife.app.domain.care.CarePolicy
import com.mengyalife.app.domain.goals.PlanTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class CareCard(
    val opportunityId: Long,
    val title: String,
    val text: String,
    val sourceType: String,
    /** 本轮新生成、可投递系统通知；旧卡片不得再次通知。 */
    val isNew: Boolean = false,
    val decisionId: Long? = null,
)

data class CareUiState(
    val card: CareCard? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val openAdvisorText: String? = null,
    val lastAction: String? = null,
)

/** Care Engine 的应用层：生成候选 → AI 决策 → 硬策略门 → 今天页卡片。 */
class CareViewModel(
    private val careRepository: CareRepository,
    private val planRepository: SmallPlanRepository,
    private val dataRepository: AdvisorDataRepository,
    private val consentRepository: AiConsentRepository,
    private val configRepository: AdvisorConfigRepository,
    private val conversations: ConversationRepository,
    private val settings: com.mengyalife.app.data.care.CareSettings,
    private val preferences: com.mengyalife.app.data.care.CarePreferenceRepository,
    private val restoreGeneration: () -> Long = { 0L },
    private val engine: AdvisorEngine,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val _state = MutableStateFlow(CareUiState())
    val state: StateFlow<CareUiState> = _state.asStateFlow()
    private val mutex = Mutex()
    private val runner = com.mengyalife.app.data.care.CareRunner(
        careRepository = careRepository,
        planRepository = planRepository,
        dataRepository = dataRepository,
        consentRepository = consentRepository,
        configRepository = configRepository,
        conversations = conversations,
        engine = engine,
        settings = settings,
        preferences = preferences,
        now = now,
        zone = zone,
        restoreGeneration = { restoreGeneration() },
    )

    fun refresh() = viewModelScope.launch {
        mutex.withLock {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                runCatching { refreshLocked() }
                    .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
                _state.value = _state.value.copy(loading = false)
            } finally {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }

    private suspend fun refreshLocked() {
        val card = runner.runOnce()
        if (card != null) {
            _state.value = _state.value.copy(card = card, error = null)
        } else {
            // 旧关心仍可展示，但不再阻塞新决策，也不产生新通知。
            val existing = careRepository.latestSentWithoutFeedback()
            if (existing != null) {
                val opp = careRepository.find(existing.opportunityId)
                if (opp != null) {
                    _state.value = _state.value.copy(
                        card = CareCard(opp.id, opp.title, existing.suggestedText ?: "上次的关心还在等你回应。",
                            opp.type, isNew = false, decisionId = existing.id),
                    )
                }
            }
        }
    }

    private fun showCard(opportunityId: Long, title: String, text: String, type: String) {
        _state.value = _state.value.copy(card = CareCard(opportunityId, title, text, type))
    }

    private fun defaultText(title: String) = "上次说的「$title」，最近有没有新进展？"

    private fun fmt(at: Long): String =
        DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(zone).format(Instant.ofEpochMilli(at))

    fun engage() {
        val card = _state.value.card ?: return
        viewModelScope.launch {
            careRepository.addFeedback(card.opportunityId, "ENGAGED")
            careRepository.markStatus(card.opportunityId, "DISMISSED")
            // 主动关心已经作为一条顾问消息存在；这里只负责进入对话，不替用户说话。
            _state.value = _state.value.copy(
                card = null,
                openAdvisorText = "",
                lastAction = "ENGAGED",
            )
        }
    }

    fun ignore() {
        val card = _state.value.card ?: return
        viewModelScope.launch {
            careRepository.addFeedback(card.opportunityId, "IGNORED")
            careRepository.markStatus(card.opportunityId, "DISMISSED")
            _state.value = _state.value.copy(card = null, lastAction = "IGNORED")
        }
    }

    fun silence() {
        val card = _state.value.card ?: return
        viewModelScope.launch {
            careRepository.addFeedback(card.opportunityId, "SILENCED")
            careRepository.markStatus(card.opportunityId, "DISMISSED")
            _state.value = _state.value.copy(card = null, lastAction = "SILENCED")
        }
    }

    fun onNotificationOpen(opportunityId: Long) {
        viewModelScope.launch {
            careRepository.addFeedback(opportunityId, "ENGAGED")
            careRepository.markStatus(opportunityId, "DISMISSED")
            _state.value = _state.value.copy(card = null, openAdvisorText = "", lastAction = "ENGAGED")
        }
    }

    fun notifyIfNeeded(context: android.content.Context) {
        val card = _state.value.card ?: return
        if (!settings.enabled || !settings.notificationsEnabled) return
        if (!card.isNew) return
        val decisionId = card.decisionId ?: return
        viewModelScope.launch {
            if (!careRepository.claimNotification(decisionId)) return@launch
            com.mengyalife.app.data.care.CareNotification.post(
                context, card.opportunityId, card.text, settings.lockScreenDetail,
            )
        }
    }

    fun consumeOpenAdvisor() {
        _state.value = _state.value.copy(openAdvisorText = null)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MengyaApplication
                CareViewModel(
                    careRepository = app.container.careRepository,
                    planRepository = app.container.smallPlanRepository,
                    dataRepository = app.container.advisorDataRepository,
                    consentRepository = app.container.aiConsentRepository,
                    configRepository = app.container.advisorConfigRepository,
                    conversations = app.container.conversationRepository,
                    settings = com.mengyalife.app.data.care.CareSettings(app),
                    preferences = app.container.carePreferenceRepository,
                    restoreGeneration = { app.container.restoreWriteBarrier.generation() },
                    engine = app.container.networkAdvisorEngine,
                )
            }
        }
    }
}
