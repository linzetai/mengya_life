package com.mengyalife.app.domain.growth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

object GrowthMemoryIds {
    private val json = Json

    fun encodeStrings(values: Collection<String>): String = buildJsonArray {
        values.distinct().sorted().forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
    }.toString()

    fun decodeStrings(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.parseToJsonElement(raw).jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
        }.getOrDefault(emptyList())
    }

    fun encodeLongs(values: Collection<Long>): String = buildJsonArray {
        values.distinct().sorted().forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
    }.toString()

    fun decodeLongs(raw: String?): List<Long> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.parseToJsonElement(raw).jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull?.toLongOrNull() }
        }.getOrDefault(emptyList())
    }
}
