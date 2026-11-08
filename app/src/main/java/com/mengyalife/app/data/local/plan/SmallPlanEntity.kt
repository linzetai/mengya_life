package com.mengyalife.app.data.local.plan

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 对话中确认的小计划；不是目标、也不是已完成进展。DB v17。 */
@Entity(
    tableName = "small_plans",
    indices = [Index(value = ["status"]), Index(value = ["updated_at"])],
)
data class SmallPlanEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "time_scope") val timeScope: String?,
    @ColumnInfo(name = "goal_id") val goalId: Long?,
    /** ACTIVE / COMPLETED / PAUSED / CANCELLED */
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "source_message_id") val sourceMessageId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** 计划 изменения статуса：只追加，不覆盖历史。 */
@Entity(tableName = "plan_events", indices = [Index(value = ["plan_id"])])
data class PlanEventEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "plan_id") val planId: Long,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** 计划的一次执行记录（0.21）；来源消息唯一，防重复提交。 */
@Entity(
    tableName = "plan_progress",
    indices = [Index(value = ["plan_id"]), Index(value = ["source_message_id"], unique = true)],
)
data class PlanProgressEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "plan_id") val planId: Long,
    @ColumnInfo(name = "note") val note: String,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long,
    @ColumnInfo(name = "source_message_id") val sourceMessageId: String?,
)

data class PlanProgressCount(
    @androidx.room.ColumnInfo(name = "plan_id") val planId: Long,
    @androidx.room.ColumnInfo(name = "total") val total: Int,
)

@Dao
interface SmallPlanDao {
    @Query("SELECT * FROM small_plans ORDER BY updated_at DESC, id DESC")
    fun observeAll(): Flow<List<SmallPlanEntity>>

    @Query("SELECT * FROM small_plans WHERE status = 'ACTIVE' ORDER BY updated_at DESC, id DESC")
    suspend fun loadActive(): List<SmallPlanEntity>

    @Query("SELECT * FROM small_plans ORDER BY updated_at DESC, id DESC")
    suspend fun loadAll(): List<SmallPlanEntity>

    @Query("SELECT * FROM small_plans WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): SmallPlanEntity?

    @Query("SELECT * FROM small_plans WHERE status = :status ORDER BY updated_at DESC, id DESC")
    fun observeByStatus(status: String): Flow<List<SmallPlanEntity>>

    @Insert
    suspend fun insert(plan: SmallPlanEntity): Long

    @Query("UPDATE small_plans SET status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, updatedAt: Long): Int

    @Insert
    suspend fun insertEvent(event: PlanEventEntity): Long

    @Query("SELECT * FROM plan_events ORDER BY id ASC")
    suspend fun loadAllEvents(): List<PlanEventEntity>

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertProgress(progress: PlanProgressEntity): Long

    @Query("SELECT COUNT(*) FROM plan_progress WHERE plan_id = :planId")
    suspend fun countProgress(planId: Long): Int

    @Query("SELECT * FROM plan_progress ORDER BY recorded_at ASC, id ASC")
    suspend fun loadAllProgress(): List<PlanProgressEntity>

    @Query("SELECT plan_id AS plan_id, COUNT(*) AS total FROM plan_progress GROUP BY plan_id")
    fun observeProgressCounts(): Flow<List<PlanProgressCount>>

    @Query("SELECT * FROM plan_events WHERE plan_id = :planId ORDER BY created_at DESC, id DESC")
    suspend fun eventsFor(planId: Long): List<PlanEventEntity>
}
