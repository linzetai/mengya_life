package com.mengyalife.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppMetaDao {
    @Query("SELECT * FROM app_meta WHERE id = 1 LIMIT 1")
    suspend fun get(): AppMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: AppMetaEntity)

    @Query("UPDATE app_meta SET restore_settings_json=:settings, restore_completed_at=:completedAt WHERE id=1")
    suspend fun markSettingsApplied(settings: String?, completedAt: Long)

    @Query("UPDATE app_meta SET restore_converged_at=:at WHERE id=1")
    suspend fun markConverged(at: Long)
}
