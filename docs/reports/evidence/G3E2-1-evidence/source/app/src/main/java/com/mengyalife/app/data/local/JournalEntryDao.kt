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

    /** 冷启动时把上次进程遗留的 ANALYZING 标记为失败（前台提取不会跨进程存活）。 */
    @Query(
        """
        UPDATE journal_entries
        SET analysis_status = 'FAILED', analysis_error = '分析被中断，请重试'
        WHERE analysis_status = 'ANALYZING'
        """
    )
    suspend fun markInterruptedAnalyses(): Int

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

    /**
     * 原子进入 ANALYZING：只有内容版本仍是 [expectedUpdatedAt] 且当前不是 ANALYZING 才成功。
     * 返回 0 表示记录已变化/已有提取在进行。
     */
    @Query(
        """
        UPDATE journal_entries
        SET analysis_status = 'ANALYZING', analysis_hash = :hash,
            analysis_error = NULL, analysis_attempts = analysis_attempts + 1,
            analysis_last_attempt_at = :now
        WHERE id = :id AND updated_at = :expectedUpdatedAt
          AND analysis_status != 'ANALYZING'
        """
    )
    suspend fun markAnalyzing(
        id: Long,
        expectedUpdatedAt: Long,
        hash: String,
        now: Long,
    ): Int

    @Query(
        """
        UPDATE journal_entries
        SET analysis_status = 'COMPLETED', analysis_hash = :hash,
            analysis_error = NULL, analyzed_at = :now,
            analysis_last_attempt_at = :now
        WHERE id = :id AND analysis_hash = :hash AND analysis_status = 'ANALYZING'
        """
    )
    suspend fun markCompleted(id: Long, hash: String, now: Long): Int

    @Query(
        """
        UPDATE journal_entries
        SET analysis_status = 'FAILED', analysis_error = :error,
            analysis_hash = :hash, analysis_last_attempt_at = :now
        WHERE id = :id AND analysis_hash = :hash AND analysis_status = 'ANALYZING'
        """
    )
    suspend fun markFailed(id: Long, hash: String, error: String, now: Long): Int
}
