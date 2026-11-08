package com.mengyalife.app.data.local.proposal

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 对话式记录候选（数据库 v13 新增）。
 *
 * 只保存结构化候选与状态；**不**直接写 goal_checkins。
 * 正式写入必须经 ProgressRecordApplyService 的确认与 Room 事务。
 * message_id 唯一，避免同一轮回复重复生成候选。
 */
@Entity(
    tableName = "record_proposals",
    indices = [
        Index(value = ["message_id"], unique = true),
        Index(value = ["status"]),
    ],
)
data class RecordProposalEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /** DRAFT / APPLIED / CANCELLED / UNDONE */
    @ColumnInfo(name = "status")
    val status: String,

    /** PROGRESS / DIARY */
    @ColumnInfo(name = "kind")
    val kind: String,

    @ColumnInfo(name = "goal_id")
    val goalId: Long?,

    @ColumnInfo(name = "goal_title")
    val goalTitle: String?,

    @ColumnInfo(name = "goal_candidates_json")
    val goalCandidatesJson: String?,

    /** DELTA / SNAPSHOT / NONE */
    @ColumnInfo(name = "amount_kind")
    val amountKind: String,

    @ColumnInfo(name = "metric_value")
    val metricValue: Double?,

    @ColumnInfo(name = "metric_unit")
    val metricUnit: String?,

    @ColumnInfo(name = "note")
    val note: String,

    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long?,

    @ColumnInfo(name = "pending_fields_json")
    val pendingFieldsJson: String?,

    @ColumnInfo(name = "intent")
    val intent: String,

    @ColumnInfo(name = "original_text")
    val originalText: String,

    /** 正式写入后的 goal_checkins.id，用于修改与撤销。 */
    @ColumnInfo(name = "applied_checkin_id")
    val appliedCheckinId: Long? = null,

    // ---- 0.17 日记草稿（v15） ----
    @ColumnInfo(name = "diary_title")
    val diaryTitle: String? = null,

    @ColumnInfo(name = "diary_body")
    val diaryBody: String? = null,

    /** 正式保存后的 journal_entries.id；修改时保持同一篇。 */
    @ColumnInfo(name = "diary_entry_id")
    val diaryEntryId: Long? = null,

    @ColumnInfo(name = "source_message_ids_json")
    val sourceMessageIdsJson: String? = null,

    // ---- 0.18 目标变更（v16） ----
    @ColumnInfo(name = "goal_change_json")
    val goalChangeJson: String? = null,

    @ColumnInfo(name = "applied_goal_ids_json")
    val appliedGoalIdsJson: String? = null,

    @ColumnInfo(name = "plan_change_json")
    val planChangeJson: String? = null,

    @ColumnInfo(name = "plan_progress_json")
    val planProgressJson: String? = null,

    @ColumnInfo(name = "applied_plan_id")
    val appliedPlanId: Long? = null,

    @ColumnInfo(name = "applied_at")
    val appliedAt: Long? = null,

    @ColumnInfo(name = "undone_at")
    val undoneAt: Long? = null,
)
