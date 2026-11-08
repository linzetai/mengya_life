package com.mengyalife.app.data.local.care

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 0.24 轻量关心偏好；明确表达与系统推测分开，可解释、可撤销。 */
@Entity(
    tableName = "care_preferences",
    indices = [Index(value = ["kind"]), Index(value = ["topic_key"])],
)
data class CarePreferenceEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    /** TOPIC / TIME / BUSY */
    @ColumnInfo(name = "kind") val kind: String,
    /** plan:12 / goal:3 / topic:读书 / null */
    @ColumnInfo(name = "topic_key") val topicKey: String?,
    @ColumnInfo(name = "topic_label") val topicLabel: String?,
    /** PAUSED / SILENCED / WELCOME / PREFER_WINDOW / BUSY */
    @ColumnInfo(name = "value") val value: String,
    /** USER_EXPLICIT / USER_FEEDBACK */
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "source_message_id") val sourceMessageId: String?,
    @ColumnInfo(name = "source_excerpt") val sourceExcerpt: String?,
    /** 例如 “SUN 20:00” */
    @ColumnInfo(name = "window_note") val windowNote: String?,
    @ColumnInfo(name = "effective_at") val effectiveAt: Long,
    @ColumnInfo(name = "expires_at") val expiresAt: Long?,
    @ColumnInfo(name = "revoked_at") val revokedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Dao
interface CarePreferenceDao {
    @Insert
    suspend fun insert(entity: CarePreferenceEntity): Long

    @Query("SELECT * FROM care_preferences WHERE revoked_at IS NULL AND (expires_at IS NULL OR expires_at > :now) ORDER BY created_at DESC")
    suspend fun active(now: Long): List<CarePreferenceEntity>

    @Query("SELECT * FROM care_preferences ORDER BY created_at DESC")
    suspend fun all(): List<CarePreferenceEntity>

    @Query("UPDATE care_preferences SET revoked_at = :at, updated_at = :at WHERE topic_key = :key AND revoked_at IS NULL")
    suspend fun revokeByTopic(key: String, at: Long)

    @Query("UPDATE care_preferences SET revoked_at = :at, updated_at = :at WHERE id = :id")
    suspend fun revoke(id: Long, at: Long)

    @Query("SELECT * FROM care_preferences WHERE topic_key = :key AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at > :now) ORDER BY created_at DESC LIMIT 1")
    suspend fun activeForTopic(key: String, now: Long): CarePreferenceEntity?

    @Query("SELECT * FROM care_preferences WHERE kind = 'TIME' AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at > :now) ORDER BY created_at DESC LIMIT 1")
    suspend fun activeTimePreference(now: Long): CarePreferenceEntity?

    @Query("SELECT * FROM care_preferences WHERE kind = 'BUSY' AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at > :now) ORDER BY created_at DESC LIMIT 1")
    suspend fun activeBusy(now: Long): CarePreferenceEntity?

    @Query("SELECT * FROM care_preferences WHERE revoked_at IS NULL AND (expires_at IS NULL OR expires_at > :now) ORDER BY created_at DESC")
    fun observeActive(now: Long): Flow<List<CarePreferenceEntity>>

    @Query("UPDATE care_opportunities SET status = 'UNKNOWN', updated_at = :at WHERE status = 'SENDING' AND updated_at < :cutoff")
    suspend fun reconcileSending(at: Long, cutoff: Long): Int
}
