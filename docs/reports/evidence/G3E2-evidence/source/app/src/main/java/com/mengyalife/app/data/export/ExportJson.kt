package com.mengyalife.app.data.export

import com.mengyalife.app.data.local.JournalEntryEntity
import com.mengyalife.app.data.local.goal.GoalCheckinEntity
import com.mengyalife.app.data.local.goal.GoalEntity
import com.mengyalife.app.domain.goals.GoalLevel
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 本地数据导出格式（Gate 2C）。
 *
 * 只包含**用户自己的成长数据**：日记、三层目标、进展记录，
 * 以及关联 id / 时间戳 / 版本信息。不包含任何 API Key、账号、设备标识或设置项。
 *
 * 版本策略：
 * - [SCHEMA_VERSION] 是**导出文件格式**的版本，与数据库版本独立；
 * - 以后加字段可以保持 schemaVersion，删改语义必须 +1；
 * - 导入功能尚未实现，本轮只承诺「导出文件可被本文件中的校验器完整解析」。
 */
object ExportJson {

    const val FORMAT = "mengyalife-backup"
    const val SCHEMA_VERSION = 1

    @OptIn(ExperimentalSerializationApi::class)
    private val pretty = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    private val fileTimestamp =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US)

    data class Counts(
        val journalEntries: Int,
        val goals: Int,
        val goalCheckins: Int,
    )

    /** 一次导出所依据的、在同一个数据库事务里读出的一致性快照。 */
    data class Bundle(
        val exportedAt: Long,
        val appPackage: String,
        val appVersionName: String,
        val appVersionCode: Long,
        val databaseVersion: Int,
        val journalEntries: List<JournalEntryEntity>,
        val goals: List<GoalEntity>,
        val goalCheckins: List<GoalCheckinEntity>,
    ) {
        val counts: Counts
            get() = Counts(
                journalEntries = journalEntries.size,
                goals = goals.size,
                goalCheckins = goalCheckins.size,
            )
    }

    /** 校验结果。`ok == false` 时 [problems] 里是可直接展示的中文原因。 */
    data class Verification(
        val ok: Boolean,
        val problems: List<String> = emptyList(),
        val counts: Counts? = null,
    )

    /** 建议文件名：mengya-life-export-20261103-112500.json */
    fun suggestedFileName(
        exportedAt: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = "mengya-life-export-" +
        fileTimestamp.withZone(zone).format(Instant.ofEpochMilli(exportedAt)) + ".json"

    // ------------------------------------------------------------------ 编码

    fun encode(bundle: Bundle): String {
        val root = buildJsonObject {
            put("format", FORMAT)
            put("schemaVersion", SCHEMA_VERSION)
            put("exportedAt", bundle.exportedAt)
            put("exportedAtIso", Instant.ofEpochMilli(bundle.exportedAt).toString())
            put(
                "app",
                buildJsonObject {
                    put("name", "Mengya Life")
                    put("package", bundle.appPackage)
                    put("versionName", bundle.appVersionName)
                    put("versionCode", bundle.appVersionCode)
                },
            )
            put(
                "database",
                buildJsonObject {
                    put("engine", "room")
                    put("schemaVersion", bundle.databaseVersion)
                },
            )
            put(
                "counts",
                buildJsonObject {
                    put("journalEntries", bundle.journalEntries.size)
                    put("goals", bundle.goals.size)
                    put("goalCheckins", bundle.goalCheckins.size)
                },
            )
            put(
                "data",
                buildJsonObject {
                    put(
                        "journalEntries",
                        buildJsonArray {
                            bundle.journalEntries.forEach { add(journalJson(it)) }
                        },
                    )
                    put(
                        "goals",
                        buildJsonArray {
                            bundle.goals.forEach { add(goalJson(it)) }
                        },
                    )
                    put(
                        "goalCheckins",
                        buildJsonArray {
                            bundle.goalCheckins.forEach { add(checkinJson(it)) }
                        },
                    )
                },
            )
        }
        return pretty.encodeToString(JsonElement.serializer(), root) + "\n"
    }

    private fun journalJson(entry: JournalEntryEntity): JsonObject = buildJsonObject {
        put("id", entry.id)
        put("title", entry.title)
        put("body", entry.body)
        put("createdAt", entry.createdAt)
        put("updatedAt", entry.updatedAt)
    }

    private fun goalJson(goal: GoalEntity): JsonObject = buildJsonObject {
        put("id", goal.id)
        put("title", goal.title)
        put("description", goal.description)
        put("level", goal.level.name)
        put("parentId", goal.parentId)
        put("domain", goal.domain.name)
        put("status", goal.status.name)
        put("startDate", goal.startDate)
        put("dueDate", goal.dueDate)
        put("createdAt", goal.createdAt)
        put("updatedAt", goal.updatedAt)
    }

    private fun checkinJson(checkin: GoalCheckinEntity): JsonObject = buildJsonObject {
        put("id", checkin.id)
        put("goalId", checkin.goalId)
        put("recordedAt", checkin.recordedAt)
        put("note", checkin.note)
        // 数据库若存在历史脏数据（NaN/Infinity），不能写出非法 JSON：
        // 非有限数一律置 null（新写入已被仓库层拒绝）。
        put("metricValue", checkin.metricValue?.takeIf { it.isFinite() })
        put("metricUnit", checkin.metricUnit)
        put("source", checkin.source)
    }

    private fun JsonObjectBuilder.put(key: String, value: Long?) {
        if (value == null) put(key, JsonNull) else put(key, JsonPrimitive(value))
    }

    private fun JsonObjectBuilder.put(key: String, value: Double?) {
        if (value == null) put(key, JsonNull) else put(key, JsonPrimitive(value))
    }

    private fun JsonObjectBuilder.put(key: String, value: String?) {
        if (value == null) put(key, JsonNull) else put(key, JsonPrimitive(value))
    }

    // ------------------------------------------------------------------ 校验

    /**
     * 解析并校验导出文本。
     *
     * @param expected 设备上写完后回读时传入原始快照，会逐项比对 id / 父子关系 / 计数；
     *                 单元测试里可以只做结构校验。
     */
    fun verify(text: String, expected: Bundle? = null): Verification {
        val problems = mutableListOf<String>()

        val root = runCatching { Json.parseToJsonElement(text).jsonObject }
            .getOrElse { return Verification(false, listOf("不是合法的 JSON 对象：${it.message}")) }

        if (root.stringOrNull("format") != FORMAT) {
            problems += "format 字段不是 $FORMAT"
        }
        if (root.intOrNull("schemaVersion") != SCHEMA_VERSION) {
            problems += "schemaVersion 不是 $SCHEMA_VERSION"
        }
        val exportedAt = root.longOrNull("exportedAt") ?: 0L
        if (exportedAt <= 0L) problems += "缺少有效的 exportedAt 时间戳"

        val data = root["data"] as? JsonObject
        if (data == null) {
            return Verification(false, problems + listOf("缺少 data 对象"))
        }

        val journals = data.arrayOrNull("journalEntries")
        val goals = data.arrayOrNull("goals")
        val checkins = data.arrayOrNull("goalCheckins")
        if (journals == null || goals == null || checkins == null) {
            return Verification(false, problems + listOf("data 下缺少 journalEntries / goals / goalCheckins"))
        }

        val journalIds = mutableSetOf<Long>()
        journals.forEachIndexed { index, element ->
            val obj = element as? JsonObject
            if (obj == null) {
                problems += "journalEntries[$index] 不是对象"
                return@forEachIndexed
            }
            val id = obj.longOrNull("id") ?: 0L
            if (id <= 0L || !journalIds.add(id)) problems += "日记 id 非法或重复：$id"
            if (obj.stringOrNull("body").isNullOrEmpty()) problems += "日记 $id 缺少正文"
            if ((obj.longOrNull("createdAt") ?: 0L) <= 0L) problems += "日记 $id 缺少 createdAt"
            if ((obj.longOrNull("updatedAt") ?: 0L) <= 0L) problems += "日记 $id 缺少 updatedAt"
        }

        val goalById = mutableMapOf<Long, GoalLevel>()
        val parentById = mutableMapOf<Long, Long?>()
        goals.forEachIndexed { index, element ->
            val obj = element as? JsonObject
            if (obj == null) {
                problems += "goals[$index] 不是对象"
                return@forEachIndexed
            }
            val id = obj.longOrNull("id") ?: 0L
            val levelName = obj.stringOrNull("level")
            val level = GoalLevel.entries.firstOrNull { it.name == levelName }
            if (id <= 0L) {
                problems += "目标 id 非法：$id"
            } else {
                if (goalById.containsKey(id)) problems += "目标 id 重复：$id"
                if (level == null) problems += "目标 $id 的层级非法：$levelName"
                else goalById[id] = level
                parentById[id] = obj.longOrNull("parentId")
            }
            if (obj.stringOrNull("title").isNullOrBlank()) problems += "目标 $id 缺少标题"
            if (obj.stringOrNull("status") == null) problems += "目标 $id 缺少状态"
            if (obj.stringOrNull("domain") == null) problems += "目标 $id 缺少领域"
            if ((obj.longOrNull("createdAt") ?: 0L) <= 0L) problems += "目标 $id 缺少 createdAt"
            if ((obj.longOrNull("updatedAt") ?: 0L) <= 0L) problems += "目标 $id 缺少 updatedAt"
        }

        // 父子关系：父必须存在，层级必须逐级嵌套，根不能有父。
        parentById.forEach { (id, parentId) ->
            val level = goalById[id]
            if (parentId == null) {
                if (level != GoalLevel.LONG_TERM) problems += "目标 $id 是非长期目标但没有父目标"
            } else {
                val parentLevel = goalById[parentId]
                if (parentLevel == null) {
                    problems += "目标 $id 的父目标 $parentId 不存在"
                } else if (level == GoalLevel.STAGE && parentLevel != GoalLevel.LONG_TERM) {
                    problems += "阶段目标 $id 的父层级不是长期目标"
                } else if (level == GoalLevel.ACTION && parentLevel != GoalLevel.STAGE) {
                    problems += "短期行动 $id 的父层级不是阶段目标"
                }
            }
        }

        val checkinIds = mutableSetOf<Long>()
        checkins.forEachIndexed { index, element ->
            val obj = element as? JsonObject
            if (obj == null) {
                problems += "goalCheckins[$index] 不是对象"
                return@forEachIndexed
            }
            val id = obj.longOrNull("id") ?: 0L
            if (id <= 0L || !checkinIds.add(id)) problems += "进展 id 非法或重复：$id"
            val goalId = obj.longOrNull("goalId") ?: 0L
            if (goalId !in goalById) problems += "进展 $id 关联的目标 $goalId 不存在"
            if ((obj.longOrNull("recordedAt") ?: 0L) <= 0L) problems += "进展 $id 缺少 recordedAt"
            if (obj.stringOrNull("source") == null) problems += "进展 $id 缺少 source"
            val metric = obj["metricValue"]
            if (metric != null && metric !is JsonNull) {
                val value = (metric as? JsonPrimitive)?.doubleOrNull
                if (value == null || !value.isFinite()) problems += "进展 $id 的数值不是有限数"
            }
        }

        val counts = Counts(journals.size, goals.size, checkins.size)
        val declared = root["counts"] as? JsonObject
        if (declared == null) {
            problems += "缺少 counts 对象"
        } else {
            if (declared.intOrNull("journalEntries") != counts.journalEntries) problems += "counts.journalEntries 与实际不符"
            if (declared.intOrNull("goals") != counts.goals) problems += "counts.goals 与实际不符"
            if (declared.intOrNull("goalCheckins") != counts.goalCheckins) problems += "counts.goalCheckins 与实际不符"
        }

        if (expected != null) {
            if (counts != expected.counts) {
                problems += "导出条数与快照不一致：文件 $counts / 快照 ${expected.counts}"
            }
            if (journalIds != expected.journalEntries.map { it.id }.toSet()) {
                problems += "日记 id 与快照不一致"
            }
            if (goalById.keys != expected.goals.map { it.id }.toSet()) {
                problems += "目标 id 与快照不一致"
            }
            val expectedParents = expected.goals.associate { it.id to it.parentId }
            if (parentById != expectedParents) {
                problems += "目标父子关系与快照不一致"
            }
            if (checkinIds != expected.goalCheckins.map { it.id }.toSet()) {
                problems += "进展 id 与快照不一致"
            }
            val expectedLinks = expected.goalCheckins.map { it.id to it.goalId }.toMap()
            val actualLinks = checkins.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val id = obj.longOrNull("id") ?: return@mapNotNull null
                val goalId = obj.longOrNull("goalId") ?: return@mapNotNull null
                id to goalId
            }.toMap()
            if (actualLinks != expectedLinks) {
                problems += "进展记录与目标的关联与快照不一致"
            }
        }

        return Verification(problems.isEmpty(), problems, counts)
    }

    // ------------------------------------------------------------- JSON 读取小工具

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.intOrNull(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.longOrNull(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.arrayOrNull(key: String): JsonArray? =
        this[key] as? JsonArray
}
