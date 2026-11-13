package com.mengyalife.app.data.insight

import com.mengyalife.app.data.advisor.AdvisorConfigRepository
import com.mengyalife.app.data.advisor.AdvisorDataRepository
import com.mengyalife.app.data.advisor.AdvisorEngine
import com.mengyalife.app.data.consent.AiConsentRepository
import com.mengyalife.app.data.local.insight.InsightReportDao
import com.mengyalife.app.data.restore.RestoreWriteBarrier
import com.mengyalife.app.data.local.insight.InsightReportEntity
import com.mengyalife.app.domain.advisor.AdvisorChatMessage
import com.mengyalife.app.domain.advisor.AdvisorChatResult
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.insight.GrowthInsight
import com.mengyalife.app.domain.insight.InsightCodec
import com.mengyalife.app.domain.insight.InsightFact
import com.mengyalife.app.domain.insight.InsightInput
import com.mengyalife.app.domain.insight.InsightParseResult
import com.mengyalife.app.domain.insight.InsightReference
import com.mengyalife.app.domain.insight.InsightReferenceResolution
import com.mengyalife.app.domain.insight.ResolvedInsightReference
import com.mengyalife.app.domain.insight.InsightType
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
    private val beforeRequest: suspend () -> Unit = {},
    private val beforeWrite: suspend () -> Unit = {},
    private val writeBarrier: RestoreWriteBarrier? = null,
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

    /** Resolve a UI pointer against current authorized facts; cached labels alone are never trusted. */
    suspend fun resolveCurrentReference(reference: InsightReference): InsightReferenceResolution {
        val consent = consentRepository.current()
        if (!consent.granted) {
            return InsightReferenceResolution.Stale("AI 授权已关闭，请重新生成洞察")
        }
        val input = InsightRules.build(dataRepository.snapshot(consent), consent, now())
        if (!input.hasEnoughData) {
            return InsightReferenceResolution.Stale("可用数据不足，请补充真实记录后重新评估")
        }
        if (input.dataVersion != reference.dataVersion) {
            return InsightReferenceResolution.Stale("原始记录或授权已变化，这条建议需要重新评估")
        }
        val report = dao.findByDataVersion(input.dataVersion)?.toDomain()
            ?: return InsightReferenceResolution.Stale("当前数据版本没有可用洞察，请重新生成")
        if (report.id != reference.reportId || report.status != InsightReportStatus.READY) {
            return InsightReferenceResolution.Stale("洞察报告已更新，请从当前建议重新开始")
        }
        val insight = report.insights.firstOrNull { it.id == reference.insightId }
            ?.takeIf { it.type == InsightType.SUGGESTION }
            ?: return InsightReferenceResolution.Stale("这条行动建议已不存在，请重新评估")
        if (insight.type == InsightType.SUGGESTION && !insight.isInference) {
            return InsightReferenceResolution.Stale("行动建议未标记为推测，不能作为确定事实")
        }
        if (insight.factIds.isEmpty()) {
            return InsightReferenceResolution.Stale("建议缺少可核验事实，不能作为行动依据")
        }
        val factsById = input.facts.associateBy { it.id }
        val facts = insight.factIds.mapNotNull(factsById::get)
        if (facts.size != insight.factIds.distinct().size) {
            return InsightReferenceResolution.Stale("建议引用的事实已失效，请重新生成洞察")
        }
        val allowed = input.allowedSources.toSet()
        val factSources = facts.flatMap { it.sourceRefs }.toSet()
        if (facts.none { it.sourceRefs.isNotEmpty() } ||
            factSources.any { it !in allowed } ||
            insight.sources.isEmpty() || insight.sources.any { it !in factSources || it !in allowed }
        ) {
            return InsightReferenceResolution.Stale("建议来源已失效或授权范围已变化，请重新评估")
        }
        return InsightReferenceResolution.Current(
            ResolvedInsightReference(reference, insight, facts),
        )
    }

    suspend fun generate(force: Boolean = false): InsightGenerationResult {
        val restoreGeneration = writeBarrier?.generation()
        val consent = consentRepository.current()
        val snapshot = dataRepository.snapshot(consent)
        val input = InsightRules.build(snapshot, consent, now())
        val cached = dao.findByDataVersion(input.dataVersion)
        if (!force && cached != null) return InsightGenerationResult.Ready(cached.toDomain())

        val boundConsent = consent
        val boundCategories = consent.categories
        val boundInput = input
        val boundConfig = configRepository.state.first()
        val boundRuntime = configRepository.runtimeConfig()
        if (!boundConsent.granted ||
            !consent.enabled(ConsentCategory.PROGRESS) &&
            !consent.enabled(ConsentCategory.GOALS) &&
            !consent.enabled(ConsentCategory.JOURNAL)
        ) {
            return saveFailed(input, "尚未授权任何成长数据类别", null, restoreGeneration)
        }
        if (!input.hasEnoughData) {
            return saveFailed(input, "数据不足；已保留本地规则事实，本次未请求 AI", null, restoreGeneration)
        }
        if (!boundConfig.configured || boundRuntime == null ||
            boundRuntime.endpoint != boundConfig.endpoint ||
            !boundConsent.isValidFor(boundRuntime.endpoint)
        ) {
            return saveFailed(input, "AI 服务未配置或授权已失效；已保留本地规则分析", null, restoreGeneration)
        }

        beforeRequest()
        val preflight = preflightProblem(boundConsent, boundCategories, boundRuntime, boundInput)
        if (preflight != null) return InsightGenerationResult.Failed(preflight, null)

        val engine = if (boundConfig.useFakeEngine && fakeEngine != null) fakeEngine else networkEngine
        val prompt = InsightParser.prompt(boundInput)
        val request: suspend () -> AdvisorChatResult = {
            engine.chat(
                config = boundRuntime,
                messages = listOf(
                    AdvisorChatMessage("system", "GROWTH_INSIGHT_V1；只输出严格 JSON；只解释给定事实，不编造、不诊断。"),
                    AdvisorChatMessage("user", prompt),
                ),
                maxTokens = 2_500,
            )
        }
        // 不允许在恢复 Gate 激活后启动新请求；in-flight 请求完成后仍需generation check。
        val result = writeBarrier?.withWrite(restoreGeneration) { request() } ?: request()
        val afterProblem = preflightProblem(boundConsent, boundCategories, boundRuntime, boundInput)
        if (afterProblem != null) return InsightGenerationResult.Failed("$afterProblem；本次 AI 结果已丢弃", null)
        return when (result) {
            is AdvisorChatResult.Failure -> saveFailed(boundInput, result.error.userMessage, null, restoreGeneration)
            is AdvisorChatResult.Success -> when (val parsed = InsightParser.parse(
                result.content, boundInput.facts, boundInput.allowedSources, newId,
            )) {
                is InsightParseResult.Failure -> saveFailed(boundInput, "洞察解析失败：${parsed.message}", null, restoreGeneration)
                is InsightParseResult.Success -> {
                    val report = InsightReport(
                        id = newId(), dataVersion = boundInput.dataVersion,
                        status = InsightReportStatus.READY, summary = parsed.summary,
                        facts = boundInput.facts, insights = parsed.insights,
                        periodStart = boundInput.periodStart, periodEnd = boundInput.periodEnd,
                        generatedAt = now(), error = null, model = null,
                    )
                    writeReport(restoreGeneration) { dao.insert(report.toEntity()) }
                    InsightGenerationResult.Ready(report)
                }
            }
        }
    }

    private suspend fun preflightProblem(
        boundConsent: com.mengyalife.app.domain.advisor.AiConsentState,
        boundCategories: Set<ConsentCategory>,
        boundRuntime: com.mengyalife.app.domain.advisor.AdvisorRuntimeConfig,
        boundInput: InsightInput,
    ): String? {
        val currentConsent = consentRepository.current()
        val currentConfig = configRepository.state.first()
        val currentRuntime = configRepository.runtimeConfig()
        if (!currentConsent.granted || currentConsent.epoch != boundConsent.epoch ||
            currentConsent.categories != boundCategories
        ) return "授权已变化，请求未发起或结果已丢弃"
        if (!currentConsent.isValidFor(boundRuntime.endpoint)) return "接收方授权已失效"
        if (currentRuntime == null || currentRuntime.endpoint != boundRuntime.endpoint ||
            currentConfig.endpoint != boundRuntime.endpoint
        ) return "模型服务地址已变化"
        val currentInput = currentInput()
        if (currentInput.dataVersion != boundInput.dataVersion) return "目标/进展数据已变化"
        return null
    }

    private suspend fun saveFailed(
        input: InsightInput,
        message: String,
        model: String?,
        restoreGeneration: Long? = null,
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
        writeReport(restoreGeneration) { dao.insert(report.toEntity()) }
        return InsightGenerationResult.Failed(message, report)
    }

    private suspend fun writeReport(expectedGeneration: Long?, block: suspend () -> Unit) {
        beforeWrite()
        if (writeBarrier != null) writeBarrier.withWrite(expectedGeneration) { block() } else block()
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
