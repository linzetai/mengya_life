package com.mengyalife.app.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mengyalife.app.MengyaApplication
import com.mengyalife.app.data.local.JournalEntryEntity
import com.mengyalife.app.data.repository.JournalRepository
import com.mengyalife.app.domain.JournalRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
) : ViewModel() {

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

    val uiState: StateFlow<DiaryUiState> =
        combine(entriesFlow, localState) { result, local ->
            DiaryUiState(
                loading = false,
                entries = result.getOrDefault(emptyList()),
                sheet = local.sheet,
                editor = local.editor,
                pendingDelete = local.pendingDelete,
                deleteError = local.deleteError,
                readError = if (result.isFailure) "读取本地日记失败，请重启应用重试" else null,
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
            val result = runCatching {
                when (sheet) {
                    JournalSheet.Creating -> repository.createEntry(editor.body, editor.title)
                    is JournalSheet.Editing -> repository.updateEntry(sheet.entry.id, editor.body, editor.title)
                    else -> Unit
                }
            }
            result
                .onSuccess {
                    localState.update {
                        it.copy(sheet = JournalSheet.Closed, editor = JournalEditor())
                    }
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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MengyaApplication
                JournalViewModel(app.container.journalRepository)
            }
        }
    }
}
