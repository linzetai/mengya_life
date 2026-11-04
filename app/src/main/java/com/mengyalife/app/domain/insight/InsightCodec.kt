package com.mengyalife.app.domain.insight

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object InsightCodec {
    private val json = Json

    fun factsToJson(facts: List<InsightFact>): String = buildJsonArray {
        facts.forEach { fact ->
            add(buildJsonObject {
                put("id", fact.id); put("text", fact.text)
                put("isInference", fact.isInference)
                put("sources", sourcesArray(fact.sourceRefs))
            })
        }
    }.toString()

    fun insightsToJson(insights: List<GrowthInsight>): String = buildJsonArray {
        insights.forEach { insight ->
            add(buildJsonObject {
                put("id", insight.id); put("type", insight.type.name)
                put("title", insight.title); put("body", insight.body)
                put("isInference", insight.isInference)
                put("confidence", insight.confidence)
                put("sources", sourcesArray(insight.sources))
            })
        }
    }.toString()

    private fun sourcesArray(refs: List<InsightSourceRef>) = buildJsonArray {
        refs.forEach { ref ->
            add(buildJsonObject {
                put("type", ref.type); put("id", ref.recordId); put("label", ref.label)
            })
        }
    }

    fun factsFromJson(raw: String?): List<InsightFact> = runCatching {
        if (raw.isNullOrBlank()) emptyList() else json.parseToJsonElement(raw).jsonArray.map { element ->
            val obj = element.jsonObject
            InsightFact(
                id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                text = obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                sourceRefs = parseSources(obj["sources"]),
                isInference = obj["isInference"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }
    }.getOrDefault(emptyList())

    fun insightsFromJson(raw: String?): List<GrowthInsight> = runCatching {
        if (raw.isNullOrBlank()) emptyList() else json.parseToJsonElement(raw).jsonArray.map { element ->
            val obj = element.jsonObject
            GrowthInsight(
                id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                type = InsightType.entries.firstOrNull { it.name == obj["type"]?.jsonPrimitive?.contentOrNull } ?: InsightType.SUGGESTION,
                title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                body = obj["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                isInference = obj["isInference"]?.jsonPrimitive?.booleanOrNull ?: true,
                confidence = obj["confidence"]?.jsonPrimitive?.doubleOrNull,
                sources = parseSources(obj["sources"]),
            )
        }
    }.getOrDefault(emptyList())

    private fun parseSources(element: kotlinx.serialization.json.JsonElement?): List<InsightSourceRef> =
        runCatching {
            if (element == null) return emptyList()
            element.jsonArray.map { item ->
                val obj = item.jsonObject
                InsightSourceRef(
                    obj["type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    obj["label"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
            }
        }.getOrDefault(emptyList())
}
