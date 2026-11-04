package com.mengyalife.app.data.local.growth

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "growth_memories",
    indices = [
        Index(value = ["theme"]),
        Index(value = ["status"]),
        Index(value = ["theme", "fingerprint"]),
    ],
)
data class GrowthMemoryEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "theme") val theme: String,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "is_inference") val isInference: Boolean,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "signal_ids_json") val signalIdsJson: String,
    @ColumnInfo(name = "source_diary_ids_json") val sourceDiaryIdsJson: String,
    @ColumnInfo(name = "user_corrected") val userCorrected: Boolean,
    @ColumnInfo(name = "version") val version: Int,
    @ColumnInfo(name = "confidence") val confidence: Double?,
    @ColumnInfo(name = "insufficient") val insufficient: Boolean,
    @ColumnInfo(name = "recent_change") val recentChange: String?,
    @ColumnInfo(name = "fingerprint") val fingerprint: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)
