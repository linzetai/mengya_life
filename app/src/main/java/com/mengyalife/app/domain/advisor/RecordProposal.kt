package com.mengyalife.app.domain.advisor

import com.mengyalife.app.domain.goals.GoalRules
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.ZoneId

/**
 * 对话式记录候选机制。
 *
 * 0.17 起支持 [RecordKind.PROGRESS] 与 [RecordKind.DIARY]。
 * 模型只能提出结构化候选；正式写入只走现有领域仓库：
 * - 进展 → [com.mengyalife.app.data.advisor.ProgressRecordApplyService] → GoalRepository；
 * - 日记 → [com.mengyalife.app.data.advisor.DiaryRecordApplyService] → JournalRepository。
 * 不存在第二套日记存储。
 */
enum class RecordKind(val label: String) {
    PROGRESS("目标进展"),
    DIARY("日记"),
    GOAL("目标"),
    PLAN("小计划"),
    PLAN_PROGRESS("计划执行"),
}

/** 增量记录与累计快照必须区分：前者是「又存了 2000」，后者是「现在有 14.7 万」。 */
enum class RecordAmountKind(val label: String) {
    DELTA("增量"),
    SNAPSHOT("累计快照"),
    NONE("无数值"),
}

enum class RecordIntent(val label: String) {
    EXPLICIT("明确记录指令"),
    SUGGESTED("聊天中的记录建议"),
}

object RecordProposalStatus {
    const val DRAFT = "DRAFT"
    const val APPLIED = "APPLIED"
    const val CANCELLED = "CANCELLED"
    const val UNDONE = "UNDONE"
}

/** 小计划：新建 / 状态更新。 */
enum class PlanChangeKind { CREATE, STATUS }

data class PlanCandidate(
    val planId: Long,
    val title: String,
    val status: String,
)

data class PlanProgressDraft(
    val planId: Long? = null,
    val planTitle: String? = null,
    val note: String = "",
    val candidates: List<PlanCandidate> = emptyList(),
)

data class PlanChangeDraft(
    val change: PlanChangeKind,
    val targetPlanId: Long? = null,
    val title: String? = null,
    val timeScope: String? = null,
    val goalId: Long? = null,
    /** ACTIVE / COMPLETED / PAUSED / CANCELLED */
    val newStatus: String? = null,
    val note: String? = null,
    val candidates: List<PlanCandidate> = emptyList(),
)

/** 目标变更：新建 / 修改已有 / 仅拆解。 */
enum class GoalChangeKind { CREATE, UPDATE, BREAKDOWN }

data class GoalBreakdownSuggestion(
    val title: String,
    val levelName: String,
    val selected: Boolean = true,
)

/** 修改前的快照，用于卡片展示差异（不写回数据库）。 */
data class GoalBeforeSnapshot(
    val goalId: Long,
    val title: String,
    val levelName: String,
    val domainName: String,
    val dueAt: Long?,
    val parentId: Long?,
)

data class GoalChangeDraft(
    val change: GoalChangeKind,
    val targetGoalId: Long? = null,
    val title: String? = null,
    val description: String? = null,
    val levelName: String? = null,
    val domainName: String? = null,
    val dueAt: Long? = null,
    val parentId: Long? = null,
    val before: GoalBeforeSnapshot? = null,
    val breakdown: List<GoalBreakdownSuggestion> = emptyList(),
    val uncertain: List<String> = emptyList(),
)

/** 澄清时给用户选择的目标候选。 */
data class RecordGoalCandidate(
    val goalId: Long,
    val title: String,
)

