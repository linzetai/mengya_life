package com.mengyalife.app.data.local.proposal

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordProposalDao {
    @Query("SELECT * FROM record_proposals ORDER BY created_at ASC, id ASC")
    fun observeAll(): Flow<List<RecordProposalEntity>>

    @Query("SELECT * FROM record_proposals ORDER BY created_at ASC, id ASC")
    suspend fun loadAll(): List<RecordProposalEntity>

    @Query("SELECT * FROM record_proposals WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): RecordProposalEntity?

    @Query("SELECT * FROM record_proposals WHERE message_id = :messageId LIMIT 1")
    suspend fun findByMessage(messageId: String): RecordProposalEntity?

    /** message_id 唯一：重复提取不产生第二条候选。 */
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insert(proposal: RecordProposalEntity): Long

    @Query(
        """
        UPDATE record_proposals
        SET goal_id = :goalId, goal_title = :goalTitle, goal_candidates_json = :candidatesJson,
            amount_kind = :amountKind, metric_value = :metricValue, metric_unit = :metricUnit,
            note = :note, occurred_at = :occurredAt, pending_fields_json = :pendingJson,
            diary_title = :diaryTitle, diary_body = :diaryBody
        WHERE id = :id AND status = 'DRAFT'
        """
    )
    suspend fun updateDraft(
        id: String,
        goalId: Long?,
        goalTitle: String?,
        candidatesJson: String?,
        amountKind: String,
        metricValue: Double?,
        metricUnit: String?,
        note: String,
        occurredAt: Long?,
        pendingJson: String?,
        diaryTitle: String?,
        diaryBody: String?,
    ): Int

    /** 原子状态迁移：只有 DRAFT 才能变成 APPLIED。 */
    @Query(
        """
        UPDATE record_proposals
        SET status = 'APPLIED', applied_at = :appliedAt, applied_checkin_id = :checkinId,
            goal_id = :goalId, goal_title = :goalTitle,
            amount_kind = :amountKind, metric_value = :metricValue, metric_unit = :metricUnit,
            note = :note, occurred_at = :occurredAt, pending_fields_json = NULL
        WHERE id = :id AND status = 'DRAFT'
        """
    )
    suspend fun markAppliedIfDraft(
        id: String,
        appliedAt: Long,
        checkinId: Long,
        goalId: Long,
        goalTitle: String?,
        amountKind: String,
        metricValue: Double?,
        metricUnit: String?,
        note: String,
        occurredAt: Long?,
    ): Int

    /** 修改已写入的记录：只更新字段，不改变 APPLIED 状态与 checkin 关联。 */
    @Query(
        """
        UPDATE record_proposals
        SET goal_id = :goalId, goal_title = :goalTitle, amount_kind = :amountKind,
            metric_value = :metricValue, metric_unit = :metricUnit, note = :note,
            occurred_at = :occurredAt
        WHERE id = :id AND status = 'APPLIED'
        """
    )
    suspend fun updateAppliedFields(
        id: String,
        goalId: Long,
        goalTitle: String?,
        amountKind: String,
        metricValue: Double?,
        metricUnit: String?,
        note: String,
        occurredAt: Long?,
    ): Int

    /** 日记正式保存：原子迁移并记录 journal_entries.id。 */
    @Query(
        """
        UPDATE record_proposals
        SET status = 'APPLIED', applied_at = :appliedAt, diary_entry_id = :entryId,
            diary_title = :title, diary_body = :body, note = :body, pending_fields_json = NULL
        WHERE id = :id AND status = 'DRAFT'
        """
    )
    suspend fun markDiaryAppliedIfDraft(
        id: String,
        appliedAt: Long,
        entryId: Long,
        title: String?,
        body: String,
    ): Int

    @Query(
        """
        UPDATE record_proposals
        SET diary_title = :title, diary_body = :body, note = :body
        WHERE id = :id AND status = 'APPLIED' AND diary_entry_id IS NOT NULL
        """
    )
    suspend fun updateDiaryApplied(id: String, title: String?, body: String): Int

    /** 目标候选正式写入：记录创建/更新的目标 id。 */
    @Query(
        """
        UPDATE record_proposals
        SET status = 'APPLIED', applied_at = :appliedAt,
            applied_goal_ids_json = :goalIdsJson, goal_change_json = :goalChangeJson,
            pending_fields_json = NULL
        WHERE id = :id AND status = 'DRAFT'
        """
    )
    suspend fun markGoalAppliedIfDraft(
        id: String,
        appliedAt: Long,
        goalIdsJson: String,
        goalChangeJson: String?,
    ): Int

    @Query(
        """
        UPDATE record_proposals
        SET goal_change_json = :goalChangeJson
        WHERE id = :id AND status = 'APPLIED'
        """
    )
    suspend fun updateGoalApplied(id: String, goalChangeJson: String?): Int

    @Query(
        """
        UPDATE record_proposals
        SET status = 'APPLIED', applied_at = :appliedAt, applied_plan_id = :planId,
            plan_change_json = :planChangeJson, pending_fields_json = NULL
        WHERE id = :id AND status = 'DRAFT'
        """
    )
    suspend fun markPlanAppliedIfDraft(
        id: String,
        appliedAt: Long,
        planId: Long,
        planChangeJson: String?,
    ): Int

    @Query("UPDATE record_proposals SET status = 'CANCELLED' WHERE id = :id AND status = 'DRAFT'")
    suspend fun markCancelledIfDraft(id: String): Int

    /** 原子撤销：只有 APPLIED 才能变成 UNDONE；返回旧 checkinId 供删除。 */
    @Query(
        """
        UPDATE record_proposals SET status = 'UNDONE', undone_at = :undoneAt
        WHERE id = :id AND status = 'APPLIED'
        """
    )
    suspend fun markUndoneIfApplied(id: String, undoneAt: Long): Int

    @Query("SELECT * FROM record_proposals WHERE applied_checkin_id IS NOT NULL")
    suspend fun loadApplied(): List<RecordProposalEntity>
}
