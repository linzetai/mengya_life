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
import com.mengyalife.app.domain.advisor.AiConfigState
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
    private val zone: () -> java.time.ZoneId = { java.time.ZoneId.systemDefault() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val beforeRequest: suspend () -> Unit = {},
    private val beforeWrite: suspend () -> Unit = {},
    private val writeBarrier: RestoreWriteBarrier? = null,
) {
    suspend fun currentInput(): InsightInput {
        val consent = consentRepository.current()
        val snapshot = dataRepository.snapshot(consent)
        return InsightRules.build(snapshot, consent, now(), zone())
    }

    suspend fun loadCurrent(): InsightReport? {
        val input = currentInput()
        return dao.findByDataVersion(input.dataVersion)?.toDomain()
    }

    /** Distinguish a naturally expired observation window from changed source facts. */
    suspend fun staleReportReason(): String? {
        val consent = consentRepository.current()
        if (!consent.granted || setOf(ConsentCategory.GOALS, ConsentCategory.PROGRESS, ConsentCategory.JOURNAL)
                .none(consent::enabled)
        ) return "AI 数据授权已关闭或类别已调整；请确认授权后重新评估"
        val input = InsightRules.build(dataRepository.snapshot(consent), consent, now(), zone())
        if (!input.hasEnoughData) return "数据不足；先补充真实记录再重新评估"
        val latest = dao.latest()?.toDomain()?.takeIf { it.status == InsightReportStatus.READY } ?: return null
        if (latest.dataVersion == input.dataVersion) return null
        return staleVersionReason(latest.dataVersion, input)
    }

    private fun staleVersionReason(previousVersion: String, current: InsightInput): String {
        val previousSource = InsightRules.sourceFingerprintFromDataVersion(previousVersion)
        return when {
            previousSource == null -> "旧版洞察无法区分来源与观察窗口，建议重新生成；不代表原始记录已修改"
            previousSource == current.sourceFingerprint -> "洞察观察时间窗口已过期或时区日期已变化；原始记录未变，请重新评估"
            else -> "原始记录或授权范围已变化，这条建议需要重新评估"
        }
    }

    /** Resolve a UI pointer against current authorized facts; cached labels alone are never trusted. */
    suspend fun resolveCurrentReference(reference: InsightReference): InsightReferenceResolution {
        val consent = consentRepository.current()
        if (!consent.granted) {
            return InsightReferenceResolution.Stale("AI 授权已关闭，请重新生成洞察")
        }
        val input = InsightRules.build(dataRepository.snapshot(consent), consent, now(), zone())
        if (!input.hasEnoughData) {
            return InsightReferenceResolution.Stale("可用数据不足，请补充真实记录后重新评估")
        }
        if (input.dataVersion != reference.dataVersion) {
            return InsightReferenceResolution.Stale(staleVersionReason(reference.dataVersion, input))
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
        val requestBarrier = writeBarrier
            ?: return InsightGenerationResult.Failed("恢复安全边界不可用；未发起 AI 请求", null)
        val restoreGeneration = requestBarrier.generation()
        val consent = consentRepository.current()
        val snapshot = dataRepository.snapshot(consent)
        val input = InsightRules.build(snapshot, consent, now(), zone())
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
            boundRuntime.endpoint != boundConfig.endpoint || boundRuntime.model != boundConfig.model ||
            !boundConsent.isValidFor(boundRuntime.endpoint)
        ) {
            return saveFailed(input, "AI 服务未配置或授权已失效；已保留本地规则分析", null, restoreGeneration)
        }

        beforeRequest()
        val preflight = preflightProblem(boundConfig, boundConsent, boundCategories, boundRuntime, boundInput, restoreGeneration)
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
        // 重新进入同一 restore mutex 后再检查；任何排队的撤权/配置/恢复都先于旧请求。
        val result = requestBarrier.withWrite(restoreGeneration) {
            beforeRequest()
            val atBoundary = preflightProblem(
                boundConfig, boundConsent, boundCategories, boundRuntime, boundInput, restoreGeneration,
            )
            if (atBoundary != null) {
                AdvisorChatResult.Failure(
                    com.mengyalife.app.domain.advisor.AdvisorError(
                        com.mengyalife.app.domain.advisor.AdvisorError.CANCELLED,
                        "$atBoundary；没有调用模型",
                    ),
                )
            } else request()
        } ?: request()
        val afterProblem = preflightProblem(boundConfig, boundConsent, boundCategories, boundRuntime, boundInput, restoreGeneration)
        if (afterProblem != null) return InsightGenerationResult.Failed("$afterProblem；本次 AI 结果已丢弃", null)
        return when (result) {
            is AdvisorChatResult.Failure -> saveFailed(
                boundInput, result.error.userMessage, null, restoreGeneration,
                boundConfig = boundConfig, boundConsent = boundConsent, boundRuntime = boundRuntime,
            )
            is AdvisorChatResult.Success -> when (val parsed = InsightParser.parse(
                result.content, boundInput.facts, boundInput.allowedSources, newId,
            )) {
                is InsightParseResult.Failure -> saveFailed(
                    boundInput, "洞察解析失败：${parsed.message}", null, restoreGeneration,
                    boundConfig = boundConfig, boundConsent = boundConsent, boundRuntime = boundRuntime,
                )
                is InsightParseResult.Success -> {
                    val report = InsightReport(
                        id = newId(), dataVersion = boundInput.dataVersion,
                        status = InsightReportStatus.READY, summary = parsed.summary,
                        facts = boundInput.facts, insights = parsed.insights,
                        periodStart = boundInput.periodStart, periodEnd = boundInput.periodEnd,
                        generatedAt = now(), error = null, model = null,
                    )
                    var finalProblem: String? = null
                    writeReport(restoreGeneration) {
                        finalProblem = preflightProblem(
                            boundConfig, boundConsent, boundCategories, boundRuntime, boundInput, restoreGeneration,
                        )
                        if (finalProblem == null) dao.insert(report.toEntity())
                    }
                    if (finalProblem != null) {
                        InsightGenerationResult.Failed("$finalProblem；本次洞察结果已丢弃", null)
                    } else InsightGenerationResult.Ready(report)
                }
            }
        }
    }

    private suspend fun preflightProblem(
        boundConfig: AiConfigState,
        boundConsent: com.mengyalife.app.domain.advisor.AiConsentState,
        boundCategories: Set<ConsentCategory>,
        boundRuntime: com.mengyalife.app.domain.advisor.AdvisorRuntimeConfig,
        boundInput: InsightInput,
        expectedRestoreGeneration: Long?,
    ): String? {
        val currentConsent = consentRepository.current()
        val currentConfig = configRepository.state.first()
        val currentRuntime = configRepository.runtimeConfig()
        if (expectedRestoreGeneration != null &&
            (writeBarrier?.isReady() == false || writeBarrier?.generation() != expectedRestoreGeneration)
        ) return "恢复 generation 已变化"
        if (!currentConsent.granted || currentConsent.epoch != boundConsent.epoch ||
            currentConsent.categories != boundCategories
        ) return "授权已变化，请求未发起或结果已丢弃"
        if (!currentConsent.isValidFor(boundRuntime.endpoint)) return "接收方授权已失效"
        if (currentConfig != boundConfig || currentRuntime != boundRuntime) {
            return "模型服务 endpoint/model/API Key 配置已变化"
        }
        val currentInput = currentInput()
        if (currentInput.dataVersion != boundInput.dataVersion) {
            return if (currentInput.sourceFingerprint == boundInput.sourceFingerprint) {
                "洞察观察时间窗口已过期或时区日期已变化；来源事实未变"
            } else {
                "授权范围或原始事实已变化"
            }
        }
        return null
    }

    private suspend fun saveFailed(
        input: InsightInput,
        message: String,
        model: String?,
        restoreGeneration: Long? = null,
        boundConfig: AiConfigState? = null,
        boundConsent: com.mengyalife.app.domain.advisor.AiConsentState? = null,
        boundRuntime: com.mengyalife.app.domain.advisor.AdvisorRuntimeConfig? = null,
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
        var staleReason: String? = null
        writeReport(restoreGeneration) {
            if (boundConfig != null && boundConsent != null && boundRuntime != null) {
                staleReason = preflightProblem(
                    boundConfig, boundConsent, boundConsent.categories, boundRuntime, input, restoreGeneration,
                )
            }
            if (staleReason == null) dao.insert(report.toEntity())
        }
        return if (staleReason == null) {
            InsightGenerationResult.Failed(message, report)
        } else {
            InsightGenerationResult.Failed("$staleReason；旧版本失败报告未保存", null)
        }
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
