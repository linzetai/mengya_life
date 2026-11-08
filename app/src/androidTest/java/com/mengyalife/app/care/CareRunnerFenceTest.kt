package com.mengyalife.app.care

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mengyalife.app.domain.advisor.AdvisorChatMessage
import com.mengyalife.app.domain.advisor.AdvisorChatResult
import com.mengyalife.app.data.advisor.AdvisorConfigRepository
import com.mengyalife.app.data.advisor.AdvisorDataRepository
import com.mengyalife.app.data.advisor.AdvisorEngine
import com.mengyalife.app.domain.advisor.AdvisorTestResult
import com.mengyalife.app.data.advisor.AiConfigSaveResult
import com.mengyalife.app.data.advisor.RoomConversationRepository
import com.mengyalife.app.data.care.CarePreferenceRepository
import com.mengyalife.app.data.care.CareRepository
import com.mengyalife.app.data.care.CareRunner
import com.mengyalife.app.data.care.CareSettings
import com.mengyalife.app.data.consent.AiConsentRepository
import com.mengyalife.app.data.consent.ConsentResetResult
import com.mengyalife.app.data.local.MengyaDatabase
import com.mengyalife.app.data.local.advisor.AdvisorMessageEntity
import com.mengyalife.app.data.local.care.CareDecisionEntity
import com.mengyalife.app.data.local.care.CareOpportunityEntity
import com.mengyalife.app.data.repository.SmallPlanRepository
import com.mengyalife.app.data.restore.RestoreException
import com.mengyalife.app.data.restore.RestoreWriteBarrier
import com.mengyalife.app.domain.advisor.AdvisorContextSnapshot
import com.mengyalife.app.domain.advisor.AdvisorError
import com.mengyalife.app.domain.advisor.AdvisorRuntimeConfig
import com.mengyalife.app.domain.advisor.AiConfigState
import com.mengyalife.app.domain.advisor.AiConsentRules
import com.mengyalife.app.domain.advisor.AiConsentState
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.advisor.ContextGoalRecord
import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId
import java.time.ZonedDateTime

private val CARE_FENCE_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

/** 2026-03-10 10:00 Asia/Shanghai：白天，避开 22:00–08:00 免打扰。 */
private val CARE_FENCE_NOW: Long =
    ZonedDateTime.of(2026, 3, 10, 10, 0, 0, 0, CARE_FENCE_ZONE).toInstant().toEpochMilli()

private const val CARE_FENCE_ENDPOINT = "https://care-fence.example.test/v1"
private const val CARE_FENCE_MODEL = "care-fence-model"
private const val CARE_FENCE_GOAL_ID = 7L
private const val AWAIT_TIMEOUT_MS = 15_000L

/**
 * Gate D：Runner 级恢复栅栏交错。
 *
 * 使用真实 Room in-memory DB + 真实 CareRepository / CarePreferenceRepository /
 * SmallPlanRepository / RoomConversationRepository。只替换：
 * - AiConsentRepository：全类别授权，recipient 与 endpoint 匹配；
 * - AdvisorConfigRepository：固定 endpoint/model；
 * - AdvisorDataRepository：返回一个 30 天没有进展的目标，稳定产生 GOAL_STALL；
 * - AdvisorEngine：不联网，从 prompt 解析“机会#N”后返回合法 SEND JSON；
 * - CareSettings：真实 SharedPreferences，关闭通知。
 *
 * 生产代码未改动；只使用 CareRunner 已有的 `beforeCommit` 事务挂起点
 * （生产默认 null）以及 `restoreGeneration` 读口。
 */
@RunWith(AndroidJUnit4::class)
class CareRunnerFenceTest {

