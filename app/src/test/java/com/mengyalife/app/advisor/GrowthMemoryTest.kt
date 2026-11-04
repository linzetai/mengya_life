package com.mengyalife.app.advisor

import com.mengyalife.app.data.FakeGrowthSignalDao
import com.mengyalife.app.data.growth.GrowthMemoryService
import com.mengyalife.app.data.local.JournalEntryDao
import com.mengyalife.app.data.local.JournalEntryEntity
import com.mengyalife.app.data.local.growth.GrowthMemoryDao
import com.mengyalife.app.data.local.growth.GrowthMemoryEntity
import com.mengyalife.app.data.local.growth.GrowthSignalEntity
import com.mengyalife.app.domain.growth.DiaryContentHash
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemoryJournalDao(private val rows: MutableList<JournalEntryEntity>) : JournalEntryDao {
    private val flow = MutableStateFlow(rows.toList())
    override fun observeAll(): Flow<List<JournalEntryEntity>> = flow
    override suspend fun findById(id: Long): JournalEntryEntity? = rows.firstOrNull { it.id == id }
    override suspend fun count(): Int = rows.size
    override suspend fun loadAll(): List<JournalEntryEntity> = rows.toList()
    override suspend fun insert(entry: JournalEntryEntity): Long = 0
    override suspend fun update(entry: JournalEntryEntity): Int = 0
    override suspend fun deleteById(id: Long): Int = 0
    override suspend fun markInterruptedAnalyses(): Int = 0
    override suspend fun markAnalyzing(id: Long, expectedUpdatedAt: Long, hash: String, now: Long): Int = 0
    override suspend fun markCompleted(id: Long, hash: String, now: Long): Int = 0
    override suspend fun markFailed(id: Long, hash: String, error: String, now: Long): Int = 0
}

private class FakeMemoryDao : GrowthMemoryDao {
    val state = MutableStateFlow<List<GrowthMemoryEntity>>(emptyList())
    override fun observeAll(): Flow<List<GrowthMemoryEntity>> = state
    override suspend fun loadAll(): List<GrowthMemoryEntity> = state.value
    override suspend fun findById(id: String): GrowthMemoryEntity? = state.value.firstOrNull { it.id == id }
    override suspend fun findActiveByTheme(theme: String): GrowthMemoryEntity? =
        state.value.firstOrNull { it.theme == theme && it.status in setOf("ACTIVE", "CORRECTED") }
    override suspend fun findDeleted(theme: String, fingerprint: String): GrowthMemoryEntity? =
        state.value.firstOrNull { it.theme == theme && it.fingerprint == fingerprint && it.status == "DELETED" }
    override suspend fun insert(memory: GrowthMemoryEntity) { state.value = state.value + memory }
    override suspend fun update(memory: GrowthMemoryEntity): Int {
        if (state.value.none { it.id == memory.id }) return 0
        state.value = state.value.map { if (it.id == memory.id) memory else it }
        return 1
    }
    override suspend fun correct(id: String, content: String, now: Long): Int {
        val found = state.value.firstOrNull { it.id == id } ?: return 0
        state.value = state.value.map {
            if (it.id == id) found.copy(content = content, status = "CORRECTED", userCorrected = true, isInference = false, version = it.version + 1, updatedAt = now) else it
        }
        return 1
    }
    override suspend fun softDelete(id: String, now: Long): Int {
        val found = state.value.firstOrNull { it.id == id } ?: return 0
        state.value = state.value.map { if (it.id == id) found.copy(status = "DELETED", deletedAt = now, updatedAt = now) else it }
        return 1
    }
    override suspend fun markStaleByTheme(theme: String, now: Long): Int {
        var count = 0
        state.value = state.value.map {
            if (it.theme == theme && it.status in setOf("ACTIVE", "CORRECTED")) { count++; it.copy(status = "STALE", updatedAt = now) } else it
        }
        return count
    }
}

class GrowthMemoryTest {

    private val journals = mutableListOf(
        JournalEntryEntity(1, "工作1", "完成了项目汇报", 1_000, 1_000),
        JournalEntryEntity(2, "工作2", "领导批准了第一阶段", 2_000, 2_000),
        JournalEntryEntity(3, "工作3", "客户反馈很好", 3_000, 3_000),
    )
    private val signalDao = FakeGrowthSignalDao()
    private val memoryDao = FakeMemoryDao()
    private val service = GrowthMemoryService(
        journalDao = MemoryJournalDao(journals),
        signalDao = signalDao,
        memoryDao = memoryDao,
        now = { 10_000L },
        newId = { "m" + (memoryDao.state.value.size + 1) },
    )

