package com.mengyalife.app.data.local.weekly

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklyAiDao {
    @Query("SELECT * FROM weekly_ai_reviews ORDER BY generated_at DESC LIMIT 1")
    fun observeLatest(): Flow<WeeklyAiEntity?>

    @Query("SELECT * FROM weekly_ai_reviews WHERE period_key = :key LIMIT 1")
    suspend fun findByPeriod(key: String): WeeklyAiEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(row: WeeklyAiEntity): Long

    @Query("UPDATE weekly_ai_reviews SET status='SENT' WHERE period_key=:key AND status='PENDING'")
    suspend fun markSent(key: String): Int

    @Query("DELETE FROM weekly_ai_reviews WHERE period_key=:key AND status='PENDING'")
    suspend fun deletePending(key: String): Int

    @Query(
        """
        UPDATE weekly_ai_reviews SET status=:status, generated_at=:now, summary=:summary,
        highlights_json=:highlights, suggestion=:suggestion, fact_ids_json=:factIds,
        error=:error, model=:model, config_hash=:configHash WHERE period_key=:key
        """
    )
    suspend fun complete(
        key: String, status: String, now: Long, summary: String, highlights: String,
        suggestion: String, factIds: String, error: String?, model: String?, configHash: String,
    )
}
