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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
    private val assembler: AdvisorContextAssembler,
    private val networkEngine: AdvisorEngine,
    private val fakeEngine: AdvisorEngine?,
) : ViewModel() {

    private data class LocalState(
        val input: String = "",
        val sending: Boolean = false,
        val errorMessage: String? = null,
        val infoMessage: String? = null,
        val applyState: ProposalApplyUiState? = null,
    )

    private val local = MutableStateFlow(LocalState())
    private var requestJob: Job? = null
    private var activeEpoch: Long? = null
    private var latestConsent: AiConsentState = AiConsentState()
    private var applying = false
    private var latestConfig: com.mengyalife.app.domain.advisor.AiConfigState =
        com.mengyalife.app.domain.advisor.AiConfigState()

    private val messagesFlow = conversations.observeMessages()
    private val configFlow = configRepository.state
    private val consentFlow = consentRepository.state
    private val proposalsFlow = proposalRepository.observeAll()

    private val configState = MutableStateFlow(com.mengyalife.app.domain.advisor.AiConfigState())
    private val consentState = MutableStateFlow(AiConsentState())

    val uiState: StateFlow<AdvisorUiState> = combine(
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
        )
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
            is AdvisorAction.UpdateProposalDraft -> updateProposalDraft(
                action.proposalId, action.draft, action.selected,
            )
            is AdvisorAction.ApplyProposal -> applyProposal(
                action.proposalId, action.draft, action.selected,
            )
            is AdvisorAction.UndoProposal -> undoProposal(action.proposalId)
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
        startRequest(text, insertUser = true, excludeMessageId = null, proposalMode = false)
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
                val assembled = assembler.assemble(input, consent, data)

                if (insertUser) {
                    conversations.appendUser(input, null, epoch)
                }

                val runtime = configRepository.runtimeConfig()
                if (runtime == null) {
                    conversations.appendFailure(
                        AdvisorError(AdvisorError.NOT_CONFIGURED, "密钥不可用，请到设置页重新保存 API Key。"),
                        cancelled = false,
                        contextEpoch = epoch,
                        proposalMode = proposalMode,
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
                when (val result = engine.chat(
                    runtime,
                    builtRequest.messages,
                    maxTokens = if (proposalMode) 3_000 else AdvisorRules.MAX_OUTPUT_TOKENS,
                )) {
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
                            )
                        } else {
                            conversations.appendAssistant(
                                content = result.content,
                                contextEpoch = epoch,
                                sources = builtRequest.usedSources,
                                proposalMode = false,
                            )
                        }
                    }
                    is AdvisorChatResult.Failure -> conversations.appendFailure(
                        error = result.error,
                        cancelled = false,
                        contextEpoch = epoch,
                        proposalMode = proposalMode,
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
    ) {
        when (val parsed = GoalProposalParser.parse(raw, existingGoals)) {
            is GoalProposalParseResult.Success -> {
                val message = conversations.appendAssistant(
                    content = "已根据本次讨论整理为目标草案：" + parsed.draft.summary.take(200) +
                        "\n\n（草案，尚未保存；不会修改正式目标。）",
                    contextEpoch = epoch,
                    sources = sources,
                    proposalMode = true,
                )
                proposalRepository.save(message.id, parsed.draft)
            }
            is GoalProposalParseResult.Failure -> {
                conversations.appendAssistant(
                    content = raw.trim().take(4_000),
                    contextEpoch = epoch,
                    sources = sources,
                    proposalError = "草案解析失败：${parsed.message}",
                    proposalMode = true,
                )
            }
        }
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
                    assembler = AdvisorContextAssembler(),
                    networkEngine = app.container.networkAdvisorEngine,
                    fakeEngine = app.container.fakeAdvisorEngine,
                )
            }
        }
    }
}