data class RecordProposalDraft(
    val kind: RecordKind,
    val goalId: Long?,
    val goalTitle: String?,
    val goalCandidates: List<RecordGoalCandidate>,
    val amountKind: RecordAmountKind,
    val metricValue: Double?,
    val metricUnit: String?,
    val note: String,
    val occurredAt: Long?,
    /** 需要用户补充的字段：goal / metricValue / occurredAt。 */
    val pendingFields: List<String>,
    val intent: RecordIntent,
    val originalText: String,
    // ---- 0.17 日记候选（与进展共用同一提案表；不建第二套存储） ----
    val diaryTitle: String? = null,
    val diaryBody: String? = null,
    /** 已落库的日记 id；用于“打开日记”和编辑后保持同一篇。 */
    val diaryEntryId: Long? = null,
    /** 这条草稿引用了哪些消息（多轮对话整理）。 */
    val sourceMessageIds: List<String> = emptyList(),
    // ---- 0.18 目标候选 ----
    val goalChange: GoalChangeDraft? = null,
    val appliedGoalIds: List<Long> = emptyList(),
    val planChange: PlanChangeDraft? = null,
    val planProgress: PlanProgressDraft? = null,
    val appliedPlanId: Long? = null,
) {
    val isDiary: Boolean get() = kind == RecordKind.DIARY
    val isGoal: Boolean get() = kind == RecordKind.GOAL
    val isPlan: Boolean get() = kind == RecordKind.PLAN
    val isPlanProgress: Boolean get() = kind == RecordKind.PLAN_PROGRESS

    val hasNumericAmount: Boolean
        get() = !isDiary && amountKind != RecordAmountKind.NONE && metricValue != null

    val isComplete: Boolean
        get() = if (isDiary) {
            diaryBody?.isNotBlank() == true
        } else if (isGoal) {
            val goal = goalChange ?: return false
            when (goal.change) {
                GoalChangeKind.UPDATE -> goal.targetGoalId != null &&
                    (goal.title?.isNotBlank() == true || goal.dueAt != null ||
                        goal.domainName != null || goal.levelName != null)
                GoalChangeKind.BREAKDOWN -> goal.targetGoalId != null && goal.breakdown.any { it.selected }
                GoalChangeKind.CREATE -> goal.title?.isNotBlank() == true
            }
        } else {
            pendingFields.isEmpty() && goalId != null && note.isNotBlank() &&
                (amountKind == RecordAmountKind.NONE || metricValue != null)
        }
}

sealed interface RecordProposalParseResult {
    data class Success(val draft: RecordProposalDraft?) : RecordProposalParseResult
    data class Failure(val message: String) : RecordProposalParseResult
}

/**
 * 严格解析模型返回的记录候选 JSON。
 *
 * 输入约定（system prompt 同时下发）：
 * {"schemaVersion":1,"record":{"type":"PROGRESS","goalId":1,"goalTitle":"…",
 *  "goalCandidates":[],"amountKind":"DELTA","metricValue":2000,"metricUnit":"元",
 *  "note":"今天存了 2000 元","occurredAt":"2026-11-07","pendingFields":[],
 *  "intent":"EXPLICIT"}}
 * 无记录意图时返回 {"schemaVersion":1,"record":null}。
 */
object RecordProposalParser {

    const val SCHEMA_VERSION = 1
    const val MAX_NOTE = GoalRules.MAX_NOTE
    const val MAX_CANDIDATES = 5
    const val MAX_DIARY_TITLE = 120
    const val MAX_DIARY_BODY = 20_000