    private lateinit var context: Context
    private lateinit var db: MengyaDatabase
    private lateinit var barrier: RestoreWriteBarrier
    private lateinit var settings: CareSettings
    private lateinit var consent: FakeConsentRepository
    private lateinit var config: FakeConfigRepository
    private lateinit var data: FakeAdvisorDataRepository
    private lateinit var engine: FakeCareEngine
    private lateinit var careRepository: CareRepository
    private lateinit var preferences: CarePreferenceRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MengyaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        barrier = RestoreWriteBarrier(initiallyReady = true)
        settings = CareSettings(context).apply {
            enabled = true
            notificationsEnabled = false
            level = "NORMAL"
            dndStartHour = 22
            dndEndHour = 8
            qaDailySendLimit = -1
        }
        consent = FakeConsentRepository()
        config = FakeConfigRepository()
        data = FakeAdvisorDataRepository()
        engine = FakeCareEngine()
        careRepository = CareRepository(db.careDao(), now = { CARE_FENCE_NOW })
        preferences = CarePreferenceRepository(db.carePreferenceDao(), now = { CARE_FENCE_NOW })
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---------------------------------------------------------------------
    // 0. 正向基线：证明夹具确实走通了真实 SEND 提交与通知认领。
    // ---------------------------------------------------------------------
    @Test
    fun runnerCommitsSendAtomicallyAndNotifiesAtMostOnce() = runBlocking {
        val runner = buildRunner()
        val card = withTimeout(AWAIT_TIMEOUT_MS) { runner.runOnce() }
        assertNotNull("夹具必须真正产生 SEND 卡片，否则后续“无残留”断言没有意义", card)

        assertEquals(1, db.advisorMessageDao().loadAll().size)
        val decision = db.careDao().latestSentDecision()
        assertNotNull("SEND 决策必须落库", decision)
        assertEquals("SEND", decision!!.action)
        assertEquals("SENT", db.careDao().findOpportunity(card!!.opportunityId)!!.status)
        assertNotNull("消息 id 必须回填到决策", decision.messageId)
        assertNotNull("delivered_at 必须回填", decision.deliveredAt)
        assertNull("通知尚未认领时 notified_at 必须为空", decision.notifiedAt)

        // CareWorker 的通知认领语义：最多一次。
        assertTrue(careRepository.claimNotification(decision.id))
        assertFalse("第二次认领必须失败", careRepository.claimNotification(decision.id))
        assertNotNull(db.careDao().latestSentDecision()!!.notifiedAt)
    }

    // ---------------------------------------------------------------------
    // a-1. 恢复先完成（含生产语义的授权重置），再调用 runOnce。
    // ---------------------------------------------------------------------
    @Test
    fun restoreBeforeRunOnceProducesNoMessageDecisionOrNotification() = runBlocking {
        withTimeout(AWAIT_TIMEOUT_MS) {
            barrier.withExclusive {
                // 生产恢复完成后会 reset 授权，避免旧授权把恢复后的数据继续送出。
                consent.resetAfterRestore(barrier.generation())
            }
        }
        barrier.setReady(true)
        assertEquals(1L, barrier.generation())
        assertFalse("恢复后授权必须是未授权", consent.current().granted)

        val runner = buildRunner()
        val card = withTimeout(AWAIT_TIMEOUT_MS) { runner.runOnce() }

        assertNull(card)
        assertEquals("恢复后不得再调用模型", 0, engine.calls.size)
        assertEquals(0, db.advisorMessageDao().loadAll().size)
        assertEquals(0, db.careDao().allDecisions().size)
        assertEquals(0, db.careDao().loadAllOpportunities().size)
        assertNull(db.careDao().latestSentDecision())
    }

