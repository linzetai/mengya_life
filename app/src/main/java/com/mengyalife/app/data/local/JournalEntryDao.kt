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

    /**
     * 导出用：一次性读出全部日记（按 id 升序，保证同一份数据的输出顺序稳定）。
     * 导出在数据库事务里调用，因此与其他表的读取处于同一个一致性快照。
     */
    @Query("SELECT * FROM journal_entries ORDER BY id ASC")
    suspend fun loadAll(): List<JournalEntryEntity>

    @Insert
    suspend fun insert(entry: JournalEntryEntity): Long

    @Update
    suspend fun update(entry: JournalEntryEntity): Int

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
