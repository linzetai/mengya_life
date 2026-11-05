package com.mengyalife.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 单行恢复元数据；数据库是否提交的唯一可信依据是 restore_attempt_id。 */
@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey @ColumnInfo("id") val id: Int = 1,
    @ColumnInfo("restore_attempt_id") val restoreAttemptId: String? = null,
    @ColumnInfo("restore_backup_id") val restoreBackupId: String? = null,
    @ColumnInfo("restore_generation") val restoreGeneration: Long = 0L,
    @ColumnInfo("restore_settings_json") val restoreSettingsJson: String? = null,
    @ColumnInfo("restore_completed_at") val restoreCompletedAt: Long? = null,
    @ColumnInfo("restore_expected_sha256") val restoreExpectedSha256: String? = null,
    @ColumnInfo("restore_payload_sha256") val restorePayloadSha256: String? = null,
    @ColumnInfo("restore_converged_at") val restoreConvergedAt: Long? = null,
)