    // ---------------------------------------------------------------------
    // a-2. 恢复发生在模型调用之前（runOnce 正在读 snapshot）。
    //     恢复完成后旧 generation 的结果必须被丢弃，模型不得被调用。
    // ---------------------------------------------------------------------
    @Test
    fun restoreBeforeModelCallDiscardsRunWithoutMessageOrDecision() = runBlocking {
        val snapshotEntered = CompletableDeferred<Unit>()
        val snapshotRelease = CompletableDeferred<Unit>()
        data.beforeSnapshot = {
            snapshotEntered.complete(Unit)
            snapshotRelease.await()
        }
        val runner = buildRunner()
        val run = async(Dispatchers.IO) { runCatching { runner.runOnce() } }

        withTimeout(AWAIT_TIMEOUT_MS) { snapshotEntered.await() }
        // 模型还没被调用、Runner 也还没拿写锁：恢复可以立刻完成。
        withTimeout(AWAIT_TIMEOUT_MS) { barrier.withExclusive { } }
        barrier.setReady(true)
        assertEquals(1L, barrier.generation())

        snapshotRelease.complete(Unit)
        val result = withTimeout(AWAIT_TIMEOUT_MS) { run.await() }
        assertTrue("旧 generation 的 runOnce 不应抛异常，只应安静放弃", result.isSuccess)
        assertNull(result.getOrNull())

        assertEquals("恢复后不得再调用模型", 0, engine.calls.size)
        assertEquals(0, db.advisorMessageDao().loadAll().size)
        assertEquals(0, db.careDao().allDecisions().size)
        // 0.24.2.1：ensure() 也受代次栅栏约束，旧代次不得留下候选。
        assertEquals(0, db.careDao().loadAllOpportunities().size)
    }

    // ---------------------------------------------------------------------
    // a-3. 恢复发生在模型调用之后（runOnce 正在 engine.chat 挂起）。
    //     模型返回后 boundOk 失败，结果必须被丢弃，不写消息/决策/通知。
    // ---------------------------------------------------------------------
    @Test
    fun restoreDuringModelCallDiscardsReturnedSendWithoutRows() = runBlocking {
        val chatEntered = CompletableDeferred<Unit>()
        val chatRelease = CompletableDeferred<Unit>()
        engine.beforeChat = {
            chatEntered.complete(Unit)
            chatRelease.await()
        }
        val runner = buildRunner()
        val run = async(Dispatchers.IO) { runCatching { runner.runOnce() } }

        withTimeout(AWAIT_TIMEOUT_MS) { chatEntered.await() }
        // 模型请求期间 Runner 没有持有写锁，恢复可以完成并使 generation 失效。
        withTimeout(AWAIT_TIMEOUT_MS) { barrier.withExclusive { } }
        barrier.setReady(true)
        assertEquals(1L, barrier.generation())

        chatRelease.complete(Unit)
        val result = withTimeout(AWAIT_TIMEOUT_MS) { run.await() }
        assertTrue(result.isSuccess)
        assertNull("恢复前的模型结果必须被丢弃", result.getOrNull())
        assertEquals(1, engine.calls.size)

        assertEquals(0, db.advisorMessageDao().loadAll().size)
        assertEquals(0, db.careDao().allDecisions().size)
        assertNull(db.careDao().latestSentDecision())
        val opportunities = db.careDao().loadAllOpportunities()
        assertEquals(1, opportunities.size)
        assertEquals("PENDING", opportunities.single().status)
    }

    // ---------------------------------------------------------------------
    // b. claim 之后、写消息之前恢复：restore 必须等待当前写事务结束，
    //    不能插入到 claim/append/decision 之间。
    //
    // 说明（重要）：RestoreWriteBarrier.withWrite 在事务的整个生命周期持有
    // mutex，withExclusive 只能在写锁释放后拿到 mutex 并 generation+1。
    // 因此“恢复在事务内部完成”在锁语义下不可能发生——这正是栅栏要证明的
    // 串行性。本用例证明：
    //   1) 恢复请求在事务挂起期间无法进入（ready 仍为 true、body 未执行）；
    //   2) 事务原子地完成（消息+决策+SENT 一起可见）；
    //   3) 恢复随后按生产语义替换数据库，旧事务结果不残留；
    //   4) 通知 claim 最多一次，恢复后旧 decision 不再可认领。
    // 旧 generation 的提交在恢复之后被拒绝的路径由 c-1/c-2 覆盖。
    // ---------------------------------------------------------------------
    @Test
    fun deferredDuringModelCallPreventsSendAndLeavesPending() = runBlocking {
        engine.beforeChat = {
            val pending = db.careDao().pendingOpportunities().first()
            db.careDao().insertFeedback(
                com.mengyalife.app.data.local.care.CareFeedbackEntity(
                    opportunityId = pending.id, kind = "DEFERRED", createdAt = CARE_FENCE_NOW,
                ),
            )
        }
        val runner = buildRunner()
        val result = runWithTimeout { runner.runOnce() }
        assertNull(result)
        assertEquals(0, db.advisorMessageDao().loadAll().size)
        // 不得出现新的 SEND；机会回到 PENDING 等 24h 后再评估。
        assertEquals(0, db.careDao().allDecisions().count { it.action == "SEND" })
        assertEquals("PENDING", db.careDao().loadAllOpportunities().single().status)
    }

