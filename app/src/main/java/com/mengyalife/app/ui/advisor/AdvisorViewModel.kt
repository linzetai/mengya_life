package com.mengyalife.app.ui.advisor

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
import com.mengyalife.app.data.advisor.GoalProposalApply
import com.mengyalife.app.data.advisor.GoalProposalRepository
import com.mengyalife.app.data.advisor.DiaryApplyResult
import com.mengyalife.app.data.advisor.DiaryRecordApply
import com.mengyalife.app.data.advisor.GoalApplyResult
import com.mengyalife.app.data.advisor.GoalRecordApply
import com.mengyalife.app.data.advisor.PlanApplyResult
import com.mengyalife.app.data.advisor.PlanRecordApply
import com.mengyalife.app.data.advisor.ProgressRecordApply
import com.mengyalife.app.data.advisor.RecordApplyResult
import com.mengyalife.app.data.advisor.RecordProposalRepository
import com.mengyalife.app.data.advisor.RecordUndoResult
import com.mengyalife.app.data.advisor.StoredRecordProposal
import com.mengyalife.app.data.consent.AiConsentRepository
import com.mengyalife.app.data.insight.InsightService
import com.mengyalife.app.data.restore.RestoreException
import com.mengyalife.app.data.restore.RestoreWriteBarrier
import com.mengyalife.app.domain.advisor.AdvisorChatResult
import com.mengyalife.app.domain.advisor.AdvisorContextAssembler
import com.mengyalife.app.domain.advisor.AdvisorContextSnapshot
import com.mengyalife.app.domain.advisor.AdvisorContextEntry
import com.mengyalife.app.domain.advisor.AdvisorSource
import com.mengyalife.app.domain.advisor.AdvisorSourceType
import com.mengyalife.app.domain.advisor.AdvisorError
import com.mengyalife.app.domain.advisor.AdvisorMessage
import com.mengyalife.app.domain.advisor.AdvisorMessageStatus
import com.mengyalife.app.domain.advisor.AdvisorRole
import com.mengyalife.app.domain.advisor.AdvisorRules
import com.mengyalife.app.domain.advisor.AdvisorRuntimeConfig
import com.mengyalife.app.domain.advisor.AiConsentRules
import com.mengyalife.app.domain.advisor.AiConsentState
import com.mengyalife.app.domain.advisor.AiConfigState
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.advisor.GoalProposalParseResult
import com.mengyalife.app.domain.advisor.GoalProposalDraft
import com.mengyalife.app.domain.advisor.GoalProposalParser
import com.mengyalife.app.domain.advisor.DiaryIntentGate
import com.mengyalife.app.domain.advisor.GoalBeforeSnapshot
import com.mengyalife.app.domain.advisor.GoalChangeKind
import com.mengyalife.app.domain.advisor.GoalIntentGate
import com.mengyalife.app.domain.advisor.PlanChangeKind
import com.mengyalife.app.domain.advisor.PlanIntentGate
import com.mengyalife.app.domain.advisor.RecordAmountKind
import com.mengyalife.app.domain.advisor.RecordIntent
import com.mengyalife.app.domain.advisor.RecordIntentGate
import com.mengyalife.app.domain.advisor.RecordKind
import com.mengyalife.app.domain.advisor.RecordProposalDraft
import com.mengyalife.app.domain.advisor.RecordProposalParseResult
import com.mengyalife.app.domain.advisor.RecordProposalParser
import com.mengyalife.app.domain.advisor.RecordProposalRules
import com.mengyalife.app.domain.insight.InsightReference
import com.mengyalife.app.domain.insight.InsightReferenceResolution
import com.mengyalife.app.domain.insight.ResolvedInsightReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 顾问 ViewModel（Gate 3B-1）。
 *
 * - 首次一次性授权；未授权不读取/不发送个人数据；
 * - 每轮由 AdvisorContextAssembler 自动检索授权类别；
 * - 历史消息按 context epoch 隔离；
 * - 授权状态在请求中变化时取消请求，避免用旧授权继续发送；
 * - 模型只能产生文本/草案，不修改业务数据。
 */
