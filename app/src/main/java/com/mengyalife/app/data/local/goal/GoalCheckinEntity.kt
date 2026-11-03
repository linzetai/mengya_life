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
)
