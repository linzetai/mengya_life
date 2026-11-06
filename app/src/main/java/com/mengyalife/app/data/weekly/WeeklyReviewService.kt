package com.mengyalife.app.data.weekly

import com.mengyalife.app.data.consent.AiConsentRepository
import com.mengyalife.app.data.local.weekly.WeeklyReviewDao
import com.mengyalife.app.data.local.weekly.WeeklyReviewEntity
import com.mengyalife.app.data.restore.RestoreWriteBarrier
import com.mengyalife.app.domain.weekly.WeeklyReview
import com.mengyalife.app.domain.weekly.WeeklyReviewCodec
import com.mengyalife.app.domain.weekly.WeeklyReviewRules
import com.mengyalife.app.domain.weekly.WeekWindow
import kotlinx.coroutines.flow.map
import java.util.UUID

class WeeklyReviewService(
    private val dataRepository: WeeklyReviewSource,
    private val dao: WeeklyReviewDao,
    private val consentRepository: AiConsentRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val canWrite: () -> Boolean = { true },
    private val writeBarrier: RestoreWriteBarrier? = null,
) {
    suspend fun generateIfNeeded(force: Boolean = false): WeeklyReview {
        val generation = writeBarrier?.generation()
        if (!canWrite()) throw IllegalStateException("恢复维护中，禁止生成周回顾")
        val work: suspend () -> WeeklyReview = { generateLocked(force) }
        return writeBarrier?.withWrite(generation) { work() } ?: work()
    }

    private suspend fun generateLocked(force: Boolean): WeeklyReview {
        val snapshot = dataRepository.load()
        val consent = consentRepository.current()
        val review = WeeklyReviewRules.build(snapshot, consent, now())
        val existing = dao.findByPeriod(review.periodKey)
        if (!force && existing != null &&
            existing.version == review.version &&
            existing.dataVersion == review.dataVersion &&
            existing.aiConsentVersion == review.aiConsentVersion
        ) return existing.toDomain()
        val saved = review.copy(id = newId())
        dao.upsert(saved.toEntity(notifiedAt = existing?.notifiedAt))
        return saved
    }

    suspend fun latest(): WeeklyReview? = dao.latest()?.toDomain()

    fun observeLatest() = dao.observeLatest().map { it?.toDomain() }

    suspend fun markNotified(periodKey: String, at: Long) {
        val generation = writeBarrier?.generation()
        val write: suspend () -> Unit = { dao.markNotified(periodKey, at); Unit }
        if (writeBarrier != null) writeBarrier.withWrite(generation) { write() } else write()
    }

    fun WeeklyReview.toEntity(notifiedAt: Long?) = WeeklyReviewEntity(
        id = id, periodKey = periodKey, periodStart = periodStart, periodEnd = periodEnd,
        generatedAt = generatedAt, version = version,
        dataVersion = dataVersion, aiConsentVersion = aiConsentVersion,
        factsJson = WeeklyReviewCodec.factsJson(facts),
        sectionsJson = WeeklyReviewCodec.sectionsJson(sections),
        notifiedAt = notifiedAt,
    )

    fun WeeklyReviewEntity.toDomain() = WeeklyReview(
        id = id, periodKey = periodKey, periodStart = periodStart, periodEnd = periodEnd,
        generatedAt = generatedAt, version = version,
        dataVersion = dataVersion, aiConsentVersion = aiConsentVersion,
        facts = WeeklyReviewCodec.factsFrom(factsJson),
        sections = WeeklyReviewCodec.sectionsFrom(sectionsJson),
    )
}
