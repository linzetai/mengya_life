package com.mengyalife.app.advisor

import com.mengyalife.app.data.insight.InsightGenerationResult
import com.mengyalife.app.data.insight.InsightService
import com.mengyalife.app.data.restore.RestoreWriteBarrier
import com.mengyalife.app.data.local.insight.InsightReportDao
import com.mengyalife.app.data.local.insight.InsightReportEntity
import com.mengyalife.app.data.advisor.AdvisorConfigRepository
import com.mengyalife.app.data.advisor.AdvisorEngine
import com.mengyalife.app.data.consent.AiConsentRepository
import com.mengyalife.app.domain.advisor.AdvisorChatMessage
import com.mengyalife.app.domain.advisor.AdvisorChatResult
import com.mengyalife.app.domain.advisor.AdvisorRuntimeConfig
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.advisor.AdvisorContextSnapshot
import com.mengyalife.app.domain.advisor.AiConsentRules
import com.mengyalife.app.domain.advisor.ContextCheckinRecord
import com.mengyalife.app.domain.advisor.ContextGoalRecord
import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalStatus
import com.mengyalife.app.domain.insight.InsightReportStatus
import com.mengyalife.app.domain.insight.InsightReference
import com.mengyalife.app.domain.insight.InsightReferenceResolution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class FakeInsightDao : InsightReportDao {
    val rows = MutableStateFlow<List<InsightReportEntity>>(emptyList())
    override fun observeLatest(): Flow<InsightReportEntity?> = rows.map { it.lastOrNull() }
    override suspend fun latest(): InsightReportEntity? = rows.value.lastOrNull()
    override suspend fun findByDataVersion(version: String): InsightReportEntity? =
        rows.value.lastOrNull { it.dataVersion == version }
    override suspend fun loadAll(): List<InsightReportEntity> = rows.value
    override suspend fun insert(report: InsightReportEntity) { rows.value = rows.value + report }
}

class InsightServiceTest {
    private val endpoint = "https://api.example.com/v1"
    private val now = 1_800_000_000_000L
    private val snapshot = AdvisorContextSnapshot(
        goals = listOf(
            ContextGoalRecord(1, "长期储蓄", null, GoalLevel.LONG_TERM, null, GoalDomain.WEALTH, GoalStatus.IN_PROGRESS, now - 1000),
        ),
        checkins = listOf(
            ContextCheckinRecord(1, 1, now - 3000, "第一期", 1000.0, "CNY"),
            ContextCheckinRecord(2, 1, now - 2000, "第二期", 1500.0, "CNY"),
        ),
    )

    private suspend fun service(
        engine: ScriptedAdvisorEngine,
        data: FakeAdvisorDataRepository = FakeAdvisorDataRepository(snapshot),
        dao: FakeInsightDao = FakeInsightDao(),
    ): Triple<InsightService, ScriptedAdvisorEngine, FakeInsightDao> {
        val config = FakeConfigRepository().apply { save(endpoint, "model", "sk-test") }
        val consent = FakeConsentRepository().apply { grant(endpoint, AiConsentRules.allCategories) }
        val barrier = RestoreWriteBarrier(initiallyReady = true)
        return Triple(
            InsightService(
                dataRepository = data,
                consentRepository = consent,
                configRepository = config,
                networkEngine = engine,
                fakeEngine = null,
                dao = dao,
                now = { now },
                newId = { "r${dao.rows.value.size + 1}" },
                writeBarrier = barrier,
            ),
            engine,
            dao,
        )
    }

    private suspend fun serviceBound(
        engine: AdvisorEngine,
        data: FakeAdvisorDataRepository,
        consent: AiConsentRepository,
        config: AdvisorConfigRepository,
        dao: FakeInsightDao,
        before: suspend () -> Unit,
    ): InsightService = InsightService(
        dataRepository = data,
        consentRepository = consent,
        configRepository = config,
        networkEngine = engine,
        fakeEngine = null,
        dao = dao,
        now = { now },
        beforeRequest = before,
        writeBarrier = RestoreWriteBarrier(initiallyReady = true),
    )

    private suspend fun boundFakes(): Triple<FakeAdvisorDataRepository, FakeConsentRepository, FakeConfigRepository> {
        val data = FakeAdvisorDataRepository(snapshot)
        val consent = FakeConsentRepository().apply { grant(endpoint, AiConsentRules.allCategories) }
        val config = FakeConfigRepository().apply { save(endpoint, "model", "sk-test") }
        return Triple(data, consent, config)
    }

