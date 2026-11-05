package com.mengyalife.app.data.local.weekly

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklyReviewDao {
    @Query("SELECT * FROM weekly_reviews ORDER BY period_start DESC, generated_at DESC LIMIT 1")
    fun observeLatest(): Flow<WeeklyReviewEntity?>

    @Query("SELECT * FROM weekly_reviews ORDER BY period_start ASC, id ASC")
    suspend fun loadAll(): List<WeeklyReviewEntity>

    @Query("SELECT * FROM weekly_reviews ORDER BY period_start DESC, generated_at DESC LIMIT 1")
    suspend fun latest(): WeeklyReviewEntity?

    @Query("SELECT * FROM weekly_reviews WHERE period_key = :key LIMIT 1")
    suspend fun findByPeriod(key: String): WeeklyReviewEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(review: WeeklyReviewEntity)

    @Query("UPDATE weekly_reviews SET notified_at = :at WHERE period_key = :key AND notified_at IS NULL")
    suspend fun tryClaimNotification(key: String, at: Long): Int

    @Query("UPDATE weekly_reviews SET notified_at = NULL WHERE period_key = :key AND notified_at = :at")
    suspend fun releaseNotification(key: String, at: Long): Int

    @Query("UPDATE weekly_reviews SET notified_at = :at WHERE period_key = :key")
    suspend fun markNotified(key: String, at: Long)
}
