package com.mengyalife.app.data.weekly

import com.mengyalife.app.data.advisor.AdvisorConfigRepository
import com.mengyalife.app.data.advisor.AdvisorEngine
import com.mengyalife.app.data.consent.AiConsentRepository
import com.mengyalife.app.data.local.weekly.WeeklyAiDao
import com.mengyalife.app.data.local.weekly.WeeklyAiEntity
import com.mengyalife.app.domain.advisor.AdvisorChatMessage
import com.mengyalife.app.domain.advisor.AdvisorChatResult
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.weekly.WeeklyAiParseResult
import com.mengyalife.app.domain.weekly.WeeklyAiParser
import com.mengyalife.app.domain.weekly.WeeklyAiReview
import com.mengyalife.app.domain.weekly.WeeklyReviewRules
import com.mengyalife.app.domain.weekly.WeeklySnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class WeeklyAiService(
    private val source: WeeklyReviewSource,
    private val consentRepository: AiConsentRepository,
    private val configRepository: AdvisorConfigRepository,
    private val networkEngine: AdvisorEngine,
    private val dao: WeeklyAiDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun canUse(): Boolean {
        val c = consentRepository.current()
        val cfg = configRepository.state.first()
        val rt = configRepository.runtimeConfig()
        return c.granted && cfg.configured && rt != null && rt.endpoint == cfg.endpoint && c.isValidFor(rt.endpoint)
    }

    suspend fun currentFor(periodKey: String): WeeklyAiReview? = dao.findByPeriod(periodKey)?.toDomain()

    fun observeLatest() = dao.observeLatest().map { it?.toDomain() }

    /** 自动/手动都调用；period_key 唯一保证每个自然周最多一次请求。 */
    suspend fun generateIfAllowed(allowManualRetry: Boolean = false): WeeklyAiReview? {
        val consent = consentRepository.current()
        val config = configRepository.state.first()
        val runtime = configRepository.runtimeConfig()
        if (!consent.granted || !config.configured || runtime == null ||
            runtime.endpoint != config.endpoint || !consent.isValidFor(runtime.endpoint)
        ) return null
        if (!consent.enabled(ConsentCategory.PROGRESS) && !consent.enabled(ConsentCategory.GOALS) &&
            !consent.enabled(ConsentCategory.JOURNAL)
        ) return null
        val snapshot = source.load()
        val filtered = snapshot.copy(
            goals = if (consent.enabled(ConsentCategory.GOALS)) snapshot.goals else emptyList(),
            checkins = if (consent.enabled(ConsentCategory.PROGRESS)) snapshot.checkins else emptyList(),
            memories = if (consent.enabled(ConsentCategory.JOURNAL)) snapshot.memories else emptyList(),
        )
        val review = WeeklyReviewRules.build(filtered, consent, now())
        val existing = dao.findByPeriod(review.periodKey)
        if (existing != null) return existing.toDomain()
        val reserved = WeeklyAiEntity(
            periodKey = review.periodKey, localDataVersion = review.dataVersion,
            aiConsentVersion = review.aiConsentVersion, status = "PENDING",
            generatedAt = now(), summary = "", highlightsJson = "[]", suggestion = "",
            factIdsJson = "[]", categories = consent.categories.joinToString(",") { it.name },
            error = null, model = null,
        )
        if (dao.insertIfAbsent(reserved) == -1L) return dao.findByPeriod(review.periodKey)?.toDomain()
        return try {
            val facts = review.facts
            val prompt = WeeklyAiParser.prompt(facts, reserved.categories)
            val result = networkEngine.chat(
                config = runtime,
                messages = listOf(
                    AdvisorChatMessage("system", "只输出严格 JSON；只解释给定 factIds；不编造。"),
                    AdvisorChatMessage("user", prompt),
                ),
                maxTokens = 900,
            )
            val afterConsent = consentRepository.current()
            if (afterConsent.epoch != consent.epoch || afterConsent.categories != consent.categories ||
                !afterConsent.isValidFor(runtime.endpoint)
            ) {
                complete(reserved, "FAILED", "", emptyList(), "", "授权已变化，AI 结果丢弃", null)
                return dao.findByPeriod(review.periodKey)?.toDomain()
            }
            when (result) {
                is AdvisorChatResult.Failure -> complete(reserved, "FAILED", "", emptyList(), "", result.error.userMessage, null)
                is AdvisorChatResult.Success -> when (val parsed = WeeklyAiParser.parse(result.content, facts, reserved.categories)) {
                    is WeeklyAiParseResult.Failure -> complete(reserved, "FAILED", "", emptyList(), "", parsed.message, null)
                    is WeeklyAiParseResult.Success -> complete(
                        reserved, "READY", parsed.summary, parsed.highlights, parsed.suggestion, null,
                        config.model,
                    )
                }
            }
            dao.findByPeriod(review.periodKey)?.toDomain()
        } catch (e: Exception) {
            complete(reserved, "FAILED", "", emptyList(), "", e.message ?: "AI 请求失败", null)
            dao.findByPeriod(review.periodKey)?.toDomain()
        }
    }

    private suspend fun complete(
        base: WeeklyAiEntity,
        status: String,
        summary: String,
        highlights: List<com.mengyalife.app.domain.weekly.WeeklyAiHighlight>,
        suggestion: String,
        error: String?,
        model: String?,
    ) {
        dao.complete(
            key = base.periodKey, status = status, now = now(), summary = summary,
            highlights = WeeklyAiParser.highlightsJson(highlights),
            suggestion = suggestion, factIds = "[]", error = error, model = model,
        )
    }

    private fun WeeklyAiEntity.toDomain() = WeeklyAiReview(
        periodKey, localDataVersion, aiConsentVersion, status, generatedAt, summary,
        WeeklyAiParser.highlightsFrom(highlightsJson), suggestion, emptyList(), categories, error, model,
    )
}
