package com.mengyalife.app.data

import com.mengyalife.app.data.local.growth.GrowthSignalDao
import com.mengyalife.app.data.local.growth.GrowthSignalEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeGrowthSignalDao : GrowthSignalDao {
    val rows = MutableStateFlow<List<GrowthSignalEntity>>(emptyList())
    override fun observeForDiary(diaryId: Long): Flow<List<GrowthSignalEntity>> =
        rows.map { list -> list.filter { it.sourceId == diaryId && it.status in setOf("ACTIVE", "CORRECTED") } }
    override suspend fun loadForDiary(diaryId: Long): List<GrowthSignalEntity> =
        rows.value.filter { it.sourceId == diaryId && it.status in setOf("ACTIVE", "CORRECTED") }
    override suspend fun loadActive(limit: Int): List<GrowthSignalEntity> =
        rows.value.filter { it.status in setOf("ACTIVE", "CORRECTED") }.take(limit)
    override suspend fun findById(id: String): GrowthSignalEntity? = rows.value.firstOrNull { it.id == id }
    override suspend fun insertIgnore(signal: GrowthSignalEntity): Long {
        if (rows.value.any { it.sourceType == signal.sourceType && it.sourceId == signal.sourceId && it.sourceVersion == signal.sourceVersion && it.contentHash == signal.contentHash }) return -1
        rows.value = rows.value + signal
        return 1
    }
    override suspend fun correct(id: String, content: String, now: Long): Int {
        val found = rows.value.firstOrNull { it.id == id } ?: return 0
        rows.value = rows.value.map { if (it.id == id) found.copy(content = content, isInference = false, status = "CORRECTED", origin = "USER", updatedAt = now) else it }
        return 1
    }
    override suspend fun softDelete(id: String, now: Long): Int {
        val found = rows.value.firstOrNull { it.id == id } ?: return 0
        rows.value = rows.value.map { if (it.id == id) found.copy(status = "DELETED", updatedAt = now) else it }
        return 1
    }
    override suspend fun markStaleForDiary(diaryId: Long, now: Long): Int {
        var count = 0
        rows.value = rows.value.map {
            if (it.sourceId == diaryId && it.status in setOf("ACTIVE", "CORRECTED")) { count++; it.copy(status = "STALE", updatedAt = now) } else it
        }
        return count
    }
    override suspend fun markStaleOtherVersions(diaryId: Long, currentVersion: String, now: Long): Int {
        var count = 0
        rows.value = rows.value.map {
            if (it.sourceId == diaryId && it.sourceVersion != currentVersion && it.status in setOf("ACTIVE", "CORRECTED")) { count++; it.copy(status = "STALE", updatedAt = now) } else it
        }
        return count
    }
    override suspend fun deleteForDiary(diaryId: Long): Int {
        val before = rows.value.size; rows.value = rows.value.filterNot { it.sourceId == diaryId }; return before - rows.value.size
    }
    override suspend fun countActive(): Int = rows.value.count { it.status in setOf("ACTIVE", "CORRECTED") }
}
