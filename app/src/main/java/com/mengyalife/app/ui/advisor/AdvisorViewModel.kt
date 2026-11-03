package com.mengyalife.app.ui.advisor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mengyalife.app.MengyaApplication
import com.mengyalife.app.data.advisor.AdvisorConfigRepository
import com.mengyalife.app.data.advisor.AdvisorEngine
import com.mengyalife.app.data.advisor.ConversationRepository
import com.mengyalife.app.domain.advisor.AdvisorChatResult
import com.mengyalife.app.domain.advisor.AdvisorConsentRequest
import com.mengyalife.app.domain.advisor.AdvisorContextSource
import com.mengyalife.app.domain.advisor.AdvisorError
import com.mengyalife.app.domain.advisor.AdvisorGoalChoice
import com.mengyalife.app.domain.advisor.AdvisorGoalContext
import com.mengyalife.app.domain.advisor.AdvisorMessage
import com.mengyalife.app.domain.advisor.AdvisorMessageStatus
import com.mengyalife.app.domain.advisor.AdvisorRole
import com.mengyalife.app.domain.advisor.AdvisorRules
import com.mengyalife.app.domain.advisor.AiConfigState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 顾问对话 ViewModel。
 *
 * 关键约束：
 * - 先持久化用户消息，再发请求；失败只写一条 FAILED 消息，不伪造 AI 回复；
 * - 重试复用原用户消息，不新增重复用户消息；
 * - 模型只能返回文本，本类不会因为模型输出而修改目标/日记/进展；
 * - 目标上下文由用户显式选择 + 每次发送前确认。
 */
