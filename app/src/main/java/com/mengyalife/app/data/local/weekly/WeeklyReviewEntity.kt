package com.mengyalife.app.data.local.weekly

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "weekly_reviews",
    indices = [Index(value = ["period_key"], unique = true)],
)
data class WeeklyReviewEntity(
    @PrimaryKey @ColumnInfo("id") val id: String,
    @ColumnInfo("period_key") val periodKey: String,
    @ColumnInfo("period_start") val periodStart: Long,
    @ColumnInfo("period_end") val periodEnd: Long,
    @ColumnInfo("generated_at") val generatedAt: Long,
    @ColumnInfo("version") val version: Int,
    @ColumnInfo("facts_json") val factsJson: String,
    @ColumnInfo("sections_json") val sectionsJson: String,
    @ColumnInfo("notified_at") val notifiedAt: Long?,
)
