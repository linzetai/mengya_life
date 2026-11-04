package com.mengyalife.app.advisor

import com.mengyalife.app.domain.advisor.AdvisorContextAssembler
import com.mengyalife.app.data.advisor.RoomConversationRepository
import com.mengyalife.app.domain.advisor.AdvisorChatResult
import com.mengyalife.app.domain.advisor.AdvisorContextSnapshot
import com.mengyalife.app.domain.advisor.AdvisorError
import com.mengyalife.app.domain.advisor.AdvisorMessageStatus
import com.mengyalife.app.domain.advisor.AdvisorRole
import com.mengyalife.app.domain.advisor.AdvisorSourceType
import com.mengyalife.app.domain.advisor.AiConsentRules
import com.mengyalife.app.domain.advisor.ContextCheckinRecord
import com.mengyalife.app.domain.advisor.ContextGoalRecord
import com.mengyalife.app.domain.advisor.ContextJournalRecord
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalStatus
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
    private val endpoint = "https://api.example.com/v1"

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private data class Fixture(
        val vm: AdvisorViewModel,
        val dao: FakeConversationDao,
        val config: FakeConfigRepository,
        val consent: FakeConsentRepository,
        val data: FakeAdvisorDataRepository,
        val proposals: FakeGoalProposalRepository,
        val engine: ScriptedAdvisorEngine,
        val apply: FakeGoalProposalApply,
    )

    private suspend fun build(
        configured: Boolean = true,
        granted: Boolean = true,
        engine: ScriptedAdvisorEngine = ScriptedAdvisorEngine(),
        snapshot: AdvisorContextSnapshot = snapshot(),
    ): Fixture {
        val config = FakeConfigRepository()
        if (configured) config.save(endpoint, "deepseek-chat", "sk-test")
        val consent = FakeConsentRepository()
        if (granted) consent.grant(endpoint, AiConsentRules.allCategories)
        val dao = FakeConversationDao()
        val repository = RoomConversationRepository(dao, now = { 1_000L })
        val data = FakeAdvisorDataRepository(snapshot)
        val proposals = FakeGoalProposalRepository()
        val apply = FakeGoalProposalApply()
        val vm = AdvisorViewModel(
            conversations = repository,
            configRepository = config,
            consentRepository = consent,
            dataRepository = data,
            proposalRepository = proposals,
            proposalApplyService = apply,
            assembler = AdvisorContextAssembler(),
            networkEngine = engine,
            fakeEngine = null,
        )
        return Fixture(vm, dao, config, consent, data, proposals, engine, apply)
    }

    private fun snapshot(query: String = "睡眠 目标"): AdvisorContextSnapshot = AdvisorContextSnapshot(
        goals = listOf(
            ContextGoalRecord(
                id = 7,
                title = "健康",
                description = "长期保持规律运动",
                level = GoalLevel.LONG_TERM,
                parentId = null,
                domain = GoalDomain.HEALTH,
                status = GoalStatus.IN_PROGRESS,
                createdAt = 1L,
            ),
        ),
        checkins = listOf(
            ContextCheckinRecord(11, 7, 1_000, "完成三次训练", 3.0, "次/周"),
        ),
        journals = listOf(
            ContextJournalRecord(21, "睡眠", "最近睡眠不好，但开始早睡", 1_000),
        ),
    )

    private fun TestScope.collectVm(vm: AdvisorViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    }

    @Test
    fun `未授权时发送被阻止，不读取也不保存`() = runTest {
        val f = build(granted = false)
        collectVm(f.vm)
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.InputChange("睡眠不好怎么办"))
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()

        assertTrue(f.vm.uiState.value.messages.isEmpty())
        assertEquals(0, f.engine.calls.size)
        assertNotNull(f.vm.uiState.value.errorMessage)
        assertFalse(f.vm.uiState.value.consentValid)
    }

    @Test
    fun `首次授权后发送，来源与回复一起保存`() = runTest {
        val f = build(granted = false)
        collectVm(f.vm)
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.GrantConsent)
        advanceUntilIdle()
        assertTrue(f.vm.uiState.value.consentValid)

        f.engine.enqueue(AdvisorChatResult.Success("建议从睡眠开始"))
        f.vm.onAction(AdvisorAction.InputChange("睡眠不好，目标怎么调整"))
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()

        val messages = f.vm.uiState.value.messages
        assertEquals(2, messages.size)
        assertEquals(AdvisorRole.ASSISTANT, messages[1].role)
        assertEquals("建议从睡眠开始", messages[1].content)
        assertTrue(messages[1].sources.any { it.type == AdvisorSourceType.GOAL })
        assertTrue(messages[1].sources.any { it.type == AdvisorSourceType.JOURNAL })
        assertTrue(messages[1].sources.any { it.type == AdvisorSourceType.PROGRESS })
    }

    @Test
    fun `关闭日记后新请求不带旧日记上下文，历史按 epoch 隔离`() = runTest {
        val f = build()
        collectVm(f.vm)
        advanceUntilIdle()
        f.engine.enqueue(AdvisorChatResult.Success("第一次回答（含日记）"))
        f.vm.onAction(AdvisorAction.InputChange("睡眠 目标"))
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()
        val firstReply = f.vm.uiState.value.messages[1].content

        f.vm.onAction(AdvisorAction.DismissInfo)
        f.consent.setCategory(ConsentCategory.JOURNAL, false)
        advanceUntilIdle()
        f.engine.enqueue(AdvisorChatResult.Success("第二次回答"))
        f.vm.onAction(AdvisorAction.InputChange("睡眠 目标 继续"))
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()

        val lastCall = f.engine.calls.last().joinToString("\n") { it.content }
        assertFalse(lastCall.contains("最近睡眠不好"))
        assertFalse(lastCall.contains(firstReply))
        val lastAssistant = f.vm.uiState.value.messages.last()
        assertTrue(lastAssistant.sources.none { it.type == AdvisorSourceType.JOURNAL })
    }

    @Test
    fun `关闭目标后不带目标摘要`() = runTest {
        val f = build()
        collectVm(f.vm)
        advanceUntilIdle()
        f.consent.setCategory(ConsentCategory.GOALS, false)
        advanceUntilIdle()
        f.engine.enqueue(AdvisorChatResult.Success("回答"))
        f.vm.onAction(AdvisorAction.InputChange("健康 目标"))
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()

        val lastCall = f.engine.calls.last().joinToString("\n") { it.content }
        assertFalse(lastCall.contains("长期保持规律运动"))
        assertTrue(f.vm.uiState.value.messages.last().sources.none { it.type == AdvisorSourceType.GOAL })
    }

    @Test
    fun `更换服务地址后授权失效，必须重新授权`() = runTest {
        val f = build()
        collectVm(f.vm)
        advanceUntilIdle()
        assertTrue(f.vm.uiState.value.consentValid)

        f.config.save("https://other.example.com/v1", "m", null)
        advanceUntilIdle()
        assertFalse(f.vm.uiState.value.consentValid)

        f.vm.onAction(AdvisorAction.InputChange("hi"))
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()
        assertEquals(0, f.engine.calls.size)
        assertTrue(f.vm.uiState.value.messages.isEmpty())

        f.vm.onAction(AdvisorAction.GrantConsent)
        advanceUntilIdle()
        assertTrue(f.vm.uiState.value.consentValid)
    }

    @Test
    fun `失败重试不重复用户消息`() = runTest {
        val f = build()
        collectVm(f.vm)
        advanceUntilIdle()
        f.engine.enqueue(AdvisorChatResult.Failure(AdvisorError.timeout()))
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
        // 失败消息保留作为错误证据；重试只追加新的助手消息，不重复用户消息
        assertEquals(3, messages.size)
        assertEquals(1, messages.count { it.role == AdvisorRole.USER })
        assertEquals(AdvisorMessageStatus.FAILED, messages[1].status)
        assertEquals("重试成功", messages[2].content)
    }

    @Test
    fun `取消请求保存 CANCELLED 且无假回复`() = runTest {
        val f = build(engine = ScriptedAdvisorEngine().apply { blockUntilCancelled = true })
        collectVm(f.vm)
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.InputChange("取消我"))
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.CancelRequest)
        advanceUntilIdle()
        val last = f.vm.uiState.value.messages.last()
        assertEquals(AdvisorMessageStatus.CANCELLED, last.status)
        assertEquals("", last.content)
    }

    @Test
    fun `runtime endpoint 与授权接收方不一致时不发送`() = runTest {
        val f = build()
        collectVm(f.vm)
        advanceUntilIdle()
        // UI 快照仍是旧地址，但运行时配置已被改成新地址：旧代码会向新地址发送
        f.config.runtimeEndpointOverride = "https://attacker.example.com/v1"
        f.engine.enqueue(AdvisorChatResult.Success("不应发送"))
        f.vm.onAction(AdvisorAction.InputChange("hi"))
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()

        assertEquals(0, f.engine.calls.size)
        val last = f.vm.uiState.value.messages.last()
        assertEquals(AdvisorMessageStatus.CANCELLED, last.status)
        assertTrue(last.errorMessage.orEmpty().contains("服务配置或授权已变化"))
    }

    @Test
    fun `请求中服务地址变化会取消，不保存旧回复`() = runTest {
        val f = build(engine = ScriptedAdvisorEngine().apply { blockUntilCancelled = true })
        collectVm(f.vm)
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.InputChange("hi"))
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()
        assertEquals(1, f.engine.calls.size)

        f.config.save("https://new.example.com/v1", "new-model", null)
        advanceUntilIdle()
        val last = f.vm.uiState.value.messages.last()
        assertEquals(AdvisorMessageStatus.CANCELLED, last.status)
        assertTrue(f.vm.uiState.value.messages.none { it.content == "default reply" })
    }

    @Test
    fun `整理为目标草案并持久化`() = runTest {
        val f = build()
        collectVm(f.vm)
        advanceUntilIdle()
        f.engine.enqueue(AdvisorChatResult.Success(proposalJson()))
        f.vm.onAction(AdvisorAction.RequestProposal)
        advanceUntilIdle()

        val message = f.vm.uiState.value.messages.last()
        assertNotNull(f.vm.uiState.value.proposals[message.id])
        assertNotNull(f.vm.uiState.value.proposals[message.id]!!.draft.nodes.firstOrNull())
        assertEquals(1, f.vm.uiState.value.proposals.size)
    }

    @Test
    fun `草案解析失败后重试仍使用 proposalMode 且不重复用户消息`() = runTest {
        val f = build()
        collectVm(f.vm)
        advanceUntilIdle()
        f.engine.enqueue(AdvisorChatResult.Success("不是 JSON"))
        f.vm.onAction(AdvisorAction.RequestProposal)
        advanceUntilIdle()
        val failed = f.vm.uiState.value.messages.last()
        assertNotNull(failed.proposalError)
        assertTrue(failed.proposalMode)
        assertEquals(1, f.vm.uiState.value.messages.count { it.role == AdvisorRole.USER })

        f.engine.enqueue(AdvisorChatResult.Success(proposalJson()))
        f.vm.onAction(AdvisorAction.Retry(failed.id))
        advanceUntilIdle()
        val messages = f.vm.uiState.value.messages
        assertEquals(1, messages.count { it.role == AdvisorRole.USER })
        assertEquals(3, messages.size)
        assertTrue(failed.proposalError != null)
        assertNotNull(f.vm.uiState.value.proposals[messages.last().id])
        assertTrue(messages.last().proposalMode)
    }

    @Test
    fun `快速双击确认只调用一次 apply`() = runTest {
        val f = build()
        collectVm(f.vm)
        advanceUntilIdle()
        f.engine.enqueue(AdvisorChatResult.Success(proposalJson()))
        f.vm.onAction(AdvisorAction.RequestProposal)
        advanceUntilIdle()
        val proposal = f.vm.uiState.value.proposals.values.first()
        val draft = proposal.draft
        f.vm.onAction(AdvisorAction.ApplyProposal(proposal.id, draft, draft.nodes.map { it.tempId }.toSet()))
        f.vm.onAction(AdvisorAction.ApplyProposal(proposal.id, draft, draft.nodes.map { it.tempId }.toSet()))
        advanceUntilIdle()
        // ViewModel 的 loading 防抖；真实事务/幂等由 GoalProposalApplyService 与真机验证
        assertEquals(1, f.apply.applyCalls)
    }

    @Test
    fun `草案 JSON 非法时保留错误，不生成草案`() = runTest {
        val f = build()
        collectVm(f.vm)
        advanceUntilIdle()
        f.engine.enqueue(AdvisorChatResult.Success("这不是 JSON"))
        f.vm.onAction(AdvisorAction.RequestProposal)
        advanceUntilIdle()

        val message = f.vm.uiState.value.messages.last()
        assertTrue(f.vm.uiState.value.proposals.isEmpty())
        assertNotNull(message.proposalError)
        assertTrue(message.content.contains("这不是 JSON"))
    }

    private fun proposalJson(): String =
        """
        {"schemaVersion":1,"summary":"从睡眠开始调整健康目标","assumptions":["每周三次可行"],
         "risks":["时间不足"],"goals":[
         {"tempId":"g1","level":"LONG","title":"健康","description":"稳定睡眠与运动","domain":"HEALTH",
          "parentTempId":null,"parentGoalId":null,"startDate":null,"dueDate":null,"basis":"对话"},
         {"tempId":"s1","level":"STAGE","title":"建立睡眠节律","description":null,"domain":"HEALTH",
          "parentTempId":"g1","parentGoalId":null,"startDate":null,"dueDate":null,"basis":"近期睡眠"},
         {"tempId":"a1","level":"ACTION","title":"每周三次早睡","description":null,"domain":"HEALTH",
          "parentTempId":"s1","parentGoalId":null,"startDate":null,"dueDate":null,"basis":"可执行"}]}
        """.trimIndent()
}
