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
import com.mengyalife.app.domain.advisor.AdvisorChatResult
import com.mengyalife.app.domain.advisor.AdvisorContextAssembler
import com.mengyalife.app.domain.advisor.AdvisorContextSnapshot
import com.mengyalife.app.domain.advisor.AdvisorError
import com.mengyalife.app.domain.advisor.AdvisorMessage
import com.mengyalife.app.domain.advisor.AdvisorMessageStatus
import com.mengyalife.app.domain.advisor.AdvisorRole
import com.mengyalife.app.domain.advisor.AdvisorRules
import com.mengyalife.app.domain.advisor.AiConsentRules
import com.mengyalife.app.domain.advisor.AiConsentState
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.advisor.GoalProposalParseResult
import com.mengyalife.app.domain.advisor.GoalProposalDraft
import com.mengyalife.app.domain.advisor.GoalProposalParser
import com.mengyalife.app.domain.advisor.DiaryIntentGate
import com.mengyalife.app.domain.advisor.GoalBeforeSnapshot
import com.mengyalife.app.domain.advisor.GoalChangeKind
import com.mengyalife.app.domain.advisor.GoalIntentGate
import com.mengyalife.app.domain.advisor.PlanChangeKind
import com.mengyalife.app.domain.advisor.PlanDraftFactory
import com.mengyalife.app.domain.advisor.PlanIntentGate
import com.mengyalife.app.domain.advisor.RecordAmountKind
import com.mengyalife.app.domain.advisor.RecordIntent
import com.mengyalife.app.domain.advisor.RecordIntentGate
import com.mengyalife.app.domain.advisor.RecordKind
import com.mengyalife.app.domain.advisor.RecordProposalDraft
import com.mengyalife.app.domain.advisor.RecordProposalParseResult
import com.mengyalife.app.domain.advisor.RecordProposalParser
import com.mengyalife.app.domain.advisor.RecordProposalRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    private val restoreWriteBarrier: com.mengyalife.app.data.restore.RestoreWriteBarrier? = null,
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
        val engine = engineFor(sendConfig)
        activeEpoch = epoch

        local.update {
            it.copy(
                sending = true,
                errorMessage = null,
                infoMessage = null,
                input = if (insertUser) "" else it.input,
            )
        }

        requestJob = viewModelScope.launch {
            try {
                val history = conversations.loadAll()
                    .filter { it.id != excludeMessageId }
                val data = dataRepository.snapshot(consent).copy(history = history)

                // 0.24 自然语言关心偏好：只识别明确表达，保存后直接确认，不交给模型扩写。
                val pref = com.mengyalife.app.domain.care.CarePreferenceParser.parse(
                    text = input,
                    plans = data.plans.map { it.id to it.title },
                    goals = data.goals.map { it.id to it.title },
                    now = System.currentTimeMillis(),
                )
                if (pref != null && careRepository != null && carePreferenceRepository != null) {
                    if (insertUser) conversations.appendUser(input, null, epoch, expectedRestoreGeneration)
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
                                topicKey = (opp?.planId?.let { "plan:$it" } ?: opp?.goalId?.let { "goal:$it" }) as String?,
                                topicLabel = opp?.title, value = "WELCOME", source = "USER_FEEDBACK",
                                sourceMessageId = decision.messageId, sourceExcerpt = input,
                            )
                            conversations.appendAssistant(
                                content = "记下了，这次「${opp?.title ?: "关心"}」对你有用。",
                                contextEpoch = epoch, sources = emptyList(), proposalMode = false,
                                expectedRestoreGeneration = expectedRestoreGeneration,
                            )
                        }
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
                    local.update { it.copy(sending = false, input = "") }
                    return@launch
                }

                val assembled = assembler.assemble(input, consent, data)

                if (insertUser) {
                    conversations.appendUser(input, null, epoch, expectedRestoreGeneration)
                }

                val runtime = configRepository.runtimeConfig()
                if (runtime == null) {
                    conversations.appendFailure(
                        AdvisorError(AdvisorError.NOT_CONFIGURED, "密钥不可用，请到设置页重新保存 API Key。"),
                        cancelled = false,
                        contextEpoch = epoch,
                        proposalMode = proposalMode,
                        expectedRestoreGeneration = expectedRestoreGeneration,
                    )
                    local.update { it.copy(sending = false) }
                    return@launch
                }

                // 关键竞态修复：发起请求的 runtime endpoint 必须仍然是
                // 用户当初授权时确认的接收方，且与 UI 快照一致。
                val currentConfig = configRepository.state.first()
                val currentConsent = consentRepository.current()
                if (runtime.endpoint != sendConfig.endpoint ||
                    currentConfig.endpoint != runtime.endpoint ||
                    !currentConsent.isValidFor(runtime.endpoint) ||
                    currentConsent.epoch != epoch
                ) {
                    conversations.appendFailure(
                        AdvisorError(AdvisorError.CANCELLED, "服务配置或授权已变化，本次请求未发送。"),
                        cancelled = true,
                        contextEpoch = epoch,
                        proposalMode = proposalMode,
                        expectedRestoreGeneration = expectedRestoreGeneration,
                    )
                    local.update { it.copy(sending = false) }
                    return@launch
                }

                val builtRequest = AdvisorRules.buildRequest(
                    history = assembled.history,
                    currentInput = input,
                    contextEntries = assembled.entries,
                    extraSystemPrompt = if (proposalMode) AdvisorRules.proposalSystemPrompt() else null,
                )
                val chat = suspend {
                    engine.chat(
                        runtime,
                        builtRequest.messages,
                        maxTokens = if (proposalMode) 3_000 else AdvisorRules.MAX_OUTPUT_TOKENS,
                    )
                }
                val result = restoreWriteBarrier?.withWrite(expectedRestoreGeneration) { chat() } ?: chat()
                when (result) {
                    is AdvisorChatResult.Success -> {
                        val afterConfig = configRepository.state.first()
                        val after = consentRepository.current()
                        if (afterConfig.endpoint != runtime.endpoint ||
                            !after.isValidFor(runtime.endpoint) ||
                            after.epoch != epoch
                        ) {
                            conversations.appendFailure(
                                AdvisorError(AdvisorError.CANCELLED, "服务配置或授权已变化，本次回复未保存。"),
                                cancelled = true,
                                contextEpoch = epoch,
                                proposalMode = proposalMode,
                                expectedRestoreGeneration = expectedRestoreGeneration,
                            )
                            local.update { it.copy(sending = false) }
                            return@launch
                        }
                        if (proposalMode) {
                            handleProposalResult(
                                raw = result.content,
                                epoch = epoch,
                                sources = builtRequest.usedSources,
                                existingGoals = data.goals.associate { it.id to it.level },
                                contextChars = builtRequest.contextChars,
                                expectedRestoreGeneration = expectedRestoreGeneration,
                            )
                        } else {
                            val assistant = conversations.appendAssistant(
                                content = AdvisorRules.stripMarkdown(result.content),
                                contextEpoch = epoch,
                                sources = builtRequest.usedSources,
                                proposalMode = false,
                                expectedRestoreGeneration = expectedRestoreGeneration,
                            )
                            // 聊天已经完成；提取候选不应让发送态一直挂着。
                            local.update { it.copy(sending = false) }
                            // 记录候选：只提取，不改变这一轮聊天的成功与否。
                            // 只整理“这一次”的对话：限制在最近 15 分钟内，避免把更早的无关话题写进日记。
                            val cutoff = System.currentTimeMillis() - 15 * 60 * 1000L
                            // 只用用户自己说过的话作为事实来源；顾问的建议不能被写成“我的反思”。
                            val recentMessages = uiState.value.messages
                                .filter { it.createdAt >= cutoff && it.role == AdvisorRole.USER }
                                .takeLast(6)
                            val recent = recentMessages.map { "用户：" + it.content }
                            val recentIds = recentMessages.map { it.id }
                            val willExtract = DiaryIntentGate.looksRelevant(input) ||
                                GoalIntentGate.looksRelevant(input) ||
                                RecordIntentGate.looksRelevant(input)
                            if (!willExtract &&
                                !local.value.planSuggestionDismissed &&
                                AdvisorRules.shouldSuggestPlanDraft(input)
                            ) {
                                local.update { it.copy(planSuggestion = true) }
                            }
                            if (DiaryIntentGate.looksRelevant(input)) {
                                maybeExtractDiary(
                                    userText = input,
                                    assistantMessageId = assistant.id,
                                    transcript = recent,
                                    sourceIds = recentIds,
                                    epoch = epoch,
                                    runtime = runtime,
                                    engine = engine,
                                    expectedRestoreGeneration = expectedRestoreGeneration,
                                )
                            } else if (data.plans.any { it.status == "ACTIVE" } &&
                                PlanIntentGate.looksLikeProgress(input)
                            ) {
                                maybeExtractPlanProgress(input, assistant.id, recentIds, data)
                            } else if (data.plans.isNotEmpty() &&
                                PlanIntentGate.looksLikeStatusUpdate(input)
                            ) {
                                maybeExtractPlan(input, assistant.id, recentIds, epoch, data, runtime, engine, expectedRestoreGeneration)
                            } else if (PlanIntentGate.looksLikeCreate(input)) {
                                maybeExtractPlan(input, assistant.id, recentIds, epoch, data, runtime, engine, expectedRestoreGeneration)
                            } else if (GoalIntentGate.looksRelevant(input)) {
                                maybeExtractGoal(
                                    userText = input,
                                    assistantMessageId = assistant.id,
                                    sourceIds = recentIds,
                                    epoch = epoch,
                                    data = data,
                                    runtime = runtime,
                                    engine = engine,
                                    expectedRestoreGeneration = expectedRestoreGeneration,
                                )
                            } else {
                                maybeExtractRecord(
                                    userText = input,
                                    assistantMessageId = assistant.id,
                                    epoch = epoch,
                                    data = data,
                                    runtime = runtime,
                                    engine = engine,
                                    expectedRestoreGeneration = expectedRestoreGeneration,
                                )
                            }
                        }
                    }
                    is AdvisorChatResult.Failure -> conversations.appendFailure(
                        error = result.error,
                        cancelled = false,
                        contextEpoch = epoch,
                        proposalMode = proposalMode,
                        expectedRestoreGeneration = expectedRestoreGeneration,
                    )
                }
                local.update { it.copy(sending = false) }
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    conversations.appendFailure(
                        AdvisorError.cancelled(),
                        cancelled = true,
                        contextEpoch = epoch,
                        proposalMode = proposalMode,
                        expectedRestoreGeneration = expectedRestoreGeneration,
                    )
                }
                local.update { it.copy(sending = false, infoMessage = "已取消本次请求。") }
                throw cancelled
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    conversations.appendFailure(
                        AdvisorError.unknown(),
                        cancelled = false,
                        contextEpoch = epoch,
                        proposalMode = proposalMode,
                        expectedRestoreGeneration = expectedRestoreGeneration,
                    )
                }
                local.update { it.copy(sending = false) }
            } finally {
                requestJob = null
                activeEpoch = null
            }
        }
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
        viewModelScope.launch {
            try {
            val saved = proposalRepository.updateDraft(proposalId, draft, selected)
            if (!saved) {
                local.update {
                    it.copy(
                        applyState = ProposalApplyUiState(
                            proposalId = proposalId,
                            error = "草案已处理或状态已变化，请重新打开",
                        ),
                    )
                }
                return@launch
            }
            when (val result = proposalApplyService.apply(proposalId, selected)) {
                is com.mengyalife.app.data.advisor.ProposalApplyResult.Success -> local.update {
                    it.copy(
                        applyState = ProposalApplyUiState(
                            proposalId = proposalId,
                            message = "已创建 ${result.created.size} 个目标",
                            createdTitles = result.created.map { goal -> goal.title },
                            canUndo = true,
                        ),
                    )
                }
                is com.mengyalife.app.data.advisor.ProposalApplyResult.Failure -> local.update {
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
        epoch: Long,
        data: AdvisorContextSnapshot,
        runtime: com.mengyalife.app.domain.advisor.AdvisorRuntimeConfig,
        engine: AdvisorEngine,
        expectedRestoreGeneration: Long,
    ) {
        if (!RecordIntentGate.looksRelevant(userText)) return
        val consent = consentRepository.current()
        if (consent.epoch != epoch) return
        if (!consent.enabled(ConsentCategory.PROGRESS)) {
            local.update {
                it.copy(
                    infoMessage = "未授权「进展」类别，本次没有生成记录候选；可在设置 → AI 与隐私中开启。",
                )
            }
            return
        }
        if (!consent.enabled(ConsentCategory.GOALS)) {
            local.update {
                it.copy(
                    infoMessage = "未授权「目标」类别，无法判断这条进展属于哪个目标；本次没有写入。",
                )
            }
            return
        }
        val goals = data.goals
        if (goals.isEmpty()) return
        val goalLines = goals.take(20).map { goal ->
            "[目标#${goal.id}] ${goal.level.label}：${goal.title}（${goal.status.label}）"
        }
        val checkinLines = data.checkins
            .sortedByDescending { it.recordedAt }
            .take(6)
            .map { checkin ->
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
            engine.chat(
                config = runtime,
                messages = listOf(
                    com.mengyalife.app.domain.advisor.AdvisorChatMessage(role = "system", content = prompt),
                    com.mengyalife.app.domain.advisor.AdvisorChatMessage(role = "user", content = userText),
                ),
                maxTokens = 600,
            )
        } finally {
            local.update { state ->
                state.copy(infoMessage = state.infoMessage?.takeIf { it != "正在整理记录…" })
            }
        }
        if (result !is AdvisorChatResult.Success) {
            local.update { it.copy(infoMessage = "记录整理失败，这一轮没有生成记录；聊天内容不受影响。") }
            return
        }
        // 提取期间授权/接收方变化：放弃，不写任何东西。
        val after = consentRepository.current()
        if (after.epoch != epoch ||
            !after.isValidFor(runtime.endpoint) ||
            !after.enabled(ConsentCategory.PROGRESS)
        ) {
            return
        }
        val allowedIds = goals.map { it.id }.toSet()
        val draft = when (val parsed = RecordProposalParser.parse(result.content, userText, allowedGoalIds = allowedIds)) {
            is RecordProposalParseResult.Success -> parsed.draft ?: return
            is RecordProposalParseResult.Failure -> {
                local.update { it.copy(infoMessage = "没有整理出可写入的记录；可补充目标、数值和单位后再试。") }
                return
            }
        }
        val stored = runCatching {
            recordRepository.save(assistantMessageId, draft, expectedRestoreGeneration)
        }.getOrNull() ?: return

        val autoApply = RecordProposalRules.shouldAutoApply(draft, after.enabled(ConsentCategory.PROGRESS))
        if (!autoApply) {
            local.update {
                it.copy(
                    recordAction = RecordActionUiState(
                        proposalId = stored.id,
                        message = if (draft.pendingFields.isNotEmpty()) {
                            "需要你确认记录信息"
                        } else {
                            "建议记录一条进展，确认后才会写入"
                        },
                    ),
                )
            }
            return
        }
        when (val applied = recordApplyService.apply(stored.id, draft)) {
            is RecordApplyResult.Success -> local.update {
                it.copy(
                    recordAction = RecordActionUiState(
                        proposalId = stored.id,
                        message = "已记录到「${applied.goalTitle}」，可修改或撤销",
                    ),
                )
            }
            is RecordApplyResult.Failure -> local.update {
                it.copy(recordAction = RecordActionUiState(proposalId = stored.id, error = applied.message))
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
        epoch: Long,
        runtime: com.mengyalife.app.domain.advisor.AdvisorRuntimeConfig,
        engine: AdvisorEngine,
        expectedRestoreGeneration: Long,
    ) {
        val consent = consentRepository.current()
        if (consent.epoch != epoch) return
        if (!consent.enabled(ConsentCategory.JOURNAL)) {
            local.update {
                it.copy(infoMessage = "未授权「日记」类别，本次没有整理日记；可在设置 → AI 与隐私中开启。")
            }
            return
        }
        local.update { it.copy(infoMessage = "正在整理日记…") }
        val prompt = AdvisorRules.diaryExtractionPrompt(
            transcriptLines = transcript,
            today = java.time.LocalDate.now().toString(),
        )
        val result = try {
            engine.chat(
                config = runtime,
                messages = listOf(
                    com.mengyalife.app.domain.advisor.AdvisorChatMessage(role = "system", content = prompt),
                    com.mengyalife.app.domain.advisor.AdvisorChatMessage(
                        role = "user",
                        content = transcript.joinToString("\n"),
                    ),
                ),
                maxTokens = 900,
            )
        } finally {
            local.update { state ->
                state.copy(infoMessage = state.infoMessage?.takeIf { it != "正在整理日记…" })
            }
        }
        if (result !is AdvisorChatResult.Success) {
            local.update { it.copy(infoMessage = "日记整理失败，这一轮没有生成草稿；聊天内容不受影响。") }
            return
        }
        val after = consentRepository.current()
        if (after.epoch != epoch || !after.isValidFor(runtime.endpoint) || !after.enabled(ConsentCategory.JOURNAL)) {
            return
        }
        val draft = when (val parsed = RecordProposalParser.parse(result.content, userText)) {
            is RecordProposalParseResult.Success -> parsed.draft ?: return
            is RecordProposalParseResult.Failure -> {
                local.update { it.copy(infoMessage = "没有整理出日记草稿，可以多聊几句再试。") }
                return
            }
        }
        if (!draft.isDiary || draft.diaryBody.isNullOrBlank()) return
        val stored = runCatching {
            recordRepository.save(
                assistantMessageId,
                draft.copy(sourceMessageIds = sourceIds),
                expectedRestoreGeneration,
            )
        }.getOrNull() ?: return
        local.update {
            it.copy(
                recordAction = RecordActionUiState(
                    proposalId = stored.id,
                    message = if (draft.intent == com.mengyalife.app.domain.advisor.RecordIntent.EXPLICIT) {
                        "日记草稿已生成，确认后保存到日记"
                    } else {
                        "这段经历可以整理成日记，确认后才会保存"
                    },
                ),
            )
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
    ) {
        val active = data.plans.filter { it.status == "ACTIVE" }
        if (active.isEmpty()) return
        val chosen = if (active.size == 1) {
            active.single()
        } else {
            active.firstOrNull { plan ->
                val keyword = plan.title.replace(Regex("[\\s\\p{Punct}，。]"), "").take(6)
                keyword.isNotEmpty() && userText.contains(keyword.take(2))
            }
        }
        if (chosen == null) {
            local.update { it.copy(infoMessage = "有多个进行中的计划，请说清是哪一个。") }
            return
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
        )
        val stored = runCatching {
            recordRepository.save(assistantMessageId, draft, null)
        }.getOrNull() ?: return
        local.update {
            it.copy(
                recordAction = RecordActionUiState(
                    proposalId = stored.id,
                    message = "记下这次执行？确认后计入「${chosen.title}」",
                ),
            )
        }
    }

    private suspend fun maybeExtractPlan(
        userText: String,
        assistantMessageId: String,
        sourceIds: List<String>,
        epoch: Long,
        data: AdvisorContextSnapshot,
        runtime: com.mengyalife.app.domain.advisor.AdvisorRuntimeConfig,
        engine: AdvisorEngine,
        expectedRestoreGeneration: Long,
    ) {
        val explicit = com.mengyalife.app.domain.advisor.PlanDraftFactory.explicitCreate(userText)
        val statusIntent = com.mengyalife.app.domain.advisor.PlanDraftFactory.statusIntent(userText)
        val consent = consentRepository.current()
        if (consent.epoch != epoch) return
        if (!consent.enabled(ConsentCategory.GOALS)) {
            local.update { it.copy(infoMessage = "未授权「目标」类别，本次没有生成小计划草稿。") }
            return
        }
        suspend fun fallback(reason: String) {
            val draft = when {
                statusIntent != null -> com.mengyalife.app.domain.advisor.PlanDraftFactory.buildStatus(
                    plans = data.plans,
                    newStatus = statusIntent,
                    text = userText,
                    intent = if (explicit) RecordIntent.EXPLICIT else RecordIntent.SUGGESTED,
                )
                com.mengyalife.app.domain.advisor.PlanIntentGate.looksLikeCreate(userText) -> {
                    com.mengyalife.app.domain.advisor.PlanDraftFactory.buildCreate(
                        text = userText,
                        intent = if (explicit) RecordIntent.EXPLICIT else RecordIntent.SUGGESTED,
                    )
                }
                else -> null
            }
            if (draft == null) {
                local.update { it.copy(infoMessage = reason) }
                return
            }
            val stored = runCatching {
                recordRepository.save(assistantMessageId, draft.copy(sourceMessageIds = sourceIds), expectedRestoreGeneration)
            }.getOrNull()
            if (stored == null) {
                local.update { it.copy(infoMessage = "计划草稿保存失败，请再试一次。") }
                return
            }
            val change = draft.planChange
            local.update {
                it.copy(
                    recordAction = RecordActionUiState(
                        proposalId = stored.id,
                        message = if (change?.change == PlanChangeKind.STATUS) {
                            "计划状态变更草稿，确认后才会更新"
                        } else if (draft.intent == RecordIntent.EXPLICIT) {
                            "好，先记作「${change?.title.orEmpty()}」；确认后才会保存"
                        } else {
                            "要把这件事记成小计划吗？确认后才会保存"
                        },
                    ),
                )
            }
        }

        val planLines = data.plans.take(10).map {
            "[计划#${it.id}] ${it.title}（${it.status}${it.timeScope?.let { s0 -> "，$s0" } ?: ""}）"
        }
        val prompt = AdvisorRules.planExtractionPrompt(
            planLines = planLines,
            goalLines = data.goals.take(12).map { "[目标#${it.id}] ${it.title}" },
            today = java.time.LocalDate.now().toString(),
        )
        local.update { it.copy(infoMessage = "正在整理小计划…") }
        val result = try {
            engine.chat(
                config = runtime,
                messages = listOf(
                    com.mengyalife.app.domain.advisor.AdvisorChatMessage(role = "system", content = prompt),
                    com.mengyalife.app.domain.advisor.AdvisorChatMessage(role = "user", content = userText),
                ),
                maxTokens = 700,
            )
        } finally {
            local.update { s0 -> s0.copy(infoMessage = s0.infoMessage?.takeIf { it != "正在整理小计划…" }) }
        }
        if (result !is AdvisorChatResult.Success) {
            return fallback("小计划整理失败，已尝试用你的原话生成草稿。")
        }
        val parsedDraft = when (val parsed = RecordProposalParser.parse(result.content, userText)) {
            is RecordProposalParseResult.Success -> parsed.draft
            is RecordProposalParseResult.Failure -> null
        }
        if (parsedDraft == null || !parsedDraft.isPlan) {
            return fallback("这次没有整理出计划草稿，可以换句话再说一次。")
        }
        val planChange = parsedDraft.planChange
        val enriched = if (planChange?.change == PlanChangeKind.STATUS) {
            val target = data.plans.firstOrNull { it.id == planChange.targetPlanId }
            parsedDraft.copy(planChange = planChange.copy(title = target?.title ?: planChange.title))
        } else {
            parsedDraft
        }
        val stored = runCatching {
            recordRepository.save(
                assistantMessageId,
                enriched.copy(sourceMessageIds = sourceIds),
                expectedRestoreGeneration = expectedRestoreGeneration,
            )
        }.getOrNull()
        if (stored == null) {
            return fallback("计划草稿保存失败，已尝试用你的原话生成草稿。")
        }
        local.update {
            it.copy(
                recordAction = RecordActionUiState(
                    proposalId = stored.id,
                    message = if (planChange?.change == PlanChangeKind.STATUS) {
                        "计划状态变更草稿，确认后才会更新"
                    } else {
                        "要记下这件事吗？确认后才会保存"
                    },
                ),
            )
        }
    }

    private suspend fun maybeExtractGoal(
        userText: String,
        assistantMessageId: String,
        sourceIds: List<String>,
        epoch: Long,
        data: AdvisorContextSnapshot,
        runtime: com.mengyalife.app.domain.advisor.AdvisorRuntimeConfig,
        engine: AdvisorEngine,
        expectedRestoreGeneration: Long,
    ) {
        val consent = consentRepository.current()
        if (consent.epoch != epoch) return
        if (!consent.enabled(ConsentCategory.GOALS)) {
            local.update {
                it.copy(infoMessage = "未授权「目标」类别，本次没有生成目标草稿；可在设置 → AI 与隐私中开启。")
            }
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
            engine.chat(
                config = runtime,
                messages = listOf(
                    com.mengyalife.app.domain.advisor.AdvisorChatMessage(role = "system", content = prompt),
                    com.mengyalife.app.domain.advisor.AdvisorChatMessage(role = "user", content = userText),
                ),
                maxTokens = 800,
            )
        } finally {
            local.update { state ->
                state.copy(infoMessage = state.infoMessage?.takeIf { it != "正在整理目标…" })
            }
        }
        if (result !is AdvisorChatResult.Success) {
            local.update { it.copy(infoMessage = "目标整理失败，这一轮没有生成草稿；聊天内容不受影响。") }
            return
        }
        val after = consentRepository.current()
        if (after.epoch != epoch || !after.isValidFor(runtime.endpoint) || !after.enabled(ConsentCategory.GOALS)) {
            return
        }
        val draft = when (val parsed = RecordProposalParser.parse(
            raw = result.content,
            originalText = userText,
            allowedGoalIds = goals.map { it.id }.toSet(),
        )) {
            is RecordProposalParseResult.Success -> parsed.draft ?: return
            is RecordProposalParseResult.Failure -> {
                local.update { it.copy(infoMessage = "没有整理出目标草稿，可以再明确一点想改什么。") }
                return
            }
        }
        if (!draft.isGoal) return
        val change = draft.goalChange ?: return
        if (change.change == GoalChangeKind.CREATE) {
            val duplicate = goals.any { it.title.trim() == change.title?.trim() }
            if (duplicate) {
                local.update { it.copy(infoMessage = "已经有同名目标，没有重复创建；可以直接说想怎么调整它。") }
                return
            }
        }
        val similar = if (change.change == GoalChangeKind.CREATE) {
            goals.firstOrNull { existing ->
                val a = existing.title.replace(Regex("[\\s\\p{Punct}，。]"), "")
                val b = change.title.orEmpty().replace(Regex("[\\s\\p{Punct}，。]"), "")
                a.length >= 4 && b.length >= 4 && (a.contains(b) || b.contains(a)) && a != b
            }
        } else {
            null
        }
        val similarWarning = similar?.let { "与已有目标「${it.title}」相近，请确认是否同一个" }
        val target = goals.firstOrNull { it.id == change.targetGoalId }
        if (change.change != GoalChangeKind.CREATE && target == null) {
            local.update { it.copy(infoMessage = "没找到要修改的目标，请说出它的完整名称再试。") }
            return
        }
        val enriched = draft.copy(
            goalChange = change.copy(
                uncertain = change.uncertain + listOfNotNull(similarWarning),
                before = target?.let {
                    GoalBeforeSnapshot(
                        goalId = it.id,
                        title = it.title,
                        levelName = it.level.name,
                        domainName = it.domain.name,
                        dueAt = it.dueDate,
                        parentId = it.parentId,
                    )
                },
            ),
            sourceMessageIds = sourceIds,
        )
        val stored = runCatching {
            recordRepository.save(assistantMessageId, enriched, expectedRestoreGeneration)
        }.getOrNull() ?: return
        val promptText = when (change.change) {
            GoalChangeKind.CREATE -> "目标草稿已生成，确认后写入目标树"
            GoalChangeKind.UPDATE -> "目标变更草稿已生成，确认后才会修改"
            GoalChangeKind.BREAKDOWN -> "拆解建议已生成，选择后才会创建"
        }
        local.update {
            it.copy(recordAction = RecordActionUiState(proposalId = stored.id, message = promptText))
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
        val stored = uiState.value.records.values.firstOrNull { it.id == proposalId } ?: return
        recording = true
        local.update { it.copy(recordAction = RecordActionUiState(proposalId = proposalId, loading = true)) }
        viewModelScope.launch {
            try {
                if (stored.draft.isPlanProgress) {
                    val out = planApplyService.applyProgress(proposalId)
                    local.update {
                        it.copy(
                            recordAction = when (out) {
                                is PlanApplyResult.Success -> RecordActionUiState(
                                    proposalId = proposalId,
                                    message = "记下了这次执行，计划仍保持原状态",
                                )
                                is PlanApplyResult.Failure -> RecordActionUiState(
                                    proposalId = proposalId,
                                    error = out.message,
                                )
                            },
                        )
                    }
                    return@launch
                }
                if (stored.draft.isPlan) {
                    val out = planApplyService.apply(proposalId)
                    local.update {
                        it.copy(
                            recordAction = when (out) {
                                is PlanApplyResult.Success -> RecordActionUiState(
                                    proposalId = proposalId,
                                    message = "已记下这件事，之后可以接着说进展",
                                )
                                is PlanApplyResult.Failure -> RecordActionUiState(
                                    proposalId = proposalId,
                                    error = out.message,
                                )
                            },
                        )
                    }
                    return@launch
                }
                if (stored.draft.isGoal) {
                    val out = goalApplyService.apply(proposalId)
                    local.update {
                        it.copy(
                            recordAction = when (out) {
                                is GoalApplyResult.Success -> RecordActionUiState(
                                    proposalId = proposalId,
                                    message = "已保存到目标，可查看或继续调整",
                                    openGoalId = out.goalIds.firstOrNull(),
                                )
                                is GoalApplyResult.Failure -> RecordActionUiState(
                                    proposalId = proposalId,
                                    error = out.message,
                                )
                            },
                        )
                    }
                    return@launch
                }
                if (stored.draft.isDiary) {
                    val out = if (stored.isApplied) {
                        diaryApplyService.editApplied(proposalId, draft)
                    } else if (!recordRepository.updateDraft(proposalId, draft)) {
                        DiaryApplyResult.Failure("候选已被处理或状态已变化，请重新打开")
                    } else {
                        diaryApplyService.apply(proposalId)
                    }
                    local.update {
                        it.copy(
                            recordAction = when (out) {
                                is DiaryApplyResult.Success -> RecordActionUiState(
                                    proposalId = proposalId,
                                    message = if (stored.isApplied) "已更新这篇日记" else "已保存到日记，可在日记页查看",
                                    openDiaryEntryId = out.entryId,
                                )
                                is DiaryApplyResult.Failure -> RecordActionUiState(
                                    proposalId = proposalId,
                                    error = out.message,
                                )
                            },
                        )
                    }
                    return@launch
                }
                val outcome: RecordApplyResult = if (stored.isApplied) {
                    recordApplyService.editApplied(proposalId, draft)
                } else {
                    if (!recordRepository.updateDraft(proposalId, draft)) {
                        RecordApplyResult.Failure("候选已被处理或状态已变化，请重新打开")
                    } else {
                        recordApplyService.apply(proposalId, draft)
                    }
                }
                local.update {
                    it.copy(
                        recordAction = when (outcome) {
                            is RecordApplyResult.Success -> RecordActionUiState(
                                proposalId = proposalId,
                                message = if (stored.isApplied) {
                                    "已更新这条进展记录"
                                } else {
                                    "已记录到「${outcome.goalTitle}」，可修改或撤销"
                                },
                            )
                            is RecordApplyResult.Failure -> RecordActionUiState(
                                proposalId = proposalId,
                                error = outcome.message,
                            )
                        },
                    )
                }
            } finally {
                recording = false
            }
        }
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
        startRequest(
            input = userMessage.content,
            insertUser = false,
            excludeMessageId = userMessage.id,
            proposalMode = failed.proposalMode,
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
                )
            }
        }
    }
}
