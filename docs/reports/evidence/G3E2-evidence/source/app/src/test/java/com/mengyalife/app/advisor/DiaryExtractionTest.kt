package com.mengyalife.app.advisor

import com.mengyalife.app.data.FakeGrowthSignalDao
import com.mengyalife.app.data.growth.DiaryExtractionOutcome
import com.mengyalife.app.data.growth.DiaryExtractionService
import com.mengyalife.app.data.advisor.QaFaultInjector
import com.mengyalife.app.data.growth.QaExtractionGate
import com.mengyalife.app.data.local.JournalEntryDao
import com.mengyalife.app.data.local.JournalEntryEntity
import com.mengyalife.app.data.repository.JournalRepository
import com.mengyalife.app.domain.advisor.AdvisorChatResult
import com.mengyalife.app.domain.advisor.AiConsentRules
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.growth.GrowthSignalStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class TestJournalDao : JournalEntryDao {
    val state = MutableStateFlow<List<JournalEntryEntity>>(emptyList())
    private var nextId = 1L
    override fun observeAll(): Flow<List<JournalEntryEntity>> = state.map { it }
    override suspend fun findById(id: Long): JournalEntryEntity? = state.value.firstOrNull { it.id == id }
    override suspend fun count(): Int = state.value.size
    override suspend fun markInterruptedAnalyses(): Int = 0
    override suspend fun loadAll(): List<JournalEntryEntity> = state.value
    override suspend fun insert(entry: JournalEntryEntity): Long {
        val id = nextId++
        state.value = state.value + entry.copy(id = id)
        return id
    }
    override suspend fun update(entry: JournalEntryEntity): Int {
        if (state.value.none { it.id == entry.id }) return 0
        state.value = state.value.map { if (it.id == entry.id) entry else it }
        return 1
    }
    override suspend fun deleteById(id: Long): Int {
        if (state.value.none { it.id == id }) return 0
        state.value = state.value.filterNot { it.id == id }
        return 1
    }
    override suspend fun markAnalyzing(id: Long, expectedUpdatedAt: Long, hash: String, now: Long): Int {
        val found = state.value.firstOrNull { it.id == id && it.updatedAt == expectedUpdatedAt && it.analysisStatus != "ANALYZING" } ?: return 0
        state.value = state.value.map { if (it.id == id) found.copy(analysisStatus = "ANALYZING", analysisHash = hash, analysisError = null, analysisLastAttemptAt = now, analysisAttempts = it.analysisAttempts + 1) else it }
        return 1
    }
    override suspend fun markCompleted(id: Long, hash: String, now: Long): Int {
        val found = state.value.firstOrNull { it.id == id } ?: return 0
        state.value = state.value.map { if (it.id == id) found.copy(analysisStatus = "COMPLETED", analysisHash = hash, analyzedAt = now, analysisError = null) else it }
        return 1
    }
    override suspend fun markFailed(id: Long, hash: String, error: String, now: Long): Int {
        val found = state.value.firstOrNull { it.id == id } ?: return 0
        state.value = state.value.map { if (it.id == id) found.copy(analysisStatus = "FAILED", analysisHash = hash, analysisError = error) else it }
        return 1
    }
}

class DiaryExtractionTest {

    private val endpoint = "https://api.example.com/v1"

    private data class Env(
        val dao: TestJournalDao,
        val signalDao: FakeGrowthSignalDao,
        val journal: JournalRepository,
        val config: FakeConfigRepository,
        val consent: FakeConsentRepository,
        val engine: ScriptedAdvisorEngine,
        val service: DiaryExtractionService,
    ) {
        suspend fun newDiary(body: String): Long = journal.createEntry(body, "标题")
    }

    private suspend fun env(
        configured: Boolean = true,
        journalAllowed: Boolean = true,
        minInterval: Long = 0,
    ): Env {
        val config = FakeConfigRepository()
        if (configured) config.save(endpoint, "model", "sk-test")
        val consent = FakeConsentRepository()
        if (configured) {
            consent.grant(
                endpoint,
                if (journalAllowed) AiConsentRules.allCategories
                else AiConsentRules.allCategories - ConsentCategory.JOURNAL,
            )
        }
        val dao = TestJournalDao()
        val signalDao = FakeGrowthSignalDao()
        val journal = JournalRepository(dao, now = { 1_000L }, growthSignalDao = signalDao)
        val engine = ScriptedAdvisorEngine()
        val service = DiaryExtractionService(
            journalRepository = journal,
            growthSignalDao = signalDao,
            configRepository = config,
            consentRepository = consent,
            networkEngine = engine,
            fakeEngine = null,
            now = { 1_000L },
            minIntervalMs = minInterval,
        )
        return Env(dao, signalDao, journal, config, consent, engine, service)
    }

