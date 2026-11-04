package com.mengyalife.app.domain.advisor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * 草案 tempId 与「tempId→正式 goalId」映射的唯一编解码实现。
 *
 * 禁止在业务代码里手工拼接 JSON 字符串；所有读写都经过这里，
 * 避免转义/类型不一致导致 appliedGoalIds 读空。
 */
object GoalProposalIds {

    /** 1–48 位，字母开头，只允许字母/数字/下划线/连字符。 */
    private val TEMP_ID = Regex("^[A-Za-z][A-Za-z0-9_-]{0,47}$")
    private val json = Json { prettyPrint = false }

    fun isValidTempId(value: String): Boolean = TEMP_ID.matches(value)

    fun requireValidTempId(value: String): String {
        require(isValidTempId(value)) { "非法 tempId: $value" }
        return value
    }

    fun encodeMapping(mapping: Map<String, Long>): String {
        mapping.keys.forEach(::requireValidTempId)
        require(mapping.values.all { it > 0 }) { "goalId 必须为正数" }
        return buildJsonObject {
            mapping.toSortedMap().forEach { (tempId, goalId) ->
                put(tempId, JsonPrimitive(goalId))
            }
        }.toString()
    }

    /** 兼容对象映射；数组形式只作为旧数据的只读兜底，不再写出。 */
    fun decodeMapping(raw: String?): Map<String, Long> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val element = json.parseToJsonElement(raw)
            when (element) {
                is JsonObject -> element.entries.mapNotNull { (key, value) ->
                    if (!isValidTempId(key)) return@mapNotNull null
                    val id = value.jsonPrimitive.longOrNull ?: return@mapNotNull null
                    if (id <= 0) null else key to id
                }.toMap()
                is JsonArray -> emptyMap() // 旧的错误数组格式没有 tempId，无法可靠还原
                else -> emptyMap()
            }
        }.getOrDefault(emptyMap())
    }

    fun encodeSelected(selected: Set<String>): String {
        selected.forEach(::requireValidTempId)
        return buildJsonArray {
            selected.sorted().forEach { add(JsonPrimitive(it)) }
        }.toString()
    }

    fun decodeSelected(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return runCatching {
            json.parseToJsonElement(raw).jsonArray.mapNotNull {
                it.jsonPrimitive.contentOrNull?.takeIf(::isValidTempId)
            }.toSet()
        }.getOrDefault(emptySet())
    }
}
