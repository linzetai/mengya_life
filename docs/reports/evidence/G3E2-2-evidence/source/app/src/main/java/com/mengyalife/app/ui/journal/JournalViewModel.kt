package com.mengyalife.app.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mengyalife.app.MengyaApplication
import com.mengyalife.app.data.local.JournalEntryEntity
import com.mengyalife.app.data.advisor.AdvisorConfigRepository
import com.mengyalife.app.data.consent.AiConsentRepository
import com.mengyalife.app.data.growth.DiaryExtractor
import com.mengyalife.app.data.growth.GrowthMemoryService
import kotlinx.coroutines.Job
import com.mengyalife.app.data.growth.GrowthSignalRepository
import com.mengyalife.app.data.repository.JournalRepository
import com.mengyalife.app.domain.JournalRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 日记页 ViewModel。
 *
 * 这里是本轮**新增业务逻辑的唯一落点**：`MengyaLifeApp.kt` 只负责把状态和回调接起来。
 *
 * 状态来源有两路：
 * 1. 数据库：`repository.observeEntries()`（Flow，数据一变界面自动更新）；
 * 2. 界面本地态：弹出层、编辑草稿、待确认删除项（[MutableStateFlow]）。
 *
 * 之所以把「弹出层 / 草稿」也放在 ViewModel 而不是 `remember`：
 * 页面在切到设置页时会被销毁，放在 ViewModel 里才能保证状态不丢。
 */
