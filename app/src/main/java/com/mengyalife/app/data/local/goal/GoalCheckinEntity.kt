package com.mengyalife.app.data.local.goal

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 目标进展记录（数据库 v2 新增）。
 *
 * 只记录**用户手动录入**的进展：不做推断、不生成"人生评分"。
 * 数值可选——非数值目标也可以记录，只是不展示数值图表。
 */
@Entity(
    tableName = "goal_checkins",
    indices = [Index(value = ["goal_id"])],
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goal_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class GoalCheckinEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "goal_id")
    val goalId: Long,

    /** 记录时间（Epoch 毫秒）。 */
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long,

    @ColumnInfo(name = "note")
    val note: String,

    /** 可选的实际指标值，例如 3.5。 */
    @ColumnInfo(name = "metric_value")
    val metricValue: Double? = null,

    /** 指标单位，例如 "次/周"、"小时"。 */
    @ColumnInfo(name = "metric_unit")
    val metricUnit: String? = null,

    /** 来源。本轮固定 manual，保留字段以便未来区分导入数据。 */
    @ColumnInfo(name = "source")
    val source: String,

    /** 进展语义：delta / snapshot / none（v14 新增；历史行迁移为 snapshot）。 */
    @ColumnInfo(name = "amount_kind", defaultValue = "snapshot")
    val amountKind: String = "snapshot",

    /** 来源原话：顾问记录时保留用户原句，便于追溯；手动记录为空。 */
    @ColumnInfo(name = "original_text")
    val originalText: String? = null,

    /** 归一到目标既有单位后的数值（仅当单位可可靠换算/继承时填写）。 */
    @ColumnInfo(name = "normalized_value")
    val normalizedValue: Double? = null,

    /** 归一后的单位；与 [metricUnit] 不同表示做过换算。 */
    @ColumnInfo(name = "normalized_unit")
    val normalizedUnit: String? = null,
)