    @Test
    fun restoreDuringModelFailureLeavesNoDropDecision() = runBlocking {
        engine.failure = true
        engine.beforeChat = {
            barrier.withExclusive { db.clearAllTables() }
            barrier.setReady(true)
        }
        val runner = buildRunner()
        val result = runCatching { runner.runOnce() }
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
        assertEquals(1, engine.calls.size) // 模型被调用；返回失败后 DROP 写被栅栏拒绝
        assertEquals(0, db.careDao().allDecisions().size)
        assertEquals(0, db.advisorMessageDao().loadAll().size)
        assertEquals(0, db.careDao().loadAllOpportunities().size)
    }

    @Test
    fun restoreWaitsForClaimedSendThenReplacementLeavesNoResidue() = runBlocking {
        val claimed = CompletableDeferred<Unit>()
        val writeRelease = CompletableDeferred<Unit>()
        val runner = buildRunner(
            beforeCommit = {
                claimed.complete(Unit)
                writeRelease.await()
            },
        )
        val run = async(Dispatchers.IO) { runCatching { runner.runOnce() } }

        withTimeout(AWAIT_TIMEOUT_MS) { claimed.await() }
        // beforeCommit 只在 claimSending 成功后才执行，因此 claimed 触发本身就证明
        // 事务内已经 claim。此时旧写锁 + Room 事务同时被持有，**不能**再从测试线程
        // 读 in-memory DB：Room 内存库是单连接，外部读会等事务释放而自死锁。
        assertTrue("写事务应已开始", run.isActive)

        val restoreAttempted = CompletableDeferred<Unit>()
        val restoreBodyStarted = CompletableDeferred<Unit>()
        val restoreBodyRelease = CompletableDeferred<Unit>()
        val restore = async(Dispatchers.IO) {
            restoreAttempted.complete(Unit)
            barrier.withExclusive {
                restoreBodyStarted.complete(Unit)
                restoreBodyRelease.await()
                // 生产恢复会用备份内容整体替换本地表；这里用 clearAllTables 近似，
                // 重点验证“替换开始前旧事务已完整结束”，不会出现半写状态。
                db.clearAllTables()
            }
        }

        withTimeout(AWAIT_TIMEOUT_MS) { restoreAttempted.await() }
        delay(200)
        assertFalse("恢复必须等待 in-flight 写事务，不能进入维护体", restoreBodyStarted.isCompleted)
        assertTrue("写事务未结束时栅栏必须仍为 ready", barrier.isReady())
        assertFalse("写事务未结束时 runOnce 不能返回", run.isCompleted)

        writeRelease.complete(Unit)
        val result = withTimeout(AWAIT_TIMEOUT_MS) { run.await() }
        val card = result.getOrNull()
        assertNotNull("释放写锁后旧 generation 的事务应完整提交并返回卡片", card)
        withTimeout(AWAIT_TIMEOUT_MS) { restoreBodyStarted.await() }
        assertFalse("恢复进入维护体后 ready 必须为 false", barrier.isReady())
        assertEquals("恢复等待期间 generation 不能提前递增", 1L, barrier.generation())

        // 恢复开始前，事务的四个副作用必须同时存在（原子提交）。
        assertEquals(1, db.advisorMessageDao().loadAll().size)
        val decision = db.careDao().latestSentDecision()
        assertNotNull(decision)
        assertEquals("SENT", db.careDao().findOpportunity(card!!.opportunityId)!!.status)
        assertNull("尚未 claim 通知", decision!!.notifiedAt)

        // CareWorker 风格：runOnce 返回后 claim 通知；最多一次。
        assertTrue(careRepository.claimNotification(decision.id))
        assertFalse(careRepository.claimNotification(decision.id))
        assertNotNull(db.careDao().latestSentDecision()!!.notifiedAt)

        // 恢复替换数据后：旧消息/决策/机会全部不残留，旧 decision 不能再被认领。
        restoreBodyRelease.complete(Unit)
        withTimeout(AWAIT_TIMEOUT_MS) { restore.await() }
        assertEquals(0, db.advisorMessageDao().loadAll().size)
        assertEquals(0, db.careDao().allDecisions().size)
        assertEquals(0, db.careDao().loadAllOpportunities().size)
        assertFalse("恢复后旧 decision 不得再被 claim", careRepository.claimNotification(decision.id))
        assertNull(db.careDao().latestSentDecision())
    }

