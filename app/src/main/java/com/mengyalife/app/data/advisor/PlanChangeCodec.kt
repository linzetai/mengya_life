package com.mengyalife.app.data.advisor

import com.mengyalife.app.domain.advisor.PlanCandidate
import com.mengyalife.app.domain.advisor.PlanChangeDraft
import com.mengyalife.app.domain.advisor.PlanChangeKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

object PlanChangeCodec {
    fun encode(change: PlanChangeDraft?): String? {
        if (change == null) return null
        return buildJsonObject {
            put("change", change.change.name)
            put("targetPlanId", change.targetPlanId)
            put("title", change.title)
            put("timeScope", change.timeScope)
            put("goalId", change.goalId)
            put("newStatus", change.newStatus)
            put("note", change.note)
        }.toString()
    }

    fun decode(raw: String?): PlanChangeDraft? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val obj = Json.parseToJsonElement(raw).jsonObject
            PlanChangeDraft(
                change = PlanChangeKind.entries.firstOrNull {
                    it.name.equals(obj.str("change"), ignoreCase = true)
                } ?: PlanChangeKind.CREATE,
                targetPlanId = obj["targetPlanId"]?.jsonPrimitive?.longOrNull,
                title = obj.str("title"),
                timeScope = obj.str("timeScope"),
                goalId = obj["goalId"]?.jsonPrimitive?.longOrNull,
                newStatus = obj.str("newStatus"),
                note = obj.str("note"),
            )
        }.getOrNull()
    }

    fun encodeProgress(progress: com.mengyalife.app.domain.advisor.PlanProgressDraft?): String? {
        if (progress == null) return null
        return buildJsonObject {
            put("planId", progress.planId)
            put("planTitle", progress.planTitle)
            put("note", progress.note)
        }.toString()
    }

    fun decodeProgress(raw: String?): com.mengyalife.app.domain.advisor.PlanProgressDraft? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val obj = Json.parseToJsonElement(raw).jsonObject
            com.mengyalife.app.domain.advisor.PlanProgressDraft(
                planId = obj["planId"]?.jsonPrimitive?.longOrNull,
                planTitle = obj.str("planTitle"),
                note = obj.str("note").orEmpty(),
            )
        }.getOrNull()
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
}
