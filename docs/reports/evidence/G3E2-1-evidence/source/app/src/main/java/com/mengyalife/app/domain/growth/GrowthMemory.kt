package com.mengyalife.app.domain.growth

enum class GrowthMemoryStatus { ACTIVE, CORRECTED, STALE, DELETED }

data class GrowthMemory(
    val id: String,
    val theme: String,
    val content: String,
    val isInference: Boolean,
    val status: GrowthMemoryStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val signalIds: List<String>,
    val sourceDiaryIds: List<Long>,
    val userCorrected: Boolean,
    val version: Int,
    val confidence: Double?,
    val insufficient: Boolean,
    val recentChange: String?,
    val fingerprint: String,
)