    // ---------------------------------------------------------------------
    // c-1. 栅栏级等价证明：恢复先完成（generation+1、ready=false→放行），
    //      再用旧 generation 尝试 commit，必须在进入 block 前抛 RestoreException，
    //      数据库行数完全不变。
    // ---------------------------------------------------------------------
    @Test
    fun staleGenerationWriteAfterRestoreIsRejectedAndRowsUnchanged() = runBlocking {
        val opportunityId = seedOpportunity(status = "PENDING")
        db.advisorMessageDao().insert(seedMessage("seed-message"))
        db.careDao().insertDecision(seedDecision(opportunityId, action = "DROP"))

        val oldGeneration = barrier.generation()
        withTimeout(AWAIT_TIMEOUT_MS) { barrier.withExclusive { } }
        barrier.setReady(true)
        assertEquals(oldGeneration + 1, barrier.generation())

        var blockEntered = false
        val failure = runCatching {
            barrier.withWrite(oldGeneration) {
                blockEntered = true
                db.advisorMessageDao().insert(seedMessage("stale-message"))
                db.careDao().insertDecision(seedDecision(opportunityId, action = "SEND"))
                db.careDao().updateOpportunityStatus(opportunityId, "SENT", CARE_FENCE_NOW)
            }
        }.exceptionOrNull()

        assertTrue("旧 generation 的写必须被拒绝", failure is RestoreException)
        assertEquals("RESTORE_STALE_RESULT", (failure as RestoreException).code)
        assertFalse("被拒绝的写不能进入 block", blockEntered)
        assertEquals("数据库行数必须不变", 1, db.advisorMessageDao().loadAll().size)
        assertEquals(1, db.careDao().allDecisions().size)
        assertEquals(1, db.careDao().loadAllOpportunities().size)
        assertEquals("PENDING", db.careDao().loadAllOpportunities().single().status)
        assertEquals("DROP", db.careDao().allDecisions().single().action)
        assertNull("不得新增通知认领", db.careDao().recentDecisions().single().notifiedAt)
    }

    // ---------------------------------------------------------------------
    // c-2. Runner 级等价证明：模拟一个在恢复前就捕获了旧 generation 的 Runner
    //      （restoreGeneration 读口返回旧值），它在恢复完成后才走到提交点。
    //      真实 CareRunner 会先 ensure 本地候选、调用模型，然后在
    //      writeBarrier.withWrite(boundGeneration) 处被拒绝：claim/message/
    //      decision/status/notified_at 全部不落库。
    // ---------------------------------------------------------------------
    @Test
    fun runnerThatCapturedOldGenerationCannotClaimOrCommitAfterRestore() = runBlocking {
        val staleGeneration = barrier.generation()
        withTimeout(AWAIT_TIMEOUT_MS) { barrier.withExclusive { } }
        barrier.setReady(true)
        assertEquals(staleGeneration + 1, barrier.generation())

        val runner = buildRunner(restoreGeneration = { staleGeneration })
        val failure = runCatching { withTimeout(AWAIT_TIMEOUT_MS) { runner.runOnce() } }
            .exceptionOrNull()

        // ensure() 也受栅栏约束：旧代次在进入模型前即放弃，不写任何业务行。
        assertNull(failure)
        assertEquals(0, engine.calls.size)
        assertEquals(0, db.careDao().loadAllOpportunities().size)
        assertEquals(0, db.advisorMessageDao().loadAll().size)
        assertEquals(0, db.careDao().allDecisions().size)
        assertNull(db.careDao().latestSentDecision())
    }

