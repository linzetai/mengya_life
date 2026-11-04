package com.mengyalife.app.data.local.growth

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "growth_signals",
    indices = [
        Index(value = ["source_type", "source_id"]),
        Index(value = ["source_id", "status"]),
        Index(
            value = ["source_type", "source_id", "source_version", "content_hash"],
            unique = true,
        ),
    ],
)
data class GrowthSignalEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "source_type")
    val sourceType: String,

    @ColumnInfo(name = "source_id")
    val sourceId: Long,

    /** 来源内容版本（日记 title+body 的 SHA-256）。 */
    @ColumnInfo(name = "source_version")
    val sourceVersion: String,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "evidence")
    val evidence: String,

    @ColumnInfo(name = "is_inference")
    val isInference: Boolean,

    @ColumnInfo(name = "confidence")
    val confidence: Double?,

    @ColumnInfo(name = "extracted_at")
    val extractedAt: Long,

    /** ACTIVE / CORRECTED / DELETED / STALE */
    @ColumnInfo(name = "status")
    val status: String,

    /** AI / USER */
    @ColumnInfo(name = "origin")
    val origin: String,

    @ColumnInfo(name = "source_title")
    val sourceTitle: String?,

    @ColumnInfo(name = "content_hash")
    val contentHash: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
