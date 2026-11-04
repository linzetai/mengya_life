package com.mengyalife.app.data.growth

import com.mengyalife.app.data.local.JournalEntryEntity
import com.mengyalife.app.data.local.growth.GrowthMemoryEntity
import com.mengyalife.app.data.local.growth.GrowthSignalEntity
import com.mengyalife.app.domain.growth.DiaryContentHash
import com.mengyalife.app.domain.growth.GrowthMemoryIds

/**
 * AdvisorDataRepository 的第二层保险：
 * 即使记忆重建失败/延迟，只要其任一线索已失效、来源日记被编辑或删除，
 * 这条记忆就不能进入 AI 上下文。
 */
object GrowthMemoryValidity {
    fun usable(
        memory: GrowthMemoryEntity,
        signalsById: Map<String, GrowthSignalEntity>,
        journalsById: Map<Long, JournalEntryEntity>,
    ): Boolean {
        val signalIds = GrowthMemoryIds.decodeStrings(memory.signalIdsJson)
        val diaryIds = GrowthMemoryIds.decodeLongs(memory.sourceDiaryIdsJson)
        if (signalIds.isEmpty() || diaryIds.isEmpty()) return false
        if (memory.status !in setOf("ACTIVE", "CORRECTED")) return false
        for (diaryId in diaryIds) {
            if (journalsById[diaryId] == null) return false
        }
        for (signalId in signalIds) {
            val signal = signalsById[signalId] ?: return false
            if (signal.status !in setOf("ACTIVE", "CORRECTED")) return false
            val journal = journalsById[signal.sourceId] ?: return false
            if (signal.sourceVersion != DiaryContentHash.of(journal.title, journal.body)) return false
        }
        return true
    }
}