    private fun validJson() = """
        {"schemaVersion":1,"summary":"储蓄在增加","insights":[
        {"type":"POSITIVE","title":"稳步储蓄","body":"两期记录都来自同一单位，金额在增加。","isInference":true,"confidence":0.8,"factIds":["trend-1-CNY"],
         "sources":[{"type":"CHECKIN","id":"2","label":"第二期"}]}]}
    """.trimIndent()

    @Test
    fun `首次生成后同版本走缓存不重复调用模型`() = runTest {
        val engine = ScriptedAdvisorEngine().apply { enqueue(AdvisorChatResult.Success(validJson())) }
        val (svc, eng, dao) = service(engine)
        val first = svc.generate(force = true)
        assertTrue(first is InsightGenerationResult.Ready)
        assertEquals(1, eng.calls.size)
        val second = svc.generate(force = false)
        assertTrue(second is InsightGenerationResult.Ready)
        assertEquals(1, eng.calls.size)
        assertEquals(InsightReportStatus.READY, (second as InsightGenerationResult.Ready).report.status)
        assertEquals(1, dao.rows.value.size)
    }

    @Test
    fun `数据不足时保留规则事实但不发起 AI 请求`() = runTest {
        val sparse = FakeAdvisorDataRepository(snapshot.copy(checkins = snapshot.checkins.take(1), journals = emptyList()))
        val engine = ScriptedAdvisorEngine().apply { enqueue(AdvisorChatResult.Success(validJson())) }
        val (svc, _, dao) = service(engine, data = sparse)
        val result = svc.generate(force = true) as InsightGenerationResult.Failed
        assertEquals(0, engine.calls.size)
        assertTrue(result.report!!.facts.any { it.id == "insufficient" })
        assertTrue(result.report!!.insights.isEmpty())
        assertTrue(dao.rows.value.single().status == "FAILED")
    }

    @Test
    fun `解析失败保留规则事实与失败状态`() = runTest {
        val engine = ScriptedAdvisorEngine().apply { enqueue(AdvisorChatResult.Success("not json")) }
        val (svc, _, dao) = service(engine)
        val result = svc.generate(force = true)
        assertTrue(result is InsightGenerationResult.Failed)
        val report = (result as InsightGenerationResult.Failed).report!!
        assertEquals(InsightReportStatus.FAILED, report.status)
        assertTrue(report.insights.isEmpty())
        assertTrue(report.facts.isNotEmpty())
        assertTrue(report.error!!.contains("解析失败"))
        assertEquals(1, dao.rows.value.size)
    }

    @Test
    fun `数据变化后旧缓存不匹配`() = runTest {
        val engine = ScriptedAdvisorEngine().apply { enqueue(AdvisorChatResult.Success(validJson())) }
        val data = FakeAdvisorDataRepository(snapshot)
        val (svc, eng, _) = service(engine, data)
        svc.generate(force = true)
        data.snapshot = snapshot.copy(
            checkins = snapshot.checkins + ContextCheckinRecord(3, 1, now - 1000, "第三期", 2000.0, "CNY"),
        )
        val current = svc.loadCurrent()
        assertEquals(null, current) // 新版本没有缓存
        assertEquals(1, eng.calls.size) // 打开页面不触发模型
    }

