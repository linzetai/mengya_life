package com.mengyalife.app.data.local.proposal

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AI 目标草案（数据库 v4 新增，v5 增加应用/撤销字段）。
 *
 * 只保存草案 JSON；它不是正式目标，**不会**直接写 goals / goal_checkins。
 * 写入必须经 GoalProposalApplyService 的用户确认与 Room 事务。
 */
@Entity(
    tableName = "goal_proposals",
    indices = [Index(value = ["message_id"])],
)
data class GoalProposalEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "schema_version")
    val schemaVersion: Int,

    /** DRAFT / APPLIED / UNDONE */
    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "summary")
    val summary: String,

    @ColumnInfo(name = "assumptions_json")
    val assumptionsJson: String,

    @ColumnInfo(name = "risks_json")
    val risksJson: String,

    @ColumnInfo(name = "nodes_json")
    val nodesJson: String,

    /** 用户最后确认的节点 tempId 集合。 */
    @ColumnInfo(name = "selected_temp_ids_json")
    val selectedTempIdsJson: String? = null,

    /** 应用时间。 */
    @ColumnInfo(name = "applied_at")
    val appliedAt: Long? = null,

    /** 本次创建的正式目标 ID（JSON 数组）。 */
    @ColumnInfo(name = "applied_goal_ids_json")
    val appliedGoalIdsJson: String? = null,

    /** 撤销时间。 */
    @ColumnInfo(name = "undone_at")
    val undoneAt: Long? = null,
)
