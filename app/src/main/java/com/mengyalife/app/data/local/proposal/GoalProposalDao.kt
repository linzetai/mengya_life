package com.mengyalife.app.data.local.proposal

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalProposalDao {
    @Query("SELECT * FROM goal_proposals ORDER BY created_at ASC, id ASC")
    suspend fun loadAll(): List<GoalProposalEntity>

    @Query("SELECT * FROM goal_proposals ORDER BY created_at ASC, id ASC")
    fun observeAll(): Flow<List<GoalProposalEntity>>

    @Query("SELECT * FROM goal_proposals WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): GoalProposalEntity?

    @Query("SELECT * FROM goal_proposals WHERE message_id = :messageId LIMIT 1")
    suspend fun findByMessage(messageId: String): GoalProposalEntity?

    @Insert
    suspend fun insert(proposal: GoalProposalEntity)

    /** 只允许 DRAFT 更新节点/选择；返回 0 表示状态已变化。 */
    @Query(
        """
        UPDATE goal_proposals
        SET nodes_json = :nodesJson, selected_temp_ids_json = :selectedJson
        WHERE id = :id AND status = 'DRAFT'
        """
    )
    suspend fun updateDraft(id: String, nodesJson: String, selectedJson: String?): Int

    /** 原子状态迁移：只有 DRAFT 才能变成 APPLIED。 */
    @Query(
        """
        UPDATE goal_proposals
        SET status = 'APPLIED', applied_at = :appliedAt,
            applied_goal_ids_json = :goalIdsJson,
            selected_temp_ids_json = :selectedJson
        WHERE id = :id AND status = 'DRAFT'
        """
    )
    suspend fun markAppliedIfDraft(
        id: String,
        appliedAt: Long,
        goalIdsJson: String,
        selectedJson: String,
    ): Int

    /** 原子状态迁移：只有 APPLIED 才能变成 UNDONE。 */
    @Query(
        """
        UPDATE goal_proposals
        SET status = 'UNDONE', undone_at = :undoneAt
        WHERE id = :id AND status = 'APPLIED'
        """
    )
    suspend fun markUndoneIfApplied(id: String, undoneAt: Long): Int

    @Query("DELETE FROM goal_proposals WHERE id = :id")
    suspend fun deleteById(id: String): Int
}