    private fun signal(
        id: String,
        diaryId: Long,
        content: String,
        category: String = "EVENT",
        inference: Boolean = false,
    ): GrowthSignalEntity {
        val journal = journals.first { it.id == diaryId }
        return GrowthSignalEntity(
            id = id,
            sourceType = "DIARY",
            sourceId = diaryId,
            sourceVersion = DiaryContentHash.of(journal.title, journal.body),
            category = category,
            content = content,
            evidence = content,
            isInference = inference,
            confidence = 0.9,
            extractedAt = diaryId * 1_000,
            status = "ACTIVE",
            origin = "AI",
            sourceTitle = journal.title,
            contentHash = id,
            updatedAt = diaryId * 1_000,
        )
    }

    @Test
    fun `多篇职业日记合并为一条记忆并保留来源`() = runTest {
        signalDao.rows.value = listOf(
            signal("s1", 1, "完成了项目汇报"),
            signal("s2", 2, "领导批准了第一阶段", "PROGRESS"),
            signal("s3", 3, "客户反馈很好", "PROGRESS"),
        )
        service.rebuildAll()
        val memory = memoryDao.state.value.single()
        assertEquals("职业成长", memory.theme)
        assertEquals(setOf(1L, 2L, 3L), com.mengyalife.app.domain.growth.GrowthMemoryIds.decodeLongs(memory.sourceDiaryIdsJson).toSet())
        assertEquals(3, com.mengyalife.app.domain.growth.GrowthMemoryIds.decodeStrings(memory.signalIdsJson).size)
        assertFalse(memory.insufficient)
    }

    @Test
    fun `新增线索更新版本并保留旧证据`() = runTest {
        signalDao.rows.value = listOf(signal("s1", 1, "完成了项目汇报"))
        service.rebuildAll()
        val first = memoryDao.state.value.single()
        signalDao.rows.value = signalDao.rows.value + signal("s2", 2, "领导批准了第一阶段", "PROGRESS")
        service.rebuildAll()
        val updated = memoryDao.state.value.single()
        assertEquals(first.id, updated.id)
        assertEquals(2, updated.version)
        assertTrue(com.mengyalife.app.domain.growth.GrowthMemoryIds.decodeStrings(updated.signalIdsJson).contains("s1"))
    }

    @Test
    fun `不相关主题不会强行合并`() = runTest {
        signalDao.rows.value = listOf(
            signal("s1", 1, "完成了项目汇报"),
            signal("s2", 2, "今天睡得很好", "EMOTION"),
        )
        service.rebuildAll()
        assertEquals(2, memoryDao.state.value.size)
    }

    @Test
    fun `来源线索全部失效后记忆转 STALE`() = runTest {
        signalDao.rows.value = listOf(signal("s1", 1, "完成了项目汇报"))
        service.rebuildAll()
        signalDao.rows.value = emptyList()
        service.rebuildAll()
        assertEquals("STALE", memoryDao.state.value.single().status)
    }

    @Test
    fun `用户纠正内容不被新线索覆盖`() = runTest {
        signalDao.rows.value = listOf(signal("s1", 1, "完成了项目汇报"))
        service.rebuildAll()
        val id = memoryDao.state.value.single().id
        memoryDao.correct(id, "用户纠正：汇报只是准备阶段", 10_000)
        signalDao.rows.value = signalDao.rows.value + signal("s2", 2, "领导批准了第一阶段", "PROGRESS")
        service.rebuildAll()
        val memory = memoryDao.state.value.single()
        assertEquals("用户纠正：汇报只是准备阶段", memory.content)
        assertEquals("CORRECTED", memory.status)
    }

    @Test
    fun `删除后的记忆同证据不会自动复活`() = runTest {
        signalDao.rows.value = listOf(signal("s1", 1, "完成了项目汇报"))
        service.rebuildAll()
        val id = memoryDao.state.value.single().id
        memoryDao.softDelete(id, 10_000)
        service.rebuildAll()
        assertTrue(memoryDao.state.value.none { it.status in setOf("ACTIVE", "CORRECTED") })

        // 新证据可以形成新记忆，但不复活被删除的旧版本
        signalDao.rows.value = signalDao.rows.value + signal("s2", 2, "领导批准了第一阶段", "PROGRESS")
        service.rebuildAll()
        assertNotNull(memoryDao.state.value.firstOrNull { it.status == "ACTIVE" })
    }
}
