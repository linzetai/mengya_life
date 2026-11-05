package com.mengyalife.app.data.local.insight

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InsightReportDao {
    @Query("SELECT * FROM insight_reports ORDER BY generated_at DESC, id DESC LIMIT 1")
    fun observeLatest(): Flow<InsightReportEntity?>

    @Query("SELECT * FROM insight_reports ORDER BY generated_at DESC, id DESC LIMIT 1")
    suspend fun latest(): InsightReportEntity?

    @Query(
        """
        SELECT * FROM insight_reports WHERE data_version = :version
        ORDER BY generated_at DESC, id DESC LIMIT 1
        """
    )
    suspend fun findByDataVersion(version: String): InsightReportEntity?

    @Query("SELECT * FROM insight_reports ORDER BY generated_at ASC, id ASC")
    suspend fun loadAll(): List<InsightReportEntity>

    @Insert
    suspend fun insert(report: InsightReportEntity)
}
