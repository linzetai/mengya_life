package com.mengyalife.app.data.local.growth

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GrowthSignalDao {

    @Query(
        """
        SELECT * FROM growth_signals
        WHERE source_type = 'DIARY' AND source_id = :diaryId
          AND status IN ('ACTIVE', 'CORRECTED')
        ORDER BY extracted_at ASC, id ASC
        """
    )
    fun observeForDiary(diaryId: Long): Flow<List<GrowthSignalEntity>>

    @Query(
        """
        SELECT * FROM growth_signals
        WHERE source_type = 'DIARY' AND source_id = :diaryId
          AND status IN ('ACTIVE', 'CORRECTED')
        ORDER BY extracted_at ASC, id ASC
        """
    )
    suspend fun loadForDiary(diaryId: Long): List<GrowthSignalEntity>

    @Query(
        """
        SELECT * FROM growth_signals
        WHERE status IN ('ACTIVE', 'CORRECTED')
        ORDER BY extracted_at DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun loadActive(limit: Int): List<GrowthSignalEntity>

    @Query("SELECT * FROM growth_signals WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): GrowthSignalEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(signal: GrowthSignalEntity): Long

    @Query(
        """
        UPDATE growth_signals
        SET content = :content, is_inference = 0, status = 'CORRECTED',
            origin = 'USER', updated_at = :now
        WHERE id = :id AND status IN ('ACTIVE', 'CORRECTED')
        """
    )
    suspend fun correct(id: String, content: String, now: Long): Int

    @Query(
        """
        UPDATE growth_signals SET status = 'DELETED', updated_at = :now
        WHERE id = :id AND status IN ('ACTIVE', 'CORRECTED')
        """
    )
    suspend fun softDelete(id: String, now: Long): Int

    @Query(
        """
        UPDATE growth_signals SET status = 'STALE', updated_at = :now
        WHERE source_type = 'DIARY' AND source_id = :diaryId
          AND status IN ('ACTIVE', 'CORRECTED')
        """
    )
    suspend fun markStaleForDiary(diaryId: Long, now: Long): Int

    @Query(
        """
        UPDATE growth_signals SET status = 'STALE', updated_at = :now
        WHERE source_type = 'DIARY' AND source_id = :diaryId
          AND source_version != :currentVersion
          AND status IN ('ACTIVE', 'CORRECTED')
        """
    )
    suspend fun markStaleOtherVersions(diaryId: Long, currentVersion: String, now: Long): Int

    @Query("DELETE FROM growth_signals WHERE source_type = 'DIARY' AND source_id = :diaryId")
    suspend fun deleteForDiary(diaryId: Long): Int

    @Query("SELECT COUNT(*) FROM growth_signals WHERE status IN ('ACTIVE', 'CORRECTED')")
    suspend fun countActive(): Int
}
