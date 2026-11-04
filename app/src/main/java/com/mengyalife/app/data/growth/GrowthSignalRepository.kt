package com.mengyalife.app.data.growth

import com.mengyalife.app.data.local.growth.GrowthSignalDao
import com.mengyalife.app.data.local.growth.GrowthSignalEntity
import com.mengyalife.app.domain.growth.GrowthCategory
import com.mengyalife.app.domain.growth.GrowthSignal
import com.mengyalife.app.domain.growth.GrowthSignalOrigin
import com.mengyalife.app.domain.growth.GrowthSignalStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GrowthSignalRepository(
    private val dao: GrowthSignalDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val onChanged: suspend () -> Unit = {},
) {
    fun observeForDiary(diaryId: Long): Flow<List<GrowthSignal>> =
        dao.observeForDiary(diaryId).map { rows -> rows.map(::toDomain) }

    suspend fun correct(id: String, content: String): Boolean {
        val clean = content.trim()
        if (clean.isEmpty() || clean.length > 500) return false
        val changed = dao.correct(id, clean, now()) > 0
        if (changed) runCatching { onChanged() }
        return changed
    }

    suspend fun delete(id: String): Boolean {
        val changed = dao.softDelete(id, now()) > 0
        if (changed) runCatching { onChanged() }
        return changed
    }

    suspend fun loadForDiary(diaryId: Long): List<GrowthSignal> =
        dao.loadForDiary(diaryId).map(::toDomain)

    suspend fun loadActive(limit: Int = 20): List<GrowthSignal> =
        dao.loadActive(limit).map(::toDomain)

    fun toDomain(entity: GrowthSignalEntity): GrowthSignal = GrowthSignal(
        id = entity.id,
        sourceType = entity.sourceType,
        sourceId = entity.sourceId,
        sourceVersion = entity.sourceVersion,
        category = GrowthCategory.entries.firstOrNull { it.name == entity.category } ?: GrowthCategory.EVENT,
        content = entity.content,
        evidence = entity.evidence,
        isInference = entity.isInference,
        confidence = entity.confidence,
        extractedAt = entity.extractedAt,
        status = GrowthSignalStatus.entries.firstOrNull { it.name == entity.status } ?: GrowthSignalStatus.STALE,
        origin = GrowthSignalOrigin.entries.firstOrNull { it.name == entity.origin } ?: GrowthSignalOrigin.AI,
        sourceTitle = entity.sourceTitle,
        updatedAt = entity.updatedAt,
    )
}
