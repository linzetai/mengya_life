package com.mengyalife.app.data.local.insight

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "insight_reports",
    indices = [Index("data_version"), Index("generated_at")],
)
data class InsightReportEntity(
    @PrimaryKey @ColumnInfo("id") val id: String,
    @ColumnInfo("data_version") val dataVersion: String,
    @ColumnInfo("status") val status: String,
    @ColumnInfo("summary") val summary: String,
    @ColumnInfo("facts_json") val factsJson: String,
    @ColumnInfo("insights_json") val insightsJson: String,
    @ColumnInfo("period_start") val periodStart: Long,
    @ColumnInfo("period_end") val periodEnd: Long,
    @ColumnInfo("generated_at") val generatedAt: Long,
    @ColumnInfo("error") val error: String?,
    @ColumnInfo("model") val model: String?,
)
