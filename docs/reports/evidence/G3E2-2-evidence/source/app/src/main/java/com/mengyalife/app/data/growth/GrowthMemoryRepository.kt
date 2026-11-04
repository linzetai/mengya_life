package com.mengyalife.app.data.growth

import com.mengyalife.app.data.local.JournalEntryDao
import com.mengyalife.app.data.local.JournalEntryEntity
import com.mengyalife.app.data.local.growth.GrowthMemoryDao
import com.mengyalife.app.data.local.growth.GrowthMemoryEntity
import com.mengyalife.app.data.local.growth.GrowthSignalDao
import com.mengyalife.app.data.local.growth.GrowthSignalEntity
import com.mengyalife.app.domain.growth.GrowthMemory
import com.mengyalife.app.domain.growth.GrowthMemoryIds
import com.mengyalife.app.domain.growth.GrowthMemoryStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class MemoryDetail(
    val memory: GrowthMemory,
    val signals: List<GrowthSignalEntity>,
    val journals: List<JournalEntryEntity>,
)

class GrowthMemoryRepository(
    private val memoryDao: GrowthMemoryDao,
    private val signalDao: GrowthSignalDao,
    private val journalDao: JournalEntryDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun observeAll(): Flow<List<GrowthMemory>> =
        memoryDao.observeAll().map { list -> list.map(::toDomain) }

    suspend fun loadDetail(id: String): MemoryDetail? {
        val entity = memoryDao.findById(id) ?: return null
        val signalIds = GrowthMemoryIds.decodeStrings(entity.signalIdsJson).toSet()
        val signals = signalIds.mapNotNull { signalDao.findById(it) }
        val diaryIds = GrowthMemoryIds.decodeLongs(entity.sourceDiaryIdsJson).toSet()
        val journals = diaryIds.mapNotNull { journalDao.findById(it) }
        return MemoryDetail(toDomain(entity), signals, journals)
    }

    suspend fun correct(id: String, content: String): Boolean {
        val clean = content.trim()
        if (clean.isEmpty() || clean.length > 2_000) return false
        return memoryDao.correct(id, clean, now()) > 0
    }

    suspend fun delete(id: String): Boolean = memoryDao.softDelete(id, now()) > 0

    fun toDomain(entity: GrowthMemoryEntity): GrowthMemory = GrowthMemory(
        id = entity.id,
        theme = entity.theme,
        content = entity.content,
        isInference = entity.isInference,
        status = GrowthMemoryStatus.entries.firstOrNull { it.name == entity.status }
            ?: GrowthMemoryStatus.STALE,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        signalIds = GrowthMemoryIds.decodeStrings(entity.signalIdsJson),
        sourceDiaryIds = GrowthMemoryIds.decodeLongs(entity.sourceDiaryIdsJson),
        userCorrected = entity.userCorrected,
        version = entity.version,
        confidence = entity.confidence,
        insufficient = entity.insufficient,
        recentChange = entity.recentChange,
        fingerprint = entity.fingerprint,
    )
}
