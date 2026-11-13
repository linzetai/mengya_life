package com.mengyalife.app.domain.insight

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface InsightParseResult {
    data class Success(val summary: String, val insights: List<GrowthInsight>) : InsightParseResult
    data class Failure(val message: String) : InsightParseResult
}

object InsightParser {
    private val json = Json { ignoreUnknownKeys = true }
    const val SCHEMA_VERSION = 1
    private const val MAX_INSIGHTS = 3

    fun parse(
        raw: String,
        facts: List<InsightFact>,
        allowedSources: List<InsightSourceRef>,
        newId: () -> String,
    ): InsightParseResult {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return InsightParseResult.Failure("没有找到 JSON 对象")
        val root = runCatching { json.parseToJsonElement(raw.substring(start, end + 1)).jsonObject }
            .getOrElse { return InsightParseResult.Failure("JSON 解析失败") }
        if (root["schemaVersion"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() != SCHEMA_VERSION) {
            return InsightParseResult.Failure("schemaVersion 必须为 $SCHEMA_VERSION")
        }
        val summary = root["summary"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (summary.isEmpty()) return InsightParseResult.Failure("缺少 summary")
        val array = root["insights"] as? JsonArray
            ?: return InsightParseResult.Failure("缺少 insights 数组")
        if (array.size > MAX_INSIGHTS) return InsightParseResult.Failure("洞察数量超过上限")
        val allowedKeys = allowedSources.map { it.type + ":" + it.recordId }.toSet()
        val allowedFactIds = facts.map { it.id }.toSet()
        val result = mutableListOf<GrowthInsight>()
        for ((index, element) in array.withIndex()) {
            val obj = element as? JsonObject
                ?: return InsightParseResult.Failure("insights[$index] 不是对象")
            val type = InsightType.entries.firstOrNull {
                it.name == obj["type"]?.jsonPrimitive?.contentOrNull?.uppercase()
            } ?: return InsightParseResult.Failure("insights[$index] type 非法")
            val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val body = obj["body"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (title.isEmpty() || body.isEmpty()) return InsightParseResult.Failure("insights[$index] 标题/正文为空")
            if (title.length > 80 || body.length > 600) return InsightParseResult.Failure("insights[$index] 过长")
            if (containsDiagnosisLabel("$title $body")) {
                return InsightParseResult.Failure("洞察包含诊断性标签，已拒绝保存 AI 结论")
            }
            val inference = obj["isInference"]?.jsonPrimitive?.booleanOrNull
                ?: return InsightParseResult.Failure("insights[$index] 缺少 isInference")
            if (type == InsightType.SUGGESTION && !inference) {
                return InsightParseResult.Failure("行动建议必须明确标记为 AI 推测")
            }
            val confidence = obj["confidence"]?.jsonPrimitive?.doubleOrNull?.also {
                if (!it.isFinite() || it < 0 || it > 1) return InsightParseResult.Failure("confidence 非法")
            }
            val factIdArray = obj["factIds"] as? JsonArray
                ?: return InsightParseResult.Failure("insights[$index] 缺少 factIds")
            if (factIdArray.isEmpty()) return InsightParseResult.Failure("insights[$index] factIds 不能为空")
            val factIds = factIdArray.mapNotNull { it.jsonPrimitive.contentOrNull }
            if (factIds.size != factIdArray.size || factIds.any { it !in allowedFactIds }) {
                return InsightParseResult.Failure("insights[$index] 引用了本轮不存在的事实")
            }
            val referencedFacts = facts.filter { it.id in factIds }
            val onlyInsufficient = referencedFacts.isNotEmpty() && referencedFacts.all { it.id == "insufficient" }
            val relatedKeys = referencedFacts.flatMap { f -> f.sourceRefs.map { it.type + ":" + it.recordId } }.toSet()
            val sourceArray = obj["sources"] as? JsonArray
                ?: return InsightParseResult.Failure("insights[$index] 缺少 sources")
            if (sourceArray.isEmpty() && !onlyInsufficient) {
                return InsightParseResult.Failure("insights[$index] sources 不能为空")
            }
            val sources = mutableListOf<InsightSourceRef>()
            for (sourceElement in sourceArray) {
                val sourceObj = sourceElement as? JsonObject
                    ?: return InsightParseResult.Failure("来源不是对象")
                val sourceType = sourceObj["type"]?.jsonPrimitive?.contentOrNull.orEmpty().uppercase()
                val rawId = sourceObj["id"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                // 兼容模型常见的 "CHECKIN:7" 写法，但校验时仍归一化为真实 ID。
                val recordId = rawId.removePrefix("$sourceType:")
                if ("$sourceType:$recordId" !in allowedKeys) {
                    return InsightParseResult.Failure("来源不在本轮授权上下文：$sourceType:$recordId")
                }
                if ("$sourceType:$recordId" !in relatedKeys) {
                    return InsightParseResult.Failure("来源与 insight 的 factIds 无关联：$sourceType:$recordId")
                }
                sources.add(
                    InsightSourceRef(
                        sourceType,
                        recordId,
                        sourceObj["label"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    ),
                )
            }
            val referenced = sources.joinToString(" ") { "${it.type}:${it.recordId}" }
            result.add(
                GrowthInsight(
                    id = newId(),
                    type = type,
                    title = title,
                    body = body,
                    isInference = inference,
                    confidence = confidence,
                    sources = sources,
                    factIds = factIds,
                ),
            )
        }
        if (result.isEmpty()) return InsightParseResult.Failure("insights 不能为空")
        return InsightParseResult.Success(summary, result)
    }

    private val diagnosisTerms = listOf(
        "抑郁症", "焦虑症", "双相障碍", "躁郁症", "精神分裂症", "自闭症", "强迫症",
        "诊断为", "确诊", "ptsd", "adhd", "major depressive disorder", "bipolar disorder", "anxiety disorder",
    )

    private fun containsDiagnosisLabel(text: String): Boolean {
        val normalized = text.lowercase()
        return diagnosisTerms.any { it in normalized }
    }

    fun prompt(input: InsightInput): String = buildString {
        appendLine("你是长期成长顾问。以下 facts 是规则层根据用户数据计算出的可核验事实；")
        appendLine("dataSufficient=${input.hasEnoughData}")
        appendLine("只能解释这些事实、提出行动建议，不能编造事实，不能声称已完成任何目标/进展。")
        appendLine("只输出严格 JSON，不要 Markdown。schemaVersion=1：")
        appendLine("{\"schemaVersion\":1,\"summary\":\"...\",\"insights\":[{\"type\":\"POSITIVE|RISK|SUGGESTION\",\"title\":\"...\",\"body\":\"...\",\"isInference\":true,\"confidence\":0.0,\"sources\":[{\"type\":\"GOAL|CHECKIN|MEMORY\",\"id\":\"...\",\"label\":\"...\"}]}]}")
        appendLine("最多 3 条；每条 body 不超过 300 字；**每条 insight 必须包含 factIds 数组**，只能从本轮事实 ID 中选择；")
        appendLine("缺少 factIds 或引用不存在的事实会被拒绝；sources 必须来自 allowedSources，且必须是 factIds 所指事实的 sourceRefs；")
        appendLine("如果只引用 insufficient 事实，sources 可以为空；其他情况 sources 不得为空；不同单位不得比较/相加；")
        appendLine("可用 factIds：${input.facts.joinToString(",") { it.id }}")
        appendLine("数据不足时明确说「信息不足」；建议必须是 isInference=true。日记或情绪内容不可推出心理/医学诊断，不得使用诊断性标签。")
        appendLine("允许来源：")
        input.allowedSources.forEach { appendLine("- ${it.type}:${it.recordId} (${it.label})") }
        appendLine("事实：")
        input.facts.forEach {
            appendLine("- [${it.id}] ${if (it.isInference) "AI 推测" else "规则事实"}：${it.text}")
            appendLine("  factSources: ${it.sourceRefs.joinToString(",") { source -> "${source.type}:${source.recordId}" }}")
        }
        appendLine("时间范围：${input.periodStart}..${input.periodEnd}")
    }
}
