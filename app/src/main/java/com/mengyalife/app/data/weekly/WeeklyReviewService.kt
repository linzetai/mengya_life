package com.mengyalife.app.data.weekly

import com.mengyalife.app.data.consent.AiConsentRepository
import com.mengyalife.app.data.local.weekly.WeeklyReviewDao
import com.mengyalife.app.data.local.weekly.WeeklyReviewEntity
import com.mengyalife.app.domain.weekly.WeeklyReview
import com.mengyalife.app.domain.weekly.WeeklyReviewCodec
import com.mengyalife.app.domain.weekly.WeeklyReviewRules
import com.mengyalife.app.domain.weekly.WeekWindow
import java.util.UUID

class WeeklyReviewService(
    private val dataRepository: WeeklyReviewSource,
    private val dao: WeeklyReviewDao,
    private val consentRepository: AiConsentRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun generateIfNeeded(force: Boolean = false): WeeklyReview {
        val snapshot = dataRepository.load()
        val consent = consentRepository.current()
        val review = WeeklyReviewRules.build(snapshot, consent, now())
        val existing = dao.findByPeriod(review.periodKey)
        if (!force && existing != null) return existing.toDomain()
        val saved = review.copy(id = newId())
        dao.upsert(saved.toEntity(notifiedAt = existing?.notifiedAt))
        return saved
    }

    suspend fun latest(): WeeklyReview? = dao.latest()?.toDomain()

    suspend fun markNotified(periodKey: String, at: Long) = dao.markNotified(periodKey, at)

    private fun WeeklyReview.toEntity(notifiedAt: Long?) = WeeklyReviewEntity(
        id = id, periodKey = periodKey, periodStart = periodStart, periodEnd = periodEnd,
        generatedAt = generatedAt, version = version,
        factsJson = WeeklyReviewCodec.factsJson(facts),
        sectionsJson = WeeklyReviewCodec.sectionsJson(sections),
        notifiedAt = notifiedAt,
    )

    private fun WeeklyReviewEntity.toDomain() = WeeklyReview(
        id = id, periodKey = periodKey, periodStart = periodStart, periodEnd = periodEnd,
        generatedAt = generatedAt, version = version,
        facts = WeeklyReviewCodec.factsFrom(factsJson),
        sections = WeeklyReviewCodec.sectionsFrom(sectionsJson),
    )
}
