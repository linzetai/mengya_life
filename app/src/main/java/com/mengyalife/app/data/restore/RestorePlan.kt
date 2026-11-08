package com.mengyalife.app.data.restore

import com.mengyalife.app.data.backup.CanonicalJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.UUID

data class RestoredSettings(
    val themeMode: String,
    val weeklyEnabled: Boolean,
    val notificationAsked: Boolean,
    val aiEndpoint: String,
    val aiModel: String,
)

data class RestorePlan(
    val attemptId: String,
    val backupId: String,
    val generation: Long,
    val payloadSha256: String,
    val expectedRestoredSha256: String,
    val perTableSha256: Map<String, String>,
    val rows: Map<String, List<JsonObject>>,
    val settings: RestoredSettings,
    val settingsJson: String,
)

object RestorePlanBuilder {
    fun build(
        payload: JsonObject,
        validation: RestoreValidator.Result,
        generation: Long,
        attemptId: String = UUID.randomUUID().toString(),
        now: Long = System.currentTimeMillis(),
    ): RestorePlan {
        val data = payload["data"] as? JsonObject ?: throw RestoreException("DATA_MISSING", "缺少 data")
        val goalIds = longIds(data["goals"])
        val journalIds = longIds(data["journalEntries"])
        val signalIds = stringIds(data["growthSignals"])

        val messages = rows(data["advisorMessages"]).map { row ->
            val gid = row["contextGoalId"]?.jsonPrimitive?.longOrNull
            if (gid != null && gid !in goalIds) row["contextGoalId"] = JsonNull
            row
        }
        val signals = rows(data["growthSignals"]).map { row ->
            val sid = row["sourceId"]?.jsonPrimitive?.longOrNull
            if (sid != null && sid !in journalIds && row["status"]?.jsonPrimitive?.content in setOf("ACTIVE", "CORRECTED")) {
                row["status"] = JsonPrimitive("STALE")
            }
            row
        }
        val memories = rows(data["growthMemories"]).map { row ->
            val refs = row["signalIdsJson"]?.jsonPrimitive?.content?.let { parseStringIds(it) } ?: emptyList()
            val missing = refs.any { it !in signalIds }
            if (missing && row["status"]?.jsonPrimitive?.content in setOf("ACTIVE", "CORRECTED")) {
                row["status"] = JsonPrimitive("STALE")
            }
            row
        }
        val journals = rows(data["journalEntries"]).map { row ->
            if (row["analysisStatus"]?.jsonPrimitive?.content == "ANALYZING") {
                row["analysisStatus"] = JsonPrimitive("FAILED")
                row["analysisError"] = JsonPrimitive("恢复时分析被中断，请重试")
            }
            row
        }

        val restored = linkedMapOf<String, List<MutableMap<String, JsonElement>>>()
        restored["journalEntries"] = journals.sortedBy { it["id"]!!.jsonPrimitive.longOrNull ?: 0 }
        restored["goals"] = rows(data["goals"]).sortedBy { it["id"]!!.jsonPrimitive.longOrNull ?: 0 }
        restored["goalCheckins"] = rows(data["goalCheckins"]).sortedBy { it["id"]!!.jsonPrimitive.longOrNull ?: 0 }
        restored["advisorMessages"] = messages.sortedBy { it["id"]!!.jsonPrimitive.content }
        restored["goalProposals"] = rows(data["goalProposals"]).sortedBy { it["id"]!!.jsonPrimitive.content }
        restored["growthSignals"] = signals.sortedBy { it["id"]!!.jsonPrimitive.content }
        restored["growthMemories"] = memories.sortedBy { it["id"]!!.jsonPrimitive.content }
        restored["insightReports"] = rows(data["insightReports"]).sortedBy { it["id"]!!.jsonPrimitive.content }
        restored["weeklyReviews"] = rows(data["weeklyReviews"]).sortedBy { it["id"]!!.jsonPrimitive.content }
        // 0.21/0.22 新增表对旧备份可缺省；缺省按空表恢复。
        fun optionalRows(key: String): List<MutableMap<String, JsonElement>> {
            val array = data[key] as? JsonArray ?: return emptyList()
            return rows(array).sortedBy { it["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L }
        }
        restored["smallPlans"] = optionalRows("smallPlans")
        restored["planEvents"] = optionalRows("planEvents")
        restored["planProgress"] = optionalRows("planProgress")
        restored["careOpportunities"] = optionalRows("careOpportunities")
        restored["careDecisions"] = optionalRows("careDecisions")
        restored["careFeedback"] = optionalRows("careFeedback")
        restored["carePreferences"] = optionalRows("carePreferences")

        val settings = payload["settings"] as? JsonObject ?: throw RestoreException("SETTINGS_MISSING", "缺少 settings")
        val weekly = settings["weeklyReview"] as? JsonObject
        val ai = settings["ai"] as? JsonObject
        val restoredSettings = RestoredSettings(
            themeMode = settings["themeMode"]?.jsonPrimitive?.content ?: "SYSTEM",
            weeklyEnabled = weekly?.get("enabled")?.jsonPrimitive?.content == "true",
            notificationAsked = weekly?.get("notificationAsked")?.jsonPrimitive?.content == "true",
            aiEndpoint = ai?.get("endpoint")?.jsonPrimitive?.content.orEmpty(),
            aiModel = ai?.get("model")?.jsonPrimitive?.content.orEmpty(),
        )
        val settingsJson = CanonicalJson.encode(buildJsonObject {
            put("themeMode", restoredSettings.themeMode)
            put("weeklyReview", buildJsonObject {
                put("enabled", restoredSettings.weeklyEnabled)
                put("aiEnabled", false)
                put("notificationAsked", restoredSettings.notificationAsked)
            })
            put("ai", buildJsonObject {
                put("endpoint", restoredSettings.aiEndpoint)
                put("model", restoredSettings.aiModel)
            })
        })
        val dataObject = buildJsonObject { restored.forEach { (k, v) -> put(k, JsonArray(v.map { JsonObject(it) })) } }
        val expectedObject = buildJsonObject {
            put("data", dataObject)
            put("settings", kotlinx.serialization.json.Json.parseToJsonElement(settingsJson))
        }
        val hashes = restored.mapValues { (_, v) -> CanonicalJson.sha256Hex(JsonArray(v.map { JsonObject(it) })) }
        return RestorePlan(
            attemptId = attemptId,
            backupId = payload["backupId"]?.jsonPrimitive?.content ?: "",
            generation = generation,
            payloadSha256 = CanonicalJson.sha256Hex(payload),
            expectedRestoredSha256 = CanonicalJson.sha256Hex(expectedObject),
            perTableSha256 = hashes,
            rows = restored.mapValues { (_, v) -> v.map { JsonObject(it) } },
            settings = restoredSettings,
            settingsJson = settingsJson,
        )
    }

    private fun rows(array: JsonElement?): List<MutableMap<String, JsonElement>> =
        (array as? JsonArray)?.map { it.jsonObject.toMutableMap() }
            ?: throw RestoreException("TABLE_MISSING", "缺少表")

    private fun longIds(array: JsonElement?): Set<Long> =
        rows(array).mapNotNull { it["id"]?.jsonPrimitive?.longOrNull }.toSet()

    private fun stringIds(array: JsonElement?): Set<String> =
        rows(array).mapNotNull { it["id"]?.jsonPrimitive?.content }.toSet()

    private fun levelOrder(row: Map<String, JsonElement>): Int = when (row["level"]?.jsonPrimitive?.content) {
        "LONG_TERM" -> 0; "STAGE" -> 1; "ACTION" -> 2; else -> 3
    }

    private fun parseStringIds(raw: String): List<String> =
        runCatching {
            (kotlinx.serialization.json.Json.parseToJsonElement(raw) as JsonArray)
                .mapNotNull { (it as? JsonPrimitive)?.content }
        }.getOrNull().orEmpty()
}
