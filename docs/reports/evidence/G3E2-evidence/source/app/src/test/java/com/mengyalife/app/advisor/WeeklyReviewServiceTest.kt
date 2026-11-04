package com.mengyalife.app.advisor

import com.mengyalife.app.data.local.weekly.WeeklyReviewDao
import com.mengyalife.app.data.local.weekly.WeeklyReviewEntity
import com.mengyalife.app.data.weekly.WeeklyReviewService
import com.mengyalife.app.data.weekly.WeeklyReviewSource
import com.mengyalife.app.domain.advisor.AiConsentRules
import com.mengyalife.app.domain.advisor.AiConsentState
import com.mengyalife.app.domain.weekly.WeeklyCheckin
import com.mengyalife.app.domain.weekly.WeeklyGoal
import com.mengyalife.app.domain.weekly.WeeklyGoalLevel
import com.mengyalife.app.domain.weekly.WeeklyGoalStatus
import com.mengyalife.app.domain.weekly.WeeklyReviewCodec
import com.mengyalife.app.domain.weekly.WeeklyReviewRules
import com.mengyalife.app.domain.weekly.WeeklySnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private class FakeWeeklyDao : WeeklyReviewDao {
    val rows = MutableStateFlow<List<WeeklyReviewEntity>>(emptyList())
    override fun observeLatest(): Flow<WeeklyReviewEntity?> = rows.map { it.lastOrNull() }
    override suspend fun latest(): WeeklyReviewEntity? = rows.value.lastOrNull()
    override suspend fun findByPeriod(key: String): WeeklyReviewEntity? = rows.value.firstOrNull { it.periodKey == key }
    override suspend fun upsert(review: WeeklyReviewEntity) {
        rows.value = rows.value.filterNot { it.periodKey == review.periodKey } + review
    }
    override suspend fun tryClaimNotification(key: String, at: Long): Int {
        val old = rows.value.firstOrNull { it.periodKey == key } ?: return 0
        if (old.notifiedAt != null) return 0
        rows.value = rows.value.map { if (it.periodKey == key) it.copy(notifiedAt = at) else it }
        return 1
    }
    override suspend fun releaseNotification(key: String, at: Long): Int {
        val old = rows.value.firstOrNull { it.periodKey == key } ?: return 0
        if (old.notifiedAt != at) return 0
        rows.value = rows.value.map { if (it.periodKey == key) it.copy(notifiedAt = null) else it }
        return 1
    }
    override suspend fun markNotified(key: String, at: Long) {
        rows.value = rows.value.map { if (it.periodKey == key) it.copy(notifiedAt = at) else it }
    }
}

private class FakeWeeklySource(var snapshot: WeeklySnapshot) : WeeklyReviewSource {
    override suspend fun load(): WeeklySnapshot = snapshot
}

class WeeklyReviewServiceTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDate.of(2026, 11, 4).atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
    private val consent = AiConsentState(
        granted = true, textVersion = AiConsentRules.TEXT_VERSION,
        categoriesVersion = AiConsentRules.CATEGORIES_VERSION,
        recipient = "https://api.example.com/v1", categories = AiConsentRules.allCategories, epoch = 1,
    )
    private val snapshot = WeeklySnapshot(
        goals = listOf(WeeklyGoal(1, "储蓄", WeeklyGoalLevel.LONG_TERM, WeeklyGoalStatus.IN_PROGRESS, null, null, now)),
        checkins = listOf(
            WeeklyCheckin(1, 1, now - 1000, "a", 1000.0, "CNY"),
            WeeklyCheckin(2, 1, now - 500, "b", 1500.0, "CNY"),
        ),
    )

    @Test
    fun `同一周期重复生成复用已有记录`() = runTest {
        val dao = FakeWeeklyDao()
        val source = FakeWeeklySource(snapshot)
        val service = WeeklyReviewService(source, dao, FakeConsentRepository().apply {
            grant("https://api.example.com/v1", AiConsentRules.allCategories)
        }, now = { now }, newId = { "id-${dao.rows.value.size}" })
        val a = service.generateIfNeeded()
        val b = service.generateIfNeeded()
        assertEquals(a.id, b.id)
        assertEquals(1, dao.rows.value.size)
    }

    @Test
    fun `codec 保留 sections 与来源`() {
        val review = WeeklyReviewRules.build(snapshot, consent, now, zone)
        val entity = WeeklyReviewEntity(
            id = "x", periodKey = review.periodKey, periodStart = review.periodStart,
            periodEnd = review.periodEnd, generatedAt = review.generatedAt, version = review.version,
            dataVersion = review.dataVersion, aiConsentVersion = review.aiConsentVersion,
            factsJson = WeeklyReviewCodec.factsJson(review.facts),
            sectionsJson = WeeklyReviewCodec.sectionsJson(review.sections), notifiedAt = null,
        )
        val decoded = WeeklyReviewCodec.factsFrom(entity.factsJson)
        assertTrue(decoded.any { it.id == "week-trend-1-CNY" && it.sources.isNotEmpty() })
        val sections = WeeklyReviewCodec.sectionsFrom(entity.sectionsJson)
        assertTrue(sections.nextSteps.isNotEmpty())
    }
}
