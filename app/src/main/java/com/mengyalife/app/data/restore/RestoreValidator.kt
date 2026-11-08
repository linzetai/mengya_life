package com.mengyalife.app.data.restore

import com.mengyalife.app.data.backup.BackupAppInfo
import com.mengyalife.app.data.backup.BackupValidator
import com.mengyalife.app.data.local.DB_SCHEMA_VERSION
import com.mengyalife.app.domain.weekly.WeeklyAiParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * 只读恢复校验：跨包/版本、枚举、可选字段类型、有限数、JSON、唯一键、引用。
 * 不执行任何修复；修复只进入显式 RestorePlan/Preview。
 */
class RestoreValidator(
    private val expectedApp: BackupAppInfo,
    private val currentSchemaVersion: Int = DB_SCHEMA_VERSION,
) {
    data class Result(
        val warnings: List<RestoreWarning> = emptyList(),
        val repairs: List<RestoreRepair> = emptyList(),
        val sourceVersionName: String = "",
        val sourceVersionCode: Long = 0,
        val sourceQa: Boolean = false,
        val sourcePackage: String = "",
        val dbSchemaVersion: Int = 0,
    )

    private val json = Json
    private val warnings = mutableListOf<RestoreWarning>()
    private val repairs = linkedMapOf<String, RestoreRepair>()
    private val goalById = mutableMapOf<Long, JsonObject>()
    private val journalIds = mutableSetOf<Long>()
    private val goalIds = mutableSetOf<Long>()
    private val messageIds = mutableSetOf<String>()

    private enum class Type { STRING, LONG, DOUBLE, BOOLEAN, ARRAY }

    private companion object { const val MAX_JSON_DEPTH = 64 }

    private val optionalTypes: Map<String, Map<String, Type>> = mapOf(
        "journalEntries" to mapOf("title" to Type.STRING, "analysisHash" to Type.STRING,
            "analysisError" to Type.STRING, "analyzedAt" to Type.LONG, "analysisLastAttemptAt" to Type.LONG),
        "goals" to mapOf("description" to Type.STRING, "parentId" to Type.LONG,
            "startDate" to Type.LONG, "dueDate" to Type.LONG),
        "goalCheckins" to mapOf("metricValue" to Type.DOUBLE, "metricUnit" to Type.STRING),
        "advisorMessages" to mapOf("errorCode" to Type.STRING, "errorMessage" to Type.STRING,
            "contextGoalId" to Type.LONG, "sourcesJson" to Type.STRING, "proposalError" to Type.STRING),
        "goalProposals" to mapOf("selectedTempIdsJson" to Type.STRING, "appliedAt" to Type.LONG,
            "appliedGoalIdsJson" to Type.STRING, "undoneAt" to Type.LONG),
        "growthSignals" to mapOf("confidence" to Type.DOUBLE, "sourceTitle" to Type.STRING),
        "growthMemories" to mapOf("confidence" to Type.DOUBLE, "recentChange" to Type.STRING,
            "deletedAt" to Type.LONG),
        "insightReports" to mapOf("error" to Type.STRING, "model" to Type.STRING),
        "weeklyReviews" to mapOf("notifiedAt" to Type.LONG),
        "weeklyAiReviews" to mapOf("error" to Type.STRING, "model" to Type.STRING),
    )

    private val enums: Map<String, Map<String, Set<String>>> = mapOf(
        "journalEntries" to mapOf("analysisStatus" to setOf("PENDING", "ANALYZING", "COMPLETED", "FAILED")),
        "goals" to mapOf(
            "level" to setOf("LONG_TERM", "STAGE", "ACTION"),
            "domain" to setOf("HEALTH", "CAREER", "WEALTH", "LIFE", "LEARNING"),
            "status" to setOf("NOT_STARTED", "IN_PROGRESS", "DONE", "PAUSED", "CANCELLED"),
        ),
        "advisorMessages" to mapOf(
            "role" to setOf("user", "assistant", "USER", "ASSISTANT"),
            "status" to setOf("SENT", "FAILED", "CANCELLED"),
        ),
        "goalProposals" to mapOf("status" to setOf("DRAFT", "APPLIED", "UNDONE")),
        "growthSignals" to mapOf(
            "sourceType" to setOf("DIARY"),
            "status" to setOf("ACTIVE", "CORRECTED", "DELETED", "STALE"),
            "origin" to setOf("AI", "USER"),
        ),
        "growthMemories" to mapOf("status" to setOf("ACTIVE", "CORRECTED", "STALE", "DELETED")),
        "insightReports" to mapOf("status" to setOf("READY", "FAILED")),
        "weeklyAiReviews" to mapOf("status" to setOf("PENDING", "READY", "FAILED", "STALE")),
    )

    @Synchronized
    fun validate(payload: JsonObject): Result {
        warnings.clear(); repairs.clear(); goalById.clear(); journalIds.clear(); goalIds.clear(); messageIds.clear()
        val generator = payload["generator"] as? JsonObject ?: fail("SOURCE_MISSING", "缺少 generator")
        val sourcePackage = generator["appPackage"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val sourceQa = generator["qaBuild"]?.jsonPrimitive?.content == "true"
        val sourceVersionName = generator["versionName"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val sourceVersionCode = generator["versionCode"]?.jsonPrimitive?.longOrNull ?: 0L
        if (sourcePackage != expectedApp.appPackage) fail("SOURCE_PACKAGE", "备份来源包名与当前应用不一致")
        if (sourceQa != expectedApp.qaBuild) fail("SOURCE_QA", "备份来源 QA/正式标记与当前构建不一致")
        if (sourceVersionName.isBlank()) fail("SOURCE_VERSION", "缺少来源版本")

        val compatibility = payload["compatibility"] as? JsonObject ?: fail("SCHEMA_MISSING", "缺少 compatibility")
        val schema = compatibility["dbSchemaVersion"]?.jsonPrimitive?.intOrNull ?: fail("SCHEMA_MISSING", "缺少 dbSchemaVersion")
        if (schema > currentSchemaVersion) fail("FUTURE_SCHEMA", "备份数据库版本 $schema 高于当前 $currentSchemaVersion，请升级应用")
        if (schema < currentSchemaVersion) warning("OLD_SCHEMA", "备份数据库版本 $schema 低于当前 $currentSchemaVersion；本阶段只预览，不做迁移")

        val structural = BackupValidator.validatePayload(payload)
        if (!structural.ok) fail("PAYLOAD_INVALID", "结构校验失败：" + structural.errors.take(8).joinToString("；"))
        structural.warnings.forEach { warning("UNKNOWN_FIELD", it) }

        val data = payload["data"] as? JsonObject ?: fail("DATA_MISSING", "缺少 data")
        val optionalTables = setOf("smallPlans", "planEvents", "planProgress", "careOpportunities", "careDecisions", "careFeedback", "carePreferences")
        val rows = RestoreTables.ALL.associateWith { key ->
            (data[key] as? JsonArray)?.map { it as? JsonObject ?: fail("ROW_TYPE", "$key 行不是对象") }
                ?: if (key in optionalTables) emptyList() else fail("TABLE_MISSING", "缺少表 $key")
        }
        validateRows(rows)
        validateReferences(rows)
        return Result(warnings.toList(), repairs.values.toList(), sourceVersionName, sourceVersionCode,
            sourceQa, sourcePackage, schema)
    }

    private fun validateRows(rows: Map<String, List<JsonObject>>) {
        for ((table, tableRows) in rows) {
            enums[table]?.forEach { (field, allowed) ->
                tableRows.forEachIndexed { index, row ->
                    val value = (row[field] as? JsonPrimitive)?.contentOrNull
                    if (value == null || value !in allowed) fail("INVALID_ENUM", "$table[$index].$field 非法：$value")
                }
            }
            optionalTypes[table]?.forEach { (field, type) ->
                tableRows.forEachIndexed { index, row ->
                    val value = row[field] ?: return@forEachIndexed
                    if (value !is JsonNull && !typeMatches(value, type)) fail("INVALID_TYPE", "$table[$index].$field 类型错误")
                }
            }
            tableRows.forEachIndexed { index, row ->
                validateJsonFields(table, index, row)
                validateNumbers(table, index, row)
            }
        }
        rows["goals"].orEmpty().forEach { row ->
            val id = row["id"]?.jsonPrimitive?.longOrNull ?: fail("ID_INVALID", "目标 id 非法")
            if (id <= 0) fail("ID_INVALID", "目标 id 必须为正")
            goalById[id] = row; goalIds += id
        }
        rows["journalEntries"].orEmpty().forEach { row ->
            val id = row["id"]?.jsonPrimitive?.longOrNull ?: fail("ID_INVALID", "日记 id 非法")
            if (id <= 0) fail("ID_INVALID", "日记 id 必须为正")
            journalIds += id
        }
        rows["advisorMessages"].orEmpty().forEach { row ->
            val id = row["id"]?.jsonPrimitive?.contentOrNull
            if (id.isNullOrBlank()) fail("ID_INVALID", "消息 id 为空")
            messageIds += id!!
        }
        val weeklyKeys = mutableSetOf<String>()
        rows["weeklyReviews"].orEmpty().forEach { row ->
            val key = row["periodKey"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (key.isBlank() || !weeklyKeys.add(key)) fail("UNIQUE_KEY", "weekly_reviews.periodKey 重复或为空")
        }
        val aiKeys = mutableSetOf<String>()
        rows["weeklyAiReviews"].orEmpty().forEach { row ->
            val key = row["periodKey"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (key.isBlank() || !aiKeys.add(key)) fail("UNIQUE_KEY", "weekly_ai_reviews.periodKey 重复或为空")
            val ids = row["factIds"]
            if (ids !is JsonArray || ids.any { it !is JsonPrimitive || !it.isString }) {
                fail("FACT_IDS", "weekly_ai_reviews.factIds 不是字符串数组")
            }
            WeeklyAiParser.decodeFactIds(row["legacyFactIdsRaw"]?.jsonPrimitive?.contentOrNull)
        }
    }

    private fun validateJsonFields(table: String, index: Int, row: JsonObject) {
        val jsonFields = setOf(
            "sourcesJson", "assumptionsJson", "risksJson", "nodesJson", "selectedTempIdsJson",
            "appliedGoalIdsJson", "signalIdsJson", "sourceDiaryIdsJson", "factsJson",
            "insightsJson", "sectionsJson", "highlightsJson",
        )
        for (field in jsonFields) {
            val value = row[field] ?: continue
            if (value is JsonNull) continue
            val text = (value as? JsonPrimitive)?.contentOrNull ?: fail("JSON_TYPE", "$table[$index].$field 不是字符串")
            if (text.isBlank()) fail("JSON_EMPTY", "$table[$index].$field 为空")
            val element = runCatching { json.parseToJsonElement(text) }
                .getOrElse { fail("JSON_INVALID", "$table[$index].$field 不是合法 JSON") }
            if (jsonDepth(element) > MAX_JSON_DEPTH) fail("JSON_DEPTH", "$table[$index].$field 嵌套过深")
        }
    }

    private fun jsonDepth(element: JsonElement): Int = when (element) {
        is JsonObject -> 1 + (element.values.maxOfOrNull { jsonDepth(it) } ?: 0)
        is JsonArray -> 1 + (element.maxOfOrNull { jsonDepth(it) } ?: 0)
        else -> 0
    }

    private fun validateNumbers(table: String, index: Int, row: JsonObject) {
        val doubles = when (table) {
            "goalCheckins" -> listOf("metricValue")
            "growthSignals", "growthMemories" -> listOf("confidence")
            else -> emptyList()
        }
        for (field in doubles) {
            val value = row[field] ?: continue
            if (value is JsonNull) continue
            val d = (value as? JsonPrimitive)?.content?.toDoubleOrNull()
            if (d == null || !d.isFinite()) fail("INVALID_NUMBER", "$table[$index].$field 不是有限数")
        }
        for (field in listOf("createdAt", "updatedAt", "recordedAt", "extractedAt", "generatedAt",
            "periodStart", "periodEnd", "analyzedAt", "analysisLastAttemptAt", "appliedAt", "undoneAt",
            "deletedAt", "notifiedAt")) {
            val value = row[field] ?: continue
            if (value is JsonNull) continue
            val n = (value as? JsonPrimitive)?.longOrNull
            if (n == null || n < 0) fail("INVALID_TIME", "$table[$index].$field 时间非法")
        }
    }

    private fun validateReferences(rows: Map<String, List<JsonObject>>) {
        rows["goals"].orEmpty().forEach { row ->
            val id = row["id"]!!.jsonPrimitive.longOrNull!!
            val level = row["level"]!!.jsonPrimitive.content
            val parent = row["parentId"]?.let { if (it is JsonNull) null else it.jsonPrimitive.longOrNull }
            when (level) {
                "LONG_TERM" -> if (parent != null) fail("STRONG_REF", "LONG_TERM 目标 $id 不应有父目标")
                "STAGE" -> {
                    val p = parent ?: fail("STRONG_REF", "STAGE 目标 $id 缺少父目标")
                    if (goalById[p]?.get("level")?.jsonPrimitive?.content != "LONG_TERM") fail("STRONG_REF", "STAGE 目标 $id 的父目标层级错误")
                }
                "ACTION" -> {
                    val p = parent ?: fail("STRONG_REF", "ACTION 目标 $id 缺少父目标")
                    if (goalById[p]?.get("level")?.jsonPrimitive?.content != "STAGE") fail("STRONG_REF", "ACTION 目标 $id 的父目标层级错误")
                }
            }
            var cursor = parent
            val seen = mutableSetOf(id)
            while (cursor != null) {
                if (!seen.add(cursor)) fail("STRONG_REF", "目标父子关系存在环")
                cursor = goalById[cursor]?.get("parentId")?.let { if (it is JsonNull) null else it.jsonPrimitive.longOrNull }
            }
        }
        rows["goalCheckins"].orEmpty().forEach { row ->
            val goalId = row["goalId"]!!.jsonPrimitive.longOrNull!!
            if (goalId !in goalIds) fail("STRONG_REF", "进展 goalId=$goalId 不存在")
        }
        rows["advisorMessages"].orEmpty().forEach { row ->
            val gid = row["contextGoalId"]?.let { if (it is JsonNull) null else it.jsonPrimitive.longOrNull }
            if (gid != null && gid !in goalIds) {
                warning("HISTORICAL_CONTEXT_GOAL", "消息 ${row["id"]?.jsonPrimitive?.contentOrNull} 的 contextGoalId 历史悬挂")
                addRepair("CONTEXT_GOAL_NULL", "advisorMessages", "挂起 contextGoalId 置 null", row["id"]?.jsonPrimitive?.contentOrNull)
            }
        }
        rows["goalProposals"].orEmpty().forEach { row ->
            val mid = row["messageId"]?.jsonPrimitive?.contentOrNull
            if (mid.isNullOrBlank() || mid !in messageIds) warning("HISTORICAL_PROPOSAL_MESSAGE", "草案 ${row["id"]?.jsonPrimitive?.contentOrNull} 的来源消息历史悬挂")
            val applied = row["appliedGoalIdsJson"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
            if (!applied.isNullOrBlank()) {
                val ids = parseLongArray("goalProposals", row["id"]?.jsonPrimitive?.contentOrNull.orEmpty(), "appliedGoalIdsJson", applied)
                if (ids.any { it !in goalIds }) warning("HISTORICAL_APPLIED_GOAL", "草案 ${row["id"]?.jsonPrimitive?.contentOrNull} 的 appliedGoalIds 历史悬挂")
            }
        }
        rows["growthSignals"].orEmpty().forEach { row ->
            val sid = row["sourceId"]?.jsonPrimitive?.longOrNull
            if (sid != null && sid !in journalIds) {
                warning("HISTORICAL_SIGNAL_SOURCE", "成长线索 ${row["id"]?.jsonPrimitive?.contentOrNull} 的来源日记历史悬挂")
                if (row["status"]?.jsonPrimitive?.content in setOf("ACTIVE", "CORRECTED")) {
                    addRepair("SIGNAL_STALE", "growthSignals", "ACTIVE/CORRECTED 线索标 STALE", row["id"]?.jsonPrimitive?.contentOrNull)
                }
            }
        }
        val signalIds = rows["growthSignals"].orEmpty().mapNotNull { it["id"]?.jsonPrimitive?.contentOrNull }.toSet()
        rows["growthMemories"].orEmpty().forEach { row ->
            val memoryId = row["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val referencedSignals = parseStringArray("growthMemories", memoryId, "signalIdsJson", row["signalIdsJson"]?.jsonPrimitive?.contentOrNull.orEmpty())
            if (referencedSignals.any { it !in signalIds }) {
                warning("HISTORICAL_MEMORY_SIGNAL", "成长记忆 $memoryId 的线索引用历史悬挂")
                if (row["status"]?.jsonPrimitive?.content in setOf("ACTIVE", "CORRECTED")) {
                    addRepair("MEMORY_STALE", "growthMemories", "ACTIVE/CORRECTED 记忆标 STALE", memoryId)
                }
            }
            val diaryIds = parseLongArray("growthMemories", memoryId, "sourceDiaryIdsJson", row["sourceDiaryIdsJson"]?.jsonPrimitive?.contentOrNull.orEmpty())
            if (diaryIds.any { it !in journalIds }) warning("HISTORICAL_MEMORY_DIARY", "成长记忆 $memoryId 的来源日记历史悬挂")
        }
        rows["journalEntries"].orEmpty().forEach { row ->
            if (row["analysisStatus"]?.jsonPrimitive?.content == "ANALYZING") {
                addRepair("ANALYZING_FAILED", "journalEntries", "ANALYZING 置 FAILED", row["id"]?.jsonPrimitive?.contentOrNull)
            }
        }
    }

    private fun parseStringArray(table: String, id: String, field: String, text: String): List<String> {
        val element = runCatching { json.parseToJsonElement(text) }.getOrElse { fail("JSON_INVALID", "$table/$id.$field 非法 JSON") }
        val array = element as? JsonArray ?: fail("JSON_TYPE", "$table/$id.$field 不是数组")
        return array.map { (it as? JsonPrimitive)?.contentOrNull ?: fail("JSON_TYPE", "$table/$id.$field 元素不是字符串") }
    }

    private fun parseLongArray(table: String, id: String, field: String, text: String): List<Long> {
        val element = runCatching { json.parseToJsonElement(text) }.getOrElse { fail("JSON_INVALID", "$table/$id.$field 非法 JSON") }
        val array = element as? JsonArray ?: fail("JSON_TYPE", "$table/$id.$field 不是数组")
        return array.map { (it as? JsonPrimitive)?.longOrNull ?: fail("JSON_TYPE", "$table/$id.$field 元素不是 Long") }
    }

    private fun typeMatches(value: JsonElement, type: Type): Boolean {
        val p = value as? JsonPrimitive ?: return type == Type.ARRAY && value is JsonArray
        return when (type) {
            Type.STRING -> p.isString
            Type.LONG -> p.longOrNull != null
            Type.DOUBLE -> p.content.toDoubleOrNull()?.isFinite() == true
            Type.BOOLEAN -> p.content == "true" || p.content == "false"
            Type.ARRAY -> false
        }
    }

    private fun addRepair(code: String, table: String, action: String, id: String?) {
        val key = "$code|$table"
        val old = repairs[key]
        repairs[key] = if (old == null) RestoreRepair(code, table, action, 1, listOfNotNull(id))
        else old.copy(count = old.count + 1, ids = old.ids + listOfNotNull(id))
    }

    private fun warning(code: String, message: String, count: Int = 1) { warnings += RestoreWarning(code, message, count) }

    private fun fail(code: String, message: String): Nothing = throw RestoreException(code, message)
}
