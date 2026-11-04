package com.mengyalife.app.advisor

import com.mengyalife.app.data.growth.GrowthMemoryValidity
import com.mengyalife.app.data.local.JournalEntryEntity
import com.mengyalife.app.data.local.growth.GrowthMemoryEntity
import com.mengyalife.app.data.local.growth.GrowthSignalEntity
import com.mengyalife.app.domain.growth.DiaryContentHash
import com.mengyalife.app.domain.growth.GrowthMemoryIds
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthMemoryValidityTest {
    private val journal = JournalEntryEntity(1, "工作", "今天完成汇报", 1_000, 1_000)
    private val hash = DiaryContentHash.of(journal.title, journal.body)
    private val signal = GrowthSignalEntity(
        id = "s1", sourceType = "DIARY", sourceId = 1, sourceVersion = hash,
        category = "EVENT", content = "完成汇报", evidence = "完成汇报",
        isInference = false, confidence = 0.9, extractedAt = 1_000,
        status = "ACTIVE", origin = "AI", sourceTitle = "工作",
        contentHash = "h1", updatedAt = 1_000,
    )
    private val memory = GrowthMemoryEntity(
        id = "m1", theme = "职业成长", content = "c", isInference = false,
        status = "ACTIVE", createdAt = 1, updatedAt = 1,
        signalIdsJson = GrowthMemoryIds.encodeStrings(listOf("s1")),
        sourceDiaryIdsJson = GrowthMemoryIds.encodeLongs(listOf(1L)),
        userCorrected = false, version = 1, confidence = 0.9,
        insufficient = false, recentChange = null, fingerprint = "f",
    )

    @Test
    fun `来源有效的记忆可用`() {
        assertTrue(GrowthMemoryValidity.usable(memory, mapOf("s1" to signal), mapOf(1L to journal)))
    }

    @Test
    fun `日记编辑后旧记忆不可用`() {
        val edited = journal.copy(body = "新内容", updatedAt = 2_000)
        assertFalse(GrowthMemoryValidity.usable(memory, mapOf("s1" to signal), mapOf(1L to edited)))
    }

    @Test
    fun `线索删除或日记删除后记忆不可用`() {
        val stale = signal.copy(status = "STALE")
        assertFalse(GrowthMemoryValidity.usable(memory, mapOf("s1" to stale), mapOf(1L to journal)))
        assertFalse(GrowthMemoryValidity.usable(memory, mapOf("s1" to signal), emptyMap()))
    }
}
