package com.mengyalife.app.data

import com.mengyalife.app.data.local.JournalEntryDao
import com.mengyalife.app.data.local.JournalEntryEntity
import com.mengyalife.app.data.repository.JournalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内存版 DAO：行为与 SQL 语义保持一致（自增 id、按 updated_at 倒序、删除/更新命中数）。
 */
private class FakeJournalEntryDao(
    initial: List<JournalEntryEntity> = emptyList(),
) : JournalEntryDao {

    private val state = MutableStateFlow(initial)
    private var nextId: Long = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeAll(): Flow<List<JournalEntryEntity>> = state.map { list ->
        list.sortedWith(compareByDescending<JournalEntryEntity> { it.updatedAt }.thenByDescending { it.id })
    }

    override suspend fun findById(id: Long): JournalEntryEntity? = state.value.firstOrNull { it.id == id }

    override suspend fun count(): Int = state.value.size

    override suspend fun insert(entry: JournalEntryEntity): Long {
        val id = nextId++
        state.update { it + entry.copy(id = id) }
        return id
    }

    override suspend fun update(entry: JournalEntryEntity): Int {
        if (state.value.none { it.id == entry.id }) return 0
        state.update { list -> list.map { if (it.id == entry.id) entry else it } }
        return 1
    }

    override suspend fun deleteById(id: Long): Int {
        if (state.value.none { it.id == id }) return 0
        state.update { list -> list.filterNot { it.id == id } }
        return 1
    }
}

class JournalRepositoryTest {

    private var clock: Long = 1_700_000_000_000L
    private fun repository(dao: JournalEntryDao) = JournalRepository(dao) { clock }

    @Test
    fun `新建日记会 trim 正文并把空标题归一为 null`() = runTest {
        val dao = FakeJournalEntryDao()
        val repo = repository(dao)

        val id = repo.createEntry("  今天把节奏调慢了一档  ", "   ")

        val saved = dao.findById(id)
        assertNotNull(saved)
        assertEquals("今天把节奏调慢了一档", saved!!.body)
        assertNull(saved.title)
    }

    @Test
    fun `新建时创建时间与更新时间相同`() = runTest {
        val dao = FakeJournalEntryDao()
        val repo = repository(dao)

        val id = repo.createEntry("内容", "标题")
        val saved = dao.findById(id)!!

        assertEquals(clock, saved.createdAt)
        assertEquals(clock, saved.updatedAt)
        assertEquals("标题", saved.title)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `空正文不能写入数据库`() = runTest {
        val dao = FakeJournalEntryDao()
        repository(dao).createEntry("   ", null)
    }

    @Test
    fun `编辑保留创建时间并刷新更新时间`() = runTest {
        val dao = FakeJournalEntryDao()
        val repo = repository(dao)
        val id = repo.createEntry("原始内容", null)

        clock += 60_000
        val updated = repo.updateEntry(id, "  修改后的内容  ", "新标题")

        assertTrue(updated)
        val saved = dao.findById(id)!!
        assertEquals("修改后的内容", saved.body)
        assertEquals("新标题", saved.title)
        assertEquals(1_700_000_000_000L, saved.createdAt)
        assertEquals(1_700_000_060_000L, saved.updatedAt)
    }

    @Test
    fun `编辑不存在的记录返回 false 且不新增`() = runTest {
        val dao = FakeJournalEntryDao()
        val repo = repository(dao)

        assertFalse(repo.updateEntry(999L, "内容", null))
        assertEquals(0, dao.count())
    }

    @Test
    fun `删除命中返回 true 再次删除返回 false`() = runTest {
        val dao = FakeJournalEntryDao()
        val repo = repository(dao)
        val id = repo.createEntry("待删除", null)

        assertTrue(repo.deleteEntry(id))
        assertFalse(repo.deleteEntry(id))
        assertEquals(0, dao.count())
    }

    @Test
    fun `列表按更新时间倒序`() = runTest {
        val dao = FakeJournalEntryDao()
        val repo = repository(dao)

        val first = repo.createEntry("先写的", null)
        clock += 1_000
        val second = repo.createEntry("后写的", null)
        clock += 1_000
        repo.updateEntry(first, "先写的（被编辑过）", null)

        val bodies = repo.observeEntries().first().map { it.body }
        // 被编辑过的 first 更新时间最新，应排在最前
        assertEquals(listOf("先写的（被编辑过）", "后写的"), bodies)
        assertEquals(second, repo.observeEntries().first()[1].id)
    }

    @Test
    fun `完整增改删流程后数据一致`() = runTest {
        val dao = FakeJournalEntryDao()
        val repo = repository(dao)

        val id = repo.createEntry("今天记录", "标题A")
        repo.updateEntry(id, "今天记录（已改）", "标题B")
        assertTrue(repo.deleteEntry(id))

        assertEquals(0, repo.count())
        assertTrue(repo.observeEntries().first().isEmpty())
    }
}
