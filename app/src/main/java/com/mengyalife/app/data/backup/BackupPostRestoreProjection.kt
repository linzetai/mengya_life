package com.mengyalife.app.data.backup

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * G4A-1 的“恢复后预期载荷”投影，仅用于校验/预览，不写 DB。
 *
 * 这不是恢复实现；它把 G4A-0 已定的转换规则应用在 JSON 上，用来验证
 * `payloadSha256` 与 `expectedRestoredSha256` 不应被混为一谈。
 */
object BackupPostRestoreProjection {

    fun project(payload: JsonObject): JsonObject {
        val data = payload["data"] as? JsonObject ?: return payload
        val journal = (data["journalEntries"] as? JsonArray).orEmptyArray().map { element ->
            val row = element.jsonObject.toMutableMap()
            if (row["analysisStatus"]?.jsonPrimitive?.content == "ANALYZING") {
                row["analysisStatus"] = kotlinx.serialization.json.JsonPrimitive("FAILED")
                row["analysisError"] = kotlinx.serialization.json.JsonPrimitive("分析被中断，请重试")
            }
            JsonObject(row)
        }
        val weekly = (data["weeklyReviews"] as? JsonArray).orEmptyArray().map { element ->
            val row = element.jsonObject.toMutableMap()
            row["notifiedAt"] = JsonNull
            JsonObject(row)
        }
        val weeklyAi = JsonArray(emptyList())

        val tables = linkedMapOf<String, JsonArray>()
        for (key in BackupFormatV2.TABLE_KEYS) {
            tables[key] = when (key) {
                "journalEntries" -> JsonArray(journal)
                "weeklyReviews" -> JsonArray(weekly)
                "weeklyAiReviews" -> weeklyAi
                else -> (data[key] as? JsonArray) ?: JsonArray(emptyList())
            }
        }
        val counts = buildJsonObject { tables.forEach { (k, v) -> put(k, v.size) } }
        val integrity = buildJsonObject {
            put("perTableSha256", buildJsonObject {
                tables.forEach { (k, v) -> put(k, CanonicalJson.sha256Hex(v)) }
            })
        }
        return buildJsonObject {
            payload.forEach { (k, v) ->
                if (k != "counts" && k != "data" && k != "integrity") put(k, v)
            }
            put("counts", counts)
            put("data", buildJsonObject { tables.forEach { (k, v) -> put(k, v) } })
            put("integrity", integrity)
        }
    }

    private fun JsonArray?.orEmptyArray(): JsonArray = this ?: JsonArray(emptyList())
}
