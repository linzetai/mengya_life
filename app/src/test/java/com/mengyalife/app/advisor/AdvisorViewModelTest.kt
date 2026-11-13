package com.mengyalife.app.advisor

import com.mengyalife.app.domain.advisor.AdvisorContextAssembler
import com.mengyalife.app.data.advisor.RoomConversationRepository
import com.mengyalife.app.data.insight.InsightGenerationResult
import com.mengyalife.app.data.insight.InsightService
import com.mengyalife.app.data.restore.RestoreWriteBarrier
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
import kotlinx.coroutines.CompletableDeferred
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
        val progressApply: FakeProgressRecordApply,
        val insightService: InsightService,
        val insightEngine: ScriptedAdvisorEngine,
        val barrier: RestoreWriteBarrier,
    )

    private suspend fun build(
        configured: Boolean = true,
        granted: Boolean = true,
        categories: Set<ConsentCategory> = AiConsentRules.allCategories,
        engine: ScriptedAdvisorEngine = ScriptedAdvisorEngine(),
        snapshot: AdvisorContextSnapshot = snapshot(),
    ): Fixture {
        val barrier = RestoreWriteBarrier(initiallyReady = true)
        val config = FakeConfigRepository(writeBarrier = barrier)
        if (configured) config.save(endpoint, "deepseek-chat", "sk-test")
        val consent = FakeConsentRepository(writeBarrier = barrier)
        if (granted) consent.grant(endpoint, categories)
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
            writeBarrier = barrier,
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
            restoreGeneration = { barrier.generation() },
            restoreWriteBarrier = barrier,
            restoreGateActive = { !barrier.isReady() },
        )
        return Fixture(
            vm, dao, config, consent, data, proposals, records, engine, apply,
            planApply, recordApply, insightService, insightEngine, barrier,
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
    fun `Insight handoff 气泡简短自然而系统仍取得完整依据`() = runTest {
        val fixture = build(snapshot = growthSnapshot())
        val (reference, _) = startInsightDiscussion(fixture)
        val handoff = fixture.vm.uiState.value.messages.first { it.role == AdvisorRole.USER }

        assertTrue(handoff.content.length < 100)
        assertFalse(handoff.content.contains("factIds"))
        assertFalse(handoff.content.contains("report="))
        assertFalse(handoff.content.contains(reference.reportId))
        assertEquals(reference.reportId, handoff.sources.single { it.type == AdvisorSourceType.INSIGHT }.metadata["reportId"])
        val actualCall = fixture.engine.calls.single().joinToString("\n") { it.content }
        assertTrue(actualCall.contains("no-recent-progress-7"))
        assertTrue(actualCall.contains(reference.reportId))
    }

    @Test
    fun `Insight goal proposal 在最终 validator 处发现事实变化时不应用`() = runTest {
        val fixture = build(snapshot = growthSnapshot())
        startInsightDiscussion(fixture)
        fixture.engine.enqueue(AdvisorChatResult.Success(proposalJson()))
        fixture.vm.onAction(AdvisorAction.RequestProposal)
        advanceUntilIdle()
        val proposal = fixture.vm.uiState.value.proposals.values.single()
        fixture.apply.beforeSourceValidation = {
            fixture.data.snapshot = growthSnapshot().copy(checkins = emptyList())
        }

        fixture.vm.onAction(AdvisorAction.ApplyProposal(
            proposalId = proposal.id,
            draft = proposal.draft,
            selected = proposal.selectedTempIds,
        ))
        advanceUntilIdle()

        assertEquals(0, fixture.apply.applyCalls)
        assertTrue(fixture.vm.uiState.value.applyState?.error.orEmpty().isNotBlank())
    }

    @Test
    fun `Insight 依据超过上下文预算时不静默截断也不发送`() = runTest {
        val longGoals = (1L..6L).map { id ->
            ContextGoalRecord(
                id = id,
                title = "QA 长来源$id " + "读".repeat(8_000),
                description = null,
                level = GoalLevel.LONG_TERM,
                parentId = null,
                domain = GoalDomain.LEARNING,
                status = GoalStatus.IN_PROGRESS,
                createdAt = 1_700_000_000_000L,
            )
        }
        val snapshot = AdvisorContextSnapshot(
            goals = longGoals,
            checkins = listOf(
                ContextCheckinRecord(51, 1, 1_700_000_000_000L, "第一条真实 QA 记录", 1.0, "次"),
                ContextCheckinRecord(52, 1, 1_700_086_400_000L, "第二条真实 QA 记录", 2.0, "次"),
            ),
        )
        val fixture = build(snapshot = snapshot)
        collectVm(fixture.vm)
        advanceUntilIdle()
        val input = fixture.insightService.currentInput()
        val fact = input.facts.first { it.id == "goal-count" }
        val sources = fact.sourceRefs.take(1).joinToString(",") {
            """{"type":"${it.type}","id":"${it.recordId}","label":"${it.label}"}"""
        }
        fixture.insightEngine.enqueue(AdvisorChatResult.Success(
            """{"schemaVersion":1,"summary":"来自原始记录的 QA 建议","insights":[{"type":"SUGGESTION","title":"试试小步骤","body":"可由你决定是否尝试。","isInference":true,"confidence":0.7,"factIds":["goal-count"],"sources":[$sources]}]}""",
        ))
        val report = (fixture.insightService.generate(force = true) as InsightGenerationResult.Ready).report
        fixture.vm.onAction(AdvisorAction.DiscussInsight(
            InsightReference(report.id, report.dataVersion, report.insights.single().id),
        ))
        advanceUntilIdle()

        assertEquals(0, fixture.engine.calls.size)
        assertTrue(fixture.vm.uiState.value.messages.isEmpty())
        assertTrue(
            "error=${fixture.vm.uiState.value.errorMessage}; info=${fixture.vm.uiState.value.infoMessage}",
            fixture.vm.uiState.value.errorMessage.orEmpty().contains("上下文上限"),
        )
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
    fun `未授权的日记进展目标数据不进入主请求且不触发对应提取`() = runTest {
        val secretSnapshot = snapshot().copy(
            goals = snapshot().goals.map { it.copy(title = "SECRET_GOALS_PRIVATE") },
            checkins = snapshot().checkins.map { it.copy(note = "SECRET_PROGRESS_PRIVATE") },
            journals = snapshot().journals.map { it.copy(body = "SECRET_JOURNAL_PRIVATE") },
        )
        val cases = listOf(
            Triple(ConsentCategory.JOURNAL, "帮我把这段记成日记", "SECRET_JOURNAL_PRIVATE"),
            Triple(ConsentCategory.PROGRESS, "我完成了 3 次跑步", "SECRET_PROGRESS_PRIVATE"),
            Triple(ConsentCategory.GOALS, "我打算这周读三次书", "SECRET_GOALS_PRIVATE"),
        )
        for ((disabled, input, secret) in cases) {
            val fixture = build(
                categories = AiConsentRules.allCategories - disabled,
                snapshot = secretSnapshot,
            )
            collectVm(fixture.vm)
            advanceUntilIdle()
            fixture.engine.enqueue(AdvisorChatResult.Success("收到。"))
            fixture.vm.onAction(AdvisorAction.InputChange(input))
            advanceUntilIdle()
            fixture.vm.onAction(AdvisorAction.Send)
            advanceUntilIdle()

            assertEquals("$disabled: primary only", 1, fixture.engine.calls.size)
            assertFalse(fixture.engine.calls.flatten().any { it.content.contains(secret) })
            assertEquals(0, fixture.records.saveCalls)
        }
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
        assertTrue(f.vm.uiState.value.messages.isEmpty())
        assertTrue(f.vm.uiState.value.errorMessage.orEmpty().contains("服务配置或授权已变化"))
    }

    @Test
    fun `取消进展附加提取不会自动写入或留候选`() = runTest {
        val engine = ScriptedAdvisorEngine().apply {
            enqueue(AdvisorChatResult.Success("主回复"))
            blockOnCall = 2
        }
        val fixture = build(engine = engine)
        collectVm(fixture.vm)
        advanceUntilIdle()
        fixture.vm.onAction(AdvisorAction.InputChange("我今天完成了三次训练，帮我记一下"))
        advanceUntilIdle()
        fixture.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()

        assertEquals(2, fixture.engine.calls.size)
        assertTrue(fixture.vm.uiState.value.sending)
        fixture.vm.onAction(AdvisorAction.CancelRequest)
        advanceUntilIdle()
        assertEquals(0, fixture.records.saveCalls)
        assertEquals(0, fixture.progressApply.applyCalls)
        assertTrue(fixture.vm.uiState.value.records.isEmpty())
    }

    @Test
    fun `请求中排队的 endpoint 变更先于旧回复验收`() = runTest {
        val engine = ScriptedAdvisorEngine().apply { enqueue(AdvisorChatResult.Success("旧 endpoint 回复")) }
        val f = build(engine = engine)
        collectVm(f.vm)
        advanceUntilIdle()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        engine.beforeChat = { call, _ -> if (call == 1) { started.complete(Unit); release.await() } }
        f.vm.onAction(AdvisorAction.InputChange("hi"))
        advanceUntilIdle()
        f.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()
        assertTrue(started.isCompleted)
        assertEquals(1, f.engine.calls.size)

        val configChange = launch { f.config.save("https://new.example.com/v1", "new-model", "sk-new") }
        advanceUntilIdle()
        release.complete(Unit)
        advanceUntilIdle()
        configChange.join()

        assertEquals(1, f.engine.calls.size)
        assertTrue(f.vm.uiState.value.messages.any { it.status == AdvisorMessageStatus.CANCELLED })
        assertTrue(f.vm.uiState.value.messages.none { it.content == "旧 endpoint 回复" })
    }

    @Test
    fun `主回复落下后撤权阻止附加计划提取`() = runTest {
        val engine = ScriptedAdvisorEngine().apply {
            enqueue(AdvisorChatResult.Success("主回复"))
            enqueue(AdvisorChatResult.Success(planExtractionJson()))
        }
        val fixture = build(engine = engine)
        collectVm(fixture.vm)
        advanceUntilIdle()
        var revoked = false
        fixture.dao.beforeInsert = { message ->
            if (!revoked && message.role == "ASSISTANT" && message.content == "主回复") {
                revoked = true
                fixture.consent.setCategory(ConsentCategory.GOALS, false)
            }
        }
        fixture.vm.onAction(AdvisorAction.InputChange("这周读三次书"))
        advanceUntilIdle()
        fixture.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()

        assertEquals(1, fixture.engine.calls.size)
        assertEquals(0, fixture.records.saveCalls)
        assertEquals(0, fixture.planApply.calls)
        assertFalse(fixture.vm.uiState.value.sending)
    }

    @Test
    fun `附加提取期间撤权不会保存或自动应用候选`() = runTest {
        val engine = ScriptedAdvisorEngine().apply {
            enqueue(AdvisorChatResult.Success("主回复"))
            enqueue(AdvisorChatResult.Success(planExtractionJson()))
        }
        val fixture = build(engine = engine)
        collectVm(fixture.vm)
        advanceUntilIdle()
        engine.beforeChat = { call, _ ->
            if (call == 2) fixture.consent.setCategory(ConsentCategory.GOALS, false)
        }
        fixture.vm.onAction(AdvisorAction.InputChange("这周读三次书"))
        advanceUntilIdle()
        fixture.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()

        assertEquals(2, fixture.engine.calls.size)
        assertEquals(0, fixture.records.saveCalls)
        assertEquals(0, fixture.planApply.calls)
        assertTrue(fixture.vm.uiState.value.records.isEmpty())
    }

    @Test
    fun `主回复后切换 endpoint 不向新配置发附加提取`() = runTest {
        val engine = ScriptedAdvisorEngine().apply {
            enqueue(AdvisorChatResult.Success("主回复"))
            enqueue(AdvisorChatResult.Success(planExtractionJson()))
        }
        val fixture = build(engine = engine)
        collectVm(fixture.vm)
        advanceUntilIdle()
        var switched = false
        fixture.dao.beforeInsert = { message ->
            if (!switched && message.role == "ASSISTANT" && message.content == "主回复") {
                switched = true
                fixture.config.save("https://new.example.com/v1", "new-model", "sk-new")
            }
        }
        fixture.vm.onAction(AdvisorAction.InputChange("这周读三次书"))
        advanceUntilIdle()
        fixture.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()

        assertEquals(1, fixture.engine.calls.size)
        assertEquals(0, fixture.records.saveCalls)
        assertEquals(0, fixture.planApply.calls)
    }

    @Test
    fun `主回复后模型名变化阻止向旧模型发起附加提取`() = runTest {
        val engine = ScriptedAdvisorEngine().apply {
            enqueue(AdvisorChatResult.Success("主回复"))
            enqueue(AdvisorChatResult.Success(planExtractionJson()))
        }
        val fixture = build(engine = engine)
        collectVm(fixture.vm)
        advanceUntilIdle()
        var switched = false
        fixture.dao.beforeInsert = { message ->
            if (!switched && message.role == "ASSISTANT" && message.content == "主回复") {
                switched = true
                fixture.config.save(endpoint, "new-model", "sk-new")
            }
        }
        fixture.vm.onAction(AdvisorAction.InputChange("这周读三次书"))
        advanceUntilIdle()
        fixture.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()

        assertEquals(1, fixture.engine.calls.size)
        assertEquals(0, fixture.records.saveCalls)
        assertEquals(0, fixture.planApply.calls)
    }

    @Test
    fun `Restore 排队进入附加提取后旧 generation 不能创建草案`() = runTest {
        val engine = ScriptedAdvisorEngine().apply {
            enqueue(AdvisorChatResult.Success("主回复"))
            enqueue(AdvisorChatResult.Success(planExtractionJson()))
        }
        val fixture = build(engine = engine)
        collectVm(fixture.vm)
        advanceUntilIdle()
        val extractionStarted = CompletableDeferred<Unit>()
        val releaseExtraction = CompletableDeferred<Unit>()
        engine.beforeChat = { call, _ ->
            if (call == 2) {
                extractionStarted.complete(Unit)
                releaseExtraction.await()
            }
        }
        fixture.vm.onAction(AdvisorAction.InputChange("这周读三次书"))
        advanceUntilIdle()
        fixture.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()
        assertTrue(extractionStarted.isCompleted)

        var restoreFinished = false
        launch {
            fixture.barrier.withExclusive {
                fixture.barrier.setReady(true)
                restoreFinished = true
            }
        }
        advanceUntilIdle()
        assertFalse(restoreFinished)
        releaseExtraction.complete(Unit)
        advanceUntilIdle()

        assertTrue(restoreFinished)
        assertEquals(2, fixture.engine.calls.size)
        assertEquals(0, fixture.records.saveCalls)
        assertEquals(0, fixture.planApply.calls)
        assertTrue(fixture.vm.uiState.value.records.isEmpty())
    }

    @Test
    fun `取消附加提取时 sending 仍有效且无候选`() = runTest {
        val engine = ScriptedAdvisorEngine().apply {
            enqueue(AdvisorChatResult.Success("主回复"))
            enqueue(AdvisorChatResult.Success(planExtractionJson()))
            blockOnCall = 2
        }
        val fixture = build(engine = engine)
        collectVm(fixture.vm)
        advanceUntilIdle()
        fixture.vm.onAction(AdvisorAction.InputChange("这周读三次书"))
        advanceUntilIdle()
        fixture.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()

        assertEquals(2, fixture.engine.calls.size)
        assertTrue(fixture.vm.uiState.value.sending)
        fixture.vm.onAction(AdvisorAction.CancelRequest)
        advanceUntilIdle()
        assertFalse(fixture.vm.uiState.value.sending)
        assertEquals(0, fixture.records.saveCalls)
        assertEquals(0, fixture.planApply.calls)
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
    fun `附加计划提取网络超时不回退创建草案且调用数可见`() = runTest {
        val engine = ScriptedAdvisorEngine().apply {
            enqueue(AdvisorChatResult.Success("主回复完成"))
            enqueue(AdvisorChatResult.Failure(AdvisorError.timeout()))
        }
        val fixture = build(engine = engine)
        collectVm(fixture.vm)
        advanceUntilIdle()
        fixture.vm.onAction(AdvisorAction.InputChange("这周读三次书"))
        advanceUntilIdle()
        fixture.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()

        assertEquals(2, fixture.engine.calls.size)
        assertEquals(0, fixture.records.saveCalls)
        assertTrue(fixture.vm.uiState.value.records.isEmpty())
        assertEquals(0, fixture.planApply.calls)
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
    fun `跨过六轮和十五分钟的同一洞察对话仍保存稳定来源`() = runTest {
        val fixture = build(snapshot = growthSnapshot())
        val (reference, handoffId) = startInsightDiscussion(fixture)
        fixture.dao.setCreatedAt(handoffId, System.currentTimeMillis() - 2L * 24 * 60 * 60 * 1000)
        repeat(7) {
            fixture.vm.onAction(AdvisorAction.InputChange("我再想想这个节奏"))
            advanceUntilIdle()
            fixture.vm.onAction(AdvisorAction.Send)
            advanceUntilIdle()
        }
        fixture.engine.enqueue(AdvisorChatResult.Success("可以按自己的节奏尝试。"))
        fixture.engine.enqueue(AdvisorChatResult.Success(planExtractionJson()))
        fixture.vm.onAction(AdvisorAction.InputChange("这周读三次书"))
        advanceUntilIdle()
        fixture.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()

        val proposal = fixture.vm.uiState.value.records.values.single()
        assertEquals(reference, proposal.draft.sourceInsightReference)
        assertTrue(proposal.draft.sourceLinkKnown)
        assertTrue(proposal.draft.sourceMessageIds.contains(handoffId))
        assertEquals(10, fixture.engine.calls.size)
        assertEquals(0, fixture.planApply.calls)
    }

    @Test
    fun `显式切换到无关话题的新计划不继承旧 Insight`() = runTest {
        val fixture = build(snapshot = growthSnapshot())
        val (reference, handoffId) = startInsightDiscussion(fixture)
        fixture.engine.enqueue(AdvisorChatResult.Success("好的，我们开始新的主题。"))
        fixture.engine.enqueue(AdvisorChatResult.Success(planExtractionJson("新话题里的读书安排")))
        fixture.vm.onAction(AdvisorAction.InputChange("聊个别的，我想这周读三次书"))
        advanceUntilIdle()
        fixture.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()

        val proposal = fixture.vm.uiState.value.records.values.single()
        assertTrue(proposal.draft.sourceLinkKnown)
        assertNull(proposal.draft.sourceInsightReference)
        assertFalse(proposal.draft.sourceMessageIds.contains(handoffId))
        assertFalse(fixture.engine.calls.drop(1).flatten().any { it.content.contains(reference.reportId) })
        fixture.data.snapshot = growthSnapshot().copy(checkins = emptyList())
        fixture.vm.onAction(AdvisorAction.ConfirmRecord(proposal.id, proposal.draft))
        advanceUntilIdle()
        assertEquals(1, fixture.planApply.calls)
        assertEquals(reference.insightId, fixture.vm.uiState.value.messages.first().sources
            .single { it.type == AdvisorSourceType.INSIGHT }.recordId)
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
    fun `旧草案最新来源消息无 Insight 时仍检查更早有效引用`() = runTest {
        val fixture = build(snapshot = growthSnapshot())
        val (reference, handoffId) = startInsightDiscussion(fixture)
        fixture.engine.enqueue(AdvisorChatResult.Success("普通对话回复"))
        fixture.vm.onAction(AdvisorAction.InputChange("今天安排不错"))
        advanceUntilIdle()
        fixture.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()
        val latestUser = fixture.vm.uiState.value.messages.last { it.role == AdvisorRole.USER }
        val legacyDraft = com.mengyalife.app.domain.advisor.RecordProposalDraft(
            kind = com.mengyalife.app.domain.advisor.RecordKind.PLAN,
            goalId = null, goalTitle = null, goalCandidates = emptyList(),
            amountKind = com.mengyalife.app.domain.advisor.RecordAmountKind.NONE,
            metricValue = null, metricUnit = null, note = "计划", occurredAt = null,
            pendingFields = emptyList(), intent = com.mengyalife.app.domain.advisor.RecordIntent.EXPLICIT,
            originalText = "旧 Insight 计划草案",
            sourceMessageIds = listOf(handoffId, latestUser.id),
            planChange = com.mengyalife.app.domain.advisor.PlanChangeDraft(
                com.mengyalife.app.domain.advisor.PlanChangeKind.CREATE, title = "旧 Insight 计划草案",
            ),
        )
        val legacy = com.mengyalife.app.data.advisor.StoredRecordProposal(
            id = "legacy-insight-plan", messageId = "missing-source-assistant", createdAt = 1L,
            draft = legacyDraft, status = "DRAFT",
        )
        fixture.records.seed(legacy)
        advanceUntilIdle()
        fixture.data.snapshot = growthSnapshot().copy(checkins = emptyList())

        fixture.vm.onAction(AdvisorAction.ConfirmRecord(legacy.id, legacyDraft))
        advanceUntilIdle()
        assertEquals(0, fixture.planApply.calls)
        assertTrue(fixture.vm.uiState.value.recordAction?.error.orEmpty().contains("已变化") ||
            fixture.vm.uiState.value.recordAction?.error.orEmpty().contains("不足"))
        assertTrue(reference.reportId.isNotBlank())
    }

    @Test
    fun `final Room source validation catches source edit after pre-confirm check`() = runTest {
        val fixture = build(snapshot = growthSnapshot())
        startInsightDiscussion(fixture)
        fixture.engine.enqueue(AdvisorChatResult.Success("可以按自己的节奏试试。"))
        fixture.engine.enqueue(AdvisorChatResult.Success(planExtractionJson()))
        fixture.vm.onAction(AdvisorAction.InputChange("这周读三次书"))
        advanceUntilIdle()
        fixture.vm.onAction(AdvisorAction.Send)
        advanceUntilIdle()
        val proposal = fixture.vm.uiState.value.records.values.single()
        fixture.planApply.beforeSourceValidation = {
            fixture.data.snapshot = growthSnapshot().copy(checkins = growthSnapshot().checkins.dropLast(1))
        }

        fixture.vm.onAction(AdvisorAction.ConfirmRecord(proposal.id, proposal.draft))
        advanceUntilIdle()
        assertEquals(0, fixture.planApply.calls)
        assertTrue(fixture.vm.uiState.value.recordAction?.error.orEmpty().contains("已变化") ||
            fixture.vm.uiState.value.recordAction?.error.orEmpty().contains("不足"))
    }

    @Test
    fun `旧版无 Insight 来源的普通计划草案仍可确认`() = runTest {
        val fixture = build()
        collectVm(fixture.vm)
        advanceUntilIdle()
        val legacyDraft = com.mengyalife.app.domain.advisor.RecordProposalDraft(
            kind = com.mengyalife.app.domain.advisor.RecordKind.PLAN,
            goalId = null,
            goalTitle = null,
            goalCandidates = emptyList(),
            amountKind = com.mengyalife.app.domain.advisor.RecordAmountKind.NONE,
            metricValue = null,
            metricUnit = null,
            note = "旧草案",
            occurredAt = null,
            pendingFields = emptyList(),
            intent = com.mengyalife.app.domain.advisor.RecordIntent.EXPLICIT,
            originalText = "旧版用户草案",
            planChange = com.mengyalife.app.domain.advisor.PlanChangeDraft(
                com.mengyalife.app.domain.advisor.PlanChangeKind.CREATE,
                title = "旧版用户草案",
            ),
        )
        val legacy = com.mengyalife.app.data.advisor.StoredRecordProposal(
            id = "legacy-proposal",
            messageId = "legacy-assistant-message",
            createdAt = 1L,
            draft = legacyDraft,
            status = "DRAFT",
        )
        fixture.records.seed(legacy)
        advanceUntilIdle()

        fixture.vm.onAction(AdvisorAction.ConfirmRecord(legacy.id, legacyDraft))
        advanceUntilIdle()
        assertEquals(1, fixture.planApply.calls)
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
