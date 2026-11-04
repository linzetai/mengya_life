package com.mengyalife.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 日记条目（数据库 v1 唯一实表）。
 *
 * 字段约定：
 * - [id]：自增主键，稳定标识；
 * - [title]：可选标题，空串一律存为 null，避免出现「有标题但没内容」的脏数据；
 * - [body]：正文，必填，写入前已 trim；
 * - 时间戳统一用 **Unix Epoch 毫秒（UTC 语义，与时区无关）**，
 *   展示时再由界面按设备时区格式化，绝不存本地时间字符串。
 */
@Entity(
    tableName = "journal_entries",
    indices = [Index(value = ["updated_at"])],
)
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "title")
    val title: String? = null,

    @ColumnInfo(name = "body")
    val body: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    /** PENDING / ANALYZING / COMPLETED / FAILED */
    @ColumnInfo(name = "analysis_status")
    val analysisStatus: String = "PENDING",

    /** 最近一次成功分析的来源内容哈希。 */
    @ColumnInfo(name = "analysis_hash")
    val analysisHash: String? = null,

    @ColumnInfo(name = "analysis_error")
    val analysisError: String? = null,

    @ColumnInfo(name = "analysis_attempts")
    val analysisAttempts: Int = 0,

    @ColumnInfo(name = "analyzed_at")
    val analyzedAt: Long? = null,

    @ColumnInfo(name = "analysis_last_attempt_at")
    val analysisLastAttemptAt: Long? = null,
)