    private fun json(vararg evidence: String): String = buildString {
        append("{\"schemaVersion\":1,\"signals\":[")
        evidence.forEachIndexed { i, e ->
            if (i > 0) append(',')
            append("{\"category\":\"EVENT\",\"content\":\"线索$i\",\"evidence\":\"$e\",\"isInference\":${i == 1},\"confidence\":0.8}")
        }
        append("]}")
    }

    @Test
    fun `未配置或未授权不发送`() = runTest {
        val noConfig = env(configured = false)
        val id1 = noConfig.newDiary("今天完成汇报")
        val r1 = noConfig.service.extract(id1)
        assertTrue(r1 is DiaryExtractionOutcome.Skipped)
        assertEquals(0, noConfig.engine.calls.size)

        val noJournal = env(journalAllowed = false)
        val id2 = noJournal.newDiary("今天完成汇报")
        val r2 = noJournal.service.extract(id2)
        assertTrue(r2 is DiaryExtractionOutcome.Skipped)
        assertEquals(0, noJournal.engine.calls.size)
    }

    @Test
    fun `成功提取并同版本幂等`() = runTest {
        val e = env()
        val id = e.newDiary("今天完成了一次重要汇报，感到紧张")
        e.engine.enqueue(AdvisorChatResult.Success(json("今天完成了一次重要汇报", "感到紧张")))
        val first = e.service.extract(id)
        assertTrue(first is DiaryExtractionOutcome.Success)
        assertEquals(2, e.signalDao.rows.value.size)
        assertEquals("COMPLETED", e.dao.state.value.first().analysisStatus)
        val again = e.service.extract(id)
        assertTrue(again is DiaryExtractionOutcome.AlreadyDone)
        assertEquals(1, e.engine.calls.size)
    }

    @Test
    fun `同内容再次保存不重复请求`() = runTest {
        val e = env()
        val id = e.newDiary("今天完成了一次重要汇报")
        e.engine.enqueue(AdvisorChatResult.Success(json("今天完成了一次重要汇报")))
        e.service.extract(id)
        assertEquals(1, e.engine.calls.size)
        assertEquals("COMPLETED", e.dao.state.value.first().analysisStatus)
        e.journal.updateEntry(id, "今天完成了一次重要汇报", "标题")
        val again = e.service.extract(id)
        assertTrue(again is DiaryExtractionOutcome.AlreadyDone)
        assertEquals(1, e.engine.calls.size)
        assertEquals(1, e.signalDao.rows.value.count { it.status == "ACTIVE" })
    }

    @Test
    fun `编辑后旧线索失效并重新提取`() = runTest {
        val e = env()
        val id = e.newDiary("今天完成了一次重要汇报")
        e.engine.enqueue(AdvisorChatResult.Success(json("今天完成了一次重要汇报")))
        e.service.extract(id)
        val old = e.signalDao.rows.value.first()
        e.journal.updateEntry(id, "今天休息了一整天", "标题")
        assertEquals("PENDING", e.dao.state.value.first().analysisStatus)
        assertEquals("STALE", e.signalDao.rows.value.first { it.id == old.id }.status)

        e.engine.enqueue(AdvisorChatResult.Success(json("今天休息了一整天")))
        e.service.extract(id, force = true)
        assertEquals(1, e.signalDao.rows.value.count { it.status == "ACTIVE" })
        assertEquals(GrowthSignalStatus.STALE, GrowthSignalStatus.valueOf(e.signalDao.rows.value.first { it.id == old.id }.status))
    }