class JournalViewModel(
    private val repository: JournalRepository,
    private val growthSignals: GrowthSignalRepository,
    private val extractionService: DiaryExtractor,
    private val consentRepository: AiConsentRepository? = null,
    private val configRepository: AdvisorConfigRepository? = null,
    private val memoryService: GrowthMemoryService? = null,
) : ViewModel() {

    private var extractionJob: Job? = null

    private data class LocalState(
        val sheet: JournalSheet = JournalSheet.Closed,
        val editor: JournalEditor = JournalEditor(),
        val pendingDelete: JournalEntryEntity? = null,
        val deleteError: String? = null,
    )

    private val localState = MutableStateFlow(LocalState())

    private val entriesFlow = repository.observeEntries()
        .map<List<JournalEntryEntity>, Result<List<JournalEntryEntity>>> { Result.success(it) }
        .catch { throwable -> emit(Result.failure(throwable)) }

    init {
        viewModelScope.launch { runCatching { repository.resetInterruptedAnalyses() } }
        if (consentRepository != null && configRepository != null) {
            viewModelScope.launch {
                combine(consentRepository.state, configRepository.state) { _, _ -> Unit }
                    .collect {
                        // 授权/服务配置变化：取消进行中的提取，服务内部还会再校验一次
                        if (extractionJob?.isActive == true) extractionJob?.cancel()
                    }
            }
        }
    }

    private fun startExtraction(entryId: Long, force: Boolean) {
        extractionJob?.cancel()
        extractionJob = viewModelScope.launch {
            runCatching { extractionService.extract(entryId, force) }
        }
    }

    private val signalsFlow = localState
        .map { (it.sheet as? JournalSheet.Viewing)?.entry?.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else growthSignals.observeForDiary(id)
        }

    val uiState: StateFlow<DiaryUiState> =
        combine(entriesFlow, signalsFlow, localState) { result, signals, local ->
            val entries = result.getOrDefault(emptyList())
            val resolvedSheet = when (val sheet = local.sheet) {
                is JournalSheet.Viewing -> JournalSheet.Viewing(
                    entries.firstOrNull { it.id == sheet.entry.id } ?: sheet.entry,
                )
                else -> sheet
            }
            DiaryUiState(
                loading = false,
                entries = entries,
                sheet = resolvedSheet,
                editor = local.editor,
                pendingDelete = local.pendingDelete,
                deleteError = local.deleteError,
                readError = if (result.isFailure) "读取本地日记失败，请重启应用重试" else null,
                signals = signals,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DiaryUiState(),
        )

    fun onAction(action: DiaryAction) {
        when (action) {
            DiaryAction.NewEntry -> localState.update {
                it.copy(sheet = JournalSheet.Creating, editor = JournalEditor())
            }

            is DiaryAction.Open -> localState.update {
                it.copy(sheet = JournalSheet.Viewing(action.entry))
            }

            is DiaryAction.Edit -> localState.update {
                it.copy(
                    sheet = JournalSheet.Editing(action.entry),
                    editor = JournalEditor(
                        body = action.entry.body,
                        title = action.entry.title.orEmpty(),
                    ),
                )
            }

            is DiaryAction.BodyChange -> localState.update {
                it.copy(editor = it.editor.copy(body = action.value, bodyError = null))
            }

            is DiaryAction.TitleChange -> localState.update {
                it.copy(editor = it.editor.copy(title = action.value))
            }

            DiaryAction.Save -> save()

            DiaryAction.CloseSheet -> localState.update {
                it.copy(sheet = JournalSheet.Closed, editor = JournalEditor())
            }

            is DiaryAction.RequestDelete -> localState.update {
                it.copy(pendingDelete = action.entry, deleteError = null)
            }

            DiaryAction.ConfirmDelete -> confirmDelete()

            DiaryAction.DismissDelete -> localState.update {
                it.copy(pendingDelete = null, deleteError = null)
            }

            is DiaryAction.RetryAnalysis -> startExtraction(action.entryId, force = true)

            is DiaryAction.DeleteSignal -> {
                viewModelScope.launch { growthSignals.delete(action.signalId) }
            }

            is DiaryAction.CorrectSignal -> {
                viewModelScope.launch { growthSignals.correct(action.signalId, action.content) }
            }
        }
    }

    private fun save() {
        val snapshot = localState.value
        val sheet = snapshot.sheet
        if (sheet != JournalSheet.Creating && sheet !is JournalSheet.Editing) return

        val editor = snapshot.editor
        // 空正文在这里被拦下，并把错误文案回填到界面（不做"静默失败"）
        val error = JournalRules.validateBody(editor.body)
        if (error != null) {
            localState.update { it.copy(editor = it.editor.copy(bodyError = error)) }
            return
        }

        localState.update { it.copy(editor = it.editor.copy(saving = true, bodyError = null)) }

        viewModelScope.launch {
            var savedId: Long? = null
            val result = runCatching {
                when (sheet) {
                    JournalSheet.Creating -> repository.createEntry(editor.body, editor.title).also { savedId = it }
                    is JournalSheet.Editing -> {
                        savedId = sheet.entry.id
                        repository.updateEntry(sheet.entry.id, editor.body, editor.title)
                    }
                    else -> Unit
                }
            }
            result
                .onSuccess {
                    localState.update {
                        it.copy(sheet = JournalSheet.Closed, editor = JournalEditor())
                    }
                    // 保存成功后再尝试分析；失败不影响日记保存。
                    savedId?.let { id -> startExtraction(id, force = false) }
                }
                .onFailure {
                    localState.update {
                        it.copy(editor = it.editor.copy(saving = false, bodyError = "保存失败，请重试"))
                    }
                }
        }
    }

    /**
     * 删除确认。
     *
     * **只有真正删除成功才关闭弹窗**；失败时保留弹窗与记录上下文，并把错误显示出来。
     * （Gate 2A 的缺陷：这里曾无条件关闭界面，导致删除失败被伪装成成功。）
     */
    private fun confirmDelete() {
        val target = localState.value.pendingDelete ?: return
        viewModelScope.launch {
            val outcome = runCatching { repository.deleteEntry(target.id) }
            val deleted = outcome.getOrNull() == true
            if (deleted) {
                memoryService?.let { runCatching { it.rebuildAll() } }
                localState.update {
                    it.copy(
                        pendingDelete = null,
                        deleteError = null,
                        sheet = JournalSheet.Closed,
                        editor = JournalEditor(),
                    )
                }
            } else {
                val reason = outcome.exceptionOrNull()?.message ?: "记录不存在或已被删除"
                localState.update { it.copy(deleteError = "删除失败：$reason") }
            }
        }
    }

    override fun onCleared() {
        extractionJob?.cancel()
        super.onCleared()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MengyaApplication
                JournalViewModel(
                    repository = app.container.journalRepository,
                    growthSignals = app.container.growthSignalRepository,
                    extractionService = app.container.diaryExtractionService,
                    consentRepository = app.container.aiConsentRepository,
                    configRepository = app.container.advisorConfigRepository,
                    memoryService = app.container.growthMemoryService,
                )
            }
        }
    }
}
