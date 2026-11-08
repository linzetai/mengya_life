package com.mengyalife.app.data.local.care

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 关心机会（0.22）；候选不等于联系用户。 */
@Entity(
    tableName = "care_opportunities",
    indices = [Index(value = ["dedupe_key"], unique = true), Index(value = ["status"])],
)
data class CareOpportunityEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "source_id") val sourceId: Long,
    @ColumnInfo(name = "plan_id") val planId: Long?,
    @ColumnInfo(name = "goal_id") val goalId: Long?,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "discovered_at") val discoveredAt: Long,
    @ColumnInfo(name = "window_start") val windowStart: Long,
    @ColumnInfo(name = "window_end") val windowEnd: Long,
    @ColumnInfo(name = "expires_at") val expiresAt: Long,
    @ColumnInfo(name = "sensitivity") val sensitivity: String,
    /** PENDING / PLANNED / SENT / DISMISSED / DROPPED / EXPIRED */
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "dedupe_key") val dedupeKey: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** AI 决策日志，可追溯；SCHEDULE/DEFER 带下一次窗口。 */
@Entity(tableName = "care_decisions", indices = [Index(value = ["opportunity_id"])])
data class CareDecisionEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "opportunity_id") val opportunityId: Long,
    @ColumnInfo(name = "action") val action: String,
    @ColumnInfo(name = "reason") val reason: String,
    @ColumnInfo(name = "source_refs") val sourceRefs: String,
    @ColumnInfo(name = "suggested_text") val suggestedText: String?,
    @ColumnInfo(name = "avoid_json") val avoidJson: String?,
    @ColumnInfo(name = "next_window_start") val nextWindowStart: Long?,
    @ColumnInfo(name = "next_window_end") val nextWindowEnd: Long?,
    @ColumnInfo(name = "model_ok") val modelOk: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "message_id") val messageId: String? = null,
    @ColumnInfo(name = "delivered_at") val deliveredAt: Long? = null,
)

/** 用户反馈：一次忽略 ≠ 永久拒绝。 */
@Entity(tableName = "care_feedback", indices = [Index(value = ["opportunity_id"])])
data class CareFeedbackEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "opportunity_id") val opportunityId: Long,
    /** ENGAGED / IGNORED / SILENCED */
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Dao
interface CareDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertOpportunity(entity: CareOpportunityEntity): Long

    @Query("SELECT * FROM care_opportunities WHERE status = 'PENDING' ORDER BY window_end ASC, id ASC")
    suspend fun pendingOpportunities(): List<CareOpportunityEntity>

    @Query("SELECT * FROM care_opportunities WHERE id = :id LIMIT 1")
    suspend fun findOpportunity(id: Long): CareOpportunityEntity?

    @Query("UPDATE care_opportunities SET status = :status, updated_at = :at WHERE id = :id")
    suspend fun updateOpportunityStatus(id: Long, status: String, at: Long)

    /** 原子占用：只有一个 worker 能把 PENDING 变成 SENDING。 */
    @Query("UPDATE care_opportunities SET status = 'SENDING', updated_at = :at WHERE id = :id AND status = 'PENDING'")
    suspend fun claimSending(id: Long, at: Long): Int

    @Query("UPDATE care_decisions SET message_id = :messageId, delivered_at = :at WHERE id = :decisionId")
    suspend fun markDecisionDelivered(decisionId: Long, messageId: String?, at: Long)

    @Insert
    suspend fun insertDecision(entity: CareDecisionEntity): Long

    @Query("SELECT * FROM care_opportunities ORDER BY id ASC")
    suspend fun loadAllOpportunities(): List<CareOpportunityEntity>

    @Query("SELECT * FROM care_feedback ORDER BY id ASC")
    suspend fun allFeedback(): List<CareFeedbackEntity>

    @Query("SELECT * FROM care_decisions WHERE action = 'SEND' ORDER BY created_at DESC LIMIT 1")
    suspend fun latestSentDecision(): CareDecisionEntity?

    @Query("SELECT * FROM care_decisions WHERE opportunity_id = :id ORDER BY created_at DESC LIMIT 1")
    suspend fun latestDecision(id: Long): CareDecisionEntity?

    @Query("SELECT * FROM care_decisions ORDER BY created_at DESC LIMIT 50")
    suspend fun recentDecisions(): List<CareDecisionEntity>

    @Insert
    suspend fun insertFeedback(entity: CareFeedbackEntity): Long

    @Query("SELECT * FROM care_feedback WHERE opportunity_id = :id ORDER BY created_at DESC LIMIT 1")
    suspend fun latestFeedback(id: Long): CareFeedbackEntity?

    @Query("SELECT COUNT(*) FROM care_decisions WHERE created_at >= :since")
    suspend fun decisionCountSince(since: Long): Int

    @Query("SELECT * FROM care_decisions WHERE action = 'SEND' AND created_at >= :since ORDER BY created_at DESC")
    suspend fun sentSince(since: Long): List<CareDecisionEntity>

    @Query("SELECT * FROM care_feedback WHERE kind = 'SILENCED'")
    suspend fun silencedFeedback(): List<CareFeedbackEntity>

    @Query("SELECT * FROM care_decisions WHERE created_at >= :since ORDER BY created_at DESC")
    fun observeDecisionsSince(since: Long): Flow<List<CareDecisionEntity>>
}
