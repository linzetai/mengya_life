package com.mengyalife.app.advisor

import com.mengyalife.app.data.insight.InsightGenerationResult
import com.mengyalife.app.data.insight.InsightService
import com.mengyalife.app.data.local.insight.InsightReportDao
import com.mengyalife.app.data.local.insight.InsightReportEntity
import com.mengyalife.app.domain.advisor.AdvisorChatResult
import com.mengyalife.app.domain.advisor.AdvisorContextSnapshot
import com.mengyalife.app.domain.advisor.AiConsentRules
import com.mengyalife.app.domain.advisor.ContextCheckinRecord
import com.mengyalife.app.domain.advisor.ContextGoalRecord
import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalStatus
import com.mengyalife.app.domain.insight.InsightReportStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeInsightDao : InsightReportDao {
    val rows = MutableStateFlow<List<InsightReportEntity>>(emptyList())
    override fun observeLatest(): Flow<InsightReportEntity?> = rows.map { it.lastOrNull() }
    override suspend fun latest(): InsightReportEntity? = rows.value.lastOrNull()
    override suspend fun findByDataVersion(version: String): InsightReportEntity? =
        rows.value.lastOrNull { it.dataVersion == version }
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
            ),
            engine,
            dao,
        )
    }

    private fun validJson() = """
        {"schemaVersion":1,"summary":"储蓄在增加","insights":[
        {"type":"POSITIVE","title":"稳步储蓄","body":"两期记录都来自同一单位，金额在增加。","isInference":true,"confidence":0.8,
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
}
