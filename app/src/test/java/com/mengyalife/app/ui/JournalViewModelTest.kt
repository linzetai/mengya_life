package com.mengyalife.app.ui

import com.mengyalife.app.data.local.JournalEntryDao
import com.mengyalife.app.data.local.JournalEntryEntity
import com.mengyalife.app.data.FakeGrowthSignalDao
import com.mengyalife.app.data.growth.DiaryExtractionOutcome
import com.mengyalife.app.data.growth.DiaryExtractor
import com.mengyalife.app.data.growth.GrowthSignalRepository
import com.mengyalife.app.data.repository.JournalRepository
import com.mengyalife.app.ui.journal.DiaryAction
import com.mengyalife.app.ui.journal.JournalSheet
import com.mengyalife.app.ui.journal.JournalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 可控制"删除是否成功"的假 DAO。 */
private class ControllableJournalDao : JournalEntryDao {
    private val state = MutableStateFlow<List<JournalEntryEntity>>(emptyList())
    private var nextId = 1L
    var failDelete: Boolean = false

    override fun observeAll(): Flow<List<JournalEntryEntity>> = state.map { it }

    override suspend fun findById(id: Long): JournalEntryEntity? = state.value.firstOrNull { it.id == id }

    override suspend fun count(): Int = state.value.size
    override suspend fun markInterruptedAnalyses(): Int = 0

    override suspend fun loadAll(): List<JournalEntryEntity> = state.value.sortedBy { it.id }

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
        if (failDelete) return 0
        if (state.value.none { it.id == id }) return 0
        state.update { list -> list.filterNot { it.id == id } }
        return 1
    }

    override suspend fun markAnalyzing(id: Long, expectedUpdatedAt: Long, hash: String, now: Long): Int = 1
    override suspend fun markCompleted(id: Long, hash: String, now: Long): Int = 1
    override suspend fun markFailed(id: Long, hash: String?, error: String, now: Long): Int = 1
}

@OptIn(ExperimentalCoroutinesApi::class)
class JournalViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(dao: ControllableJournalDao): JournalViewModel {
        val repo = JournalRepository(dao, now = { 1_700_000_000_000L })
        return JournalViewModel(
            repository = repo,
            growthSignals = GrowthSignalRepository(FakeGrowthSignalDao()),
            extractionService = object : DiaryExtractor {
                override suspend fun extract(diaryId: Long, force: Boolean): DiaryExtractionOutcome =
                    DiaryExtractionOutcome.Skipped("test")
            },
        )
    }

    @Test
    fun `空正文保存会给出错误且不关闭面板`() = runTest {
        val dao = ControllableJournalDao()
        val vm = viewModel(dao)
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onAction(DiaryAction.NewEntry)
        vm.onAction(DiaryAction.BodyChange("   "))
        vm.onAction(DiaryAction.Save)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("正文不能为空", state.editor.bodyError)
        assertTrue(state.sheet is JournalSheet.Creating)
        assertEquals(0, dao.count())
    }

    @Test
    fun `删除成功才关闭确认框`() = runTest {
        val dao = ControllableJournalDao()
        val vm = viewModel(dao)
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onAction(DiaryAction.NewEntry)
        vm.onAction(DiaryAction.BodyChange("要删除的记录"))
        vm.onAction(DiaryAction.Save)
        advanceUntilIdle()

        val entry = vm.uiState.value.entries.first()
        vm.onAction(DiaryAction.Open(entry))
        vm.onAction(DiaryAction.RequestDelete(entry))
        vm.onAction(DiaryAction.ConfirmDelete)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull(state.pendingDelete)
        assertNull(state.deleteError)
        assertTrue(state.sheet is JournalSheet.Closed)
        assertEquals(0, dao.count())
    }

    @Test
    fun `删除失败时保留上下文并显示错误`() = runTest {
        val dao = ControllableJournalDao()
        val vm = viewModel(dao)
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onAction(DiaryAction.NewEntry)
        vm.onAction(DiaryAction.BodyChange("删不掉的记录"))
        vm.onAction(DiaryAction.Save)
        advanceUntilIdle()

        val entry = vm.uiState.value.entries.first()
        vm.onAction(DiaryAction.Open(entry))
        vm.onAction(DiaryAction.RequestDelete(entry))

        dao.failDelete = true
        vm.onAction(DiaryAction.ConfirmDelete)
        advanceUntilIdle()

        val state = vm.uiState.value
        // 弹窗与记录上下文仍在
        assertNotNull(state.pendingDelete)
        assertEquals(entry.id, state.pendingDelete?.id)
        // 且给出了明确错误，而不是假装成功
        assertNotNull(state.deleteError)
        assertTrue(state.deleteError!!.startsWith("删除失败"))
        assertEquals(1, dao.count())
    }
}
