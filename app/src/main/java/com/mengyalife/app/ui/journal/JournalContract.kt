package com.mengyalife.app.ui.journal

import com.mengyalife.app.data.local.JournalEntryEntity
import com.mengyalife.app.domain.growth.GrowthSignal

/**
 * 日记页的「弹出层」状态。
 * 只有这三种半屏/对话框形态，没有引入 Navigation 组件。
 */
sealed interface JournalSheet {
    data object Closed : JournalSheet
    data object Creating : JournalSheet
    data class Viewing(val entry: JournalEntryEntity) : JournalSheet
    data class Editing(val entry: JournalEntryEntity) : JournalSheet
}

/** 编辑态：正文、标题、错误文案、是否正在保存。 */
data class JournalEditor(
    val body: String = "",
    val title: String = "",
    val bodyError: String? = null,
    val saving: Boolean = false,
)

/** 日记页一次性对外暴露的完整 UI 状态。 */
data class DiaryUiState(
    val loading: Boolean = true,
    val entries: List<JournalEntryEntity> = emptyList(),
    val sheet: JournalSheet = JournalSheet.Closed,
    val editor: JournalEditor = JournalEditor(),
    val pendingDelete: JournalEntryEntity? = null,
    val deleteError: String? = null,
    val readError: String? = null,
    val signals: List<GrowthSignal> = emptyList(),
)

/** Compose 只发这些「用户意图」，不直接碰数据层。 */
sealed interface DiaryAction {
    data object NewEntry : DiaryAction
    data class Open(val entry: JournalEntryEntity) : DiaryAction
    /** 从顾问保存成功后跳转：按 id 打开同一篇正式日记。 */
    data class OpenById(val entryId: Long) : DiaryAction
    data class Edit(val entry: JournalEntryEntity) : DiaryAction
    data class BodyChange(val value: String) : DiaryAction
    data class TitleChange(val value: String) : DiaryAction
    data object Save : DiaryAction
    data object CloseSheet : DiaryAction
    data class RequestDelete(val entry: JournalEntryEntity) : DiaryAction
    data object ConfirmDelete : DiaryAction
    data object DismissDelete : DiaryAction
    data class RetryAnalysis(val entryId: Long) : DiaryAction
    data class DeleteSignal(val signalId: String) : DiaryAction
    data class CorrectSignal(val signalId: String, val content: String) : DiaryAction
}
