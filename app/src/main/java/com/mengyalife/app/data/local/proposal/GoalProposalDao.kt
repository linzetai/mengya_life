package com.mengyalife.app.data.local.proposal

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalProposalDao {
    @Query("SELECT * FROM goal_proposals ORDER BY created_at ASC, id ASC")
    fun observeAll(): Flow<List<GoalProposalEntity>>

    @Query("SELECT * FROM goal_proposals WHERE message_id = :messageId LIMIT 1")
    suspend fun findByMessage(messageId: String): GoalProposalEntity?

    @Insert
    suspend fun insert(proposal: GoalProposalEntity)

    @Query("DELETE FROM goal_proposals WHERE id = :id")
    suspend fun deleteById(id: String): Int
}