class AdvisorViewModel(
    private val conversations: ConversationRepository,
    private val configRepository: AdvisorConfigRepository,
    private val networkEngine: AdvisorEngine,
    private val fakeEngine: AdvisorEngine?,
    private val contextSource: AdvisorContextSource,
) : ViewModel() {

    private data class LocalState(
        val input: String = "",
        val sending: Boolean = false,
        val contextGoal: AdvisorGoalChoice? = null,
        val contextPayload: AdvisorGoalContext? = null,
        val goalPickerOpen: Boolean = false,
        val consent: AdvisorConsentRequest? = null,
        val errorMessage: String? = null,
        val infoMessage: String? = null,
    )

    private val local = MutableStateFlow(LocalState())
    private var requestJob: Job? = null

    private val messagesFlow = conversations.observeMessages()
    private val configFlow = configRepository.state
    private val goalsFlow = contextSource.observeGoalChoices()

    val uiState: StateFlow<AdvisorUiState> = combine(
        messagesFlow,
        configFlow,
        goalsFlow,
        local,
    ) { messages, config, goals, localState ->
        AdvisorUiState(
            messages = messages,
            input = localState.input,
            sending = localState.sending,
            config = config,
            configured = config.configured,
            goalChoices = goals,
            contextGoal = localState.contextGoal,
            contextPayload = localState.contextPayload,
            goalPickerOpen = localState.goalPickerOpen,
            consent = localState.consent,
            errorMessage = localState.errorMessage,
            infoMessage = localState.infoMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AdvisorUiState(),
    )

    fun onAction(action: AdvisorAction) {
        when (action) {
            is AdvisorAction.InputChange -> local.update { it.copy(input = action.value) }
            AdvisorAction.Send -> send()
            is AdvisorAction.Retry -> retry(action.messageId)
            AdvisorAction.CancelRequest -> cancelRequest()
            AdvisorAction.OpenGoalPicker -> local.update { it.copy(goalPickerOpen = true) }
            AdvisorAction.DismissGoalPicker -> local.update { it.copy(goalPickerOpen = false) }
            is AdvisorAction.SelectGoal -> selectGoal(action.goal)
            AdvisorAction.ClearContext -> local.update {
                it.copy(contextGoal = null, contextPayload = null)
            }
            AdvisorAction.ConfirmConsent -> confirmConsent()
            AdvisorAction.CancelConsent -> local.update {
                it.copy(consent = null, infoMessage = "已取消发送，个人数据没有发出。")
            }
            AdvisorAction.DismissError -> local.update { it.copy(errorMessage = null) }
            AdvisorAction.DismissInfo -> local.update { it.copy(infoMessage = null) }
        }
    }

    private fun send() {
        val state = uiState.value
        if (state.sending) return
        // 以 local 的最新输入为准，避免「输入后立刻点发送」时 stateIn 尚未重组
        val text = local.value.input.trim()
        if (text.isEmpty()) return
        if (!state.configured) {
            local.update {
                it.copy(errorMessage = AdvisorError.notConfigured().userMessage)
            }
            return
        }
        AdvisorRules.validateInput(text)?.let { error ->
            local.update { it.copy(errorMessage = error) }
            return
        }
        val payload = state.contextPayload
        if (payload != null) {
            // 带个人数据：先弹出授权摘要，确认后才真正发送。
            local.update { it.copy(consent = AdvisorConsentRequest(text, payload)) }
            return
        }
        startSend(
            input = text,
            context = null,
            insertUserMessage = true,
            excludeMessageId = null,
        )
    }

    private fun confirmConsent() {
        val consent = local.value.consent ?: return
        local.update { it.copy(consent = null) }
        startSend(
            input = consent.input,
            context = consent.context,
            insertUserMessage = true,
            excludeMessageId = null,
        )
    }

    private fun startSend(
        input: String,
        context: AdvisorGoalContext?,
        insertUserMessage: Boolean,
        excludeMessageId: String?,
    ) {
        if (local.value.sending) return
        if (!uiState.value.configured) {
            local.update { it.copy(errorMessage = AdvisorError.notConfigured().userMessage) }
            return
        }
        val engine = engineFor(uiState.value.config)

        local.update {
            it.copy(
                sending = true,
                errorMessage = null,
                infoMessage = null,
                input = if (insertUserMessage) "" else it.input,
            )
        }

        requestJob = viewModelScope.launch {
            try {
                // 先读取历史，再落用户消息；当前问题单独放在最后。
                val historyBefore = conversations.loadAll()
                    .filter { it.id != excludeMessageId && it.status == AdvisorMessageStatus.SENT }
                if (insertUserMessage) {
                    conversations.appendUser(input, context?.goalId)
                }

                val runtime = configRepository.runtimeConfig()
                if (runtime == null) {
                    conversations.appendFailure(
                        AdvisorError(AdvisorError.NOT_CONFIGURED, "密钥不可用，请到设置页重新保存 API Key。"),
                        cancelled = false,
                    )
                    local.update { it.copy(sending = false) }
                    return@launch
                }

                val requestMessages = AdvisorRules.buildRequestMessages(
                    history = historyBefore,
                    currentInput = input,
                    goalContext = context,
                )
                when (val result = engine.chat(runtime, requestMessages)) {
                    is AdvisorChatResult.Success -> {
                        conversations.appendAssistant(result.content)
                    }
                    is AdvisorChatResult.Failure -> {
                        conversations.appendFailure(result.error, cancelled = false)
                    }
                }
                local.update { it.copy(sending = false) }
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    conversations.appendFailure(AdvisorError.cancelled(), cancelled = true)
                }
                local.update { it.copy(sending = false, infoMessage = "已取消本次请求。") }
                throw cancelled
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    conversations.appendFailure(AdvisorError.unknown(), cancelled = false)
                }
                local.update { it.copy(sending = false) }
            } finally {
                requestJob = null
            }
        }
    }

    private fun retry(messageId: String) {
        val state = uiState.value
        if (state.sending) return
        val failed = state.messages.firstOrNull { it.id == messageId } ?: return
        if (failed.status == AdvisorMessageStatus.SENT || failed.role != AdvisorRole.ASSISTANT) return
        val userMessage = state.messages
            .takeWhile { it.id != failed.id }
            .lastOrNull { it.role == AdvisorRole.USER && it.status == AdvisorMessageStatus.SENT }
            ?: run {
                local.update { it.copy(errorMessage = "找不到对应的用户问题，无法重试。") }
                return
            }
        viewModelScope.launch {
            conversations.deleteMessage(failed.id)
            startSend(
                input = userMessage.content,
                context = null,
                insertUserMessage = false,
                excludeMessageId = userMessage.id,
            )
        }
        local.update {
            it.copy(infoMessage = "正在重试；不会重复发送你的上一条问题，也不会重复附带目标摘要。")
        }
    }

    private fun cancelRequest() {
        val job = requestJob ?: return
        local.update { it.copy(infoMessage = "正在取消…") }
        job.cancel()
    }

    private fun selectGoal(goal: AdvisorGoalChoice) {
        local.update { it.copy(goalPickerOpen = false) }
        viewModelScope.launch {
            val context = contextSource.buildContext(goal.id)
            if (context == null) {
                local.update { it.copy(errorMessage = "目标不存在或已被删除。") }
                return@launch
            }
            local.update {
                it.copy(
                    contextGoal = goal,
                    contextPayload = context,
                    infoMessage = "已选择目标上下文；发送前会再次展示发送范围并等待你确认。",
                )
            }
        }
    }

    private fun engineFor(config: AiConfigState): AdvisorEngine =
        if (config.useFakeEngine && fakeEngine != null) fakeEngine else networkEngine

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MengyaApplication
                AdvisorViewModel(
                    conversations = app.container.conversationRepository,
                    configRepository = app.container.advisorConfigRepository,
                    networkEngine = app.container.networkAdvisorEngine,
                    fakeEngine = app.container.fakeAdvisorEngine,
                    contextSource = app.container.goalRepository,
                )
            }
        }
    }
}