    @Test
    fun `解析失败不写线索且可重试`() = runTest {
        val e = env()
        val id = e.newDiary("今天完成了一次重要汇报")
        e.engine.enqueue(AdvisorChatResult.Success("not json"))
        val failed = e.service.extract(id)
        assertTrue(failed is DiaryExtractionOutcome.Failed)
        assertEquals("FAILED", e.dao.state.value.first().analysisStatus)
        assertTrue(e.signalDao.rows.value.isEmpty())

        e.engine.enqueue(AdvisorChatResult.Success(json("今天完成了一次重要汇报")))
        val retry = e.service.extract(id, force = true)
        assertTrue(retry is DiaryExtractionOutcome.Success)
        assertEquals(1, e.signalDao.rows.value.count { it.status == "ACTIVE" })
    }

    @Test
    fun `请求前配置变化不调用引擎`() = runTest {
        val e = env()
        val id = e.newDiary("今天完成了一次重要汇报")
        val service = DiaryExtractionService(
            journalRepository = e.journal,
            growthSignalDao = e.signalDao,
            configRepository = e.config,
            consentRepository = e.consent,
            networkEngine = e.engine,
            fakeEngine = null,
            now = { 1_000L },
            minIntervalMs = 0,
            preRequestHook = { e.config.save("https://new.example.com/v1", "new", null) },
        )
        val result = service.extract(id)
        assertTrue(result is DiaryExtractionOutcome.Failed)
        assertEquals(0, e.engine.calls.size)
        assertTrue(e.signalDao.rows.value.isEmpty())
    }

    @Test
    fun `请求前编辑日记不发送旧内容`() = runTest {
        val e = env()
        val id = e.newDiary("今天完成了一次重要汇报")
        val service = DiaryExtractionService(
            journalRepository = e.journal,
            growthSignalDao = e.signalDao,
            configRepository = e.config,
            consentRepository = e.consent,
            networkEngine = e.engine,
            fakeEngine = null,
            now = { 1_000L },
            minIntervalMs = 0,
            preRequestHook = { e.journal.updateEntry(id, "已经换成新的内容", "标题") },
        )
        val result = service.extract(id)
        assertTrue(result is DiaryExtractionOutcome.Failed || result is DiaryExtractionOutcome.Superseded)
        assertEquals(0, e.engine.calls.size)
        assertTrue(e.signalDao.rows.value.isEmpty())
        assertEquals("已经换成新的内容", e.dao.state.value.first().body)
    }

    @Test
    fun `授权在请求发起前撤回不会调用引擎`() = runTest {
        QaExtractionGate.reset()
        QaExtractionGate.withdrawBeforeRequest = true
        try {
            val e = env()
            val id = e.newDiary("今天完成了一次重要汇报")
            val result = e.service.extract(id)
            assertTrue(result is DiaryExtractionOutcome.Failed)
            assertEquals(0, e.engine.calls.size)
            assertTrue(e.signalDao.rows.value.isEmpty())
            assertEquals("FAILED", e.dao.state.value.first().analysisStatus)
        } finally {
            QaExtractionGate.reset()
        }
    }

    @Test
    fun `旧版本结果不覆盖新版本状态与线索`() = runTest {
        val e = env()
        val id = e.newDiary("今天完成了一次重要汇报")
        val engine = object : com.mengyalife.app.data.advisor.AdvisorEngine {
            override suspend fun chat(
                config: com.mengyalife.app.domain.advisor.AdvisorRuntimeConfig,
                messages: List<com.mengyalife.app.domain.advisor.AdvisorChatMessage>,
                maxTokens: Int,
            ): AdvisorChatResult {
                e.journal.updateEntry(id, "今天已经改成新的内容", "标题")
                return AdvisorChatResult.Success(json("今天完成了一次重要汇报"))
            }
            override suspend fun testConnection(config: com.mengyalife.app.domain.advisor.AdvisorRuntimeConfig) =
                com.mengyalife.app.domain.advisor.AdvisorTestResult(true, "ok")
        }
        val service = DiaryExtractionService(
            journalRepository = e.journal,
            growthSignalDao = e.signalDao,
            configRepository = e.config,
            consentRepository = e.consent,
            networkEngine = engine,
            fakeEngine = null,
            now = { 1_000L },
            minIntervalMs = 0,
        )
        val result = service.extract(id)
        assertTrue(result is DiaryExtractionOutcome.Failed || result is DiaryExtractionOutcome.Superseded)
        assertTrue(e.signalDao.rows.value.isEmpty())
        assertEquals("PENDING", e.dao.state.value.first().analysisStatus)
        assertEquals("今天已经改成新的内容", e.dao.state.value.first().body)
    }

