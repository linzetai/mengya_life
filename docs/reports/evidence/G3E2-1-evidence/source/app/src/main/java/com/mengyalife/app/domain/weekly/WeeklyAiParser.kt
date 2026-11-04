package com.mengyalife.app.domain.weekly

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

sealed interface WeeklyAiParseResult {
    data class Success(val summary: String, val highlights: List<WeeklyAiHighlight>, val suggestion: String) : WeeklyAiParseResult
    data class Failure(val message: String) : WeeklyAiParseResult
}

object WeeklyAiParser {
    private val json = Json

    fun prompt(facts: List<WeeklyFact>, categories: String): String = buildString {
        appendLine("你是长期成长顾问。以下是本周本地规则事实；只能解释事实、给个性化建议。")
        appendLine("只输出严格 JSON，不用 Markdown。schemaVersion=1，1-3 条 highlights，每条必须带 factIds（只能从下面 facts 选）和 sources（必须是 factIds 对应事实的来源）。")
        appendLine("{\"schemaVersion\":1,\"summary\":\"...\",\"highlights\":[{\"type\":\"POSITIVE\",\"title\":\"...\",\"body\":\"...\",\"isInference\":true,\"factIds\":[\"week-counts\"],\"sources\":[{\"type\":\"CHECKIN\",\"id\":\"1\",\"label\":\"...\"}]}],\"suggestion\":\"...\"}")
        appendLine("可用类别：$categories；不同单位不得比较/相加；信息不足必须明确说，不编造。")
        facts.forEach { appendLine("- [${it.id}] ${it.text}") }
    }

    fun parse(raw: String, facts: List<WeeklyFact>, categories: String): WeeklyAiParseResult {
        val start = raw.indexOf('{'); val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return WeeklyAiParseResult.Failure("没有 JSON")
        val root = runCatching { json.parseToJsonElement(raw.substring(start, end + 1)).jsonObject }
            .getOrElse { return WeeklyAiParseResult.Failure("JSON 解析失败") }
        if (root["schemaVersion"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() != 1) {
            return WeeklyAiParseResult.Failure("schemaVersion 非法")
        }
        val summary = root["summary"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        val suggestion = root["suggestion"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        if (summary.isEmpty() || suggestion.isEmpty()) return WeeklyAiParseResult.Failure("summary/suggestion 为空")
        val array = root["highlights"] as? kotlinx.serialization.json.JsonArray
            ?: return WeeklyAiParseResult.Failure("缺少 highlights")
        if (array.size !in 1..3) return WeeklyAiParseResult.Failure("highlights 数量非法")
        val byId = facts.associateBy { it.id }
        val highlights = mutableListOf<WeeklyAiHighlight>()
        for (el in array) {
            val o = el.jsonObject
            val type = o["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (type !in setOf("POSITIVE", "RISK", "SUGGESTION")) return WeeklyAiParseResult.Failure("type 非法")
            val title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            val body = o["body"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            if (title.isEmpty() || body.isEmpty()) return WeeklyAiParseResult.Failure("highlight 内容为空")
            val ids = (o["factIds"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            if (ids.isEmpty() || ids.any { it !in byId }) return WeeklyAiParseResult.Failure("factIds 非法")
            val related = ids.flatMap { byId[it]!!.sources.map { s -> s.type + ":" + s.recordId } }.toSet()
            val sources = (o["sources"] as? kotlinx.serialization.json.JsonArray)?.map { s ->
                val so = s.jsonObject
                WeeklySourceRef(
                    so["type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    so["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    so["label"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
            } ?: emptyList()
            val onlyInsufficient = ids.all { it == "insufficient" }
            if (sources.isEmpty() && !onlyInsufficient) return WeeklyAiParseResult.Failure("sources 不能为空")
            if (sources.any { "${it.type}:${it.recordId}" !in related }) {
                return WeeklyAiParseResult.Failure("sources 与 factIds 无关联")
            }
            highlights += WeeklyAiHighlight(type, title, body, ids, sources, o["isInference"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true)
        }
        return WeeklyAiParseResult.Success(summary, highlights, suggestion)
    }

    fun highlightsJson(highlights: List<WeeklyAiHighlight>): String = buildJsonArray {
        highlights.forEach { h ->
            add(buildJsonObject {
                put("type", h.type); put("title", h.title); put("body", h.body)
                put("factIds", buildJsonArray { h.factIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                put("sources", buildJsonArray {
                    h.sources.forEach { s -> add(buildJsonObject { put("type", s.type); put("id", s.recordId); put("label", s.label) }) }
                })
                put("isInference", h.isInference)
            })
        }
    }.toString()

    fun highlightsFrom(raw: String?): List<WeeklyAiHighlight> = runCatching {
        (json.parseToJsonElement(raw.orEmpty()).jsonArray).map { el ->
            val o = el.jsonObject
            WeeklyAiHighlight(
                o["type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                o["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                (o["factIds"]?.jsonArray ?: emptyList()).mapNotNull { it.jsonPrimitive.contentOrNull },
                (o["sources"]?.jsonArray ?: emptyList()).map { s ->
                    val so = s.jsonObject
                    WeeklySourceRef(so["type"]?.jsonPrimitive?.contentOrNull.orEmpty(), so["id"]?.jsonPrimitive?.contentOrNull.orEmpty(), so["label"]?.jsonPrimitive?.contentOrNull.orEmpty())
                },
                o["isInference"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true,
            )
        }
    }.getOrDefault(emptyList())
}
