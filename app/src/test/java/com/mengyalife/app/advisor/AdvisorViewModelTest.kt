package com.mengyalife.app.advisor

import com.mengyalife.app.data.advisor.AdvisorConfigRepository
import com.mengyalife.app.data.advisor.AdvisorEngine
import com.mengyalife.app.data.advisor.RoomConversationRepository
import com.mengyalife.app.domain.advisor.AdvisorChatResult
import com.mengyalife.app.domain.advisor.AdvisorContextSource
import com.mengyalife.app.domain.advisor.AdvisorError
import com.mengyalife.app.domain.advisor.AdvisorMessageStatus
import com.mengyalife.app.domain.advisor.AdvisorRole
import com.mengyalife.app.ui.advisor.AdvisorAction
import com.mengyalife.app.ui.advisor.AdvisorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdvisorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Fixture(
        val vm: AdvisorViewModel,
        val dao: FakeConversationDao,
        val config: FakeConfigRepository,
        val engine: ScriptedAdvisorEngine,
        val context: FakeContextSource,
        val repository: RoomConversationRepository,
    )

    private suspend fun build(
        configured: Boolean = true,
        engine: ScriptedAdvisorEngine = ScriptedAdvisorEngine(),
        context: FakeContextSource = FakeContextSource(
            goals = listOf(sampleGoal()),
            contexts = mapOf(1L to sampleContext()),
        ),
    ): Fixture {
        val config = FakeConfigRepository()
        if (configured) {
            config.save("https://api.example.com/v1", "deepseek-chat", "sk-test")
        }
        val dao = FakeConversationDao()
        val repository = RoomConversationRepository(dao, now = { 1_000L })
        val vm = AdvisorViewModel(
            conversations = repository,
            configRepository = config,
            networkEngine = engine,
            fakeEngine = null,
            contextSource = context,
        )
        return Fixture(vm, dao, config, engine, context, repository)
    }

    private fun TestScope.collectVm(vm: AdvisorViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
    }

    @Test
    fun `发送成功会先保存用户消息再保存助手回复`() = runTest {
        val f = build()
        collectVm(f.vm)
        advanceUntilIdle()
        f.engine.enqueue(AdvisorChatResult.Success("建议先做一件小事"))
        f.vm.onAction(AdvisorAction.InputChange("我该怎么开始？"))
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()

        val state = f.vm.uiState.value
        assertEquals(2, state.messages.size)
        assertEquals(AdvisorRole.USER, state.messages[0].role)
        assertEquals("我该怎么开始？", state.messages[0].content)
        assertEquals(AdvisorRole.ASSISTANT, state.messages[1].role)
        assertEquals("建议先做一件小事", state.messages[1].content)
        assertEquals(AdvisorMessageStatus.SENT, state.messages[1].status)
        assertEquals(1, f.engine.calls.size)
        assertFalse(state.sending)
    }

    @Test
    fun `重启后从数据库恢复对话`() = runTest {
        val f = build()
        collectVm(f.vm)
        advanceUntilIdle()
        f.engine.enqueue(AdvisorChatResult.Success("第一次回复"))
        f.vm.onAction(AdvisorAction.InputChange("第一问"))
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()

        val vm2 = AdvisorViewModel(
            conversations = RoomConversationRepository(f.dao, now = { 2_000L }),
            configRepository = f.config,
            networkEngine = f.engine,
            fakeEngine = null,
            contextSource = f.context,
        )
        collectVm(vm2)
        advanceUntilIdle()
        assertEquals(2, vm2.uiState.value.messages.size)
        assertEquals("第一问", vm2.uiState.value.messages[0].content)
        assertEquals("第一次回复", vm2.uiState.value.messages[1].content)
    }

    @Test
    fun `失败只保存失败状态，不伪造助手回复`() = runTest {
        val f = build()
        collectVm(f.vm)
        advanceUntilIdle()
        f.engine.enqueue(AdvisorChatResult.Failure(AdvisorError.timeout()))
        f.vm.onAction(AdvisorAction.InputChange("会失败的问题"))
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()

        val state = f.vm.uiState.value
        assertEquals(2, state.messages.size)
        assertEquals(AdvisorMessageStatus.FAILED, state.messages[1].status)
        assertEquals("", state.messages[1].content)
        assertEquals(AdvisorError.TIMEOUT, state.messages[1].errorCode)
        assertTrue(state.messages[1].errorMessage!!.contains("超时"))
    }

    @Test
    fun `重试不会重复用户消息`() = runTest {
        val f = build()
        collectVm(f.vm)
        advanceUntilIdle()
        f.engine.enqueue(AdvisorChatResult.Failure(AdvisorError.offline()))
        f.vm.onAction(AdvisorAction.InputChange("需要重试"))
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()
        val failed = f.vm.uiState.value.messages.last()
        assertEquals(AdvisorMessageStatus.FAILED, failed.status)

        f.engine.enqueue(AdvisorChatResult.Success("重试成功"))
        f.vm.onAction(AdvisorAction.Retry(failed.id))
        advanceUntilIdle()

        val messages = f.vm.uiState.value.messages
        assertEquals(2, messages.size)
        assertEquals(AdvisorRole.USER, messages[0].role)
        assertEquals(1, messages.count { it.role == AdvisorRole.USER })
        assertEquals("重试成功", messages[1].content)
        assertEquals(2, f.engine.calls.size)
    }

    @Test
    fun `取消请求保存取消状态且没有回复`() = runTest {
        val f = build(engine = ScriptedAdvisorEngine().apply { blockUntilCancelled = true })
        collectVm(f.vm)
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.InputChange("取消我"))
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.CancelRequest)
        advanceUntilIdle()

        val state = f.vm.uiState.value
        assertEquals(2, state.messages.size)
        assertEquals(AdvisorMessageStatus.CANCELLED, state.messages[1].status)
        assertEquals("", state.messages[1].content)
        assertFalse(state.sending)
    }

    @Test
    fun `未配置模型时不发送也不落消息`() = runTest {
        val f = build(configured = false)
        collectVm(f.vm)
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.InputChange("你好"))
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()

        assertTrue(f.vm.uiState.value.messages.isEmpty())
        assertEquals(0, f.engine.calls.size)
        assertNotNull(f.vm.uiState.value.errorMessage)
    }

    @Test
    fun `附带目标时必须确认，确认后才发送并携带摘要`() = runTest {
        val f = build()
        collectVm(f.vm)
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.SelectGoal(sampleGoal()))
        advanceUntilIdle()
        assertNotNull(f.vm.uiState.value.contextPayload)

        f.vm.onAction(AdvisorAction.InputChange("看看我的目标"))
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()

        // 还没确认：不发送、不落库
        assertNotNull(f.vm.uiState.value.consent)
        assertTrue(f.vm.uiState.value.messages.isEmpty())
        assertEquals(0, f.engine.calls.size)

        f.engine.enqueue(AdvisorChatResult.Success("基于目标建议"))
        f.vm.onAction(AdvisorAction.ConfirmConsent)
        advanceUntilIdle()

        assertEquals(2, f.vm.uiState.value.messages.size)
        assertEquals(1L, f.vm.uiState.value.messages[0].contextGoalId)
        assertEquals(1, f.engine.calls.size)
        val sent = f.engine.calls[0].joinToString("\n") { it.content }
        assertTrue(sent.contains("健康"))
        assertTrue(sent.contains("未包含日记正文"))
        assertTrue(sent.contains("看看我的目标"))
    }

    @Test
    fun `取消授权不发送个人数据`() = runTest {
        val f = build()
        collectVm(f.vm)
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.SelectGoal(sampleGoal()))
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.InputChange("带目标的问题"))
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.CancelConsent)
        advanceUntilIdle()

        assertTrue(f.vm.uiState.value.messages.isEmpty())
        assertNull(f.vm.uiState.value.consent)
        assertEquals(0, f.engine.calls.size)
    }

    @Test
    fun `多轮对话只带最近历史，失败消息不进入`() = runTest {
        val f = build()
        collectVm(f.vm)
        advanceUntilIdle()
        f.engine.enqueue(AdvisorChatResult.Success("答1"))
        f.vm.onAction(AdvisorAction.InputChange("问1"))
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()

        f.engine.enqueue(AdvisorChatResult.Failure(AdvisorError.server()))
        f.vm.onAction(AdvisorAction.InputChange("问2"))
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()

        f.engine.enqueue(AdvisorChatResult.Success("答3"))
        f.vm.onAction(AdvisorAction.InputChange("问3"))
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()

        val lastCall = f.engine.calls.last().joinToString("\n") { it.content }
        assertTrue(lastCall.contains("问1"))
        assertTrue(lastCall.contains("答1"))
        assertTrue(lastCall.contains("问3"))
        // 失败的是助手回复；用户自己的“问2”仍作为历史保留，但失败回复的文案不会进入
        assertFalse(lastCall.contains("模型服务暂时不可用"))
        assertEquals(6, f.vm.uiState.value.messages.size)
    }
}
