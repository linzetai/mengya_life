package com.mengyalife.app.domain.weekly

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object WeeklyReviewCodec {
    private val json = Json

    fun factsJson(facts: List<WeeklyFact>): String = arrayJson(facts)
    fun sectionsJson(sections: WeeklySections): String = buildJsonObject {
        put("changes", kotlinx.serialization.json.Json.parseToJsonElement(arrayJson(sections.changes)))
        put("wins", kotlinx.serialization.json.Json.parseToJsonElement(arrayJson(sections.wins)))
        put("attention", kotlinx.serialization.json.Json.parseToJsonElement(arrayJson(sections.attention)))
        put("nextSteps", kotlinx.serialization.json.Json.parseToJsonElement(arrayJson(sections.nextSteps)))
    }.toString()

    private fun arrayJson(facts: List<WeeklyFact>) = buildJsonArray {
        facts.forEach { fact ->
            add(buildJsonObject {
                put("id", fact.id); put("text", fact.text)
                put("sources", buildJsonArray {
                    fact.sources.forEach { s ->
                        add(buildJsonObject {
                            put("type", s.type); put("id", s.recordId); put("label", s.label)
                        })
                    }
                })
            })
        }
    }.toString()

    fun factsFrom(raw: String?): List<WeeklyFact> = runCatching {
        if (raw.isNullOrBlank()) emptyList() else json.parseToJsonElement(raw).jsonArray.map { element ->
            val obj = element.jsonObject
            WeeklyFact(
                id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                text = obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                sources = (obj["sources"]?.jsonArray ?: emptyList()).map { s ->
                    val so = s.jsonObject
                    WeeklySourceRef(
                        so["type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        so["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        so["label"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    )
                },
            )
        }
    }.getOrDefault(emptyList())

    fun sectionsFrom(raw: String?): WeeklySections = runCatching {
        val obj = json.parseToJsonElement(raw.orEmpty()).jsonObject
        WeeklySections(
            changes = factsFrom(obj["changes"].toString()),
            wins = factsFrom(obj["wins"].toString()),
            attention = factsFrom(obj["attention"].toString()),
            nextSteps = factsFrom(obj["nextSteps"].toString()),
        )
    }.getOrDefault(WeeklySections(emptyList(), emptyList(), emptyList(), emptyList()))
}
