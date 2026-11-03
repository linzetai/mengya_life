package com.mengyalife.app.data.local.goal

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** 某个目标的进展聚合统计。 */
data class GoalCheckinStat(
    @androidx.room.ColumnInfo(name = "goal_id") val goalId: Long,
    @androidx.room.ColumnInfo(name = "checkin_count") val checkinCount: Int,
    @androidx.room.ColumnInfo(name = "last_at") val lastAt: Long?,
)

@Dao
interface GoalDao {

    @Query("SELECT * FROM goals ORDER BY level ASC, created_at ASC, id ASC")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Query(
        """
        SELECT goal_id AS goal_id,
               COUNT(*) AS checkin_count,
               MAX(recorded_at) AS last_at
        FROM goal_checkins
        GROUP BY goal_id
        """
    )
    fun observeCheckinStats(): Flow<List<GoalCheckinStat>>

    @Query("SELECT * FROM goal_checkins WHERE goal_id = :goalId ORDER BY recorded_at DESC, id DESC")
    fun observeCheckins(goalId: Long): Flow<List<GoalCheckinEntity>>

    @Query("SELECT * FROM goal_checkins ORDER BY recorded_at DESC, id DESC LIMIT :limit")
    fun observeRecentCheckins(limit: Int): Flow<List<GoalCheckinEntity>>

    @Query("SELECT * FROM goals WHERE id = :id LIMIT 1")
    suspend fun findGoal(id: Long): GoalEntity?

    @Query("SELECT * FROM goals")
    suspend fun loadAllGoals(): List<GoalEntity>

    @Insert
    suspend fun insertGoal(goal: GoalEntity): Long

    @Update
    suspend fun updateGoal(goal: GoalEntity): Int

    @Query("DELETE FROM goals WHERE id IN (:ids)")
    suspend fun deleteGoalsByIds(ids: List<Long>): Int

    @Insert
    suspend fun insertCheckin(checkin: GoalCheckinEntity): Long

    @Query("DELETE FROM goal_checkins WHERE goal_id IN (:goalIds)")
    suspend fun deleteCheckinsForGoals(goalIds: List<Long>): Int

    @Query("SELECT COUNT(*) FROM goal_checkins WHERE goal_id IN (:goalIds)")
    suspend fun countCheckinsForGoals(goalIds: List<Long>): Int

    /**
     * 删除整棵目标树：先删进展记录，再删目标。
     * 放在事务里，避免中途失败留下"孤儿进展"。
     */
    @Transaction
    suspend fun deleteGoalTree(ids: List<Long>) {
        if (ids.isEmpty()) return
        deleteCheckinsForGoals(ids)
        deleteGoalsByIds(ids)
    }
}