    @Test
    fun `线索插入中途失败回滚且不完成`() = runTest {
        val e = env()
        val id = e.newDiary("今天完成了一次重要汇报，感到紧张")
        e.engine.enqueue(AdvisorChatResult.Success(json("今天完成了一次重要汇报", "感到紧张")))
        QaFaultInjector.failSignalInsertAt = 2
        val transactional = DiaryExtractionService(
            journalRepository = e.journal,
            growthSignalDao = e.signalDao,
            configRepository = e.config,
            consentRepository = e.consent,
            networkEngine = e.engine,
            fakeEngine = null,
            now = { 1_000L },
            minIntervalMs = 0,
            transaction = { block ->
                val before = e.signalDao.rows.value
                try {
                    block()
                } catch (error: Throwable) {
                    e.signalDao.rows.value = before
                    throw error
                }
            },
        )
        try {
            val result = transactional.extract(id)
            assertTrue(result is DiaryExtractionOutcome.Failed)
            assertTrue(e.signalDao.rows.value.isEmpty())
            assertEquals("FAILED", e.dao.state.value.first().analysisStatus)
        } finally {
            QaFaultInjector.reset()
        }
    }

    @Test
    fun `网络失败只标记失败不写线索`() = runTest {
        val e = env()
        val id = e.newDiary("今天完成了一次重要汇报")
        e.engine.enqueue(AdvisorChatResult.Failure(com.mengyalife.app.domain.advisor.AdvisorError.timeout()))
        val result = e.service.extract(id)
        assertTrue(result is DiaryExtractionOutcome.Failed)
        assertTrue(e.signalDao.rows.value.isEmpty())
        assertEquals("FAILED", e.dao.state.value.first().analysisStatus)
    }

    @Test
    fun `处理中撤回授权不写线索`() = runTest {
        val e = env()
        val id = e.newDiary("今天完成了一次重要汇报")
        val consent = e.consent
        val engine = object : com.mengyalife.app.data.advisor.AdvisorEngine {
            override suspend fun chat(
                config: com.mengyalife.app.domain.advisor.AdvisorRuntimeConfig,
                messages: List<com.mengyalife.app.domain.advisor.AdvisorChatMessage>,
                maxTokens: Int,
            ): AdvisorChatResult {
                consent.withdraw()
                return AdvisorChatResult.Success(json("今天完成了一次重要汇报"))
            }
            override suspend fun testConnection(config: com.mengyalife.app.domain.advisor.AdvisorRuntimeConfig) =
                com.mengyalife.app.domain.advisor.AdvisorTestResult(true, "ok")
        }
        val service = DiaryExtractionService(
            journalRepository = e.journal,
            growthSignalDao = e.signalDao,
            configRepository = e.config,
            consentRepository = e.consent,
            networkEngine = engine,
            fakeEngine = null,
            now = { 1_000L },
            minIntervalMs = 0,
        )
        val result = service.extract(id)
        assertTrue(result is DiaryExtractionOutcome.Failed)
        assertTrue(e.signalDao.rows.value.isEmpty())
        assertEquals("FAILED", e.dao.state.value.first().analysisStatus)
    }

    @Test
    fun `删除日记删除线索`() = runTest {
        val e = env()
        val id = e.newDiary("今天完成了一次重要汇报")
        e.engine.enqueue(AdvisorChatResult.Success(json("今天完成了一次重要汇报")))
        e.service.extract(id)
        assertFalse(e.signalDao.rows.value.isEmpty())
        e.journal.deleteEntry(id)
        assertTrue(e.signalDao.rows.value.isEmpty())
    }

    @Test
    fun `频率限制阻止短时间重复请求`() = runTest {
        val e = env(minInterval = 60_000)
        val id = e.newDiary("今天完成了一次重要汇报")
        e.engine.enqueue(AdvisorChatResult.Success(json("今天完成了一次重要汇报")))
        e.service.extract(id)
        e.journal.updateEntry(id, "今天休息", "标题")
        val r = e.service.extract(id)
        assertTrue(r is DiaryExtractionOutcome.TooSoon)
        assertEquals(1, e.engine.calls.size)
    }
}
