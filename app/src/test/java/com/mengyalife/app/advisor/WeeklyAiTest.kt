package com.mengyalife.app.advisor

import com.mengyalife.app.data.local.weekly.WeeklyAiDao
import com.mengyalife.app.data.local.weekly.WeeklyAiEntity
import com.mengyalife.app.data.weekly.WeeklyAiService
import com.mengyalife.app.data.weekly.WeeklyReviewSource
import com.mengyalife.app.domain.advisor.AiConsentRules
import com.mengyalife.app.domain.advisor.AiConsentState
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.advisor.AdvisorChatResult
import com.mengyalife.app.domain.weekly.WeeklyAiParser
import com.mengyalife.app.domain.weekly.WeeklyCheckin
import com.mengyalife.app.domain.weekly.WeeklyFact
import com.mengyalife.app.domain.weekly.WeeklyGoal
import com.mengyalife.app.domain.weekly.WeeklyGoalLevel
import com.mengyalife.app.domain.weekly.WeeklyGoalStatus
import com.mengyalife.app.domain.weekly.WeeklySnapshot
import com.mengyalife.app.domain.weekly.WeeklySourceRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private class FakeAiDao : WeeklyAiDao {
    var onMarkSent: suspend () -> Unit = {}
    val rows = MutableStateFlow<List<WeeklyAiEntity>>(emptyList())
    override fun observeLatest(): Flow<WeeklyAiEntity?> = rows.map { it.lastOrNull() }
    override suspend fun findByPeriod(key: String): WeeklyAiEntity? = rows.value.firstOrNull { it.periodKey == key }
    override suspend fun loadAll(): List<WeeklyAiEntity> = rows.value
    override suspend fun insertIfAbsent(row: WeeklyAiEntity): Long {
        if (rows.value.any { it.periodKey == row.periodKey }) return -1
        rows.value = rows.value + row; return 1
    }
    override suspend fun markSent(key: String): Int {
        val old = rows.value.firstOrNull { it.periodKey == key } ?: return 0
        if (old.status != "PENDING") return 0
        onMarkSent()
        rows.value = rows.value.map { if (it.periodKey == key) it.copy(status = "SENT") else it }; return 1
    }
    override suspend fun deletePending(key: String): Int {
        val target = rows.value.firstOrNull { it.periodKey == key && it.status == "PENDING" } ?: return 0
        rows.value = rows.value.filterNot { it.periodKey == key }; return 1
    }
    override suspend fun complete(key: String, status: String, now: Long, summary: String, highlights: String, suggestion: String, factIds: String, error: String?, model: String?, configHash: String) {
        rows.value = rows.value.map { if (it.periodKey == key) it.copy(status = status, generatedAt = now, summary = summary, highlightsJson = highlights, suggestion = suggestion, error = error, model = model, configHash = configHash) else it }
    }
}
private class AiSource(var s: WeeklySnapshot) : WeeklyReviewSource { override suspend fun load() = s }

class WeeklyAiTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDate.of(2026, 11, 4).atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
    private val consent = AiConsentState(true, AiConsentRules.TEXT_VERSION, AiConsentRules.CATEGORIES_VERSION, 1L, "https://api.example.com/v1", AiConsentRules.allCategories, 1L)
    private val snapshot = WeeklySnapshot(
        goals = listOf(WeeklyGoal(1, "储蓄", WeeklyGoalLevel.LONG_TERM, WeeklyGoalStatus.IN_PROGRESS, null, null, now)),
        checkins = listOf(WeeklyCheckin(1, 1, now - 1000, "a", 10.0, "CNY"), WeeklyCheckin(2, 1, now - 500, "b", 20.0, "CNY")),
    )
    private val valid = """{"schemaVersion":1,"summary":"本周有记录","highlights":[{"type":"POSITIVE","title":"记录增加","body":"同单位记录从10到20","isInference":true,"factIds":["week-trend-1-CNY"],"sources":[{"type":"CHECKIN","id":"2","label":"储蓄"}]}],"suggestion":"继续保持"}"""

    @Test
    fun `每周AI最多一次且缓存复用`() = runTest {
        val engine = ScriptedAdvisorEngine().apply { enqueue(AdvisorChatResult.Success(valid)) }
        val dao = FakeAiDao()
        val config = FakeConfigRepository().apply { save("https://api.example.com/v1", "m", "sk") }
        val svc = WeeklyAiService(AiSource(snapshot), FakeConsentRepository().apply { grant("https://api.example.com/v1", AiConsentRules.allCategories) }, config, engine, dao) { now }
        val a = svc.generateIfAllowed()
        val b = svc.generateIfAllowed()
        assertEquals("READY", a?.status)
        assertEquals(a?.periodKey, b?.periodKey)
        assertEquals(1, engine.calls.size)
    }

    @Test
    fun `AI输入只包含授权类别`() = runTest {
        val engine = ScriptedAdvisorEngine().apply { enqueue(AdvisorChatResult.Success(valid)) }
        val config = FakeConfigRepository().apply { save("https://api.example.com/v1", "m", "sk") }
        val goalsOnly = consent.copy(categories = setOf(ConsentCategory.GOALS))
        WeeklyAiService(AiSource(snapshot), FakeConsentRepository().apply { grant("https://api.example.com/v1", setOf(ConsentCategory.GOALS)) }, config, engine, FakeAiDao()) { now }.generateIfAllowed()
        val prompt = engine.calls.joinToString(" ") { it.joinToString(" ") { m -> m.content } }
        assertTrue(prompt.contains("GOAL") || prompt.contains("储蓄"))
        assertTrue(!prompt.contains("CHECKIN:"))
    }

    private suspend fun testService(
        engine: ScriptedAdvisorEngine, source: AiSource, dao: FakeAiDao,
        before: suspend () -> Unit = {}, after: suspend () -> Unit = {},
    ): WeeklyAiService {
        val config = FakeConfigRepository().apply { save("https://api.example.com/v1", "m", "sk") }
        return WeeklyAiService(
            source, FakeConsentRepository().apply { grant("https://api.example.com/v1", AiConsentRules.allCategories) },
            config, engine, dao, now = { now }, beforeRequest = before, beforeComplete = after,
        )
    }

    @Test
    fun `请求前撤回授权不调用引擎且释放预算`() = runTest {
        val engine = ScriptedAdvisorEngine().apply { enqueue(AdvisorChatResult.Success(valid)) }
        val dao = FakeAiDao(); val source = AiSource(snapshot)
        val consent = FakeConsentRepository().apply { grant("https://api.example.com/v1", AiConsentRules.allCategories) }
        val config = FakeConfigRepository().apply { save("https://api.example.com/v1", "m", "sk") }
        val svc = WeeklyAiService(source, consent, config, engine, dao, now = { now }, beforeRequest = { consent.withdraw() })
        assertEquals(null, svc.generateIfAllowed())
        assertEquals(0, engine.calls.size)
        assertEquals(0, dao.rows.value.size)
    }

    @Test
    fun `请求前切换endpoint不调用引擎`() = runTest {
        val engine = ScriptedAdvisorEngine().apply { enqueue(AdvisorChatResult.Success(valid)) }
        val dao = FakeAiDao(); val source = AiSource(snapshot)
        val consent = FakeConsentRepository().apply { grant("https://api.example.com/v1", AiConsentRules.allCategories) }
        val config = FakeConfigRepository().apply { save("https://api.example.com/v1", "m", "sk") }
        val svc = WeeklyAiService(source, consent, config, engine, dao, beforeRequest = { config.save("https://other.example.com/v1", "m", null) }) { now }
        assertEquals(null, svc.generateIfAllowed())
        assertEquals(0, engine.calls.size)
    }

    @Test
    fun `请求前修改数据不调用引擎`() = runTest {
        val engine = ScriptedAdvisorEngine().apply { enqueue(AdvisorChatResult.Success(valid)) }
        val dao = FakeAiDao(); val source = AiSource(snapshot)
        val svc = WeeklyAiService(
            source, FakeConsentRepository().apply { grant("https://api.example.com/v1", AiConsentRules.allCategories) },
            FakeConfigRepository().apply { save("https://api.example.com/v1", "m", "sk") }, engine, dao,
            beforeRequest = { source.s = snapshot.copy(checkins = snapshot.checkins + WeeklyCheckin(9, 1, now, "new", 30.0, "CNY")) },
        ) { now }
        assertEquals(null, svc.generateIfAllowed())
        assertEquals(0, engine.calls.size)
    }

    @Test
    fun `请求中数据变化不写READY`() = runTest {
        val engine = ScriptedAdvisorEngine().apply { enqueue(AdvisorChatResult.Success(valid)) }
        val dao = FakeAiDao(); val source = AiSource(snapshot)
        val svc = WeeklyAiService(
            source, FakeConsentRepository().apply { grant("https://api.example.com/v1", AiConsentRules.allCategories) },
            FakeConfigRepository().apply { save("https://api.example.com/v1", "m", "sk") }, engine, dao,
            now = { now },
            beforeComplete = { source.s = snapshot.copy(checkins = snapshot.checkins + WeeklyCheckin(9, 1, now, "new", 30.0, "CNY")) },
        )
        val result = svc.generateIfAllowed()
        assertEquals(1, engine.calls.size)
        assertTrue(result?.status != "READY")
    }

    @Test
    fun `markSent临界区切换endpoint不调用引擎`() = runTest {
        val engine = ScriptedAdvisorEngine().apply { enqueue(AdvisorChatResult.Success(valid)) }
        val dao = FakeAiDao(); val source = AiSource(snapshot)
        val consent = FakeConsentRepository().apply { grant("https://api.example.com/v1", AiConsentRules.allCategories) }
        val config = FakeConfigRepository().apply { save("https://api.example.com/v1", "m", "sk") }
        dao.onMarkSent = { config.save("https://other.example.com/v1", "m", null) }
        val svc = WeeklyAiService(source, consent, config, engine, dao, now = { now })
        val result = svc.generateIfAllowed()
        assertEquals(0, engine.calls.size)
        assertTrue(result?.status != "READY")
    }

    @Test
    fun `并发调用只发起一次`() = runTest {
        val engine = ScriptedAdvisorEngine().apply {
            enqueue(AdvisorChatResult.Success(valid)); enqueue(AdvisorChatResult.Success(valid))
        }
        val dao = FakeAiDao(); val source = AiSource(snapshot)
        val consent = FakeConsentRepository().apply { grant("https://api.example.com/v1", AiConsentRules.allCategories) }
        val config = FakeConfigRepository().apply { save("https://api.example.com/v1", "m", "sk") }
        val svc = WeeklyAiService(source, consent, config, engine, dao, now = { now })
        kotlinx.coroutines.coroutineScope {
            launch { svc.generateIfAllowed() }
            launch { svc.generateIfAllowed() }
        }
        assertEquals(1, engine.calls.size)
    }

    @Test
    fun `prompt 示例是合法 JSON`() {
        val prompt = WeeklyAiParser.prompt(emptyList(), "PROGRESS")
        val example = prompt.lines().first { it.trim().startsWith("{\"schemaVersion\"") }
        assertTrue(runCatching { kotlinx.serialization.json.Json.parseToJsonElement(example) }.isSuccess)
    }

    @Test
    fun `信息不足允许无来源`() {
        val insufficient = WeeklyFact("insufficient", "数据不足", emptyList())
        val json = """{"schemaVersion":1,"summary":"信息不足","highlights":[{"type":"SUGGESTION","title":"先记录","body":"数据不足","isInference":true,"factIds":["insufficient"],"sources":[]}],"suggestion":"先记录一天"}"""
        assertTrue(WeeklyAiParser.parse(json, listOf(insufficient), "GOALS") is com.mengyalife.app.domain.weekly.WeeklyAiParseResult.Success)
    }

    @Test
    fun `模型不输出事实或引用未知事实时失败`() {
        val facts = listOf(WeeklyFact("week-trend-1-CNY", "x", listOf(WeeklySourceRef("CHECKIN", "2", "储蓄"))))
        val bad = valid.replace("\"factIds\":[\"week-trend-1-CNY\"]", "\"factIds\":[\"unknown\"]")
        assertTrue(WeeklyAiParser.parse(bad, facts, "PROGRESS") is com.mengyalife.app.domain.weekly.WeeklyAiParseResult.Failure)
    }
}
