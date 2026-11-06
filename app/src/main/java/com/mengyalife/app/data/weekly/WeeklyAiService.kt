package com.mengyalife.app.data.weekly

import com.mengyalife.app.data.advisor.AdvisorConfigRepository
import com.mengyalife.app.data.advisor.AdvisorEngine
import com.mengyalife.app.data.consent.AiConsentRepository
import com.mengyalife.app.data.local.weekly.WeeklyAiDao
import com.mengyalife.app.data.local.weekly.WeeklyAiEntity
import com.mengyalife.app.data.restore.RestoreWriteBarrier
import com.mengyalife.app.domain.advisor.AdvisorChatMessage
import com.mengyalife.app.domain.advisor.AdvisorChatResult
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.weekly.WeeklyAiParseResult
import com.mengyalife.app.domain.weekly.WeeklyAiParser
import com.mengyalife.app.domain.weekly.WeeklyAiReview
import com.mengyalife.app.domain.weekly.WeeklyReviewRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

class WeeklyAiService(
    private val source: WeeklyReviewSource,
    private val consentRepository: AiConsentRepository,
    private val configRepository: AdvisorConfigRepository,
    private val networkEngine: AdvisorEngine,
    private val dao: WeeklyAiDao,
    /** QA 构建专用假引擎；正式包为 null，行为与之前完全一致。 */
    private val fakeEngine: AdvisorEngine? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val autoEnabled: suspend () -> Boolean = { true },
    private val writeBarrier: RestoreWriteBarrier? = null,
    private val beforeRequest: suspend () -> Unit = {},
    private val beforeComplete: suspend () -> Unit = {},
) {
    private data class Bound(
        val consent: com.mengyalife.app.domain.advisor.AiConsentState,
        val endpoint: String,
        val model: String,
        val configHash: String,
        val dataVersion: String,
        val auto: Boolean,
    )

    suspend fun canUse(): Boolean {
        val c = consentRepository.current()
        val cfg = configRepository.state.first()
        val rt = configRepository.runtimeConfig()
        return c.granted && cfg.configured && rt != null && rt.endpoint == cfg.endpoint &&
            c.isValidFor(rt.endpoint) && c.categories.isNotEmpty()
    }

    suspend fun currentFor(periodKey: String): WeeklyAiReview? {
        val entity = dao.findByPeriod(periodKey) ?: return null
        val cfg = configRepository.state.first()
        val consent = consentRepository.current()
        val current = buildInput(consent)
        val stale = entity.configHash != configHash(cfg.endpoint, cfg.model) ||
            !consent.granted ||
            entity.aiConsentVersion != current.aiConsentVersion ||
            entity.localDataVersion != current.dataVersion
        return if (stale) entity.toDomain().copy(status = "STALE", error = "本周数据/授权/模型已变化，AI 结果过期") else entity.toDomain()
    }

    fun observeLatest() = dao.observeLatest().map { it?.toDomain() }

    suspend fun generateIfAllowed(autoRequest: Boolean = false): WeeklyAiReview? {
        val restoreGeneration = writeBarrier?.generation()
        val initialConsent = consentRepository.current()
        val initialConfig = configRepository.state.first()
        val initialRuntime = configRepository.runtimeConfig()
        if (!initialConsent.granted || !initialConfig.configured || initialRuntime == null ||
            initialRuntime.endpoint != initialConfig.endpoint || !initialConsent.isValidFor(initialRuntime.endpoint) ||
            initialConsent.categories.isEmpty() || (autoRequest && !autoEnabled())
        ) return null
        val bound = Bound(
            initialConsent, initialRuntime.endpoint, initialConfig.model,
            configHash(initialConfig.endpoint, initialConfig.model),
            buildInput(initialConsent).dataVersion,
            autoRequest,
        )
        val review = buildInput(initialConsent)
        val existing = dao.findByPeriod(review.periodKey)
        if (existing != null) {
            // 崩溃遗留的 PENDING（未发出）可安全恢复；SENT/FAILED/READY 都消耗本周预算。
            if (existing.status == "PENDING") {
                guardedWrite(restoreGeneration) { dao.deletePending(review.periodKey); Unit }
            } else {
                return existing.toDomain()
            }
        }
        val reserved = WeeklyAiEntity(
            periodKey = review.periodKey, localDataVersion = bound.dataVersion,
            aiConsentVersion = review.aiConsentVersion, status = "PENDING",
            generatedAt = now(), summary = "", highlightsJson = "[]", suggestion = "",
            factIdsJson = "[]", categories = initialConsent.categories.joinToString(",") { it.name },
            error = null, model = null, configHash = bound.configHash,
        )
        if (guardedWrite(restoreGeneration) { dao.insertIfAbsent(reserved) } == -1L) return dao.findByPeriod(review.periodKey)?.toDomain()
        val before = try {
            beforeRequest()
            validate(bound, autoRequest)
        } catch (e: Exception) { e.message ?: "请求前状态变化" }
        if (before != null) {
            // 尚未调用 engine：撤销预算预留，允许以后安全重试。
            guardedWrite(restoreGeneration) { dao.deletePending(review.periodKey); Unit }
            return null
        }
        if (guardedWrite(restoreGeneration) { dao.markSent(review.periodKey) } != 1) return dao.findByPeriod(review.periodKey)?.toDomain()
        // markSent 之后、真正发送之前的最后临界区检查
        val afterMark = validate(bound, autoRequest)
        if (afterMark != null) {
            complete(reserved, "FAILED", "", emptyList(), "", "发送边界状态变化，未调用引擎", initialConfig.model, bound.configHash, expectedGeneration = restoreGeneration)
            return dao.findByPeriod(review.periodKey)?.toDomain()
        }
        return try {
            val facts = review.facts.take(20).map { it.copy(text = it.text.take(300)) }
            val prompt = WeeklyAiParser.prompt(facts, reserved.categories)
            val engine = if (initialConfig.useFakeEngine && fakeEngine != null) fakeEngine else networkEngine
            val chat = suspend {
                engine.chat(
                config = initialRuntime,
                messages = listOf(
                    AdvisorChatMessage("system", "只输出严格 JSON；只解释给定 factIds；不编造。"),
                    AdvisorChatMessage("user", prompt),
                ),
                maxTokens = 900,
                )
            }
            val result = writeBarrier?.withWrite(restoreGeneration) { chat() } ?: chat()
            beforeComplete()
            val after = validate(bound, autoRequest)
            if (after != null) {
                complete(reserved, "FAILED", "", emptyList(), "", "请求后状态变化，AI 结果已丢弃", initialConfig.model, bound.configHash, expectedGeneration = restoreGeneration)
                return dao.findByPeriod(review.periodKey)?.toDomain()
            }
            when (result) {
                is AdvisorChatResult.Failure -> complete(reserved, "FAILED", "", emptyList(), "", result.error.userMessage, initialConfig.model, bound.configHash, expectedGeneration = restoreGeneration)
                is AdvisorChatResult.Success -> when (val parsed = WeeklyAiParser.parse(result.content, facts, reserved.categories)) {
                    is WeeklyAiParseResult.Failure -> complete(reserved, "FAILED", "", emptyList(), "", parsed.message, initialConfig.model, bound.configHash, expectedGeneration = restoreGeneration)
                    is WeeklyAiParseResult.Success -> complete(reserved, "READY", parsed.summary, parsed.highlights, parsed.suggestion, null, initialConfig.model, bound.configHash, parsed.factIds, expectedGeneration = restoreGeneration)
                }
            }
            dao.findByPeriod(review.periodKey)?.toDomain()
        } catch (cancelled: CancellationException) {
            complete(reserved, "FAILED", "", emptyList(), "", "请求已取消；服务端是否接收未知，预算已占用", null, bound.configHash, expectedGeneration = restoreGeneration)
            throw cancelled
        } catch (e: Exception) {
            complete(reserved, "FAILED", "", emptyList(), "", e.message ?: "AI 请求失败", null, bound.configHash, expectedGeneration = restoreGeneration)
            dao.findByPeriod(review.periodKey)?.toDomain()
        }
    }

    private suspend fun validate(bound: Bound, autoRequest: Boolean): String? {
        val c = consentRepository.current()
        val cfg = configRepository.state.first()
        val rt = configRepository.runtimeConfig()
        if (!c.granted || c.epoch != bound.consent.epoch || c.categories != bound.consent.categories) return "授权已变化"
        if (!c.isValidFor(bound.endpoint)) return "接收方授权失效"
        if (rt == null || rt.endpoint != bound.endpoint || cfg.endpoint != bound.endpoint || cfg.model != bound.model) return "模型配置已变化"
        if (autoRequest && !autoEnabled()) return "自动 AI 已关闭"
        val current = buildInput(c)
        if (current.dataVersion != bound.dataVersion || current.aiConsentVersion != bound.consent.consentVersion()) return "数据或授权版本已变化"
        return null
    }

    private suspend fun buildInput(consent: com.mengyalife.app.domain.advisor.AiConsentState): com.mengyalife.app.domain.weekly.WeeklyReview {
        val snapshot = source.load()
        val filtered = snapshot.copy(
            goals = if (consent.enabled(ConsentCategory.GOALS)) snapshot.goals else emptyList(),
            checkins = if (consent.enabled(ConsentCategory.PROGRESS)) snapshot.checkins else emptyList(),
            memories = if (consent.enabled(ConsentCategory.JOURNAL)) snapshot.memories else emptyList(),
        )
        return WeeklyReviewRules.build(filtered, consent, now())
    }

    private fun com.mengyalife.app.domain.advisor.AiConsentState.consentVersion(): String =
        WeeklyReviewRules.aiConsentVersion(this)

    private fun configHash(endpoint: String, model: String): String =
        MessageDigest.getInstance("SHA-256").digest("$endpoint|$model".toByteArray())
            .joinToString("") { "%02x".format(it) }

    private suspend fun complete(
        base: WeeklyAiEntity, status: String, summary: String,
        highlights: List<com.mengyalife.app.domain.weekly.WeeklyAiHighlight>,
        suggestion: String, error: String?, model: String?, configHash: String,
        factIds: List<String> = emptyList(),
        expectedGeneration: Long? = null,
    ) {
        guardedWrite(expectedGeneration) {
            dao.complete(base.periodKey, status, now(), summary, WeeklyAiParser.highlightsJson(highlights),
                suggestion, WeeklyAiParser.encodeFactIds(factIds), error, model, configHash)
            Unit
        }
    }

    private suspend fun <T> guardedWrite(expectedGeneration: Long?, block: suspend () -> T): T =
        writeBarrier?.withWrite(expectedGeneration) { block() } ?: block()

    private fun WeeklyAiEntity.toDomain() = WeeklyAiReview(
        periodKey, localDataVersion, aiConsentVersion, status, generatedAt, summary,
        WeeklyAiParser.highlightsFrom(highlightsJson), suggestion,
        WeeklyAiParser.decodeFactIds(factIdsJson), categories, error, model,
    )
}
