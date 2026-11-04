package com.mengyalife.app.data.local.growth

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GrowthMemoryDao {
    @Query("SELECT * FROM growth_memories ORDER BY updated_at DESC, id DESC")
    fun observeAll(): Flow<List<GrowthMemoryEntity>>

    @Query("SELECT * FROM growth_memories ORDER BY updated_at DESC, id DESC")
    suspend fun loadAll(): List<GrowthMemoryEntity>

    @Query("SELECT * FROM growth_memories WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): GrowthMemoryEntity?

    @Query(
        """
        SELECT * FROM growth_memories
        WHERE theme = :theme AND status IN ('ACTIVE', 'CORRECTED')
        ORDER BY updated_at DESC LIMIT 1
        """
    )
    suspend fun findActiveByTheme(theme: String): GrowthMemoryEntity?

    @Query(
        """
        SELECT * FROM growth_memories
        WHERE theme = :theme AND fingerprint = :fingerprint AND status = 'DELETED'
        LIMIT 1
        """
    )
    suspend fun findDeleted(theme: String, fingerprint: String): GrowthMemoryEntity?

    @Insert
    suspend fun insert(memory: GrowthMemoryEntity)

    @Update
    suspend fun update(memory: GrowthMemoryEntity): Int

    @Query(
        """
        UPDATE growth_memories
        SET content = :content, status = 'CORRECTED', user_corrected = 1,
            is_inference = 0, version = version + 1, updated_at = :now
        WHERE id = :id AND status IN ('ACTIVE', 'CORRECTED')
        """
    )
    suspend fun correct(id: String, content: String, now: Long): Int

    @Query(
        """
        UPDATE growth_memories SET status = 'DELETED', deleted_at = :now, updated_at = :now
        WHERE id = :id AND status IN ('ACTIVE', 'CORRECTED')
        """
    )
    suspend fun softDelete(id: String, now: Long): Int

    @Query(
        """
        UPDATE growth_memories SET status = 'STALE', updated_at = :now
        WHERE theme = :theme AND status IN ('ACTIVE', 'CORRECTED')
        """
    )
    suspend fun markStaleByTheme(theme: String, now: Long): Int
}
