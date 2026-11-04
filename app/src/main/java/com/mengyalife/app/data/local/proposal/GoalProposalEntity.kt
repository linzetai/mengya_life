package com.mengyalife.app.data.local.proposal

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AI 目标草案（数据库 v4 新增）。
 *
 * 只保存草案 JSON；它不是正式目标，**不会**写入 goals / goal_checkins。
 * `status` 本轮固定 DRAFT，留给 Gate 3B-2 做确认写入。
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
)
