package com.mengyalife.app.advisor

import com.mengyalife.app.domain.advisor.AdvisorContextAssembler
import com.mengyalife.app.data.advisor.RoomConversationRepository
import com.mengyalife.app.data.insight.InsightGenerationResult
import com.mengyalife.app.data.insight.InsightService
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
import com.mengyalife.app.domain.insight.InsightReference
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
        val records: FakeRecordProposalRepository,
        val engine: ScriptedAdvisorEngine,
        val apply: FakeGoalProposalApply,
        val planApply: FakePlanRecordApply,
        val insightService: InsightService,
        val insightEngine: ScriptedAdvisorEngine,
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
        val repository = RoomConversationRepository(dao, now = { System.currentTimeMillis() })
        val data = FakeAdvisorDataRepository(snapshot)
        val proposals = FakeGoalProposalRepository()
        val apply = FakeGoalProposalApply()
        val records = FakeRecordProposalRepository()
        val recordApply = FakeProgressRecordApply()
        val goalApply = FakeGoalRecordApply()
        val planApply = FakePlanRecordApply()
        val insightDao = FakeInsightDao()
        val insightEngine = ScriptedAdvisorEngine()
        var insightId = 0
        val insightService = InsightService(
            dataRepository = data,
            consentRepository = consent,
            configRepository = config,
            networkEngine = insightEngine,
            fakeEngine = null,
            dao = insightDao,
            now = { 1_800_000_000_000L },
            newId = { "vm-insight-${++insightId}" },
        )
        val vm = AdvisorViewModel(
            conversations = repository,
            configRepository = config,
            consentRepository = consent,
            dataRepository = data,
            proposalRepository = proposals,
            proposalApplyService = apply,
            recordRepository = records,
            recordApplyService = recordApply,
            diaryApplyService = FakeDiaryRecordApply(),
            goalApplyService = goalApply,
            planApplyService = planApply,
            insightService = insightService,
            assembler = AdvisorContextAssembler(),
            networkEngine = engine,
            fakeEngine = null,
        )
        return Fixture(
            vm, dao, config, consent, data, proposals, records, engine, apply,
            planApply, insightService, insightEngine,
        )
    }

    private fun growthSnapshot(): AdvisorContextSnapshot = snapshot().copy(
        goals = snapshot().goals.map { it.copy(title = "长期阅读", description = "保持阅读节奏", domain = GoalDomain.LEARNING) },
        checkins = listOf(
            ContextCheckinRecord(11, 7, 1_700_000_000_000L, "第一周阅读 1 次", 1.0, "CNY"),
            ContextCheckinRecord(12, 7, 1_700_086_400_000L, "第二周阅读 2 次", 2.0, "CNY"),
        ),
    )

    private suspend fun generateSuggestion(
        fixture: Fixture,
        factPrefix: String = "no-recent-progress-",
    ): InsightReference {
        val input = fixture.insightService.currentInput()
        val fact = input.facts.first { it.id.startsWith(factPrefix) }
        val sourceJson = fact.sourceRefs.joinToString(",") { source ->
            """{"type":"${source.type}","id":"${source.recordId}","label":"${source.label}"}"""
        }
        val noRecentProgress = fact.id.startsWith("no-recent-progress-")
        val title = if (noRecentProgress) "是否想为阅读目标安排一个小步骤？" else "阅读记录正在增加，是否调整节奏？"
        val body = if (noRecentProgress) {
            "系统记录显示最近没有新进展；这不代表没有执行，可以由你决定是否试一个小行动。"
        } else {
            "两条同单位记录呈上升趋势，可以由你判断是否要调整计划。"
        }
        fixture.insightEngine.enqueue(
            AdvisorChatResult.Success(
                """{"schemaVersion":1,"summary":"阅读记录有变化","insights":[{"type":"SUGGESTION","title":"$title","body":"$body","isInference":true,"confidence":0.7,"factIds":["${fact.id}"],"sources":[$sourceJson]}]}""",
            ),
        )
        val report = (fixture.insightService.generate(force = true) as InsightGenerationResult.Ready).report
        assertEquals(1, report.insights.size)
        return InsightReference(report.id, report.dataVersion, report.insights.single().id)
    }

    private fun planExtractionJson(title: String = "这周读三次书") =
        """{"schemaVersion":1,"record":{"type":"PLAN","change":"CREATE","title":"$title","timeScope":"这周","intent":"SUGGESTED"}}"""

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

    private suspend fun TestScope.startInsightDiscussion(
        fixture: Fixture,
        factPrefix: String = "no-recent-progress-",
    ): Pair<InsightReference, String> {
        collectVm(fixture.vm)
        advanceUntilIdle()
        val reference = generateSuggestion(fixture, factPrefix)
        fixture.engine.enqueue(AdvisorChatResult.Success("这条建议引用了已记录的事实；是否尝试由你决定。"))
        fixture.vm.onAction(AdvisorAction.DiscussInsight(reference))
        advanceUntilIdle()
        val userMessage = fixture.vm.uiState.value.messages.first { it.role == AdvisorRole.USER }
        return reference to userMessage.id
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

    @Test
    fun `Insight 结构化交接进入唯一对话并持久化来源`() = runTest {
        val fixture = build(snapshot = growthSnapshot())
        val (reference, userMessageId) = startInsightDiscussion(fixture)
        val messages = fixture.vm.uiState.value.messages
        assertEquals(2, messages.size)
        assertEquals(1, messages.count { it.role == AdvisorRole.USER })
        val user = messages.first { it.role == AdvisorRole.USER }
        assertEquals(userMessageId, user.id)
        val insightSource = user.sources.single { it.type == AdvisorSourceType.INSIGHT }
        assertEquals(reference.reportId, insightSource.metadata["reportId"])
        assertEquals(reference.dataVersion, insightSource.metadata["dataVersion"])
        assertTrue(insightSource.metadata["factIds"].orEmpty().contains("no-recent-progress-7"))
        assertTrue(user.sources.any { it.type == AdvisorSourceType.INSIGHT_FACT })
        assertTrue(user.sources.any { it.type == AdvisorSourceType.PROGRESS && it.recordId == "12" })
        val request = fixture.engine.calls.single().joinToString("\n") { it.content }
        assertTrue(request.contains("AI 推测") || request.contains("规则事实"))
        assertTrue(request.contains("原始来源引用"))
        assertEquals(0, fixture.records.saveCalls)
        assertTrue(fixture.vm.uiState.value.proposals.isEmpty())
        assertEquals(1, fixture.engine.calls.size)
        fixture.vm.onAction(AdvisorAction.DiscussInsight(reference))
        advanceUntilIdle()
        assertEquals("重复进入仍复用同一条对话", 2, fixture.vm.uiState.value.messages.size)
        assertEquals("重复进入不重复请求模型", 1, fixture.engine.calls.size)
    }

    @Test
    fun `建议来源数据或授权变化后不追加消息也不调用顾问`() = runTest {
        val edited = build(snapshot = growthSnapshot())
        collectVm(edited.vm)
        advanceUntilIdle()
        val reference = generateSuggestion(edited)
        edited.data.snapshot = growthSnapshot().copy(goals = growthSnapshot().goals.map { it.copy(title = "修改后的目标") })
        edited.vm.onAction(AdvisorAction.DiscussInsight(reference))
        advanceUntilIdle()
        assertTrue(edited.vm.uiState.value.messages.isEmpty())
        assertEquals(0, edited.engine.calls.size)
        assertTrue(edited.vm.uiState.value.errorMessage.orEmpty().contains("变化"))

        val revoked = build(snapshot = growthSnapshot())
        collectVm(revoked.vm)
        advanceUntilIdle()
        val revokedReference = generateSuggestion(revoked)
        revoked.consent.setCategory(ConsentCategory.PROGRESS, false)
        advanceUntilIdle()
        revoked.vm.onAction(AdvisorAction.DiscussInsight(revokedReference))
        advanceUntilIdle()
        assertTrue(revoked.vm.uiState.value.messages.isEmpty())
        assertEquals(0, revoked.engine.calls.size)
    }

    @Test
    fun `用户反对洞察建议只讨论不创建业务候选`() = runTest {
        val fixture = build(snapshot = growthSnapshot())
        startInsightDiscussion(fixture, factPrefix = "trend-")
        fixture.engine.enqueue(AdvisorChatResult.Success("明白，我们保留你的判断，不更改任何目标或计划。"))
        fixture.vm.onAction(AdvisorAction.InputChange("我不同意这个建议，不要调整原目标。"))
        advanceUntilIdle()
        fixture.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()
        assertEquals(0, fixture.records.saveCalls)
        assertTrue(fixture.vm.uiState.value.proposals.isEmpty())
        assertEquals(0, fixture.planApply.calls)
        assertEquals(4, fixture.vm.uiState.value.messages.size)
    }

    @Test
    fun `讨论后的小计划草案引用Insight消息且确认前不落正式计划`() = runTest {
        val fixture = build(snapshot = growthSnapshot())
        val (reference, insightMessageId) = startInsightDiscussion(fixture)
        fixture.engine.enqueue(AdvisorChatResult.Success("可以从一周三次开始，先按你的节奏试试。"))
        fixture.engine.enqueue(AdvisorChatResult.Success(planExtractionJson()))
        fixture.vm.onAction(AdvisorAction.InputChange("这周读三次书"))
        advanceUntilIdle()
        fixture.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()

        val proposal = fixture.vm.uiState.value.records.values.single()
        assertTrue(proposal.draft.isPlan)
        assertEquals("这周读三次书", proposal.draft.planChange?.title)
        assertTrue(proposal.draft.sourceMessageIds.contains(insightMessageId))
        assertEquals(0, fixture.planApply.calls)

        val editedDraft = proposal.draft.copy(
            planChange = proposal.draft.planChange!!.copy(title = "这周读四次书"),
        )
        fixture.vm.onAction(AdvisorAction.UpdateRecordDraft(proposal.id, editedDraft))
        advanceUntilIdle()
        assertEquals("这周读四次书", fixture.vm.uiState.value.records.values.single().draft.planChange?.title)
        fixture.vm.onAction(AdvisorAction.ConfirmRecord(proposal.id, editedDraft))
        advanceUntilIdle()
        assertEquals(1, fixture.planApply.calls)
        assertTrue(fixture.vm.uiState.value.records.values.single().draft.sourceMessageIds.contains(insightMessageId))
        assertEquals(reference.insightId, fixture.vm.uiState.value.messages.first().sources
            .single { it.type == AdvisorSourceType.INSIGHT }.recordId)
    }

    @Test
    fun `顾问不能从日记来源输出诊断标签`() = runTest {
        val fixture = build()
        collectVm(fixture.vm)
        advanceUntilIdle()
        fixture.engine.enqueue(AdvisorChatResult.Success("仅凭日记推断你可能患有抑郁症。"))
        fixture.vm.onAction(AdvisorAction.InputChange("日记里记录了最近睡眠不好，请分析"))
        advanceUntilIdle()
        fixture.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()
        val answer = fixture.vm.uiState.value.messages.last()
        assertEquals(AdvisorRole.ASSISTANT, answer.role)
        assertTrue(answer.sources.any { it.type == AdvisorSourceType.JOURNAL })
        assertFalse(answer.content.contains("抑郁症"))
        assertTrue(answer.content.contains("不能根据日记"))
    }

    @Test
    fun `原始事实在草案确认前失效则阻止应用`() = runTest {
        val fixture = build(snapshot = growthSnapshot())
        startInsightDiscussion(fixture)
        fixture.engine.enqueue(AdvisorChatResult.Success("可以先试一个轻量计划。"))
        fixture.engine.enqueue(AdvisorChatResult.Success(planExtractionJson()))
        fixture.vm.onAction(AdvisorAction.InputChange("这周读三次书"))
        advanceUntilIdle()
        fixture.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()
        val proposal = fixture.vm.uiState.value.records.values.single()
        fixture.data.snapshot = growthSnapshot().copy(checkins = growthSnapshot().checkins.dropLast(1))
        fixture.vm.onAction(AdvisorAction.ConfirmRecord(proposal.id, proposal.draft))
        advanceUntilIdle()
        assertEquals(0, fixture.planApply.calls)
        assertTrue(fixture.vm.uiState.value.recordAction?.error.orEmpty().contains("变化") ||
            fixture.vm.uiState.value.recordAction?.error.orEmpty().contains("不足"))
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
