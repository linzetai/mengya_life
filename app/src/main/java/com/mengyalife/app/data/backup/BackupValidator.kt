package com.mengyalife.app.data.backup

import com.mengyalife.app.data.local.DB_SCHEMA_VERSION
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * 导出侧结构/完整性校验器。
 *
 * 不执行恢复；只确认“即将写出的 v2 载荷符合契约”。恢复器需要更严格的
 * 引用修复/事务校验，见 G4A-2 设计。
 */
object BackupValidator {
    const val MAX_FILE_BYTES = 64 * 1024 * 1024
    const val MAX_PAYLOAD_BYTES = 48 * 1024 * 1024
    const val MAX_TOTAL_ROWS = 200_000
    const val MAX_TABLE_ROWS = 100_000
    const val MAX_TEXT_FIELD_BYTES = 1024 * 1024
    const val MAX_JSON_FIELD_BYTES = 2 * 1024 * 1024
    const val MAX_ARRAY_ELEMENTS = 5_000

    data class Result(
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
    ) {
        val ok: Boolean get() = errors.isEmpty()
    }

    private data class Fields(val required: Map<String, FieldType>, val optional: Set<String> = emptySet())

    private enum class FieldType { STRING, LONG, DOUBLE, BOOLEAN, ARRAY }

    private val fieldSpecs: Map<String, Fields> = mapOf(
        "journalEntries" to Fields(
            required = mapOf(
                "id" to FieldType.LONG, "body" to FieldType.STRING,
                "createdAt" to FieldType.LONG, "updatedAt" to FieldType.LONG,
                "analysisStatus" to FieldType.STRING, "analysisAttempts" to FieldType.LONG,
            ),
            optional = setOf("title", "analysisHash", "analysisError", "analyzedAt", "analysisLastAttemptAt"),
        ),
        "goals" to Fields(
            required = mapOf(
                "id" to FieldType.LONG, "title" to FieldType.STRING, "level" to FieldType.STRING,
                "domain" to FieldType.STRING, "status" to FieldType.STRING,
                "createdAt" to FieldType.LONG, "updatedAt" to FieldType.LONG,
            ),
            optional = setOf("description", "parentId", "startDate", "dueDate"),
        ),
        "goalCheckins" to Fields(
            required = mapOf(
                "id" to FieldType.LONG, "goalId" to FieldType.LONG, "recordedAt" to FieldType.LONG,
                "note" to FieldType.STRING, "source" to FieldType.STRING,
            ),
            // 0.16A 新增字段均为可选；旧备份缺少时按 v14 列默认值（snapshot）恢复。
            optional = setOf(
                "metricValue", "metricUnit", "amountKind", "originalText",
                "normalizedValue", "normalizedUnit",
            ),
        ),
        "advisorMessages" to Fields(
            required = mapOf(
                "id" to FieldType.STRING, "role" to FieldType.STRING, "content" to FieldType.STRING,
                "createdAt" to FieldType.LONG, "status" to FieldType.STRING,
                "contextEpoch" to FieldType.LONG, "proposalMode" to FieldType.BOOLEAN,
            ),
            optional = setOf("errorCode", "errorMessage", "contextGoalId", "sourcesJson", "proposalError"),
        ),
        "goalProposals" to Fields(
            required = mapOf(
                "id" to FieldType.STRING, "messageId" to FieldType.STRING, "createdAt" to FieldType.LONG,
                "schemaVersion" to FieldType.LONG, "status" to FieldType.STRING, "summary" to FieldType.STRING,
                "assumptionsJson" to FieldType.STRING, "risksJson" to FieldType.STRING, "nodesJson" to FieldType.STRING,
            ),
            optional = setOf("selectedTempIdsJson", "appliedAt", "appliedGoalIdsJson", "undoneAt"),
        ),
        "growthSignals" to Fields(
            required = mapOf(
                "id" to FieldType.STRING, "sourceType" to FieldType.STRING, "sourceId" to FieldType.LONG,
                "sourceVersion" to FieldType.STRING, "category" to FieldType.STRING, "content" to FieldType.STRING,
                "evidence" to FieldType.STRING, "isInference" to FieldType.BOOLEAN,
                "extractedAt" to FieldType.LONG, "status" to FieldType.STRING, "origin" to FieldType.STRING,
                "contentHash" to FieldType.STRING, "updatedAt" to FieldType.LONG,
            ),
            optional = setOf("confidence", "sourceTitle"),
        ),
        "growthMemories" to Fields(
            required = mapOf(
                "id" to FieldType.STRING, "theme" to FieldType.STRING, "content" to FieldType.STRING,
                "isInference" to FieldType.BOOLEAN, "status" to FieldType.STRING,
                "createdAt" to FieldType.LONG, "updatedAt" to FieldType.LONG,
                "signalIdsJson" to FieldType.STRING, "sourceDiaryIdsJson" to FieldType.STRING,
                "userCorrected" to FieldType.BOOLEAN, "version" to FieldType.LONG,
                "insufficient" to FieldType.BOOLEAN, "fingerprint" to FieldType.STRING,
            ),
            optional = setOf("confidence", "recentChange", "deletedAt"),
        ),
        "insightReports" to Fields(
            required = mapOf(
                "id" to FieldType.STRING, "dataVersion" to FieldType.STRING, "status" to FieldType.STRING,
                "summary" to FieldType.STRING, "factsJson" to FieldType.STRING, "insightsJson" to FieldType.STRING,
                "periodStart" to FieldType.LONG, "periodEnd" to FieldType.LONG, "generatedAt" to FieldType.LONG,
            ),
            optional = setOf("error", "model"),
        ),
        "weeklyReviews" to Fields(
            required = mapOf(
                "id" to FieldType.STRING, "periodKey" to FieldType.STRING,
                "periodStart" to FieldType.LONG, "periodEnd" to FieldType.LONG,
                "generatedAt" to FieldType.LONG, "version" to FieldType.LONG,
                "dataVersion" to FieldType.STRING, "aiConsentVersion" to FieldType.STRING,
                "factsJson" to FieldType.STRING, "sectionsJson" to FieldType.STRING,
            ),
            optional = setOf("notifiedAt"),
        ),
        "weeklyAiReviews" to Fields(
            required = mapOf(
                "periodKey" to FieldType.STRING, "localDataVersion" to FieldType.STRING,
                "aiConsentVersion" to FieldType.STRING, "status" to FieldType.STRING,
                "generatedAt" to FieldType.LONG, "summary" to FieldType.STRING,
                "highlightsJson" to FieldType.STRING, "suggestion" to FieldType.STRING,
                "factIds" to FieldType.ARRAY, "legacyFactIdsRaw" to FieldType.STRING,
                "categories" to FieldType.STRING, "configHash" to FieldType.STRING,
            ),
            optional = setOf("error", "model"),
        ),
        "smallPlans" to Fields(
            required = mapOf(
                "id" to FieldType.LONG, "title" to FieldType.STRING, "status" to FieldType.STRING,
                "createdAt" to FieldType.LONG, "updatedAt" to FieldType.LONG,
            ),
            optional = setOf("timeScope", "goalId", "sourceMessageId"),
        ),
        "careOpportunities" to Fields(
            required = mapOf(
                "id" to FieldType.LONG, "type" to FieldType.STRING, "sourceId" to FieldType.LONG,
                "title" to FieldType.STRING, "discoveredAt" to FieldType.LONG,
                "windowStart" to FieldType.LONG, "windowEnd" to FieldType.LONG,
                "expiresAt" to FieldType.LONG, "sensitivity" to FieldType.STRING,
                "status" to FieldType.STRING, "dedupeKey" to FieldType.STRING,
                "createdAt" to FieldType.LONG, "updatedAt" to FieldType.LONG,
            ),
            optional = setOf("planId", "goalId"),
        ),
        "careDecisions" to Fields(
            required = mapOf(
                "id" to FieldType.LONG, "opportunityId" to FieldType.LONG,
                "action" to FieldType.STRING, "reason" to FieldType.STRING,
                "sourceRefs" to FieldType.STRING, "modelOk" to FieldType.BOOLEAN,
                "createdAt" to FieldType.LONG,
            ),
            optional = setOf("suggestedText", "avoidJson", "nextWindowStart", "nextWindowEnd", "messageId", "deliveredAt", "notifiedAt"),
        ),
        "recordProposals" to Fields(
            required = mapOf(
                "id" to FieldType.STRING, "messageId" to FieldType.STRING, "createdAt" to FieldType.LONG,
                "status" to FieldType.STRING, "kind" to FieldType.STRING, "amountKind" to FieldType.STRING,
                "note" to FieldType.STRING, "intent" to FieldType.STRING, "originalText" to FieldType.STRING,
            ),
            optional = setOf(
                "goalId", "goalTitle", "goalCandidatesJson", "metricValue", "metricUnit", "occurredAt",
                "pendingFieldsJson", "appliedCheckinId", "diaryTitle", "diaryBody", "diaryEntryId",
                "sourceMessageIdsJson", "goalChangeJson", "appliedGoalIdsJson", "planChangeJson",
                "planProgressJson", "appliedPlanId", "appliedAt", "undoneAt",
            ),
        ),
        "carePreferences" to Fields(
            required = mapOf(
                "id" to FieldType.LONG, "kind" to FieldType.STRING, "value" to FieldType.STRING,
                "source" to FieldType.STRING, "effectiveAt" to FieldType.LONG,
                "createdAt" to FieldType.LONG, "updatedAt" to FieldType.LONG,
            ),
            optional = setOf("topicKey", "topicLabel", "sourceMessageId", "sourceExcerpt", "windowNote", "expiresAt", "revokedAt"),
        ),
        "careFeedback" to Fields(
            required = mapOf(
                "id" to FieldType.LONG, "opportunityId" to FieldType.LONG,
                "kind" to FieldType.STRING, "createdAt" to FieldType.LONG,
            ),
        ),
        "planProgress" to Fields(
            required = mapOf(
                "id" to FieldType.LONG, "planId" to FieldType.LONG,
                "note" to FieldType.STRING, "recordedAt" to FieldType.LONG,
            ),
            optional = setOf("sourceMessageId"),
        ),
        "planEvents" to Fields(
            required = mapOf(
                "id" to FieldType.LONG, "planId" to FieldType.LONG, "status" to FieldType.STRING,
                "createdAt" to FieldType.LONG,
            ),
            optional = setOf("note"),
        ),
    )

