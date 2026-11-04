package com.mengyalife.app.data.local.weekly

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weekly_ai_reviews")
data class WeeklyAiEntity(
    @PrimaryKey @ColumnInfo("period_key") val periodKey: String,
    @ColumnInfo("local_data_version") val localDataVersion: String,
    @ColumnInfo("ai_consent_version") val aiConsentVersion: String,
    @ColumnInfo("status") val status: String,
    @ColumnInfo("generated_at") val generatedAt: Long,
    @ColumnInfo("summary") val summary: String,
    @ColumnInfo("highlights_json") val highlightsJson: String,
    @ColumnInfo("suggestion") val suggestion: String,
    @ColumnInfo("fact_ids_json") val factIdsJson: String,
    @ColumnInfo("categories") val categories: String,
    @ColumnInfo("error") val error: String?,
    @ColumnInfo("model") val model: String?,
)