    @Test
    fun `请求前撤回授权不调用引擎`() = runTest {
        val (data, consent, config) = boundFakes()
        val engine = ScriptedAdvisorEngine().apply { enqueue(AdvisorChatResult.Success(validJson())) }
        val dao = FakeInsightDao()
        val svc = serviceBound(engine, data, consent, config, dao) { consent.withdraw() }
        val result = svc.generate(force = true)
        assertTrue(result is InsightGenerationResult.Failed)
        assertEquals(0, engine.calls.size)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun `请求前关闭 JOURNAL 不调用引擎`() = runTest {
        val (data, consent, config) = boundFakes()
        val engine = ScriptedAdvisorEngine().apply { enqueue(AdvisorChatResult.Success(validJson())) }
        val dao = FakeInsightDao()
        val svc = serviceBound(engine, data, consent, config, dao) {
            consent.setCategory(ConsentCategory.JOURNAL, false)
        }
        val result = svc.generate(force = true)
        assertTrue(result is InsightGenerationResult.Failed)
        assertEquals(0, engine.calls.size)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun `请求前切换 endpoint 不调用引擎`() = runTest {
        val (data, consent, config) = boundFakes()
        val engine = ScriptedAdvisorEngine().apply { enqueue(AdvisorChatResult.Success(validJson())) }
        val dao = FakeInsightDao()
        val svc = serviceBound(engine, data, consent, config, dao) {
            config.save("https://other.example.com/v1", "model", null)
        }
        val result = svc.generate(force = true)
        assertTrue(result is InsightGenerationResult.Failed)
        assertEquals(0, engine.calls.size)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun `请求前模型名变化不调用引擎`() = runTest {
        val (data, consent, config) = boundFakes()
        val engine = ScriptedAdvisorEngine().apply { enqueue(AdvisorChatResult.Success(validJson())) }
        val result = serviceBound(engine, data, consent, config, FakeInsightDao()) {
            config.save(endpoint, "changed-model", "sk-test")
        }.generate(force = true)
        assertTrue(result is InsightGenerationResult.Failed)
        assertEquals(0, engine.calls.size)
    }

    @Test
    fun `请求前编辑进展不调用引擎`() = runTest {
        val (data, consent, config) = boundFakes()
        val engine = ScriptedAdvisorEngine().apply { enqueue(AdvisorChatResult.Success(validJson())) }
        val dao = FakeInsightDao()
        val svc = serviceBound(engine, data, consent, config, dao) {
            data.snapshot = snapshot.copy(
                checkins = snapshot.checkins + ContextCheckinRecord(9, 1, now - 100, "new", 3000.0, "CNY"),
            )
        }
        val result = svc.generate(force = true)
        assertTrue(result is InsightGenerationResult.Failed)
        assertEquals(0, engine.calls.size)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun `Insight 网络失败后的事实变化不会保存旧 FAILED 事实`() = runTest {
        val (data, consent, config) = boundFakes()
        val engine = ScriptedAdvisorEngine().apply {
            enqueue(AdvisorChatResult.Failure(
                com.mengyalife.app.domain.advisor.AdvisorError(
                    com.mengyalife.app.domain.advisor.AdvisorError.OFFLINE, "网络不可用",
                ),
            ))
        }
        val dao = FakeInsightDao()
        val service = InsightService(
            dataRepository = data,
            consentRepository = consent,
            configRepository = config,
            networkEngine = engine,
            fakeEngine = null,
            dao = dao,
            now = { now },
            beforeWrite = {
                data.snapshot = snapshot.copy(checkins = snapshot.checkins.dropLast(1))
            },
            writeBarrier = RestoreWriteBarrier(initiallyReady = true),
        )

        val result = service.generate(force = true)
        assertEquals(1, engine.calls.size)
        assertTrue(result is InsightGenerationResult.Failed)
        assertNull((result as InsightGenerationResult.Failed).report)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun `请求期间数据变化调用一次但不写入 AI 结果`() = runTest {
        val (data, consent, config) = boundFakes()
        val base = ScriptedAdvisorEngine().apply { enqueue(AdvisorChatResult.Success(validJson())) }
        val engine = object : AdvisorEngine {
            override suspend fun chat(
                config: AdvisorRuntimeConfig,
                messages: List<AdvisorChatMessage>,
                maxTokens: Int,
            ): AdvisorChatResult {
                data.snapshot = snapshot.copy(
                    checkins = snapshot.checkins + ContextCheckinRecord(10, 1, now - 50, "new", 5000.0, "CNY"),
                )
                return base.chat(config, messages, maxTokens)
            }

            override suspend fun testConnection(config: AdvisorRuntimeConfig) = base.testConnection(config)
        }
        val dao = FakeInsightDao()
        val svc = serviceBound(engine, data, consent, config, dao) {}
        val result = svc.generate(force = true)
        assertTrue(result is InsightGenerationResult.Failed)
        assertEquals(1, base.calls.size)
        assertTrue(dao.rows.value.none { it.status == "READY" })
    }

    private class MutableClock(var value: Long)

    private data class ReferenceFixture(
        val service: InsightService,
        val data: FakeAdvisorDataRepository,
        val consent: FakeConsentRepository,
        val reference: InsightReference,
        val clock: MutableClock,
    )

    private suspend fun referenceFixture(): ReferenceFixture {
        val data = FakeAdvisorDataRepository(snapshot)
        val consent = FakeConsentRepository().apply { grant(endpoint, AiConsentRules.allCategories) }
        val config = FakeConfigRepository().apply { save(endpoint, "model", "sk-test") }
        val dao = FakeInsightDao()
        var id = 0
        val clock = MutableClock(now)
        val engine = ScriptedAdvisorEngine().apply {
            enqueue(
                AdvisorChatResult.Success(
                    """{"schemaVersion":1,"summary":"记录有变化","insights":[{"type":"SUGGESTION","title":"尝试稳定节奏","body":"可以继续记录稳定的一周节奏。","isInference":true,"confidence":0.7,"factIds":["trend-1-CNY"],"sources":[{"type":"CHECKIN","id":"2","label":"长期储蓄"}]}]}""",
                ),
            )
        }
        val service = InsightService(
            dataRepository = data,
            consentRepository = consent,
            configRepository = config,
            networkEngine = engine,
            fakeEngine = null,
            dao = dao,
            now = { clock.value },
            zone = { ZoneId.of("Asia/Shanghai") },
            newId = { "insight-${++id}" },
            writeBarrier = RestoreWriteBarrier(initiallyReady = true),
        )
        val report = (service.generate(force = true) as InsightGenerationResult.Ready).report
        return ReferenceFixture(
            service,
            data,
            consent,
            InsightReference(report.id, report.dataVersion, report.insights.single().id),
            clock,
        )
    }

    @Test
    fun `当前有来源的建议可以解析为结构化引用`() = runTest {
        val fixture = referenceFixture()
        val result = fixture.service.resolveCurrentReference(fixture.reference)
        assertTrue(result is InsightReferenceResolution.Current)
        val resolved = (result as InsightReferenceResolution.Current).value
        assertEquals("trend-1-CNY", resolved.insight.factIds.single())
        assertTrue(resolved.facts.single().sourceRefs.any { it.type == "CHECKIN" && it.recordId == "2" })
    }

    @Test
    fun `跨天仅窗口过期而事实来源未变`() = runTest {
        val fixture = referenceFixture()
        fixture.clock.value += 24L * 60 * 60 * 1000
        val result = fixture.service.resolveCurrentReference(fixture.reference)
        assertTrue(result is InsightReferenceResolution.Stale)
        val reason = (result as InsightReferenceResolution.Stale).reason
        assertTrue(reason.contains("时间窗口"))
        assertTrue(!reason.contains("原始记录已修改"))
        assertTrue(fixture.service.staleReportReason().orEmpty().contains("原始记录未变"))
    }

    @Test
    fun `来源进展删除或目标修改后旧建议失效`() = runTest {
        val deleted = referenceFixture()
        deleted.data.snapshot = snapshot.copy(checkins = snapshot.checkins.filterNot { it.id == 2L })
        assertTrue(deleted.service.resolveCurrentReference(deleted.reference) is InsightReferenceResolution.Stale)

        val edited = referenceFixture()
        edited.data.snapshot = snapshot.copy(goals = snapshot.goals.map { it.copy(title = "已调整的目标") })
        val editedResult = edited.service.resolveCurrentReference(edited.reference)
        assertTrue(editedResult is InsightReferenceResolution.Stale)
        assertTrue((editedResult as InsightReferenceResolution.Stale).reason.contains("原始记录或授权"))
    }

    @Test
    fun `来源类别授权关闭或事实不足后旧建议失效`() = runTest {
        val revoked = referenceFixture()
        revoked.consent.setCategory(ConsentCategory.PROGRESS, false)
        assertTrue(revoked.service.resolveCurrentReference(revoked.reference) is InsightReferenceResolution.Stale)

        val insufficient = referenceFixture()
        insufficient.data.snapshot = snapshot.copy(checkins = snapshot.checkins.take(1), growthMemories = emptyList())
        val result = insufficient.service.resolveCurrentReference(insufficient.reference)
        assertTrue(result is InsightReferenceResolution.Stale)
        assertTrue((result as InsightReferenceResolution.Stale).reason.contains("不足"))
    }
}