    // ---------------------------------------------------------------------
    // helpers / fakes
    // ---------------------------------------------------------------------

    private suspend fun <T> runWithTimeout(block: suspend () -> T): T? =
        withTimeoutOrNull(AWAIT_TIMEOUT_MS) { block() }

    private fun buildRunner(
        beforeCommit: (suspend () -> Unit)? = null,
        restoreGeneration: () -> Long = { barrier.generation() },
    ): CareRunner = CareRunner(
        careRepository = careRepository,
        planRepository = SmallPlanRepository(
            db.smallPlanDao(),
            now = { CARE_FENCE_NOW },
            writeBarrier = barrier,
        ),
        dataRepository = data,
        consentRepository = consent,
        configRepository = config,
        conversations = RoomConversationRepository(
            db.advisorMessageDao(),
            now = { CARE_FENCE_NOW },
            writeBarrier = barrier,
        ),
        engine = engine,
        settings = settings,
        preferences = preferences,
        restoreGeneration = restoreGeneration,
        database = db,
        writeBarrier = barrier,
        beforeCommit = beforeCommit,
        now = { CARE_FENCE_NOW },
        zone = CARE_FENCE_ZONE,
    )

    private suspend fun seedOpportunity(status: String): Long =
        db.careDao().insertOpportunity(
            CareOpportunityEntity(
                type = "GOAL_STALL", sourceId = CARE_FENCE_GOAL_ID, planId = null,
                goalId = CARE_FENCE_GOAL_ID, title = "每天读一点书",
                discoveredAt = CARE_FENCE_NOW, windowStart = CARE_FENCE_NOW,
                windowEnd = CARE_FENCE_NOW + 24L * 3600_000,
                expiresAt = CARE_FENCE_NOW + 48L * 3600_000,
                sensitivity = "NORMAL", status = status,
                dedupeKey = "goal:$CARE_FENCE_GOAL_ID:seed",
                createdAt = CARE_FENCE_NOW, updatedAt = CARE_FENCE_NOW,
            ),
        )

    private fun seedMessage(id: String) = AdvisorMessageEntity(
        id = id,
        role = "ASSISTANT",
        content = "seed",
        createdAt = CARE_FENCE_NOW,
        status = "SENT",
        contextEpoch = 1,
    )

    private fun seedDecision(opportunityId: Long, action: String) = CareDecisionEntity(
        opportunityId = opportunityId,
        action = action,
        reason = "seed",
        sourceRefs = "seed",
        suggestedText = "seed",
        avoidJson = null,
        nextWindowStart = null,
        nextWindowEnd = null,
        modelOk = true,
        createdAt = CARE_FENCE_NOW,
    )

    private class FakeConsentRepository : AiConsentRepository {
        private val mutable = MutableStateFlow(
            AiConsentState(
                granted = true,
                textVersion = AiConsentRules.TEXT_VERSION,
                categoriesVersion = AiConsentRules.CATEGORIES_VERSION,
                grantedAt = CARE_FENCE_NOW,
                recipient = AiConsentRules.normalizeRecipient(CARE_FENCE_ENDPOINT),
                categories = ConsentCategory.entries.toSet(),
                epoch = 1L,
            ),
        )

        override val state: Flow<AiConsentState> = mutable

        override suspend fun current(): AiConsentState = mutable.value