    fun validatePayload(payload: JsonObject, expectedPayloadSha256: String? = null,
                        payloadBytes: ByteArray? = null): Result {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (payload["format"]?.jsonPrimitive?.contentOrNull != BackupFormatV2.FORMAT) {
            errors += "format 非法"
        }
        if (payload["backupFormatVersion"]?.jsonPrimitive?.intOrNull != BackupFormatV2.VERSION) {
            errors += "backupFormatVersion 不是 2"
        }
        val backupId = payload["backupId"]?.jsonPrimitive?.contentOrNull
        if (backupId.isNullOrBlank()) errors += "缺少 backupId"
        val createdAt = payload["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L
        if (createdAt <= 0) errors += "createdAt 非法"

        val compatibility = payload["compatibility"] as? JsonObject
        val dbSchema = compatibility?.get("dbSchemaVersion")?.jsonPrimitive?.intOrNull ?: -1
        if (dbSchema !in 1..DB_SCHEMA_VERSION) errors += "dbSchemaVersion 不支持：$dbSchema"
        val minReader = compatibility?.get("minReaderBackupFormatVersion")?.jsonPrimitive?.intOrNull ?: -1
        if (minReader > BackupFormatV2.VERSION) errors += "minReaderBackupFormatVersion 过高"
        val features = compatibility?.get("requiredFeatures") as? JsonArray
        if (features == null) {
            errors += "缺少 requiredFeatures"
        } else {
            val names = features.mapNotNull { it.jsonPrimitive.contentOrNull }
            if (names.any { it !in BackupFormatV2.REQUIRED_FEATURES }) {
                errors += "包含未知 requiredFeatures"
            }
            if (!names.containsAll(BackupFormatV2.REQUIRED_FEATURES)) {
                errors += "缺少必需 requiredFeatures"
            }
        }

        val knownTop = setOf(
            "format", "backupFormatVersion", "backupId", "createdAt", "createdAtIso",
            "generator", "compatibility", "counts", "data", "settings", "integrity",
        )
        val unknownTop = payload.keys - knownTop
        if (unknownTop.isNotEmpty()) warnings += "忽略未知 payload 字段：${unknownTop.joinToString(",")}"

        val data = payload["data"] as? JsonObject
        if (data == null) {
            errors += "缺少 data"
            return Result(errors, warnings)
        }
        val unknownTables = data.keys - BackupFormatV2.TABLE_KEYS.toSet()
        if (unknownTables.isNotEmpty()) errors += "未知数据表：${unknownTables.joinToString(",")}"
        val counts = payload["counts"] as? JsonObject
        if (counts == null) errors += "缺少 counts"

        var totalRows = 0
        val tableArrays = linkedMapOf<String, JsonArray>()
        val optionalTables = setOf(
            "smallPlans", "planEvents", "planProgress",
            "careOpportunities", "careDecisions", "careFeedback", "carePreferences", "recordProposals",
        )
        for (key in BackupFormatV2.TABLE_KEYS) {
            val array = data[key] as? JsonArray
            if (array == null) {
                if (key in optionalTables) {
                    tableArrays[key] = JsonArray(emptyList())
                    continue
                }
                errors += "缺少表：$key"
                continue
            }
            tableArrays[key] = array
            totalRows += array.size
            if (array.size > MAX_TABLE_ROWS) errors += "$key 行数超限"
            val declaredCount = counts?.get(key)?.jsonPrimitive?.intOrNull
            if (declaredCount != null && declaredCount != array.size) {
                errors += "$key counts 与实际不符"
            }
            val spec = fieldSpecs.getValue(key)
            val ids = mutableSetOf<String>()
            array.forEachIndexed { index, element ->
                val row = element as? JsonObject
                if (row == null) {
                    errors += "$key[$index] 不是对象"
                    return@forEachIndexed
                }
                for ((field, type) in spec.required) {
                    val value = row[field]
                    if (value == null || value is JsonNull || !typeMatches(value, type)) {
                        errors += "$key[$index].$field 缺失或类型错误"
                    }
                }
                val extras = row.keys - spec.required.keys - spec.optional
                if (extras.isNotEmpty()) warnings += "$key[$index] 忽略未知字段：${extras.joinToString(",")}"
                for ((field, value) in row) {
                    if (value is JsonNull) continue
                    if (value is JsonPrimitive && value.isString) {
                        val bytes = value.content.toByteArray(Charsets.UTF_8).size
                        val limit = if (field.endsWith("Json", ignoreCase = true) || field.endsWith("Raw", ignoreCase = true)) {
                            MAX_JSON_FIELD_BYTES
                        } else {
                            MAX_TEXT_FIELD_BYTES
                        }
                        if (bytes > limit) errors += "$key[$index].$field 文本超限"
                    }
                    if (value is JsonArray && value.size > MAX_ARRAY_ELEMENTS) {
                        errors += "$key[$index].$field 数组超限"
                    }
                }
                val id = when (key) {
                    "weeklyAiReviews" -> row["periodKey"]?.jsonPrimitive?.contentOrNull
                    else -> row["id"]?.jsonPrimitive?.contentOrNull ?: row["id"]?.jsonPrimitive?.longOrNull?.toString()
                }
                if (id != null && !ids.add(id)) errors += "$key id 重复：$id"
            }
        }
        if (totalRows > MAX_TOTAL_ROWS) errors += "总行数超限"

        val integrity = payload["integrity"] as? JsonObject
        val hashes = integrity?.get("perTableSha256") as? JsonObject
        if (hashes == null) {
            errors += "缺少 integrity.perTableSha256"
        } else {
            for ((key, array) in tableArrays) {
                val expected = hashes[key]?.jsonPrimitive?.contentOrNull
                if (expected != CanonicalJson.sha256Hex(array)) {
                    errors += "$key perTableSha256 不匹配"
                }
            }
        }

        if (payloadBytes != null && payloadBytes.size > MAX_PAYLOAD_BYTES) {
            errors += "payload 超过大小限制"
        }
        if (expectedPayloadSha256 != null) {
            val actual = CanonicalJson.sha256Hex(payload)
            if (actual != expectedPayloadSha256) errors += "payloadSha256 不匹配"
        }

        validateReferences(tableArrays, errors, warnings)
        return Result(errors, warnings)
    }

    fun validateEnvelopeSize(bytes: ByteArray): Result = validateEnvelopeSize(bytes.size)

    fun validateEnvelopeSize(size: Int): Result {
        val errors = mutableListOf<String>()
        if (size > MAX_FILE_BYTES) errors += "备份文件超过 64 MiB"
        if (size <= 0) errors += "备份文件为空"
        return Result(errors)
    }

    private fun validateReferences(tables: Map<String, JsonArray>, errors: MutableList<String>,
                                   warnings: MutableList<String>) {
        fun ids(key: String, field: String = "id"): Set<String> =
            tables[key].safeArray().mapNotNull { it.jsonObject[field]?.jsonPrimitive?.contentOrNull ?: it.jsonObject[field]?.jsonPrimitive?.longOrNull?.toString() }.toSet()

        val goalIds = ids("goals")
        val journalIds = ids("journalEntries")
        tables["goals"]?.forEach { element ->
            val row = element.jsonObject
            val id = row["id"]?.jsonPrimitive?.longOrNull
            val parent = row["parentId"]?.jsonPrimitive?.longOrNull
            val level = row["level"]?.jsonPrimitive?.contentOrNull
            if (parent != null && parent.toString() !in goalIds) errors += "目标 $id 的父目标不存在"
            if (parent == null && level != "LONG_TERM") errors += "目标 $id 非长期目标但缺少父目标"
        }
        tables["goalCheckins"]?.forEach { element ->
            val row = element.jsonObject
            val goalId = row["goalId"]?.jsonPrimitive?.longOrNull?.toString()
            if (goalId != null && goalId !in goalIds) errors += "进展的 goalId 不存在：$goalId"
        }
        // 历史合法悬挂：只警告，不拒绝。恢复阶段按设计置空/STALE。
        tables["advisorMessages"]?.forEach { element ->
            val row = element.jsonObject
            val gid = row["contextGoalId"]?.jsonPrimitive?.longOrNull?.toString()
            if (gid != null && gid !in goalIds) warnings += "消息 contextGoalId 历史悬挂"
        }
        tables["goalProposals"]?.forEach { element ->
            val row = element.jsonObject
            val mid = row["messageId"]?.jsonPrimitive?.contentOrNull
            if (mid.isNullOrBlank()) warnings += "草案缺少 messageId"
        }
        tables["growthSignals"]?.forEach { element ->
            val row = element.jsonObject
            val sid = row["sourceId"]?.jsonPrimitive?.longOrNull?.toString()
            if (sid != null && sid !in journalIds) warnings += "成长线索来源日记历史悬挂"
        }
        tables["growthMemories"]?.forEach { element ->
            val row = element.jsonObject
            val raw = row["sourceDiaryIdsJson"]?.jsonPrimitive?.contentOrNull.orEmpty()
            runCatching {
                raw.trim().removePrefix("[").removeSuffix("]").split(",").map { it.trim().trim('"') }
                    .filter { it.isNotBlank() }.forEach { if (it !in journalIds) warnings += "成长记忆来源日记历史悬挂" }
            }
        }
    }

    private fun typeMatches(value: kotlinx.serialization.json.JsonElement, type: FieldType): Boolean {
        val p = value as? JsonPrimitive ?: return type == FieldType.ARRAY
        return when (type) {
            FieldType.STRING -> p.isString
            FieldType.LONG -> p.longOrNull != null
            FieldType.DOUBLE -> p.longOrNull != null || runCatching { p.content.toDouble() }.isSuccess
            FieldType.BOOLEAN -> p.content == "true" || p.content == "false"
            FieldType.ARRAY -> false
        }
    }

    private fun JsonArray?.safeArray(): JsonArray = this ?: JsonArray(emptyList())
}
