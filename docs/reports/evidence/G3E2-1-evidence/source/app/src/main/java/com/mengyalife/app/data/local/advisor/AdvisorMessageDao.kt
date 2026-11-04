package com.mengyalife.app.data.local.advisor

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AdvisorMessageDao {

    /** 单会话：按时间升序；同一毫秒用 id 兜底，保证顺序稳定。 */
    @Query("SELECT * FROM advisor_messages ORDER BY created_at ASC, id ASC")
    fun observeAll(): Flow<List<AdvisorMessageEntity>>

    @Query("SELECT * FROM advisor_messages ORDER BY created_at ASC, id ASC")
    suspend fun loadAll(): List<AdvisorMessageEntity>

    @Insert
    suspend fun insert(message: AdvisorMessageEntity)

    @Query("SELECT MAX(created_at) FROM advisor_messages")
    suspend fun maxCreatedAt(): Long?

    @Query("DELETE FROM advisor_messages WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM advisor_messages")
    suspend fun deleteAll(): Int
}