        override suspend fun grant(recipient: String, categories: Set<ConsentCategory>) {
            mutable.value = mutable.value.copy(
                granted = true,
                textVersion = AiConsentRules.TEXT_VERSION,
                categoriesVersion = AiConsentRules.CATEGORIES_VERSION,
                grantedAt = CARE_FENCE_NOW,
                recipient = AiConsentRules.normalizeRecipient(recipient),
                categories = categories,
                epoch = mutable.value.epoch + 1,
            )
        }

        override suspend fun setCategory(category: ConsentCategory, enabled: Boolean) {
            val categories = mutable.value.categories.toMutableSet().apply {
                if (enabled) add(category) else remove(category)
            }
            mutable.value = mutable.value.copy(
                categories = categories,
                epoch = mutable.value.epoch + 1,
            )
        }

        override suspend fun withdraw() {
            mutable.value = AiConsentState(
                granted = false,
                recipient = mutable.value.recipient,
                epoch = mutable.value.epoch + 1,
            )
        }

        override suspend fun resetAfterRestore(generation: Long): ConsentResetResult {
            mutable.value = AiConsentState(
                granted = false,
                epoch = maxOf(mutable.value.epoch + 1, generation),
            )
            return ConsentResetResult.RESET
        }
    }

    private class FakeConfigRepository : AdvisorConfigRepository {
        private val mutable = MutableStateFlow(
            AiConfigState(
                endpoint = CARE_FENCE_ENDPOINT,
                model = CARE_FENCE_MODEL,
                hasKey = true,
            ),
        )

        override val state: Flow<AiConfigState> = mutable

        override suspend fun save(endpoint: String, model: String, apiKey: String?): AiConfigSaveResult =
            AiConfigSaveResult(true)

        override suspend fun deleteKey() = Unit

        override suspend fun runtimeConfig(): AdvisorRuntimeConfig =
            AdvisorRuntimeConfig(CARE_FENCE_ENDPOINT, CARE_FENCE_MODEL, "test-key")

        override suspend fun setUseFakeEngine(enabled: Boolean) = Unit
    }

    /** 一个 30 天没有进展的目标，稳定产生 GOAL_STALL 候选；不联网。 */
    private class FakeAdvisorDataRepository : AdvisorDataRepository {
        var beforeSnapshot: (suspend () -> Unit)? = null

        override suspend fun snapshot(consent: AiConsentState): AdvisorContextSnapshot {
            beforeSnapshot?.invoke()
            return AdvisorContextSnapshot(
                goals = listOf(
                    ContextGoalRecord(
                        id = CARE_FENCE_GOAL_ID,
                        title = "每天读一点书",
                        description = null,
                        level = GoalLevel.LONG_TERM,
                        parentId = null,
                        domain = GoalDomain.LEARNING,
                        status = GoalStatus.IN_PROGRESS,
                        createdAt = CARE_FENCE_NOW - 30L * 24 * 3600_000,
                    ),
                ),
            )
        }
    }

    private class FakeCareEngine : AdvisorEngine {
        val calls = mutableListOf<List<AdvisorChatMessage>>()
        var beforeChat: (suspend () -> Unit)? = null
        var failure: Boolean = false

        override suspend fun chat(
            config: AdvisorRuntimeConfig,
            messages: List<AdvisorChatMessage>,
            maxTokens: Int,
        ): AdvisorChatResult {
            calls += messages
            beforeChat?.invoke()
            if (failure) return AdvisorChatResult.Failure(AdvisorError.unknown())
            val prompt = messages.firstOrNull { it.role == "system" }?.content.orEmpty()
            val id = Regex("机会#(\\d+)").find(prompt)?.groupValues?.get(1)?.toLongOrNull()
                ?: return AdvisorChatResult.Failure(AdvisorError.unknown())
            return AdvisorChatResult.Success(
                """{"action":"SEND","opportunityId":$id,""" +
                    """"reason":"目标停滞，适合温和问候",""" +
                    """"suggestedText":"最近怎么样？要不要聊聊你在读的书？"}""",
            )
        }

        override suspend fun testConnection(config: AdvisorRuntimeConfig): AdvisorTestResult =
            AdvisorTestResult(true, "ok")
    }
}