class AdvisorViewModel(
    private val conversations: ConversationRepository,
    private val configRepository: AdvisorConfigRepository,
    private val consentRepository: AiConsentRepository,
    private val dataRepository: AdvisorDataRepository,
    private val proposalRepository: GoalProposalRepository,
    private val proposalApplyService: GoalProposalApply,
    private val recordRepository: RecordProposalRepository,
    private val recordApplyService: ProgressRecordApply,
    private val diaryApplyService: DiaryRecordApply,
    private val goalApplyService: GoalRecordApply,
    private val planApplyService: PlanRecordApply,
    private val careRepository: com.mengyalife.app.data.care.CareRepository? = null,
    private val carePreferenceRepository: com.mengyalife.app.data.care.CarePreferenceRepository? = null,
    private val assembler: AdvisorContextAssembler,
    private val networkEngine: AdvisorEngine,
    private val fakeEngine: AdvisorEngine?,
    private val restoreGeneration: () -> Long = { 0L },
    private val restoreWriteBarrier: RestoreWriteBarrier? = null,
    private val restoreGateActive: () -> Boolean = { false },
    private val insightService: InsightService? = null,
) : ViewModel() {

    private data class LocalState(
        val input: String = "",
        val sending: Boolean = false,
        val errorMessage: String? = null,
        val infoMessage: String? = null,
        val applyState: ProposalApplyUiState? = null,
        val recordAction: RecordActionUiState? = null,
        val planSuggestion: Boolean = false,
        val planSuggestionDismissed: Boolean = false,
    )

    /** Immutable security/config/data binding shared by the main chat and every extra extraction. */
    private data class AiRequestBinding(
        val config: AiConfigState,
        val runtime: AdvisorRuntimeConfig,
        val consent: AiConsentState,
        val restoreGeneration: Long,
        val data: AdvisorContextSnapshot,
        val engine: AdvisorEngine,
    )

    private data class LegacyInsightLink(
        val hasInsightMarker: Boolean,
        val reference: InsightReference?,
    )

    private val local = MutableStateFlow(LocalState())
    private var requestJob: Job? = null
    private var activeEpoch: Long? = null
    private var latestConsent: AiConsentState = AiConsentState()
    private var applying = false
    private var recording = false
    /** 用户刚拒绝过拆解的目标；只在本轮会话内生效，避免反复催促。 */
    private val dismissedBreakdownGoalIds = mutableSetOf<Long>()
    private var latestConfig: com.mengyalife.app.domain.advisor.AiConfigState =
        com.mengyalife.app.domain.advisor.AiConfigState()

    private val messagesFlow = conversations.observeMessages()
    private val configFlow = configRepository.state
    private val consentFlow = consentRepository.state
    private val proposalsFlow = proposalRepository.observeAll()
    private val recordsFlow = recordRepository.observeAll()

    private val configState = MutableStateFlow(com.mengyalife.app.domain.advisor.AiConfigState())
    private val consentState = MutableStateFlow(AiConsentState())

    private val baseState: Flow<AdvisorUiState> = combine(
        messagesFlow,
        configState,
        consentState,
        proposalsFlow,
        local,
    ) { messages, config, consent, proposals, localState ->
        AdvisorUiState(
            messages = messages,
            input = localState.input,
            sending = localState.sending,
            config = config,
            consent = consent,
            consentValid = consent.isValidFor(config.endpoint) && config.configured,
            proposals = proposals.associateBy { it.messageId },
            errorMessage = localState.errorMessage,
            infoMessage = localState.infoMessage,
            applyState = localState.applyState,
            recordAction = localState.recordAction,
            planSuggestion = localState.planSuggestion,
        )
    }

    val uiState: StateFlow<AdvisorUiState> = combine(
        baseState,
        recordsFlow,
    ) { state, records ->
        state.copy(records = records.associateBy { it.messageId })
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AdvisorUiState(),
    )

    init {
        viewModelScope.launch {
            configFlow.collect { config ->
                val previous = latestConfig
                latestConfig = config
                configState.value = config
                val changed = previous.endpoint != config.endpoint ||
                    previous.model != config.model ||
                    previous.hasKey != config.hasKey ||
                    previous.useFakeEngine != config.useFakeEngine
                // 服务配置变化时，进行中的请求不能继续用旧 endpoint 发出去
                if (local.value.sending && changed) {
                    requestJob?.cancel()
                }
            }
        }
        viewModelScope.launch {
            consentFlow.collect { consent ->
                latestConsent = consent
                consentState.value = consent
                val epoch = activeEpoch
                // 授权/类别/接收方变化时，进行中的请求必须安全取消
                if (local.value.sending && epoch != null && epoch != consent.epoch) {
                    requestJob?.cancel()
                }
            }
        }
    }

    fun onAction(action: AdvisorAction) {
        when (action) {
            is AdvisorAction.InputChange -> local.update { it.copy(input = action.value) }
            is AdvisorAction.DiscussInsight -> discussInsight(action.reference)
            AdvisorAction.Send -> send()
            is AdvisorAction.Retry -> retry(action.messageId)
            AdvisorAction.CancelRequest -> cancelRequest()
            AdvisorAction.GrantConsent -> grantConsent()
            AdvisorAction.DeclineConsent -> local.update {
                it.copy(
                    infoMessage = "未授权：顾问不会读取或发送目标、进展、日记与历史对话。你仍可以在本机查看已有聊天。",
                )
            }
            AdvisorAction.RequestProposal -> requestProposal()
            AdvisorAction.AcceptPlanSuggestion -> acceptPlanSuggestion()
            AdvisorAction.DismissPlanSuggestion -> local.update {
                it.copy(planSuggestion = false, planSuggestionDismissed = true)
            }
            is AdvisorAction.UpdateProposalDraft -> updateProposalDraft(
                action.proposalId, action.draft, action.selected,
            )
            is AdvisorAction.ApplyProposal -> applyProposal(
                action.proposalId, action.draft, action.selected,
            )
            is AdvisorAction.UndoProposal -> undoProposal(action.proposalId)
            is AdvisorAction.ConfirmRecord -> confirmRecord(action.proposalId, action.draft)
            is AdvisorAction.UpdateRecordDraft -> updateRecordDraft(action.proposalId, action.draft)
            is AdvisorAction.UndoRecord -> undoRecord(action.proposalId)
            is AdvisorAction.OpenDiary -> openDiary(action.proposalId)
            is AdvisorAction.OpenGoal -> openGoal(action.proposalId)
            is AdvisorAction.CancelRecord -> cancelRecord(action.proposalId)
            AdvisorAction.DismissRecordAction -> local.update { it.copy(recordAction = null) }
            AdvisorAction.DismissApplyState -> local.update { it.copy(applyState = null) }
            AdvisorAction.DismissError -> local.update { it.copy(errorMessage = null) }
            AdvisorAction.DismissInfo -> local.update { it.copy(infoMessage = null) }
        }
    }

    private fun discussInsight(reference: InsightReference) {
        if (local.value.sending) return
        viewModelScope.launch {
            val service = insightService
            if (service == null) {
                local.update { it.copy(errorMessage = "当前无法验证建议来源，请重新生成洞察") }
                return@launch
            }
            val resolved = when (val resolution = service.resolveCurrentReference(reference)) {
                is InsightReferenceResolution.Stale -> {
                    local.update { it.copy(errorMessage = resolution.reason) }
                    return@launch
                }
                is InsightReferenceResolution.Current -> resolution.value
            }
            val alreadyInConversation = conversations.loadAll().any { message ->
                message.role == AdvisorRole.USER && message.sources.any { source ->
                    source.type == AdvisorSourceType.INSIGHT &&
                        source.metadata["dataVersion"] == reference.dataVersion &&
                        source.label == resolved.insight.title
                }
            }
            if (alreadyInConversation) {
                local.update {
                    it.copy(infoMessage = "这条建议已在顾问对话中；继续在同一对话补充，不会重复创建记录。")
                }
                return@launch
            }
            startRequest(
                input = "请先解释我选中的成长建议如何从依据得出，并区分已记录事实与 AI 推测。允许我质疑或修改前提；未经我确认，不要修改目标、计划或进展。",
                insertUser = true,
                excludeMessageId = null,
                proposalMode = false,
                insightReference = reference,
            )
        }
    }

    private fun requiresInsightContinuity(input: String, proposalMode: Boolean): Boolean =
        proposalMode ||
            PlanIntentGate.looksLikeCreate(input) ||
            GoalIntentGate.looksRelevant(input) ||
            listOf("不同意", "不认同", "质疑", "这条建议", "这个建议", "修改前提", "调整前提", "解释依据", "怎么得出", "为什么")
                .any { input.contains(it) }

    private fun insightReferenceIn(sources: List<AdvisorSource>): InsightReference? =
        sources.firstOrNull { it.type == AdvisorSourceType.INSIGHT }?.metadata?.let { metadata ->
            val reportId = metadata["reportId"] ?: return@let null
            val version = metadata["dataVersion"] ?: return@let null
            val insightId = metadata["insightId"] ?: return@let null
            InsightReference(reportId, version, insightId)
        }

    private fun isExplicitTopicSwitch(text: String): Boolean = listOf(
        "换个话题", "换个问题", "聊点别的", "聊个别的", "另一个话题", "另一个问题",
        "先聊点别的", "先不聊这个", "不谈刚才那个", "这和前面无关", "与前面无关",
        "不相关的话题", "说个无关的", "另有一件事", "我有另一个问题", "新问题是",
    ).any { phrase -> text.contains(phrase) }

    private fun historyForCurrentTopic(
        history: List<AdvisorMessage>,
        input: String,
        directInsightHandoff: Boolean,
    ): List<AdvisorMessage> {
        if (directInsightHandoff || isExplicitTopicSwitch(input)) return emptyList()
        val boundary = history.indexOfLast {
            it.role == AdvisorRole.USER && isExplicitTopicSwitch(it.content)
        }
        return if (boundary >= 0) history.drop(boundary) else history
    }

    private fun messagesForCurrentTopic(messages: List<AdvisorMessage>, input: String): List<AdvisorMessage> {
        if (isExplicitTopicSwitch(input)) return emptyList()
        val boundary = messages.indexOfLast {
            it.role == AdvisorRole.USER && isExplicitTopicSwitch(it.content)
        }
        return if (boundary >= 0) messages.drop(boundary) else messages
    }

    private fun findActiveInsightReference(messages: List<AdvisorMessage>): InsightReference? {
        for (message in messages.asReversed()) {
            if (message.role != AdvisorRole.USER || message.status != AdvisorMessageStatus.SENT) continue
            if (isExplicitTopicSwitch(message.content)) return null
            insightReferenceIn(message.sources)?.let { return it }
        }
        return null
    }

    private suspend fun findLegacyInsightLink(
        messageIds: List<String>,
        proposalMessageId: String,
    ): LegacyInsightLink {
        val allowed = messageIds.toSet() + proposalMessageId
        val related = conversations.loadAll().asReversed().filter { it.id in allowed }
        for (message in related) {
            if (message.role == AdvisorRole.USER && isExplicitTopicSwitch(message.content)) {
                return LegacyInsightLink(hasInsightMarker = false, reference = null)
            }
            val marker = message.sources.any {
                it.type == AdvisorSourceType.INSIGHT || it.type == AdvisorSourceType.INSIGHT_FACT
            }
            if (marker) return LegacyInsightLink(true, insightReferenceIn(message.sources))
        }
        return LegacyInsightLink(false, null)
    }

    private suspend fun insightReferenceError(reference: InsightReference): String? {
        val service = insightService ?: return "成长建议来源无法验证，请重新生成洞察"
        return when (val resolution = service.resolveCurrentReference(reference)) {
            is InsightReferenceResolution.Current -> null
            is InsightReferenceResolution.Stale -> resolution.reason
        }
    }

    private fun advisorSourcesForInsight(
        resolved: ResolvedInsightReference,
        isHandoff: Boolean = false,
    ): List<AdvisorSource> = buildList {
        val reference = resolved.reference
        add(
            AdvisorSource(
                type = AdvisorSourceType.INSIGHT,
                recordId = resolved.insight.id,
                label = resolved.insight.title,
                metadata = mapOf(
                    "reportId" to reference.reportId,
                    "dataVersion" to reference.dataVersion,
                    "insightId" to reference.insightId,
                    "factIds" to resolved.insight.factIds.joinToString(","),
                ) + if (isHandoff) mapOf("handoff" to "true") else emptyMap(),
            ),
        )
        resolved.facts.forEach { fact ->
            add(
                AdvisorSource(
                    type = AdvisorSourceType.INSIGHT_FACT,
                    recordId = fact.id,
                    label = if (fact.isInference) "AI 推测" else "规则事实",
                ),
            )
        }
        resolved.insight.sources.forEach { source ->
            val type = when (source.type.uppercase()) {
                "GOAL" -> AdvisorSourceType.GOAL
                "CHECKIN", "PROGRESS" -> AdvisorSourceType.PROGRESS
                "JOURNAL" -> AdvisorSourceType.JOURNAL
                "GROWTH" -> AdvisorSourceType.GROWTH
                "MEMORY" -> AdvisorSourceType.MEMORY
                else -> return@forEach
            }
            add(AdvisorSource(type, source.recordId, source.label))
        }
    }.distinctBy { it.type to it.recordId }

    private fun advisorContextEntriesForInsight(resolved: ResolvedInsightReference): List<AdvisorContextEntry> {
        val sources = advisorSourcesForInsight(resolved).associateBy { it.type to it.recordId }
        val entries = mutableListOf<AdvisorContextEntry>()
        val insight = resolved.insight
        entries += AdvisorContextEntry(
            "用户选中的成长建议（${insight.type.label}，${if (insight.isInference) "AI 推测" else "建议整理"}）：" +
                "${insight.title}：${insight.body}",
            sources[AdvisorSourceType.INSIGHT to insight.id],
        )
        resolved.facts.forEach { fact ->
            val kind = if (fact.isInference) "AI 推测" else "规则事实"
            entries += AdvisorContextEntry(
                "[$kind#${fact.id}] ${fact.text}",
                sources[AdvisorSourceType.INSIGHT_FACT to fact.id],
            )
        }
        insight.sources.forEach { source ->
            val type = when (source.type.uppercase()) {
                "GOAL" -> AdvisorSourceType.GOAL
                "CHECKIN", "PROGRESS" -> AdvisorSourceType.PROGRESS
                "JOURNAL" -> AdvisorSourceType.JOURNAL
                "GROWTH" -> AdvisorSourceType.GROWTH
                "MEMORY" -> AdvisorSourceType.MEMORY
                else -> return@forEach
            }
            entries += AdvisorContextEntry(
                "[原始来源引用] ${source.type}#${source.recordId} ${source.label}",
                sources[type to source.recordId],
            )
        }
        return entries
    }

    private suspend fun captureAiRequestBinding(
        expectedConfig: AiConfigState,
        expectedConsent: AiConsentState,
        expectedRestoreGeneration: Long,
        data: AdvisorContextSnapshot,
    ): AiRequestBinding? {
        val barrier = restoreWriteBarrier
        if (barrier == null || restoreGateActive() || !barrier.isReady() ||
            restoreGeneration() != expectedRestoreGeneration
        ) {
            local.update { it.copy(errorMessage = "恢复状态未就绪或已变化，本次没有请求模型") }
            return null
        }
        val firstConfig = configRepository.state.first()
        val runtime = configRepository.runtimeConfig()
        val secondConfig = configRepository.state.first()
        val consent = consentRepository.current()
        if (firstConfig != expectedConfig || secondConfig != expectedConfig ||
            consent.epoch != expectedConsent.epoch || consent.categories != expectedConsent.categories ||
            !consent.isValidFor(expectedConfig.endpoint) || runtime == null ||
            runtime.endpoint != secondConfig.endpoint || runtime.model != secondConfig.model
        ) {
            local.update { it.copy(errorMessage = "服务配置或授权已变化，本次没有请求模型") }
            return null
        }
        val currentData = authorizedSnapshot(dataRepository.snapshot(consent), consent, data.history)
        if (!sameAuthorizedData(data, currentData, consent.categories)) {
            local.update { it.copy(errorMessage = "授权数据已变化，请重新发送") }
            return null
        }
        return AiRequestBinding(
            config = secondConfig,
            runtime = runtime,
            consent = consent,
            restoreGeneration = expectedRestoreGeneration,
            data = data,
            engine = engineFor(secondConfig),
        )
    }

    private fun authorizedSnapshot(
        snapshot: AdvisorContextSnapshot,
        consent: AiConsentState,
        history: List<AdvisorMessage>,
    ): AdvisorContextSnapshot = snapshot.copy(
        goals = snapshot.goals.takeIf { consent.enabled(ConsentCategory.GOALS) }.orEmpty(),
        plans = snapshot.plans.takeIf { consent.enabled(ConsentCategory.GOALS) }.orEmpty(),
        checkins = snapshot.checkins.takeIf { consent.enabled(ConsentCategory.PROGRESS) }.orEmpty(),
        journals = snapshot.journals.takeIf { consent.enabled(ConsentCategory.JOURNAL) }.orEmpty(),
        growthSignals = snapshot.growthSignals.takeIf { consent.enabled(ConsentCategory.JOURNAL) }.orEmpty(),
        growthMemories = snapshot.growthMemories.takeIf { consent.enabled(ConsentCategory.JOURNAL) }.orEmpty(),
        history = history.takeIf { consent.enabled(ConsentCategory.CONVERSATION) }.orEmpty(),
    )

    private fun sameAuthorizedData(
        expected: AdvisorContextSnapshot,
        current: AdvisorContextSnapshot,
        categories: Set<ConsentCategory>,
    ): Boolean =
        (ConsentCategory.GOALS !in categories ||
            expected.goals == current.goals && expected.plans == current.plans) &&
            (ConsentCategory.PROGRESS !in categories || expected.checkins == current.checkins) &&
            (ConsentCategory.JOURNAL !in categories ||
                expected.journals == current.journals &&
                expected.growthSignals == current.growthSignals &&
                expected.growthMemories == current.growthMemories)

    private fun requiredCategoriesForSources(
        sources: List<AdvisorSource>,
    ): Set<ConsentCategory> = buildSet {
        sources.forEach { source ->
            when (source.type) {
                AdvisorSourceType.GOAL, AdvisorSourceType.PLAN, AdvisorSourceType.CARE -> add(ConsentCategory.GOALS)
                AdvisorSourceType.PROGRESS -> add(ConsentCategory.PROGRESS)
                AdvisorSourceType.JOURNAL, AdvisorSourceType.GROWTH, AdvisorSourceType.MEMORY ->
                    add(ConsentCategory.JOURNAL)
                else -> Unit
            }
        }
    }

    private suspend fun aiBindingProblem(
        binding: AiRequestBinding,
        requiredCategories: Set<ConsentCategory>,
    ): String? {
        val barrier = restoreWriteBarrier ?: return "恢复安全边界不可用"
        if (restoreGateActive() || !barrier.isReady() || restoreGeneration() != binding.restoreGeneration) {
            return "恢复状态或 generation 已变化"
        }
        val config = configRepository.state.first()
        val runtime = configRepository.runtimeConfig()
        val consent = consentRepository.current()
        if (config != binding.config || runtime != binding.runtime) return "endpoint/model/API Key 配置已变化"
        if (!consent.granted || consent.epoch != binding.consent.epoch ||
            consent.categories != binding.consent.categories || !consent.isValidFor(binding.runtime.endpoint)
        ) return "授权 epoch、接收方或数据类别已变化"
        if (requiredCategories.any { !consent.enabled(it) }) return "本轮所需数据类别未授权"
        val current = authorizedSnapshot(dataRepository.snapshot(consent), consent, binding.data.history)
        if (!sameAuthorizedData(binding.data, current, requiredCategories)) return "请求所依据的目标/进展/日记已变化"
        if (ConsentCategory.CONVERSATION in requiredCategories) {
            val stored = conversations.loadAll().associateBy { it.id }
            if (binding.data.history.any { stored[it.id] != it }) return "历史对话来源已变化"
        }
        return null
    }

    /** Shared outbound boundary for the main reply and all four advisor extraction calls. */
    private suspend fun <T : Any> withAiRequestBoundary(
        binding: AiRequestBinding,
        requiredCategories: Set<ConsentCategory>,
        block: suspend () -> T,
    ): T? {
        val barrier = restoreWriteBarrier ?: run {
            local.update { it.copy(errorMessage = "恢复安全边界不可用，本次没有请求模型") }
            return null
        }
        return try {
            barrier.withWrite(binding.restoreGeneration) {
                val problem = aiBindingProblem(binding, requiredCategories)
                if (problem != null) {
                    local.update { it.copy(errorMessage = "$problem；本次没有继续发送或保存") }
                    null
                } else {
                    currentCoroutineContext().ensureActive()
                    block()
                }
            }
        } catch (error: RestoreException) {
            local.update { it.copy(errorMessage = "${error.message ?: "恢复维护中"}；本次没有保存") }
            null
        }
    }

    private suspend fun requestAi(
        binding: AiRequestBinding,
        requiredCategories: Set<ConsentCategory>,
        messages: List<com.mengyalife.app.domain.advisor.AdvisorChatMessage>,
        maxTokens: Int,
        insightReference: InsightReference? = null,
    ): AdvisorChatResult? {
        val response = withAiRequestBoundary(binding, requiredCategories) {
            val stale = insightReference?.let { reference -> insightReferenceError(reference) }
            if (stale != null) {
                local.update { it.copy(errorMessage = stale) }
                AdvisorChatResult.Failure(AdvisorError(AdvisorError.CANCELLED, stale))
            } else {
                binding.engine.chat(binding.runtime, messages, maxTokens)
            }
        } ?: return null
        currentCoroutineContext().ensureActive()
        // Re-acquire after network so queued consent/config/restore writes win before accepting it.
        return withAiRequestBoundary(binding, requiredCategories) {
            currentCoroutineContext().ensureActive()
            val stale = insightReference?.let { reference -> insightReferenceError(reference) }
            if (stale != null) {
                local.update { it.copy(errorMessage = stale) }
                AdvisorChatResult.Failure(AdvisorError(AdvisorError.CANCELLED, stale))
            } else response
        }
    }

    private fun insightDiscussionSystemPrompt(resolved: ResolvedInsightReference): String = buildString {
        appendLine("当前对话由用户主动打开一条成长建议（report=${resolved.reference.reportId}，insight=${resolved.reference.insightId}）。")
        appendLine("只能基于本轮列出的规则事实和原始来源解释建议；清楚区分记录事实与 AI 推测。")
        appendLine("事实缺失时承认信息不足；不得给日记内容下心理/医学诊断，不得把建议写成已经发生的进展。")
        append("用户可以反对或修改前提；不要自动创建、更新目标/计划/进展，正式写入必须等待独立草案确认。")
    }

    private fun insightDiscussionMessage(resolved: ResolvedInsightReference): String =
        "我想聊聊这条建议。你能结合我记录的依据解释一下吗？我也可以质疑或调整前提。"

    private fun send() {
        val text = local.value.input.trim()
        if (text.isEmpty() || local.value.sending) return
        val state = uiState.value
        if (!state.configured) {
            local.update { it.copy(errorMessage = AdvisorError.notConfigured().userMessage) }
            return
        }
        if (!state.consentValid) {
            local.update { it.copy(errorMessage = "请先完成 AI 数据授权，再由顾问使用个人数据。") }
            return
        }
        AdvisorRules.validateInput(text)?.let { error ->
            local.update { it.copy(errorMessage = error) }
            return
        }
        // 原「整理为目标草案」按钮的能力，改为自然语言意图触发（多节点计划草案）。
        startRequest(
            input = text,
            insertUser = true,
            excludeMessageId = null,
            proposalMode = AdvisorRules.looksLikePlanDraftRequest(text),
        )
    }

    private fun grantConsent() {
        val state = uiState.value
        if (!state.configured) {
            local.update { it.copy(errorMessage = "请先配置模型服务，再授权数据使用。") }
            return
        }
        viewModelScope.launch {
            consentRepository.grant(
                recipient = state.config.endpoint,
                categories = AiConsentRules.allCategories,
            )
            local.update { it.copy(infoMessage = "已授权。顾问会按需使用你允许的数据类别。") }
        }
    }

    private fun startRequest(
        input: String,
        insertUser: Boolean,
        excludeMessageId: String?,
        proposalMode: Boolean,
        insightReference: InsightReference? = null,
    ) {
        if (local.value.sending) return
        val state = uiState.value
        if (!state.configured) {
            local.update { it.copy(errorMessage = AdvisorError.notConfigured().userMessage) }
            return
        }
        if (!state.consentValid) {
            local.update { it.copy(errorMessage = "授权已失效，请重新授权后再发送。") }
            return
        }
        val consent = state.consent
        val epoch = consent.epoch
        val expectedRestoreGeneration = restoreGeneration()
        val sendConfig = state.config
        activeEpoch = epoch

        local.update {
            it.copy(
                sending = true,
                errorMessage = null,
                infoMessage = null,
                input = if (insertUser) "" else it.input,
            )
        }

        val launched = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val thisJob = currentCoroutineContext()[Job]
            try {
                if (restoreWriteBarrier == null || restoreGateActive() || !restoreWriteBarrier.isReady() ||
                    restoreGeneration() != expectedRestoreGeneration
                ) {
                    local.update { it.copy(errorMessage = "恢复状态未就绪或已变化，本次没有请求模型") }
                    return@launch
                }
                val freshConsent = consentRepository.current()
                if (freshConsent.epoch != epoch || !freshConsent.isValidFor(sendConfig.endpoint)) {
                    local.update { it.copy(errorMessage = "授权或接收方已变化，本次没有请求模型") }
                    return@launch
                }
                val history = if (freshConsent.enabled(ConsentCategory.CONVERSATION)) {
                    conversations.loadAll().filter { it.id != excludeMessageId }
                } else emptyList()
                val contextHistory = historyForCurrentTopic(history, input, insightReference != null)
                val data = authorizedSnapshot(
                    dataRepository.snapshot(freshConsent), freshConsent, contextHistory,
                )
                val binding = captureAiRequestBinding(
                    expectedConfig = sendConfig,
                    expectedConsent = consent,
                    expectedRestoreGeneration = expectedRestoreGeneration,
                    data = data,
                ) ?: return@launch
                val carriesInsightContext = insightReference != null ||
                    (!isExplicitTopicSwitch(input) && requiresInsightContinuity(input, proposalMode) &&
                        findActiveInsightReference(history) != null)
                val referenceToValidate = insightReference ?: if (carriesInsightContext) {
                    findActiveInsightReference(history)
                } else {
                    null
                }
                val resolvedInsight = referenceToValidate?.let { reference ->
                    val service = insightService
                    if (service == null) {
                        local.update { it.copy(errorMessage = "当前无法验证建议来源，请重新生成洞察") }
                        return@launch
                    }
                    when (val resolution = service.resolveCurrentReference(reference)) {
                        is InsightReferenceResolution.Current -> resolution.value
                        is InsightReferenceResolution.Stale -> {
                            local.update { it.copy(errorMessage = resolution.reason) }
                            return@launch
                        }
                    }
                }
                val consentAfterInsight = consentRepository.current()
                if (resolvedInsight != null &&
                    (consentAfterInsight.epoch != epoch || !consentAfterInsight.isValidFor(sendConfig.endpoint))
                ) {
                    local.update { it.copy(errorMessage = "授权已变化，这条建议不能继续用于讨论") }
                    return@launch
                }
                val requestInput = if (insightReference != null && resolvedInsight != null) {
                    insightDiscussionMessage(resolvedInsight)
                } else {
                    input
                }
                val insightSources = resolvedInsight?.let {
                    advisorSourcesForInsight(it, isHandoff = insightReference != null)
                }.orEmpty()

                // 0.24 自然语言关心偏好：只识别明确表达，保存后直接确认，不交给模型扩写。
                val pref = if (resolvedInsight == null) com.mengyalife.app.domain.care.CarePreferenceParser.parse(
                    text = requestInput,
                    plans = data.plans.map { it.id to it.title },
                    goals = data.goals.map { it.id to it.title },
                    now = System.currentTimeMillis(),
                ) else null
                if (pref != null && careRepository != null && carePreferenceRepository != null) {
                    val handled = withAiRequestBoundary(binding, emptySet()) {
                        if (insertUser) conversations.appendUser(
                            requestInput, null, epoch, expectedRestoreGeneration, sources = insightSources,
                        )
                        if (pref.kind == "FEEDBACK") {
                            val decision = careRepository.latestSentDecision()
                            if (decision == null) {
                                conversations.appendAssistant(
                                    content = "我还不能确定你指的是哪一次关心。你可以在今天页的关心卡上选择，或说得更具体一点。",
                                    contextEpoch = epoch, sources = emptyList(), proposalMode = false,
                                    expectedRestoreGeneration = expectedRestoreGeneration,
                                )
                            } else {
                                val opp = careRepository.find(decision.opportunityId)
                                carePreferenceRepository.save(
                                    kind = "FEEDBACK",
                                    topicKey = opp?.planId?.let { "plan:$it" } ?: opp?.goalId?.let { "goal:$it" },
                                    topicLabel = opp?.title, value = "WELCOME", source = "USER_FEEDBACK",
                                    sourceMessageId = decision.messageId, sourceExcerpt = input,
                                )
                                conversations.appendAssistant(
                                    content = "记下了，这次「${opp?.title ?: "关心"}」对你有用。",
                                    contextEpoch = epoch, sources = emptyList(), proposalMode = false,
                                    expectedRestoreGeneration = expectedRestoreGeneration,
                                )
                            }
                        } else if (pref.kind == "REVOKE") {
                            pref.topicKey?.let { carePreferenceRepository.revokeByTopic(it) }
                            conversations.appendAssistant(
                                content = pref.reply, contextEpoch = epoch, sources = emptyList(),
                                proposalMode = false, expectedRestoreGeneration = expectedRestoreGeneration,
                            )
                        } else if (pref.kind == "CLARIFY") {
                            conversations.appendAssistant(
                                content = pref.reply, contextEpoch = epoch, sources = emptyList(),
                                proposalMode = false, expectedRestoreGeneration = expectedRestoreGeneration,
                            )
                        } else {
                            carePreferenceRepository.save(
                                kind = pref.kind, topicKey = pref.topicKey, topicLabel = pref.topicLabel,
                                value = pref.value, source = "USER_EXPLICIT", sourceMessageId = null,
                                sourceExcerpt = pref.sourceExcerpt, windowNote = pref.windowNote,
                                expiresAt = pref.expiresAt,
                            )
                            conversations.appendAssistant(
                                content = pref.reply, contextEpoch = epoch, sources = emptyList(),
                                proposalMode = false, expectedRestoreGeneration = expectedRestoreGeneration,
                            )
                        }
                        local.update { it.copy(input = "") }
                        true
                    }
                    if (handled != true) return@launch
                    return@launch
                }

                val assembled = assembler.assemble(requestInput, binding.consent, data)
                val insightEntries = resolvedInsight?.let(::advisorContextEntriesForInsight).orEmpty()
                // The selected, still-valid Insight evidence has priority over generic retrieved context.
                val contextEntries = insightEntries + assembled.entries
                val extraPrompt = buildList {
                    if (proposalMode) add(AdvisorRules.proposalSystemPrompt())
                    resolvedInsight?.let { add(insightDiscussionSystemPrompt(it)) }
                }.joinToString("\n\n").takeIf { it.isNotBlank() }

                val builtRequest = AdvisorRules.buildRequest(
                    history = assembled.history,
                    currentInput = requestInput,
                    contextEntries = contextEntries,
                    extraSystemPrompt = extraPrompt,
                )
                if (resolvedInsight != null &&
                    (builtRequest.truncated || insightEntries.any { it !in builtRequest.includedEntries })
                ) {
                    local.update { it.copy(errorMessage = "所选建议的完整依据超出上下文上限，本次没有发送；请缩短对话后重试。") }
                    return@launch
                }
                val requiredCategories = requiredCategoriesForSources(builtRequest.usedSources) +
                    (if (proposalMode) setOf(ConsentCategory.GOALS) else emptySet()) +
                    (if (assembled.history.isNotEmpty()) setOf(ConsentCategory.CONVERSATION) else emptySet())
                var currentUserMessageId: String? = excludeMessageId
                if (insertUser) {
                    val appended = withAiRequestBoundary(binding, requiredCategories) {
                        conversations.appendUser(
                            content = requestInput,
                            contextGoalId = null,
                            contextEpoch = epoch,
                            expectedRestoreGeneration = expectedRestoreGeneration,
                            sources = insightSources,
                        )
                    } ?: return@launch
                    currentUserMessageId = appended.id
                }

                referenceToValidate?.let { reference ->
                    val staleReason = insightReferenceError(reference)
                    if (staleReason != null) {
                        local.update { it.copy(errorMessage = staleReason) }
                        return@launch
                    }
                }
                val result = requestAi(
                    binding = binding,
                    requiredCategories = requiredCategories,
                    messages = builtRequest.messages,
                    maxTokens = if (proposalMode) 3_000 else AdvisorRules.MAX_OUTPUT_TOKENS,
                    insightReference = referenceToValidate,
                ) ?: run {
                    withContext(NonCancellable) {
                        runCatching {
                            conversations.appendFailure(
                                AdvisorError(AdvisorError.CANCELLED, "服务配置、授权或恢复状态已变化，本次回复未保存。"),
                                cancelled = true,
                                contextEpoch = epoch,
                                proposalMode = proposalMode,
                                expectedRestoreGeneration = expectedRestoreGeneration,
                            )
                        }
                    }
                    return@launch
                }
                when (result) {
                    is AdvisorChatResult.Success -> {
                        val usesJournalEvidence = builtRequest.usedSources.any {
                            it.type == AdvisorSourceType.JOURNAL || it.type == AdvisorSourceType.MEMORY ||
                                it.type == AdvisorSourceType.GROWTH
                        }
                        var assistant: AdvisorMessage? = null
                        val accepted = withAiRequestBoundary(binding, requiredCategories) {
                            val staleReason = referenceToValidate?.let { reference -> insightReferenceError(reference) }
                            if (staleReason != null) {
                                local.update { it.copy(errorMessage = staleReason) }
                                false
                            } else if (usesJournalEvidence && AdvisorRules.containsDiagnosticLanguage(result.content)) {
                                val safeReply = "我不能根据日记或成长记忆判断心理/医学诊断。我们可以继续讨论你亲自记录的具体事件、感受和想采取的行动；如担心健康问题，请咨询专业人员。"
                                conversations.appendAssistant(
                                    content = safeReply,
                                    contextEpoch = epoch,
                                    sources = builtRequest.usedSources,
                                    proposalMode = false,
                                    expectedRestoreGeneration = expectedRestoreGeneration,
                                )
                                local.update { it.copy(infoMessage = "已拦截缺乏依据的诊断性表述") }
                                false
                            } else if (proposalMode) {
                                handleProposalResult(
                                    raw = result.content,
                                    epoch = epoch,
                                    sources = builtRequest.usedSources,
                                    existingGoals = data.goals.associate { it.id to it.level },
                                    contextChars = builtRequest.contextChars,
                                    expectedRestoreGeneration = expectedRestoreGeneration,
                                )
                                true
                            } else {
                                assistant = conversations.appendAssistant(
                                    content = AdvisorRules.stripMarkdown(result.content),
                                    contextEpoch = epoch,
                                    sources = builtRequest.usedSources,
                                    proposalMode = false,
                                    expectedRestoreGeneration = expectedRestoreGeneration,
                                )
                                true
                            }
                        } ?: return@launch
                        if (!accepted || proposalMode) return@launch
                        val assistantMessage = assistant ?: return@launch
                        val allMessages = if (binding.consent.enabled(ConsentCategory.CONVERSATION)) {
                            conversations.loadAll()
                        } else emptyList()
                        val topicMessages = messagesForCurrentTopic(allMessages, input)
                        val recentMessages = if (binding.consent.enabled(ConsentCategory.CONVERSATION)) {
                            val cutoff = System.currentTimeMillis() - 15 * 60 * 1000L
                            topicMessages.filter {
                                it.createdAt >= cutoff && it.role == AdvisorRole.USER && it.status == AdvisorMessageStatus.SENT
                            }.takeLast(6)
                        } else {
                            listOfNotNull(currentUserMessageId?.let { id -> allMessages.firstOrNull { it.id == id } })
                        }
                        val recent = if (binding.consent.enabled(ConsentCategory.CONVERSATION)) {
                            recentMessages.map { "用户：" + it.content }
                        } else listOf("用户：$input")
                        val originMessageId = referenceToValidate?.let { reference ->
                            allMessages.firstOrNull { message ->
                                message.role == AdvisorRole.USER &&
                                    insightReferenceIn(message.sources) == reference &&
                                    message.sources.any { it.type == AdvisorSourceType.INSIGHT && it.metadata["handoff"] == "true" }
                            }?.id
                        }
                        val recentIds = (recentMessages.map { it.id } + listOfNotNull(currentUserMessageId, originMessageId)).distinct()
                        val willExtract = DiaryIntentGate.looksRelevant(input) ||
                            GoalIntentGate.looksRelevant(input) ||
                            RecordIntentGate.looksRelevant(input)
                        if (!willExtract &&
                            !local.value.planSuggestionDismissed &&
                            AdvisorRules.shouldSuggestPlanDraft(input)
                        ) {
                            local.update { it.copy(planSuggestion = true) }
                        }
                        if (insightReference == null) {
                            when {
                                DiaryIntentGate.looksRelevant(input) -> maybeExtractDiary(
                                    userText = input,
                                    assistantMessageId = assistantMessage.id,
                                    transcript = recent,
                                    sourceIds = recentIds,
                                    binding = binding,
                                    insightReference = referenceToValidate,
                                )
                                binding.data.plans.any { it.status == "ACTIVE" } && PlanIntentGate.looksLikeProgress(input) ->
                                    maybeExtractPlanProgress(
                                        input, assistantMessage.id, recentIds, binding.data, binding, referenceToValidate,
                                    )
                                binding.data.plans.isNotEmpty() && PlanIntentGate.looksLikeStatusUpdate(input) ->
                                    maybeExtractPlan(
                                        input, assistantMessage.id, recentIds, binding, referenceToValidate,
                                    )
                                PlanIntentGate.looksLikeCreate(input) -> maybeExtractPlan(
                                    input, assistantMessage.id, recentIds, binding, referenceToValidate,
                                )
                                GoalIntentGate.looksRelevant(input) -> maybeExtractGoal(
                                    userText = input,
                                    assistantMessageId = assistantMessage.id,
                                    sourceIds = recentIds,
                                    data = binding.data,
                                    binding = binding,
                                    insightReference = referenceToValidate,
                                )
                                else -> maybeExtractRecord(
                                    userText = input,
                                    assistantMessageId = assistantMessage.id,
                                    data = binding.data,
                                    binding = binding,
                                    insightReference = referenceToValidate,
                                    sourceIds = recentIds,
                                )
                            }
                        }
                    }
                    is AdvisorChatResult.Failure -> {
                        withAiRequestBoundary(binding, requiredCategories) {
                            conversations.appendFailure(
                                error = result.error,
                                cancelled = result.error.code == AdvisorError.CANCELLED,
                                contextEpoch = epoch,
                                proposalMode = proposalMode,
                                expectedRestoreGeneration = expectedRestoreGeneration,
                            )
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    runCatching {
                        conversations.appendFailure(
                            AdvisorError.cancelled(),
                            cancelled = true,
                            contextEpoch = epoch,
                            proposalMode = proposalMode,
                            expectedRestoreGeneration = expectedRestoreGeneration,
                        )
                    }
                }
                local.update { it.copy(infoMessage = "已取消本次请求。") }
                throw cancelled
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    runCatching {
                        conversations.appendFailure(
                            AdvisorError.unknown(),
                            cancelled = false,
                            contextEpoch = epoch,
                            proposalMode = proposalMode,
                            expectedRestoreGeneration = expectedRestoreGeneration,
                        )
                    }
                }
                local.update { it.copy(errorMessage = error.message ?: "请求失败") }
            } finally {
                if (requestJob === thisJob) {
                    requestJob = null
                    activeEpoch = null
                    local.update { it.copy(sending = false) }
                }
            }
        }
        requestJob = launched
        launched.start()
    }

    private suspend fun handleProposalResult(
        raw: String,
        epoch: Long,
        sources: List<com.mengyalife.app.domain.advisor.AdvisorSource>,
        existingGoals: Map<Long, com.mengyalife.app.domain.goals.GoalLevel>,
        contextChars: Int,
        expectedRestoreGeneration: Long,
    ) {
        when (val parsed = GoalProposalParser.parse(raw, existingGoals)) {
            is GoalProposalParseResult.Success -> {
                val message = conversations.appendAssistant(
                    content = "已根据本次讨论整理为目标草案：" + parsed.draft.summary.take(200) +
                        "\n\n（草案，尚未保存；不会修改正式目标。）",
                    contextEpoch = epoch,
                    sources = sources,
                    proposalMode = true,
                    expectedRestoreGeneration = expectedRestoreGeneration,
                )
                proposalRepository.save(message.id, parsed.draft, expectedRestoreGeneration)
            }
            is GoalProposalParseResult.Failure -> {
                conversations.appendAssistant(
                    content = raw.trim().take(4_000),
                    contextEpoch = epoch,
                    sources = sources,
                    proposalError = "草案解析失败：${parsed.message}",
                    proposalMode = true,
                    expectedRestoreGeneration = expectedRestoreGeneration,
                )
            }
        }
    }

    private fun acceptPlanSuggestion() {
        if (local.value.sending) return
        local.update { it.copy(planSuggestion = false, planSuggestionDismissed = true) }
        startRequest(
            input = "请把刚才讨论的计划整理为目标草案（只输出约定 JSON）。",
            insertUser = true,
            excludeMessageId = null,
            proposalMode = true,
        )
    }

    private fun requestProposal() {
        val state = uiState.value
        if (!state.configured) {
            local.update { it.copy(errorMessage = "请先配置模型服务。") }
            return
        }
        if (!state.consentValid) {
            local.update { it.copy(errorMessage = "请先完成 AI 数据授权。") }
            return
        }
        if (local.value.sending) return
        startRequest(
            input = "请把我们的讨论整理为目标草案（只输出约定 JSON）。",
            insertUser = true,
            excludeMessageId = null,
            proposalMode = true,
        )
    }

    private fun updateProposalDraft(
        proposalId: String,
        draft: GoalProposalDraft,
        selected: Set<String>,
    ) {
        viewModelScope.launch {
            proposalRepository.updateDraft(proposalId, draft, selected)
        }
    }

    private fun applyProposal(
        proposalId: String,
        draft: GoalProposalDraft,
        selected: Set<String>,
    ) {
        // 同步防抖，不能只依赖 StateFlow 的下一帧
        if (applying) return
        applying = true
        val current = uiState.value.applyState
        if (current?.loading == true) {
            applying = false
            return
        }
        local.update {
            it.copy(
                applyState = ProposalApplyUiState(proposalId = proposalId, loading = true),
            )
        }
        val expectedGeneration = restoreGeneration()
        viewModelScope.launch {
            try {
                val barrier = restoreWriteBarrier
                if (barrier == null) {
                    local.update { it.copy(applyState = ProposalApplyUiState(proposalId, error = "恢复安全边界不可用，目标未修改")) }
                    return@launch
                }
                barrier.withWrite(expectedGeneration) {
                    if (restoreGateActive()) {
                        local.update { it.copy(applyState = ProposalApplyUiState(proposalId, error = "恢复维护中，目标未修改")) }
                        return@withWrite
                    }
                    val stored = proposalRepository.findById(proposalId)
                    if (stored == null || !stored.isDraft) {
                        local.update { it.copy(applyState = ProposalApplyUiState(proposalId, error = "草案已处理或不存在")) }
                        return@withWrite
                    }
                    val proposalMessage = conversations.loadAll().firstOrNull { it.id == stored.messageId }
                    val hasInsightMarker = proposalMessage?.sources?.any {
                        it.type == AdvisorSourceType.INSIGHT || it.type == AdvisorSourceType.INSIGHT_FACT
                    } == true
                    val insightReference = proposalMessage?.let { insightReferenceIn(it.sources) }
                    if (hasInsightMarker && insightReference == null) {
                        local.update { it.copy(applyState = ProposalApplyUiState(proposalId, error = "洞察来源链不完整，目标未修改")) }
                        return@withWrite
                    }
                    if (insightReference != null) {
                        val staleReason = insightReferenceError(insightReference)
                        if (staleReason != null) {
                            local.update { it.copy(applyState = ProposalApplyUiState(proposalId, error = "$staleReason；目标未修改")) }
                            return@withWrite
                        }
                    }
                    if (!proposalRepository.updateDraft(proposalId, draft, selected)) {
                        local.update { it.copy(applyState = ProposalApplyUiState(proposalId, error = "草案已处理或状态已变化，请重新打开")) }
                        return@withWrite
                    }
                    when (val result = proposalApplyService.applyValidated(proposalId, selected) {
                        insightReference?.let { reference -> insightReferenceError(reference) }
                    }) {
                        is com.mengyalife.app.data.advisor.ProposalApplyResult.Success -> local.update {
                            it.copy(applyState = ProposalApplyUiState(
                                proposalId = proposalId,
                                message = "已创建 ${result.created.size} 个目标",
                                createdTitles = result.created.map { goal -> goal.title },
                                canUndo = true,
                            ))
                        }
                        is com.mengyalife.app.data.advisor.ProposalApplyResult.Failure -> local.update {
                            it.copy(applyState = ProposalApplyUiState(proposalId = proposalId, error = result.message))
                        }
                    }
                }
            } catch (error: RestoreException) {
                local.update { it.copy(applyState = ProposalApplyUiState(proposalId, error = "${error.message}；目标未修改")) }
            } finally {
                applying = false
            }
        }
    }

    private fun undoProposal(proposalId: String) {
        if (applying) return
        applying = true
        if (uiState.value.applyState?.loading == true) {
            applying = false
            return
        }
        local.update {
            it.copy(
                applyState = ProposalApplyUiState(proposalId = proposalId, loading = true),
            )
        }
        viewModelScope.launch {
            try {
            when (val result = proposalApplyService.undo(proposalId)) {
                is com.mengyalife.app.data.advisor.ProposalUndoResult.Success -> local.update {
                    it.copy(
                        applyState = ProposalApplyUiState(
                            proposalId = proposalId,
                            message = if (result.alreadyUndone) "本次创建已撤销（无需重复操作）" else "已撤销本次创建",
                            undone = true,
                        ),
                    )
                }
                is com.mengyalife.app.data.advisor.ProposalUndoResult.Failure -> local.update {
                    it.copy(
                        applyState = ProposalApplyUiState(
                            proposalId = proposalId,
                            error = result.message,
                        ),
                    )
                }
            }
            } finally {
                applying = false
            }
        }
    }

    /**
     * 记录候选提取：识别「记录意图 + 目标 + 增量/累计 + 数值/单位 + 时间」。
     *
     * 只在已授权（目标 + 进展）时读取目标摘要；失败/无 JSON 只是不出现卡片，
     * 不影响已经成功的聊天回复。明确指令且字段完整时才直接写入。
     */
    private suspend fun maybeExtractRecord(
        userText: String,
        assistantMessageId: String,
        data: AdvisorContextSnapshot,
        binding: AiRequestBinding,
        insightReference: InsightReference?,
        sourceIds: List<String>,
    ) {
        if (!RecordIntentGate.looksRelevant(userText)) return
        val required = setOf(ConsentCategory.GOALS, ConsentCategory.PROGRESS)
        if (!binding.consent.enabled(ConsentCategory.PROGRESS)) {
            local.update { it.copy(infoMessage = "未授权「进展」类别，本次没有生成记录候选。") }
            return
        }
        if (!binding.consent.enabled(ConsentCategory.GOALS)) {
            local.update { it.copy(infoMessage = "未授权「目标」类别，本次没有生成记录候选。") }
            return
        }
        val goals = data.goals
        if (goals.isEmpty()) return
        val goalLines = goals.take(20).map { goal ->
            "[目标#${goal.id}] ${goal.level.label}：${goal.title}（${goal.status.label}）"
        }
        val checkinLines = data.checkins.sortedByDescending { it.recordedAt }.take(6).map { checkin ->
            val amount = checkin.metricValue?.let { value ->
                " " + com.mengyalife.app.util.MetricFormat.withUnit(value, checkin.metricUnit)
            }.orEmpty()
            "${com.mengyalife.app.util.Timestamps.formatDate(checkin.recordedAt)} ${checkin.note.take(60)}$amount"
        }
        val prompt = AdvisorRules.recordExtractionPrompt(
            goalLines = goalLines,
            recentCheckinLines = checkinLines,
            today = java.time.LocalDate.now().toString(),
        )
        local.update { it.copy(infoMessage = "正在整理记录…") }
        val result = try {
            requestAi(
                binding = binding,
                requiredCategories = required,
                messages = listOf(
                    com.mengyalife.app.domain.advisor.AdvisorChatMessage(role = "system", content = prompt),
                    com.mengyalife.app.domain.advisor.AdvisorChatMessage(role = "user", content = userText),
                ),
                maxTokens = 600,
                insightReference = insightReference,
            )
        } finally {
            local.update { state -> state.copy(infoMessage = state.infoMessage?.takeIf { it != "正在整理记录…" }) }
        } ?: return
        if (result !is AdvisorChatResult.Success) {
            local.update { it.copy(infoMessage = "记录整理失败；没有创建草案或应用记录。") }
            return
        }
        withAiRequestBoundary(binding, required) {
            val stale = insightReference?.let { reference -> insightReferenceError(reference) }
            if (stale != null) {
                local.update { it.copy(infoMessage = "$stale；没有创建记录草案") }
                return@withAiRequestBoundary
            }
            val allowedIds = data.goals.map { it.id }.toSet()
            val parsedDraft = when (val parsed = RecordProposalParser.parse(result.content, userText, allowedGoalIds = allowedIds)) {
                is RecordProposalParseResult.Success -> parsed.draft
                is RecordProposalParseResult.Failure -> {
                    local.update { it.copy(infoMessage = "没有整理出可写入的记录；没有创建草案。") }
                    null
                }
            } ?: return@withAiRequestBoundary
            val draft = parsedDraft.copy(
                sourceMessageIds = sourceIds,
                sourceLinkKnown = true,
                sourceInsightReference = insightReference,
            )
            val stored = recordRepository.save(assistantMessageId, draft, binding.restoreGeneration)
            val autoApply = RecordProposalRules.shouldAutoApply(draft, binding.consent.enabled(ConsentCategory.PROGRESS))
            if (!autoApply) {
                local.update {
                    it.copy(recordAction = RecordActionUiState(
                        proposalId = stored.id,
                        message = if (draft.pendingFields.isNotEmpty()) "需要你确认记录信息" else "建议记录一条进展，确认后才会写入",
                    ))
                }
                return@withAiRequestBoundary
            }
            when (val applied = recordApplyService.apply(stored.id, draft)) {
                is RecordApplyResult.Success -> local.update {
                    it.copy(recordAction = RecordActionUiState(
                        proposalId = stored.id,
                        message = "已记录到「${applied.goalTitle}」，可修改或撤销",
                    ))
                }
                is RecordApplyResult.Failure -> local.update {
                    it.copy(recordAction = RecordActionUiState(proposalId = stored.id, error = applied.message))
                }
            }
        }
    }

    /**
     * 日记草稿提取：把最近相关对话整理成第一人称日记。
     * 只有 JOURNAL 授权才发送；草稿永远需要用户确认，不会自动写入。
     */
    private suspend fun maybeExtractDiary(
        userText: String,
        assistantMessageId: String,
        transcript: List<String>,
        sourceIds: List<String>,
        binding: AiRequestBinding,
        insightReference: InsightReference?,
    ) {
        if (!binding.consent.enabled(ConsentCategory.JOURNAL)) {
            local.update { it.copy(infoMessage = "未授权「日记」类别，本次没有整理日记。") }
            return
        }
        val required = setOf(ConsentCategory.JOURNAL) +
            if (transcript.size > 1) setOf(ConsentCategory.CONVERSATION) else emptySet()
        local.update { it.copy(infoMessage = "正在整理日记…") }
        val prompt = AdvisorRules.diaryExtractionPrompt(
            transcriptLines = transcript,
            today = java.time.LocalDate.now().toString(),
        )
        val result = try {
            requestAi(
                binding = binding,
                requiredCategories = required,
                messages = listOf(
                    com.mengyalife.app.domain.advisor.AdvisorChatMessage(role = "system", content = prompt),
                    com.mengyalife.app.domain.advisor.AdvisorChatMessage(role = "user", content = transcript.joinToString("\n")),
                ),
                maxTokens = 900,
                insightReference = insightReference,
            )
        } finally {
            local.update { state -> state.copy(infoMessage = state.infoMessage?.takeIf { it != "正在整理日记…" }) }
        } ?: return
        if (result !is AdvisorChatResult.Success) {
            local.update { it.copy(infoMessage = "日记整理失败；没有创建草稿。") }
            return
        }
        withAiRequestBoundary(binding, required) {
            val stale = insightReference?.let { reference -> insightReferenceError(reference) }
            if (stale != null) {
                local.update { it.copy(infoMessage = "$stale；没有创建日记草稿") }
                return@withAiRequestBoundary
            }
            val parsedDraft = when (val parsed = RecordProposalParser.parse(result.content, userText)) {
                is RecordProposalParseResult.Success -> parsed.draft
                is RecordProposalParseResult.Failure -> {
                    local.update { it.copy(infoMessage = "没有整理出日记草稿；没有创建候选。") }
                    null
                }
            } ?: return@withAiRequestBoundary
            if (!parsedDraft.isDiary || parsedDraft.diaryBody.isNullOrBlank()) return@withAiRequestBoundary
            val draft = parsedDraft.copy(
                sourceMessageIds = sourceIds,
                sourceLinkKnown = true,
                sourceInsightReference = insightReference,
            )
            val stored = recordRepository.save(assistantMessageId, draft, binding.restoreGeneration)
            local.update {
                it.copy(recordAction = RecordActionUiState(
                    proposalId = stored.id,
                    message = if (draft.intent == com.mengyalife.app.domain.advisor.RecordIntent.EXPLICIT) {
                        "日记草稿已生成，确认后保存到日记"
                    } else "这段经历可以整理成日记，确认后才会保存",
                ))
            }
        }
    }

    /**
     * 目标候选：创建 / 修改已有 / 拆解。
     * 只生成草稿，正式写入必须由用户确认；修改走 updateGoal，不删除重建。
     */
    private suspend fun maybeExtractPlanProgress(
        userText: String,
        assistantMessageId: String,
        sourceIds: List<String>,
        data: AdvisorContextSnapshot,
        binding: AiRequestBinding,
        insightReference: InsightReference?,
    ) {
        withAiRequestBoundary(binding, setOf(ConsentCategory.GOALS)) {
            val stale = insightReference?.let { reference -> insightReferenceError(reference) }
            if (stale != null) {
                local.update { it.copy(infoMessage = "$stale；没有创建执行草案") }
                return@withAiRequestBoundary
            }
            val active = data.plans.filter { it.status == "ACTIVE" }
            if (active.isEmpty()) return@withAiRequestBoundary
            val chosen = if (active.size == 1) active.single() else active.firstOrNull { plan ->
                val keyword = plan.title.replace(Regex("[\\s\\p{Punct}，。]"), "").take(6)
                keyword.isNotEmpty() && userText.contains(keyword.take(2))
            }
            if (chosen == null) {
                local.update { it.copy(infoMessage = "有多个进行中的计划，请说清是哪一个。") }
                return@withAiRequestBoundary
            }
            val draft = RecordProposalDraft(
                kind = RecordKind.PLAN_PROGRESS,
                goalId = null, goalTitle = null, goalCandidates = emptyList(),
                amountKind = RecordAmountKind.NONE, metricValue = null, metricUnit = null,
                note = userText.trim(), occurredAt = null, pendingFields = emptyList(),
                intent = RecordIntent.EXPLICIT, originalText = userText.trim(),
                planProgress = com.mengyalife.app.domain.advisor.PlanProgressDraft(
                    planId = chosen.id, planTitle = chosen.title, note = userText.trim(),
                ),
                sourceMessageIds = sourceIds,
                sourceLinkKnown = true,
                sourceInsightReference = insightReference,
            )
            val stored = recordRepository.save(assistantMessageId, draft, binding.restoreGeneration)
            local.update {
                it.copy(recordAction = RecordActionUiState(
                    proposalId = stored.id,
                    message = "记下这次执行？确认后计入「${chosen.title}」",
                ))
            }
        }
    }

    private suspend fun maybeExtractPlan(
        userText: String,
        assistantMessageId: String,
        sourceIds: List<String>,
        binding: AiRequestBinding,
        insightReference: InsightReference? = null,
    ) {
        val required = setOf(ConsentCategory.GOALS)
        if (!binding.consent.enabled(ConsentCategory.GOALS)) {
            local.update { it.copy(infoMessage = "未授权「目标」类别，本次没有生成小计划草稿。") }
            return
        }
        val planLines = binding.data.plans.take(10).map {
            "[计划#${it.id}] ${it.title}（${it.status}${it.timeScope?.let { scope -> "，$scope" } ?: ""}）"
        }
        val prompt = AdvisorRules.planExtractionPrompt(
            planLines = planLines,
            goalLines = binding.data.goals.take(12).map { "[目标#${it.id}] ${it.title}" },
            today = java.time.LocalDate.now().toString(),
        )
        local.update { it.copy(infoMessage = "正在整理小计划…") }
        val result = try {
            requestAi(
                binding = binding,
                requiredCategories = required,
                messages = listOf(
                    com.mengyalife.app.domain.advisor.AdvisorChatMessage(role = "system", content = prompt),
                    com.mengyalife.app.domain.advisor.AdvisorChatMessage(role = "user", content = userText),
                ),
                maxTokens = 700,
                insightReference = insightReference,
            )
        } finally {
            local.update { state -> state.copy(infoMessage = state.infoMessage?.takeIf { it != "正在整理小计划…" }) }
        } ?: return
        if (result !is AdvisorChatResult.Success) {
            local.update { it.copy(infoMessage = "小计划提取失败；未创建草案。") }
            return
        }
        withAiRequestBoundary(binding, required) {
            val stale = insightReference?.let { reference -> insightReferenceError(reference) }
            if (stale != null) {
                local.update { it.copy(infoMessage = "$stale；未创建计划草案") }
                return@withAiRequestBoundary
            }
            val parsedDraft = when (val parsed = RecordProposalParser.parse(result.content, userText)) {
                is RecordProposalParseResult.Success -> parsed.draft
                is RecordProposalParseResult.Failure -> null
            }
            if (parsedDraft == null || !parsedDraft.isPlan) {
                local.update { it.copy(infoMessage = "这次没有整理出计划草案；没有创建候选。") }
                return@withAiRequestBoundary
            }
            val planChange = parsedDraft.planChange
            val enriched = if (planChange?.change == PlanChangeKind.STATUS) {
                val target = binding.data.plans.firstOrNull { it.id == planChange.targetPlanId }
                parsedDraft.copy(planChange = planChange.copy(title = target?.title ?: planChange.title))
            } else parsedDraft
            val draft = enriched.copy(
                sourceMessageIds = sourceIds,
                sourceLinkKnown = true,
                sourceInsightReference = insightReference,
            )
            val stored = recordRepository.save(assistantMessageId, draft, binding.restoreGeneration)
            local.update {
                it.copy(recordAction = RecordActionUiState(
                    proposalId = stored.id,
                    message = if (planChange?.change == PlanChangeKind.STATUS) {
                        "计划状态变更草稿，确认后才会更新"
                    } else "要记下这件事吗？确认后才会保存",
                ))
            }
        }
    }

    private suspend fun maybeExtractGoal(
        userText: String,
        assistantMessageId: String,
        sourceIds: List<String>,
        data: AdvisorContextSnapshot,
        binding: AiRequestBinding,
        insightReference: InsightReference? = null,
    ) {
        val required = setOf(ConsentCategory.GOALS)
        if (!binding.consent.enabled(ConsentCategory.GOALS)) {
            local.update { it.copy(infoMessage = "未授权「目标」类别，本次没有生成目标草稿。") }
            return
        }
        val goals = data.goals
        val goalLines = goals.take(20).map { goal ->
            "[目标#${goal.id}] ${goal.level.label}｜${goal.title}｜领域 ${goal.domain.label}｜" +
                (goal.dueDate?.let { "截止 " + com.mengyalife.app.util.Timestamps.formatDateUtc(it) } ?: "无截止")
        }
        val prompt = AdvisorRules.goalExtractionPrompt(
            goalLines = goalLines,
            today = java.time.LocalDate.now().toString(),
            dismissedBreakdownGoalIds = dismissedBreakdownGoalIds.toList(),
        )
        local.update { it.copy(infoMessage = "正在整理目标…") }
        val result = try {
            requestAi(
                binding = binding,
                requiredCategories = required,
                messages = listOf(
                    com.mengyalife.app.domain.advisor.AdvisorChatMessage(role = "system", content = prompt),
                    com.mengyalife.app.domain.advisor.AdvisorChatMessage(role = "user", content = userText),
                ),
                maxTokens = 800,
                insightReference = insightReference,
            )
        } finally {
            local.update { state -> state.copy(infoMessage = state.infoMessage?.takeIf { it != "正在整理目标…" }) }
        } ?: return
        if (result !is AdvisorChatResult.Success) {
            local.update { it.copy(infoMessage = "目标提取失败；没有创建草案。") }
            return
        }
        withAiRequestBoundary(binding, required) {
            val stale = insightReference?.let { reference -> insightReferenceError(reference) }
            if (stale != null) {
                local.update { it.copy(infoMessage = "$stale；目标草稿未保存") }
                return@withAiRequestBoundary
            }
            val draft = when (val parsed = RecordProposalParser.parse(
                raw = result.content,
                originalText = userText,
                allowedGoalIds = goals.map { it.id }.toSet(),
            )) {
                is RecordProposalParseResult.Success -> parsed.draft
                is RecordProposalParseResult.Failure -> {
                    local.update { it.copy(infoMessage = "没有整理出目标草稿；没有创建候选。") }
                    null
                }
            } ?: return@withAiRequestBoundary
            if (!draft.isGoal) return@withAiRequestBoundary
            val change = draft.goalChange ?: return@withAiRequestBoundary
            if (change.change == GoalChangeKind.CREATE && goals.any { it.title.trim() == change.title?.trim() }) {
                local.update { it.copy(infoMessage = "已经有同名目标，没有重复创建；可以直接说想怎么调整它。") }
                return@withAiRequestBoundary
            }
            val similar = if (change.change == GoalChangeKind.CREATE) goals.firstOrNull { existing ->
                val a = existing.title.replace(Regex("[\\s\\p{Punct}，。]"), "")
                val b = change.title.orEmpty().replace(Regex("[\\s\\p{Punct}，。]"), "")
                a.length >= 4 && b.length >= 4 && (a.contains(b) || b.contains(a)) && a != b
            } else null
            val target = goals.firstOrNull { it.id == change.targetGoalId }
            if (change.change != GoalChangeKind.CREATE && target == null) {
                local.update { it.copy(infoMessage = "没找到要修改的目标，请说出它的完整名称再试。") }
                return@withAiRequestBoundary
            }
            val enriched = draft.copy(
                goalChange = change.copy(
                    uncertain = change.uncertain + listOfNotNull(similar?.let { "与已有目标「${it.title}」相近，请确认是否同一个" }),
                    before = target?.let {
                        GoalBeforeSnapshot(it.id, it.title, it.level.name, it.domain.name, it.dueDate, it.parentId)
                    },
                ),
                sourceMessageIds = sourceIds,
                sourceLinkKnown = true,
                sourceInsightReference = insightReference,
            )
            val stored = recordRepository.save(assistantMessageId, enriched, binding.restoreGeneration)
            val message = when (change.change) {
                GoalChangeKind.CREATE -> "目标草稿已生成，确认后写入目标树"
                GoalChangeKind.UPDATE -> "目标变更草稿已生成，确认后才会修改"
                GoalChangeKind.BREAKDOWN -> "拆解建议已生成，选择后才会创建"
            }
            local.update { it.copy(recordAction = RecordActionUiState(proposalId = stored.id, message = message)) }
        }
    }

    private fun openGoal(proposalId: String) {
        val stored = uiState.value.records.values.firstOrNull { it.id == proposalId } ?: return
        val goalId = stored.appliedGoalIds.firstOrNull() ?: stored.draft.goalChange?.targetGoalId ?: return
        local.update {
            it.copy(recordAction = RecordActionUiState(proposalId = proposalId, openGoalId = goalId))
        }
    }

    private fun openDiary(proposalId: String) {
        val stored = uiState.value.records.values.firstOrNull { it.id == proposalId } ?: return
        val entryId = stored.diaryEntryId ?: return
        local.update {
            it.copy(recordAction = RecordActionUiState(proposalId = proposalId, openDiaryEntryId = entryId))
        }
    }

    private fun confirmRecord(proposalId: String, draft: RecordProposalDraft) {
        if (recording || applying) return
        if (uiState.value.records.values.none { it.id == proposalId }) return
        recording = true
        local.update { it.copy(recordAction = RecordActionUiState(proposalId = proposalId, loading = true)) }
        val expectedGeneration = restoreGeneration()
        viewModelScope.launch {
            try {
                val barrier = restoreWriteBarrier
                if (barrier == null) {
                    local.update { it.copy(recordAction = RecordActionUiState(proposalId, error = "恢复安全边界不可用，草案未保存")) }
                    return@launch
                }
                barrier.withWrite(expectedGeneration) {
                    if (restoreGateActive()) {
                        local.update { it.copy(recordAction = RecordActionUiState(proposalId, error = "恢复维护中，草案未保存")) }
                        return@withWrite
                    }
                    val stored = recordRepository.findById(proposalId)
                    if (stored == null) {
                        local.update { it.copy(recordAction = RecordActionUiState(proposalId, error = "草案不存在，未写入")) }
                        return@withWrite
                    }
                    val explicitNoLinkHasInsightMarker = stored.draft.sourceLinkKnown &&
                        stored.draft.sourceInsightReference == null &&
                        findLegacyInsightLink(stored.draft.sourceMessageIds, stored.messageId).hasInsightMarker
                    val link = when {
                        stored.draft.sourceLinkInvalid -> LegacyInsightLink(true, null)
                        stored.draft.sourceLinkKnown -> LegacyInsightLink(
                            hasInsightMarker = stored.draft.sourceInsightReference != null,
                            reference = stored.draft.sourceInsightReference,
                        )
                        else -> findLegacyInsightLink(stored.draft.sourceMessageIds, stored.messageId)
                    }
                    if (stored.draft.sourceLinkInvalid || explicitNoLinkHasInsightMarker ||
                        (link.hasInsightMarker && link.reference == null)
                    ) {
                        local.update { it.copy(recordAction = RecordActionUiState(
                            proposalId = proposalId, error = "洞察来源链无法验证，草案未保存",
                        )) }
                        return@withWrite
                    }
                    link.reference?.let { reference ->
                        val stale = insightReferenceError(reference)
                        if (stale != null) {
                            local.update { it.copy(recordAction = RecordActionUiState(
                                proposalId = proposalId, error = "$stale；草案未保存",
                            )) }
                            return@withWrite
                        }
                    }
                    val durableDraft = draft.copy(
                        sourceMessageIds = stored.draft.sourceMessageIds,
                        sourceLinkKnown = stored.draft.sourceLinkKnown,
                        sourceLinkInvalid = stored.draft.sourceLinkInvalid,
                        sourceInsightReference = stored.draft.sourceInsightReference,
                    )
                    confirmStoredRecord(stored, durableDraft, link.reference, expectedGeneration)
                }
            } catch (error: RestoreException) {
                local.update { it.copy(recordAction = RecordActionUiState(
                    proposalId = proposalId, error = "${error.message ?: "恢复维护中"}；草案未保存",
                )) }
            } finally {
                recording = false
            }
        }
    }

    private suspend fun confirmStoredRecord(
        stored: StoredRecordProposal,
        draft: RecordProposalDraft,
        insightReference: InsightReference?,
        expectedRestoreGeneration: Long,
    ) {
        val proposalId = stored.id
        if (stored.draft.isPlanProgress) {
            val out = planApplyService.applyProgressValidated(proposalId, expectedRestoreGeneration) {
                insightReference?.let { insightReferenceError(it) }
            }
            local.update { it.copy(recordAction = when (out) {
                is PlanApplyResult.Success -> RecordActionUiState(proposalId, message = "记下了这次执行，计划仍保持原状态")
                is PlanApplyResult.Failure -> RecordActionUiState(proposalId, error = out.message)
            }) }
            return
        }
        if (stored.draft.isPlan) {
            val out = if (stored.isDraft && !recordRepository.updateDraft(proposalId, draft)) {
                PlanApplyResult.Failure("候选已被处理或状态已变化，请重新打开")
            } else planApplyService.applyValidated(proposalId, expectedRestoreGeneration) {
                insightReference?.let { insightReferenceError(it) }
            }
            local.update { it.copy(recordAction = when (out) {
                is PlanApplyResult.Success -> RecordActionUiState(proposalId, message = "已记下这件事，之后可以接着说进展")
                is PlanApplyResult.Failure -> RecordActionUiState(proposalId, error = out.message)
            }) }
            return
        }
        if (stored.draft.isGoal) {
            val out = goalApplyService.apply(proposalId)
            local.update { it.copy(recordAction = when (out) {
                is GoalApplyResult.Success -> RecordActionUiState(
                    proposalId, message = "已保存到目标，可查看或继续调整", openGoalId = out.goalIds.firstOrNull(),
                )
                is GoalApplyResult.Failure -> RecordActionUiState(proposalId, error = out.message)
            }) }
            return
        }
        if (stored.draft.isDiary) {
            val out = if (stored.isApplied) {
                diaryApplyService.editApplied(proposalId, draft)
            } else if (!recordRepository.updateDraft(proposalId, draft)) {
                DiaryApplyResult.Failure("候选已被处理或状态已变化，请重新打开")
            } else diaryApplyService.apply(proposalId)
            local.update { it.copy(recordAction = when (out) {
                is DiaryApplyResult.Success -> RecordActionUiState(
                    proposalId,
                    message = if (stored.isApplied) "已更新这篇日记" else "已保存到日记，可在日记页查看",
                    openDiaryEntryId = out.entryId,
                )
                is DiaryApplyResult.Failure -> RecordActionUiState(proposalId, error = out.message)
            }) }
            return
        }
        val outcome = if (stored.isApplied) {
            recordApplyService.editApplied(proposalId, draft)
        } else if (!recordRepository.updateDraft(proposalId, draft)) {
            RecordApplyResult.Failure("候选已被处理或状态已变化，请重新打开")
        } else recordApplyService.apply(proposalId, draft)
        local.update { it.copy(recordAction = when (outcome) {
            is RecordApplyResult.Success -> RecordActionUiState(
                proposalId,
                message = if (stored.isApplied) "已更新这条进展记录" else "已记录到「${outcome.goalTitle}」，可修改或撤销",
            )
            is RecordApplyResult.Failure -> RecordActionUiState(proposalId, error = outcome.message)
        }) }
    }

    private fun updateRecordDraft(proposalId: String, draft: RecordProposalDraft) {
        viewModelScope.launch {
            recordRepository.updateDraft(proposalId, draft)
        }
    }

    private fun cancelRecord(proposalId: String) {
        viewModelScope.launch {
            val stored = uiState.value.records.values.firstOrNull { it.id == proposalId }
            val cancelled = recordRepository.cancel(proposalId)
            if (cancelled && stored?.draft?.isGoal == true) {
                val change = stored.draft.goalChange
                if (change?.change == GoalChangeKind.BREAKDOWN) {
                    change.targetGoalId?.let { dismissedBreakdownGoalIds += it }
                }
            }
            val noWrite = if (stored?.draft?.isDiary == true) "已取消，没有写入日记" else "已取消，没有写入任何进展"
            local.update {
                it.copy(
                    recordAction = RecordActionUiState(
                        proposalId = proposalId,
                        message = if (cancelled) noWrite else "候选已处理，无需再次取消",
                    ),
                )
            }
        }
    }

    private fun undoRecord(proposalId: String) {
        if (recording) return
        val stored = uiState.value.records.values.firstOrNull { it.id == proposalId }
        if (stored?.draft?.isDiary == true) {
            local.update {
                it.copy(
                    recordAction = RecordActionUiState(
                        proposalId = proposalId,
                        message = "日记请到日记页修改或删除",
                    ),
                )
            }
            return
        }
        recording = true
        local.update { it.copy(recordAction = RecordActionUiState(proposalId = proposalId, loading = true)) }
        viewModelScope.launch {
            try {
                when (val result = recordApplyService.undo(proposalId)) {
                    is RecordUndoResult.Success -> local.update {
                        it.copy(
                            recordAction = RecordActionUiState(
                                proposalId = proposalId,
                                message = if (result.alreadyUndone) {
                                    "这条记录已撤销（无需重复操作）"
                                } else {
                                    "已撤销这条进展记录"
                                },
                            ),
                        )
                    }
                    is RecordUndoResult.Failure -> local.update {
                        it.copy(recordAction = RecordActionUiState(proposalId = proposalId, error = result.message))
                    }
                }
            } finally {
                recording = false
            }
        }
    }

    private fun retry(messageId: String) {
        val state = uiState.value
        if (local.value.sending) return
        val failed = state.messages.firstOrNull { it.id == messageId } ?: return
        if (failed.role != AdvisorRole.ASSISTANT) return
        // 草案解析失败保存为 SENT + proposalError（保留原文），也允许重试；
        // 普通成功消息不能重试。
        if (failed.status == AdvisorMessageStatus.SENT && failed.proposalError == null) return
        val userMessage = state.messages
            .takeWhile { it.id != failed.id }
            .lastOrNull { it.role == AdvisorRole.USER && it.status == AdvisorMessageStatus.SENT }
            ?: run {
                local.update { it.copy(errorMessage = "找不到对应的用户问题，无法重试。") }
                return
            }
        // 不删除失败消息：保留原始错误证据；重试只追加新的助手消息，
        // 且草案失败必须继续用 proposalMode，不能退化成普通聊天。
        val insightReference = userMessage.sources
            .firstOrNull { it.type == AdvisorSourceType.INSIGHT && it.metadata["handoff"] == "true" }
            ?.let { insightReferenceIn(listOf(it)) }
        startRequest(
            input = userMessage.content,
            insertUser = false,
            excludeMessageId = userMessage.id,
            proposalMode = failed.proposalMode,
            insightReference = insightReference,
        )
        local.update {
            it.copy(
                infoMessage = if (failed.proposalMode) {
                    "正在重试目标草案；不会重复用户消息或重复创建草案。"
                } else {
                    "正在重试；不会重复发送你的上一条问题。"
                },
            )
        }
    }

    private fun cancelRequest() {
        val job = requestJob ?: return
        local.update { it.copy(infoMessage = "正在取消…") }
        job.cancel()
    }

    private fun engineFor(config: com.mengyalife.app.domain.advisor.AiConfigState): AdvisorEngine =
        if (config.useFakeEngine && fakeEngine != null) fakeEngine else networkEngine

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MengyaApplication
                AdvisorViewModel(
                    conversations = app.container.conversationRepository,
                    configRepository = app.container.advisorConfigRepository,
                    consentRepository = app.container.aiConsentRepository,
                    dataRepository = app.container.advisorDataRepository,
                    proposalRepository = app.container.goalProposalRepository,
                    proposalApplyService = app.container.goalProposalApplyService,
                    recordRepository = app.container.recordProposalRepository,
                    recordApplyService = app.container.progressRecordApplyService,
                    diaryApplyService = app.container.diaryRecordApplyService,
                    goalApplyService = app.container.goalRecordApplyService,
                    planApplyService = app.container.planRecordApplyService,
                    careRepository = app.container.careRepository,
                    carePreferenceRepository = app.container.carePreferenceRepository,
                    assembler = AdvisorContextAssembler(),
                    networkEngine = app.container.networkAdvisorEngine,
                    fakeEngine = app.container.fakeAdvisorEngine,
                    restoreGeneration = { app.container.restoreWriteBarrier.generation() },
                    restoreWriteBarrier = app.container.restoreWriteBarrier,
                    restoreGateActive = { app.container.restoreCoordinator.isGateActive() },
                    insightService = app.container.insightService,
                )
            }
        }
    }
}
