package com.mengyalife.app.data.advisor

import com.mengyalife.app.domain.advisor.GoalBeforeSnapshot
import com.mengyalife.app.domain.advisor.GoalBreakdownSuggestion
import com.mengyalife.app.domain.advisor.GoalChangeDraft
import com.mengyalife.app.domain.advisor.GoalChangeKind
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

/** 目标变更草稿的稳定 JSON（存在 record_proposals.goal_change_json）。 */
object GoalChangeCodec {

    fun encode(change: GoalChangeDraft?): String? {
        if (change == null) return null
        return buildJsonObject {
            put("change", change.change.name)
            put("targetGoalId", change.targetGoalId)
            put("title", change.title)
            put("description", change.description)
            put("level", change.levelName)
            put("domain", change.domainName)
            put("dueAt", change.dueAt)
            put("parentId", change.parentId)
            change.before?.let { before ->
                put(
                    "before",
                    buildJsonObject {
                        put("goalId", before.goalId)
                        put("title", before.title)
                        put("level", before.levelName)
                        put("domain", before.domainName)
                        put("dueAt", before.dueAt)
                        put("parentId", before.parentId)
                    },
                )
            }
            put(
                "breakdown",
                buildJsonArray {
                    change.breakdown.forEach { item ->
                        add(
                            buildJsonObject {
                                put("title", item.title)
                                put("level", item.levelName)
                                put("selected", item.selected)
                            },
                        )
                    }
                },
            )
            put(
                "uncertain",
                buildJsonArray {
                    change.uncertain.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                },
            )
        }.toString()
    }

    fun decode(raw: String?): GoalChangeDraft? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val obj = Json.parseToJsonElement(raw).jsonObject
            GoalChangeDraft(
                change = GoalChangeKind.entries
                    .firstOrNull { it.name.equals(obj.str("change"), ignoreCase = true) }
                    ?: GoalChangeKind.CREATE,
                targetGoalId = obj["targetGoalId"]?.jsonPrimitive?.longOrNull,
                title = obj.str("title"),
                description = obj.str("description"),
                levelName = obj.str("level"),
                domainName = obj.str("domain"),
                dueAt = obj["dueAt"]?.jsonPrimitive?.longOrNull,
                parentId = obj["parentId"]?.jsonPrimitive?.longOrNull,
                before = (obj["before"] as? JsonObject)?.let { before ->
                    GoalBeforeSnapshot(
                        goalId = before["goalId"]?.jsonPrimitive?.longOrNull ?: 0L,
                        title = before.str("title").orEmpty(),
                        levelName = before.str("level").orEmpty(),
                        domainName = before.str("domain").orEmpty(),
                        dueAt = before["dueAt"]?.jsonPrimitive?.longOrNull,
                        parentId = before["parentId"]?.jsonPrimitive?.longOrNull,
                    )
                },
                breakdown = (obj["breakdown"] as? JsonArray)?.mapNotNull { element ->
                    val item = element as? JsonObject ?: return@mapNotNull null
                    val title = item.str("title") ?: return@mapNotNull null
                    GoalBreakdownSuggestion(
                        title = title,
                        levelName = item.str("level") ?: "ACTION",
                        selected = item["selected"]?.jsonPrimitive?.contentOrNull != "false",
                    )
                }.orEmpty(),
                uncertain = (obj["uncertain"] as? JsonArray)?.mapNotNull {
                    it.jsonPrimitive.contentOrNull?.takeIf { value -> value.isNotBlank() }
                }.orEmpty(),
            )
        }.getOrNull()
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
}
