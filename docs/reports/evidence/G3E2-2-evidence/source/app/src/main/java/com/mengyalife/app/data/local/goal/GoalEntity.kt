package com.mengyalife.app.data.local.goal

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalStatus

/**
 * 目标表（数据库 v2 新增）。
 *
 * 层级与父子约束由 [com.mengyalife.app.domain.goals.GoalRules] 在仓库层保证；
 * 数据库层再兜一层外键（父目标被删时子目标级联删除，且删除前 UI 会明确告知影响范围）。
 */
@Entity(
    tableName = "goals",
    indices = [
        Index(value = ["parent_id"]),
        Index(value = ["level"]),
        Index(value = ["status"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class GoalEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    /** LONG_TERM / STAGE / ACTION，以枚举名存 TEXT。 */
    @ColumnInfo(name = "level")
    val level: GoalLevel,

    /** 根目标为 null。 */
    @ColumnInfo(name = "parent_id")
    val parentId: Long? = null,

    @ColumnInfo(name = "domain")
    val domain: GoalDomain,

    @ColumnInfo(name = "status")
    val status: GoalStatus,

    /** 可选开始时间（Epoch 毫秒）。 */
    @ColumnInfo(name = "start_date")
    val startDate: Long? = null,

    /** 可选截止时间（Epoch 毫秒）。 */
    @ColumnInfo(name = "due_date")
    val dueDate: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
