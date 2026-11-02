package com.mengyalife.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 日记 DAO。
 *
 * 列表固定按 `updated_at DESC` 排序：
 * 刚编辑过的日记会回到列表顶部，符合「最近改动优先查看」的直觉。
 */
@Dao
interface JournalEntryDao {

    @Query("SELECT * FROM journal_entries ORDER BY updated_at DESC, id DESC")
    fun observeAll(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): JournalEntryEntity?

    @Query("SELECT COUNT(*) FROM journal_entries")
    suspend fun count(): Int

    @Insert
    suspend fun insert(entry: JournalEntryEntity): Long

    @Update
    suspend fun update(entry: JournalEntryEntity): Int

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
