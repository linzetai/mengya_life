package com.mengyalife.app.domain.care

import com.mengyalife.app.data.local.plan.SmallPlanEntity
import com.mengyalife.app.domain.goals.PlanTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.ZoneId

enum class CareAction { SEND, SCHEDULE, DEFER, DROP }

data class CareCandidate(
    val opportunityId: Long,
    val type: String,
    val title: String,
    val planId: Long?,
    val goalId: Long?,
    val status: String,
    val timeScope: String?,
    val progressCount: Int,
    val lastProgressAt: Long?,
    val windowEnd: Long,
    val expiresAt: Long,
)

data class CareDecision(
    val action: CareAction,
    val opportunityId: Long,
    val reason: String,
    val suggestedText: String?,
    val avoid: List<String>,
    val nextWindowStart: Long?,
    val nextWindowEnd: Long?,
)

object CareParser {
    fun parse(raw: String, allowedIds: Set<Long>): CareDecision? {
        val start = raw.indexOf('{'); val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val parsed = runCatching { Json.parseToJsonElement(raw.substring(start, end + 1)).jsonObject }.getOrNull() ?: return null
        val obj = (parsed["decision"] as? JsonObject) ?: (parsed["record"] as? JsonObject) ?: parsed
        val action = when ((obj.str("action") ?: obj.str("decision"))?.uppercase()) {
            "SEND" -> CareAction.SEND
            "SCHEDULE" -> CareAction.SCHEDULE
            "DEFER" -> CareAction.DEFER
            "DROP" -> CareAction.DROP
            else -> return null
        }
        val rawId = obj["opportunityId"] ?: obj["opportunity_id"] ?: obj["candidateId"] ?: obj["id"]
        val id = rawId?.jsonPrimitive?.longOrNull
            ?: rawId?.jsonPrimitive?.contentOrNull?.filter { it.isDigit() }?.toLongOrNull()
            ?: return null
        if (id !in allowedIds) return null
        val reason = obj.str("reason") ?: return null
        val suggested = obj.str("suggestedText")
        val avoid = (obj["avoid"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        return CareDecision(
            action = action, opportunityId = id, reason = reason,
            suggestedText = suggested, avoid = avoid,
            nextWindowStart = obj["nextWindowStart"]?.jsonPrimitive?.longOrNull,
            nextWindowEnd = obj["nextWindowEnd"]?.jsonPrimitive?.longOrNull,
        )
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
}

/** 从真实记录生成候选；只生成机会，不决定是否打扰。 */
object CareOpportunityFactory {

    data class Draft(
        val type: String,
        val sourceId: Long,
        val planId: Long?,
        val goalId: Long?,
        val title: String,
        val dedupeKey: String,
        val windowStart: Long,
        val windowEnd: Long,
        val expiresAt: Long,
    )

    fun fromPlans(
        plans: List<SmallPlanEntity>,
        progressCounts: Map<Long, Int>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Draft> = plans.filter { it.status == "ACTIVE" }.mapNotNull { plan ->
        val range = PlanTime.resolve(plan.timeScope, plan.createdAt, zone) ?: return@mapNotNull null
        if (now < range.startAt || now > range.endAt) return@mapNotNull null
        val weekKey = range.startAt / (7L * 24 * 3600_000)
        Draft(
            type = "PLAN_CHECKIN",
            sourceId = plan.id,
            planId = plan.id,
            goalId = plan.goalId,
            title = plan.title,
            dedupeKey = "plan:${plan.id}:$weekKey",
            windowStart = now,
            windowEnd = range.endAt,
            expiresAt = range.endAt + 24 * 3600_000,
        )
    }

    fun fromGoal(
        goalId: Long,
        title: String,
        lastCheckinAt: Long?,
        now: Long,
        dayKey: Long,
    ): Draft? {
        val stale = lastCheckinAt == null || now - lastCheckinAt >= 7L * 24 * 3600_000
        if (!stale) return null
        return Draft(
            type = "GOAL_STALL", sourceId = goalId, planId = null, goalId = goalId, title = title,
            dedupeKey = "goal:$goalId:$dayKey",
            windowStart = now, windowEnd = now + 24 * 3600_000, expiresAt = now + 48 * 3600_000,
        )
    }

    fun dayKey(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()
}