    val PENDING_FIELDS = setOf("goal", "metricValue", "metricUnit", "occurredAt")

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(
        raw: String,
        originalText: String,
        now: Long = System.currentTimeMillis(),
        allowedGoalIds: Set<Long> = emptySet(),
    ): RecordProposalParseResult {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return RecordProposalParseResult.Failure("没有找到 JSON 对象")
        val root = runCatching {
            json.parseToJsonElement(raw.substring(start, end + 1)).jsonObject
        }.getOrElse { return RecordProposalParseResult.Failure("JSON 解析失败") }

        if (root["schemaVersion"]?.jsonPrimitive?.intOrNull != SCHEMA_VERSION) {
            return RecordProposalParseResult.Failure("schemaVersion 必须为 $SCHEMA_VERSION")
        }
        val recordElement = root["record"]
            ?: return RecordProposalParseResult.Failure("缺少 record 字段")
        if (recordElement is kotlinx.serialization.json.JsonNull) {
            return RecordProposalParseResult.Success(null)
        }
        val record = recordElement as? JsonObject
            ?: return RecordProposalParseResult.Failure("record 必须是对象或 null")

        val kind = when (record["type"]?.jsonPrimitive?.contentOrNull?.uppercase()) {
            "PROGRESS" -> RecordKind.PROGRESS
            "DIARY" -> RecordKind.DIARY
            "GOAL" -> RecordKind.GOAL
            "PLAN" -> RecordKind.PLAN
            else -> return RecordProposalParseResult.Failure("暂不支持的记录类型")
        }
        val intent = when (record["intent"]?.jsonPrimitive?.contentOrNull?.uppercase()) {
            "EXPLICIT" -> RecordIntent.EXPLICIT
            else -> RecordIntent.SUGGESTED
        }
        if (kind == RecordKind.DIARY) {
            return parseDiary(record, originalText, now, intent)
        }
        if (kind == RecordKind.GOAL) {
            return parseGoal(record, originalText, now, intent, allowedGoalIds)
        }
        if (kind == RecordKind.PLAN) {
            return parsePlan(record, originalText, intent)
        }
        if (kind == RecordKind.PLAN_PROGRESS) {
            val planId = record["planId"]?.jsonPrimitive?.longOrNull
            val note = record["note"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (note.isEmpty()) return RecordProposalParseResult.Failure("缺少执行说明")
            val draft = PlanProgressDraft(planId = planId, note = note)
            return RecordProposalParseResult.Success(
                RecordProposalDraft(
                    kind = RecordKind.PLAN_PROGRESS,
                    goalId = null, goalTitle = null, goalCandidates = emptyList(),
                    amountKind = RecordAmountKind.NONE, metricValue = null, metricUnit = null,
                    note = note, occurredAt = null, pendingFields = emptyList(),
                    intent = intent, originalText = originalText.trim(), planProgress = draft,
                ),
            )
        }
        val amountKind = when (record["amountKind"]?.jsonPrimitive?.contentOrNull?.uppercase()) {
            "DELTA" -> RecordAmountKind.DELTA
            "SNAPSHOT" -> RecordAmountKind.SNAPSHOT
            "NONE", null -> RecordAmountKind.NONE
            else -> return RecordProposalParseResult.Failure("amountKind 非法")
        }
        val metricValue = record["metricValue"]?.jsonPrimitive?.doubleOrNull
        if (amountKind != RecordAmountKind.NONE && metricValue == null) {
            return RecordProposalParseResult.Failure("增量/累计记录必须有数值")
        }
        GoalRules.validateMetricValue(metricValue)?.let {
            return RecordProposalParseResult.Failure(it)
        }
        val metricUnit = record["metricUnit"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        GoalRules.validateUnit(metricUnit)?.let { return RecordProposalParseResult.Failure(it) }

        val note = record["note"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (note.isEmpty()) return RecordProposalParseResult.Failure("缺少记录说明")
        if (note.length > MAX_NOTE) return RecordProposalParseResult.Failure("记录说明过长")

        var goalId = record["goalId"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 }
        if (goalId != null && allowedGoalIds.isNotEmpty() && goalId !in allowedGoalIds) {
            // 模型引用了未授权/不存在的目标：不猜，转为待澄清。
            goalId = null
        }
        val goalCandidates = parseCandidates(record["goalCandidates"], allowedGoalIds)
        val goalTitle = record["goalTitle"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            ?: goalCandidates.firstOrNull { it.goalId == goalId }?.title

        val pending = parsePending(record["pendingFields"])
        val effectivePending = buildSet {
            addAll(pending)
            if (goalId == null) add("goal")
            // 有数值却缺单位：不能静默并进已有单位，先向用户确认。
            if (amountKind != RecordAmountKind.NONE && metricValue != null && metricUnit == null) {
                add("metricUnit")
            }
        }.toList()

        val occurredAt = parseOccurredAt(record["occurredAt"]?.jsonPrimitive?.contentOrNull, now)
            ?: if (record["occurredAt"] != null && record["occurredAt"] !is kotlinx.serialization.json.JsonNull) {
                return RecordProposalParseResult.Failure("occurredAt 必须是 yyyy-MM-dd")
            } else null

        return RecordProposalParseResult.Success(
            RecordProposalDraft(
                kind = kind,
                goalId = goalId,
                goalTitle = goalTitle,
                goalCandidates = goalCandidates,
                amountKind = amountKind,
                metricValue = metricValue,
                metricUnit = metricUnit,
                note = note,
                occurredAt = occurredAt,
                pendingFields = effectivePending,
                intent = intent,
                originalText = originalText.trim(),
            ),
        )
    }

    private fun parsePlan(
        record: JsonObject,
        originalText: String,
        intent: RecordIntent,
    ): RecordProposalParseResult {
        val change = when (record["change"]?.jsonPrimitive?.contentOrNull?.uppercase()) {
            "STATUS" -> PlanChangeKind.STATUS
            else -> PlanChangeKind.CREATE
        }
        if (change == PlanChangeKind.CREATE) {
            val title = record["title"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                ?: return RecordProposalParseResult.Failure("缺少计划内容")
            if (title.length > com.mengyalife.app.domain.goals.GoalRules.MAX_TITLE) {
                return RecordProposalParseResult.Failure("计划内容过长")
            }
            val changeDraft = PlanChangeDraft(
                change = PlanChangeKind.CREATE,
                title = title,
                timeScope = record["timeScope"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() },
                goalId = record["goalId"]?.jsonPrimitive?.longOrNull,
            )
            return RecordProposalParseResult.Success(
                RecordProposalDraft(
                    kind = RecordKind.PLAN,
                    goalId = changeDraft.goalId,
                    goalTitle = null,
                    goalCandidates = emptyList(),
                    amountKind = RecordAmountKind.NONE,
                    metricValue = null,
                    metricUnit = null,
                    note = title,
                    occurredAt = null,
                    pendingFields = emptyList(),
                    intent = intent,
                    originalText = originalText.trim(),
                    planChange = changeDraft,
                ),
            )
        }
        val targetId = record["goalId"]?.jsonPrimitive?.longOrNull
            ?: record["planId"]?.jsonPrimitive?.longOrNull
            ?: return RecordProposalParseResult.Failure("没有定位到要更新的小计划")
        val status = record["status"]?.jsonPrimitive?.contentOrNull?.uppercase()?.takeIf {
            it in setOf("ACTIVE", "COMPLETED", "PAUSED", "CANCELLED")
        } ?: return RecordProposalParseResult.Failure("计划状态非法")
        val candidates = (record["planCandidates"] as? JsonArray)?.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj["planId"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            PlanCandidate(
                planId = id,
                title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                status = obj["status"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }.orEmpty()
        val changeDraft = PlanChangeDraft(
            change = PlanChangeKind.STATUS,
            targetPlanId = targetId,
            newStatus = status,
            note = record["note"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() },
            candidates = candidates,
        )
        return RecordProposalParseResult.Success(
            RecordProposalDraft(
                kind = RecordKind.PLAN,
                goalId = null,
                goalTitle = null,
                goalCandidates = emptyList(),
                amountKind = RecordAmountKind.NONE,
                metricValue = null,
                metricUnit = null,
                note = changeDraft.note ?: "更新小计划状态",
                occurredAt = null,
                pendingFields = emptyList(),
                intent = intent,
                originalText = originalText.trim(),
                planChange = changeDraft,
            ),
        )
    }

    private fun parseGoal(
        record: JsonObject,
        originalText: String,
        now: Long,
        intent: RecordIntent,
        allowedGoalIds: Set<Long>,
    ): RecordProposalParseResult {
        val change = when (record["change"]?.jsonPrimitive?.contentOrNull?.uppercase()) {
            "UPDATE" -> GoalChangeKind.UPDATE
            "BREAKDOWN" -> GoalChangeKind.BREAKDOWN
            else -> GoalChangeKind.CREATE
        }
        val targetId = record["goalId"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 }
        if (change != GoalChangeKind.CREATE) {
            if (targetId == null) return RecordProposalParseResult.Failure("没有定位到要修改的目标")
            if (allowedGoalIds.isNotEmpty() && targetId !in allowedGoalIds) {
                return RecordProposalParseResult.Failure("目标不在已授权范围内")
            }
        }
        val title = record["title"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        if (change != GoalChangeKind.BREAKDOWN && title == null) {
            return RecordProposalParseResult.Failure("缺少目标标题")
        }
        if (title != null && title.length > com.mengyalife.app.domain.goals.GoalRules.MAX_TITLE) {
            return RecordProposalParseResult.Failure("目标标题过长")
        }
        val level = record["level"]?.jsonPrimitive?.contentOrNull
            ?.uppercase()
            ?.takeIf { it in setOf("LONG_TERM", "STAGE", "ACTION") }
        val domain = record["domain"]?.jsonPrimitive?.contentOrNull
            ?.uppercase()
            ?.takeIf { it in setOf("CAREER", "HEALTH", "WEALTH", "LIFE", "LEARNING") }
        val dueAt = record["dueAt"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
            runCatching {
                java.time.LocalDate.parse(raw)
                    .atStartOfDay(java.time.ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            }.getOrElse { return RecordProposalParseResult.Failure("dueAt 必须是 yyyy-MM-dd") }
        }
        val breakdown = (record["breakdown"] as? JsonArray)?.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val itemTitle = obj["title"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val itemLevel = obj["level"]?.jsonPrimitive?.contentOrNull?.uppercase()
                ?.takeIf { it == "STAGE" || it == "ACTION" } ?: "ACTION"
            GoalBreakdownSuggestion(itemTitle.take(com.mengyalife.app.domain.goals.GoalRules.MAX_TITLE), itemLevel)
        }.orEmpty().take(5)
        if (change == GoalChangeKind.BREAKDOWN && breakdown.isEmpty()) {
            return RecordProposalParseResult.Failure("没有可用的拆解建议")
        }
        val uncertain = (record["uncertain"] as? JsonArray)?.mapNotNull {
            it.jsonPrimitive.contentOrNull?.takeIf { value -> value.isNotBlank() }
        }.orEmpty()
        val goalDraft = GoalChangeDraft(
            change = change,
            targetGoalId = targetId,
            title = title,
            description = record["description"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() },
            levelName = level,
            domainName = domain,
            dueAt = dueAt,
            parentId = record["parentId"]?.jsonPrimitive?.longOrNull,
            breakdown = breakdown,
            uncertain = uncertain,
        )
        return RecordProposalParseResult.Success(
            RecordProposalDraft(
                kind = RecordKind.GOAL,
                goalId = targetId,
                goalTitle = title,
                goalCandidates = emptyList(),
                amountKind = RecordAmountKind.NONE,
                metricValue = null,
                metricUnit = null,
                note = title ?: "目标拆解建议",
                occurredAt = null,
                pendingFields = emptyList(),
                intent = intent,
                originalText = originalText.trim(),
                goalChange = goalDraft,
            ),
        )
    }

    private fun parseDiary(
        record: JsonObject,
        originalText: String,
        now: Long,
        intent: RecordIntent,
    ): RecordProposalParseResult {
        val title = record["title"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        if (title != null && title.length > MAX_DIARY_TITLE) {
            return RecordProposalParseResult.Failure("日记标题过长")
        }
        val body = record["body"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (body.isEmpty()) return RecordProposalParseResult.Failure("日记正文为空")
        if (body.length > MAX_DIARY_BODY) return RecordProposalParseResult.Failure("日记正文过长")
        val occurredAt = parseOccurredAt(record["occurredAt"]?.jsonPrimitive?.contentOrNull, now)
            ?: if (record["occurredAt"] != null && record["occurredAt"] !is kotlinx.serialization.json.JsonNull) {
                return RecordProposalParseResult.Failure("occurredAt 必须是 yyyy-MM-dd")
            } else null
        return RecordProposalParseResult.Success(
            RecordProposalDraft(
                kind = RecordKind.DIARY,
                goalId = null,
                goalTitle = null,
                goalCandidates = emptyList(),
                amountKind = RecordAmountKind.NONE,
                metricValue = null,
                metricUnit = null,
                note = body,
                occurredAt = occurredAt,
                pendingFields = emptyList(),
                intent = intent,
                originalText = originalText.trim(),
                diaryTitle = title,
                diaryBody = body,
            ),
        )
    }

    /** 序列化成稳定的 JSON 行，写入 record_proposals 表。 */
    fun encode(draft: RecordProposalDraft): String = buildJsonObject {
        put("kind", draft.kind.name)
        put("goalId", draft.goalId)
        put("goalTitle", draft.goalTitle)
        put(
            "goalCandidates",
            buildJsonArray {
                draft.goalCandidates.forEach { candidate ->
                    add(
                        buildJsonObject {
                            put("goalId", candidate.goalId)
                            put("title", candidate.title)
                        },
                    )
                }
            },
        )
        put("amountKind", draft.amountKind.name)
        put("metricValue", draft.metricValue)
        put("metricUnit", draft.metricUnit)
        put("note", draft.note)
        put("occurredAt", draft.occurredAt)
        put(
            "pendingFields",
            buildJsonArray { draft.pendingFields.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } },
        )
        put("intent", draft.intent.name)
        put("originalText", draft.originalText)
        put("diaryTitle", draft.diaryTitle)
        put("diaryBody", draft.diaryBody)
        put("diaryEntryId", draft.diaryEntryId)
        put(
            "sourceMessageIds",
            buildJsonArray { draft.sourceMessageIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } },
        )
    }.toString()

    fun decode(raw: String): RecordProposalDraft? = runCatching {
        val obj = Json.parseToJsonElement(raw).jsonObject
        RecordProposalDraft(
            kind = RecordKind.entries.firstOrNull { it.name == obj.str("kind") } ?: RecordKind.PROGRESS,
            goalId = obj["goalId"]?.jsonPrimitive?.longOrNull,
            goalTitle = obj.str("goalTitle"),
            goalCandidates = (obj["goalCandidates"] as? JsonArray)?.mapNotNull { element ->
                val candidate = element as? JsonObject ?: return@mapNotNull null
                val id = candidate["goalId"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                RecordGoalCandidate(id, candidate.str("title").orEmpty())
            }.orEmpty(),
            amountKind = RecordAmountKind.entries.firstOrNull { it.name == obj.str("amountKind") }
                ?: RecordAmountKind.NONE,
            metricValue = obj["metricValue"]?.jsonPrimitive?.doubleOrNull,
            metricUnit = obj.str("metricUnit"),
            note = obj.str("note").orEmpty(),
            occurredAt = obj["occurredAt"]?.jsonPrimitive?.longOrNull,
            pendingFields = (obj["pendingFields"] as? JsonArray)?.mapNotNull {
                it.jsonPrimitive.contentOrNull
            }.orEmpty(),
            intent = RecordIntent.entries.firstOrNull { it.name == obj.str("intent") }
                ?: RecordIntent.SUGGESTED,
            originalText = obj.str("originalText").orEmpty(),
            diaryTitle = obj.str("diaryTitle"),
            diaryBody = obj.str("diaryBody"),
            diaryEntryId = obj["diaryEntryId"]?.jsonPrimitive?.longOrNull,
            sourceMessageIds = (obj["sourceMessageIds"] as? JsonArray)?.mapNotNull {
                it.jsonPrimitive.contentOrNull
            }.orEmpty(),
        )
    }.getOrNull()

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun parseCandidates(element: kotlinx.serialization.json.JsonElement?, allowed: Set<Long>): List<RecordGoalCandidate> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj["goalId"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 } ?: return@mapNotNull null
            if (allowed.isNotEmpty() && id !in allowed) return@mapNotNull null
            val rawTitle = obj.str("title").orEmpty()
            val cleanTitle = listOf("长期目标：", "阶段目标：", "短期行动：")
                .firstOrNull { rawTitle.startsWith(it) }
                ?.let { prefix -> rawTitle.removePrefix(prefix) }
                ?: rawTitle
            RecordGoalCandidate(id, cleanTitle)
        }.distinctBy { it.goalId }.take(MAX_CANDIDATES)
    }

    private fun parsePending(element: kotlinx.serialization.json.JsonElement?): List<String> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
            .filter { it in PENDING_FIELDS }
            .distinct()
    }

    private fun parseOccurredAt(raw: String?, now: Long): Long? {
        if (raw.isNullOrBlank()) return null
        val date = runCatching { LocalDate.parse(raw.trim()) }.getOrNull() ?: return null
        val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // 未来超过一天的时间不自动接受（可能是模型笔误）。
        if (millis > now + 24 * 60 * 60 * 1000L) return null
        return millis
    }
}

/**
 * 本地预处理闸门：只有像「记录/存/攒/完成/学习 + 数字」这类消息才值得再发一次提取请求，
 * 避免每条闲聊都多一次模型调用。
 */
object RecordIntentGate {
    private val RECORD_WORDS = listOf(
        "记录", "记一下", "记一笔", "帮我记", "存了", "存入", "攒到", "攒了", "累计",
        "完成了", "学了", "跑了", "读了", "练习", "进展", "说错", "更正", "改成",
        "练了", "做了", "跑了",
        // 0.16A：无数字的定性进展也要能识别
        "终于", "坚持", "面试", "聊了", "沟通", "汇报", "运动", "健身", "读书", "复盘",
    )
    private val NUMBER = Regex("\\d")
    private val CHINESE_AMOUNT = Regex("[一二三四五六七八九十百千两半]\\s*(个|次|小时|分钟|天|周|月|年|元|块|万|公里|页|本|分|秒)")

    fun looksRelevant(text: String): Boolean {
        val value = text.trim()
        if (value.isEmpty()) return false
        if (value.length > AdvisorRules.MAX_INPUT_CHARS) return false
        if (value.length < 3) return false
        // 数字不是必要条件：定性进展（完成面试、坚持运动、聊了职业规划）同样值得提取。
        return RECORD_WORDS.any { value.contains(it) }
    }
}

/**
 * 日记闸门：
 * - 明确指令（“整理成日记”）一定提取；
 * - 只有“足够长且有具体事件”的叙述才考虑建议，纯情绪表达不卡片。
 */
object DiaryIntentGate {
    private val EXPLICIT = listOf(
        "整理成日记", "整理为日记", "整理一下日记", "写成日记", "写日记", "记日记",
        "记到日记", "记进日记", "存到日记", "存进日记", "日记里", "帮我写一篇日记",
        "把刚才", "把这段", "把这次聊",
    )
    private val EVENT = listOf(
        "完成", "去了", "见了", "聊了", "发生", "决定", "开始", "结束", "面试",
        "旅行", "搬家", "报名", "提交", "拿到", "解决", "尝试", "坚持", "练习",
    )
    private val EMOTION = listOf(
        "难过", "开心", "焦虑", "很累", "烦", "委屈", "压力大", "孤独", "失落", "兴奋",
    )

    fun explicit(text: String): Boolean {
        val value = text.trim()
        return EXPLICIT.any { value.contains(it) }
    }

    /** 普通情绪表达（“今天有点难过”）不应触发卡片。 */
    fun shouldSuggest(text: String): Boolean {
        val value = text.trim()
        if (value.length < 40) return false
        if (!EVENT.any { value.contains(it) }) return false
        // 只有情绪、没有事件的短句/感叹，不主动建议。
        if (EMOTION.any { value.contains(it) } && EVENT.none { value.contains(it) }) return false
        return true
    }

    fun looksRelevant(text: String): Boolean = explicit(text) || shouldSuggest(text)
}

/**
 * 目标闸门：
 * - 明确“设为目标 / 改成 / 拆解”才提取；
 * - 带明确时间跨度且有具体方向（“三年内找到…”）也可生成草稿；
 * - 模糊愿望（“有点想学吉他”）只聊天，不建目标。
 */
object GoalIntentGate {
    private val EXPLICIT = listOf(
        "设成目标", "设为目标", "设为目标", "设成一个目标", "设为一个目标", "设一个目标",
        "整理成一个目标", "整理为一个目标", "生成一个目标",
        "设置为目标", "建一个目标", "建个目标", "创建目标",
        "加一个目标", "加个目标", "改成", "调整为", "拆解", "拆成", "拆成阶段",
        "阶段目标", "行动计划", "写进目标",
    )
    private val BREAKDOWN = listOf(
        "不知道从哪里开始", "不知道从哪开始", "怎么开始", "帮我规划", "帮我理一下", "梳理一下目标",
    )
    private val TIMED = Regex("(一|两|三|四|五|六|七|八|九|十|\\d+)\\s*(年|个月|月)内")
    private val DIRECTION = listOf("找到", "完成", "积累", "建立", "成为", "换", "转行", "拿到", "通过", "存到", "减到", "学会")

    fun explicit(text: String): Boolean = EXPLICIT.any { text.contains(it) }
    fun breakdownRequested(text: String): Boolean = BREAKDOWN.any { text.contains(it) }
    fun looksRelevant(text: String): Boolean {
        val value = text.trim()
        if (value.isEmpty()) return false
        if (explicit(value) || breakdownRequested(value)) return true
        return TIMED.containsMatchIn(value) && DIRECTION.any { value.contains(it) }
    }
}

/**
 * 小计划闸门：
 * - 明确未来行动意向（准备/打算/计划 + 时间）→ 可生成创建草稿；
 * - 引用既有计划并说要完成/暂停/取消 → 状态更新；
 * - “今天练了半小时”是已完成事实，走进展，不走计划。
 */
object PlanIntentGate {
    private val FUTURE = listOf("准备", "打算", "计划", "想要", "想每天", "明天", "周末", "这周", "下周", "接下来")
    private val PAST_DONE = listOf("完成了", "做完了", "练了", "跑了", "已经", "昨天", "前天", "今天做了")
    private val STATUS = listOf("完成", "暂停", "取消", "不做了", "算了", "继续", "开始", "还没开始")
    private val PLAN_REF = listOf("计划", "说好的", "之前说", "约定", "那个")

    private val ACTION = listOf("练", "读", "跑", "写", "整理", "学", "做", "开始", "减", "存", "背", "投")
    private val QUESTION = listOf("多少", "哪些", "怎么样", "如何", "是否", "吗", "？", "?")

    fun looksLikeCreate(text: String): Boolean {
        val value = text.trim()
        if (value.isEmpty()) return false
        if (PAST_DONE.any { value.contains(it) }) return false
        if (QUESTION.any { value.contains(it) }) return false
        return FUTURE.any { value.contains(it) } && ACTION.any { value.contains(it) }
    }

    fun looksLikeStatusUpdate(text: String): Boolean {
        val value = text.trim()
        if (value.isEmpty()) return false
        if (STATUS.any { value.contains(it) } && PLAN_REF.any { value.contains(it) }) return true
        // “三次都练完了 / 这周先不练了 / 不打算继续了”：没有“计划”二字也要能接住。
        val actionContext = listOf("练", "读", "跑", "做", "吉他", "计划", "这个", "那个")
        return PlanDraftFactory.statusIntent(value) != null && actionContext.any { value.contains(it) }
    }

    fun looksLikeProgress(text: String): Boolean {
        val value = text.trim()
        if (value.isEmpty()) return false
        val done = listOf("练了", "做了", "跑了", "读了", "整理了一半", "完成了一半")
        val count = listOf("一次", "两次", "1 次", "2 次", "又")
        val finish = listOf("都练完", "都做完", "完成了", "练完了", "暂停", "取消", "不做了")
        if (finish.any { value.contains(it) }) return false
        return done.any { value.contains(it) } && count.any { value.contains(it) }
    }

    fun looksRelevant(text: String): Boolean =
        looksLikeCreate(text) || looksLikeStatusUpdate(text) || looksLikeProgress(text)
}

object RecordProposalRules {

    /** 明确指令 + 字段完整 + 有进展读取授权时，才允许本轮直接写入；日记/目标永远需要确认。 */
    fun shouldAutoApply(draft: RecordProposalDraft, progressAuthorized: Boolean): Boolean =
        !draft.isDiary && !draft.isGoal && !draft.isPlan && !draft.isPlanProgress &&
            draft.intent == RecordIntent.EXPLICIT &&
            draft.isComplete &&
            progressAuthorized

    /** 卡片标题保持克制：类型由状态与字段表达，不再重复目标名。 */
    fun summaryLine(draft: RecordProposalDraft): String = when {
        draft.isDiary -> "整理成日记"
        draft.isPlan -> if (draft.planChange?.change == PlanChangeKind.STATUS) "更新小计划" else "本周小计划"
        draft.isPlanProgress -> "记录一次计划执行"
        draft.isGoal -> when (draft.goalChange?.change) {
            GoalChangeKind.UPDATE -> "调整目标"
            GoalChangeKind.BREAKDOWN -> "目标拆解建议"
            else -> "新建目标"
        }
        draft.intent == RecordIntent.EXPLICIT -> "记录一条进展"
        else -> "建议记录一条进展"
    }

    fun amountLine(draft: RecordProposalDraft): String? {
        if (draft.isDiary || draft.isGoal || draft.isPlan) return null
        if (draft.amountKind == RecordAmountKind.NONE) return null
        val value = draft.metricValue ?: return null
        val number = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
        val unit = draft.metricUnit?.takeIf { it.isNotBlank() }.orEmpty()
        val amount = if (unit.isEmpty()) number else "$number $unit"
        return when (draft.amountKind) {
            RecordAmountKind.DELTA -> "增量 $amount"
            RecordAmountKind.SNAPSHOT -> "累计快照 $amount"
            RecordAmountKind.NONE -> amount
        }
    }
}
