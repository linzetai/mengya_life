package com.mengyalife.app.data.insight

import com.mengyalife.app.data.advisor.AdvisorConfigRepository
import com.mengyalife.app.data.advisor.AdvisorDataRepository
import com.mengyalife.app.data.advisor.AdvisorEngine
import com.mengyalife.app.data.consent.AiConsentRepository
import com.mengyalife.app.data.local.insight.InsightReportDao
import com.mengyalife.app.data.local.insight.InsightReportEntity
import com.mengyalife.app.domain.advisor.AdvisorChatMessage
import com.mengyalife.app.domain.advisor.AdvisorChatResult
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.insight.GrowthInsight
import com.mengyalife.app.domain.insight.InsightCodec
import com.mengyalife.app.domain.insight.InsightFact
import com.mengyalife.app.domain.insight.InsightInput
import com.mengyalife.app.domain.insight.InsightParseResult
import com.mengyalife.app.domain.insight.InsightParser
import com.mengyalife.app.domain.insight.InsightReport
import com.mengyalife.app.domain.insight.InsightReportStatus
import com.mengyalife.app.domain.insight.InsightRules
import kotlinx.coroutines.flow.first
import java.util.UUID

sealed interface InsightGenerationResult {
    data class Ready(val report: InsightReport) : InsightGenerationResult
    data class Failed(val message: String, val report: InsightReport?) : InsightGenerationResult
}

/**
 * 成长洞察 V1：
 * - 规则层先算 facts/数据版本；
 * - AI 只解释 facts 并给建议，严格 JSON + 来源白名单；
 * - 报告按 dataVersion 缓存，过期不展示；
 * - 只由用户点击触发网络请求。
 */
class InsightService(
    private val dataRepository: AdvisorDataRepository,
    private val consentRepository: AiConsentRepository,
    private val configRepository: AdvisorConfigRepository,
    private val networkEngine: AdvisorEngine,
    private val fakeEngine: AdvisorEngine?,
    private val dao: InsightReportDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun currentInput(): InsightInput {
        val consent = consentRepository.current()
        val snapshot = dataRepository.snapshot(consent)
        return InsightRules.build(snapshot, consent, now())
    }

    suspend fun loadCurrent(): InsightReport? {
        val input = currentInput()
        return dao.findByDataVersion(input.dataVersion)?.toDomain()
    }

    suspend fun generate(force: Boolean = false): InsightGenerationResult {
        val consent = consentRepository.current()
        val snapshot = dataRepository.snapshot(consent)
        val input = InsightRules.build(snapshot, consent, now())
        val cached = dao.findByDataVersion(input.dataVersion)
        if (!force && cached != null) return InsightGenerationResult.Ready(cached.toDomain())

        val rules = input
        val config = configRepository.state.first()
        val runtime = configRepository.runtimeConfig()
        if (!consent.granted ||
            !consent.enabled(ConsentCategory.PROGRESS) &&
            !consent.enabled(ConsentCategory.GOALS) &&
            !consent.enabled(ConsentCategory.JOURNAL)
        ) {
            return saveFailed(input, "尚未授权任何成长数据类别", null)
        }
        if (!config.configured || runtime == null || runtime.endpoint != config.endpoint ||
            !consent.isValidFor(runtime.endpoint)
        ) {
            return saveFailed(input, "AI 服务未配置或授权已失效；已保留本地规则分析", null)
        }

        val engine = if (config.useFakeEngine && fakeEngine != null) fakeEngine else networkEngine
        val prompt = InsightParser.prompt(input)
        val result = engine.chat(
            config = runtime,
            messages = listOf(
                AdvisorChatMessage("system", "只输出严格 JSON；只解释给定事实，不编造。"),
                AdvisorChatMessage("user", prompt),
            ),
            maxTokens = 2_500,
        )
        // 请求后数据/授权变化则不保存 AI 结果
        val afterConsent = consentRepository.current()
        val afterInput = InsightRules.build(dataRepository.snapshot(afterConsent), afterConsent, now())
        if (afterInput.dataVersion != input.dataVersion || !afterConsent.isValidFor(runtime.endpoint)) {
            return saveFailed(input, "数据或授权已变化，本次 AI 洞察已丢弃；请重新生成", null)
        }
        return when (result) {
            is AdvisorChatResult.Failure -> saveFailed(input, result.error.userMessage, null)
            is AdvisorChatResult.Success -> when (val parsed = InsightParser.parse(
                result.content, input.facts, input.allowedSources, newId,
            )) {
                is InsightParseResult.Failure -> saveFailed(input, "洞察解析失败：${parsed.message}", null)
                is InsightParseResult.Success -> {
                    val report = InsightReport(
                        id = newId(),
                        dataVersion = input.dataVersion,
                        status = InsightReportStatus.READY,
                        summary = parsed.summary,
                        facts = input.facts,
                        insights = parsed.insights,
                        periodStart = input.periodStart,
                        periodEnd = input.periodEnd,
                        generatedAt = now(),
                        error = null,
                        model = null,
                    )
                    dao.insert(report.toEntity())
                    InsightGenerationResult.Ready(report)
                }
            }
        }
    }

    private suspend fun saveFailed(
        input: InsightInput,
        message: String,
        model: String?,
    ): InsightGenerationResult {
        val report = InsightReport(
            id = newId(),
            dataVersion = input.dataVersion,
            status = InsightReportStatus.FAILED,
            summary = "",
            facts = input.facts,
            insights = emptyList(),
            periodStart = input.periodStart,
            periodEnd = input.periodEnd,
            generatedAt = now(),
            error = message.take(300),
            model = model,
        )
        dao.insert(report.toEntity())
        return InsightGenerationResult.Failed(message, report)
    }

    private fun InsightReport.toEntity() = InsightReportEntity(
        id = id, dataVersion = dataVersion, status = status.name,
        summary = summary, factsJson = InsightCodec.factsToJson(facts),
        insightsJson = InsightCodec.insightsToJson(insights),
        periodStart = periodStart, periodEnd = periodEnd,
        generatedAt = generatedAt, error = error, model = model,
    )

    private fun InsightReportEntity.toDomain() = InsightReport(
        id = id, dataVersion = dataVersion,
        status = InsightReportStatus.entries.firstOrNull { it.name == status } ?: InsightReportStatus.FAILED,
        summary = summary, facts = InsightCodec.factsFromJson(factsJson),
        insights = InsightCodec.insightsFromJson(insightsJson),
        periodStart = periodStart, periodEnd = periodEnd,
        generatedAt = generatedAt, error = error, model = model,
    )
}
