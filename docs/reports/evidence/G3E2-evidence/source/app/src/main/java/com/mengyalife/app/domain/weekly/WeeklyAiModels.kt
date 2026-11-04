package com.mengyalife.app.domain.weekly

data class WeeklyAiHighlight(
    val type: String,
    val title: String,
    val body: String,
    val factIds: List<String>,
    val sources: List<WeeklySourceRef>,
    val isInference: Boolean,
)
data class WeeklyAiReview(
    val periodKey: String,
    val localDataVersion: String,
    val aiConsentVersion: String,
    val status: String, // PENDING / READY / FAILED
    val generatedAt: Long,
    val summary: String,
    val highlights: List<WeeklyAiHighlight>,
    val suggestion: String,
    val factIds: List<String>,
    val categories: String,
    val error: String?,
    val model: String?,
)
