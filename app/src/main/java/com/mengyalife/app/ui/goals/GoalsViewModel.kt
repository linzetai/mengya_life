package com.mengyalife.app.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mengyalife.app.MengyaApplication
import com.mengyalife.app.data.local.goal.GoalCheckinEntity
import com.mengyalife.app.data.local.goal.GoalEntity
import com.mengyalife.app.data.repository.GoalDraft
import com.mengyalife.app.data.repository.GoalRepository
import com.mengyalife.app.data.repository.GoalResult
import com.mengyalife.app.data.repository.GoalSnapshot
import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalRules
import com.mengyalife.app.domain.goals.GoalStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 目标页 ViewModel。
 *
 * 状态 = 数据库快照（Flow） + 界面本地态（编辑表单 / 弹窗 / 展开集合 / 进展历史）。
 * 所有写操作都经过 [GoalRepository]，失败会把错误文案回填到表单，不静默吞掉。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModel(
    private val repository: GoalRepository,
) : ViewModel() {

    private data class LocalState(
        val expandedIds: Set<Long> = emptySet(),
        val editor: GoalEditorState = GoalEditorState(),
        val checkin: CheckinDraftState = CheckinDraftState(),
        val historyFor: GoalEntity? = null,
        val delete: DeleteConfirmState = DeleteConfirmState(),
        val statusPicker: StatusPickerState = StatusPickerState(),
        val message: String? = null,
        val focusedGoalId: Long? = null,
    )

    private val localState = MutableStateFlow(LocalState())

    private val snapshotFlow: StateFlow<GoalSnapshot> = repository.observeSnapshot()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalSnapshot())

    private val trendsFlow = repository.observeTrends()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.mengyalife.app.data.repository.TrendSnapshot())

    private val historyFlow = localState
        .distinctUntilChanged { old, new -> old.historyFor?.id == new.historyFor?.id }
        .flatMapLatest { state ->
            val id = state.historyFor?.id
            if (id == null) flowOf(emptyList()) else repository.observeCheckins(id)
        }

    val uiState: StateFlow<GoalsUiState> = combine(
        snapshotFlow,
        localState,
        historyFlow,
        trendsFlow,
    ) { snapshot, local, history, trends ->
        GoalsUiState(
            loading = false,
            snapshot = snapshot,
            trends = trends,
            expandedIds = local.expandedIds,
            editor = local.editor,
            checkin = local.checkin,
            historyOpenFor = local.historyFor,
            history = history,
            delete = local.delete,
            statusPicker = local.statusPicker,
            message = local.message,
            focusedGoalId = local.focusedGoalId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GoalsUiState(),
    )

    fun onAction(action: GoalAction) {
        when (action) {
            is GoalAction.FocusGoal -> localState.update {
                it.copy(focusedGoalId = action.id)
            }

            is GoalAction.ToggleExpand -> localState.update { state ->
                val expanded = state.expandedIds.toMutableSet()
                if (!expanded.add(action.id)) expanded.remove(action.id)
                state.copy(expandedIds = expanded)
            }

            is GoalAction.NewGoal -> localState.update {
                it.copy(
                    editor = GoalEditorState(
                        open = true,
                        mode = EditorMode.CREATE,
                        level = action.level,
                        parentId = action.parentId,
                        parentTitle = action.parentTitle,
                        title = action.initialTitle,
                        description = action.initialDescription,
                        domain = action.initialDomain ?: GoalDomain.LIFE,
                    ),
                )
            }

            is GoalAction.EditGoal -> localState.update {
                it.copy(
                    editor = GoalEditorState(
                        open = true,
                        mode = EditorMode.EDIT,
                        level = action.goal.level,
                        parentId = action.goal.parentId,
                        goalId = action.goal.id,
                        title = action.goal.title,
                        description = action.goal.description.orEmpty(),
                        domain = action.goal.domain,
                        status = action.goal.status,
                        dueDate = action.goal.dueDate,
                    ),
                )
            }

            is GoalAction.TitleChange -> localState.update {
                it.copy(editor = it.editor.copy(title = action.value, error = null))
            }

            is GoalAction.DescriptionChange -> localState.update {
                it.copy(editor = it.editor.copy(description = action.value, error = null))
            }

            is GoalAction.DomainChange -> localState.update {
                it.copy(editor = it.editor.copy(domain = action.value))
            }

            is GoalAction.EditorStatusChange -> localState.update {
                it.copy(editor = it.editor.copy(status = action.value))
            }

            is GoalAction.DueDateChange -> localState.update {
                it.copy(editor = it.editor.copy(dueDate = action.value, error = null))
            }

            GoalAction.CloseEditor -> localState.update { it.copy(editor = GoalEditorState()) }

            GoalAction.SaveEditor -> saveEditor()

            is GoalAction.RequestStatusChange -> localState.update {
                it.copy(statusPicker = StatusPickerState(target = action.goal))
            }

            GoalAction.DismissStatusPicker -> localState.update { it.copy(statusPicker = StatusPickerState()) }

            is GoalAction.SetStatus -> {
                localState.update { it.copy(statusPicker = StatusPickerState()) }
                viewModelScope.launch {
                    when (val result = repository.changeStatus(action.goal.id, action.status)) {
                        is GoalResult.Ok -> localState.update { it.copy(message = "状态已更新为「${action.status.label}」") }
                        is GoalResult.Error -> localState.update { it.copy(message = result.message) }
                    }
                }
            }

            is GoalAction.RequestDelete -> {
                localState.update { it.copy(delete = DeleteConfirmState(target = action.goal, assessing = true)) }
                viewModelScope.launch {
                    val impact = repository.assessDeleteImpact(action.goal.id)
                    localState.update { it.copy(delete = it.delete.copy(impact = impact, assessing = false)) }
                }
            }

            GoalAction.DismissDelete -> localState.update { it.copy(delete = DeleteConfirmState()) }

            GoalAction.ConfirmDelete -> confirmDelete()

            is GoalAction.OpenCheckin -> localState.update {
                it.copy(
                    checkin = CheckinDraftState(
                        open = true,
                        goalId = action.goal.id,
                        goalTitle = action.goal.title,
                    ),
                )
            }

            is GoalAction.NoteChange -> localState.update {
                it.copy(checkin = it.checkin.copy(note = action.value, error = null))
            }

            is GoalAction.MetricChange -> localState.update {
                it.copy(checkin = it.checkin.copy(metric = action.value, error = null))
            }

            is GoalAction.UnitChange -> localState.update {
                it.copy(checkin = it.checkin.copy(unit = action.value))
            }

            GoalAction.CloseCheckin -> localState.update { it.copy(checkin = CheckinDraftState()) }

            GoalAction.SaveCheckin -> saveCheckin()

            is GoalAction.OpenHistory -> localState.update {
                it.copy(historyFor = action.goal)
            }

            GoalAction.CloseHistory -> localState.update { it.copy(historyFor = null) }

            GoalAction.ConsumeMessage -> localState.update { it.copy(message = null) }
        }
    }

    private fun saveEditor() {
        val editor = localState.value.editor
        if (!editor.open || editor.saving) return

        val draft = GoalDraft(
            title = editor.title,
            description = editor.description.takeIf { it.isNotBlank() },
            domain = editor.domain,
            status = editor.status,
            startDate = null,
            dueDate = editor.dueDate,
        )

        localState.update { it.copy(editor = it.editor.copy(saving = true, error = null)) }

        viewModelScope.launch {
            val result = if (editor.isEdit) {
                val id = editor.goalId
                if (id == null) GoalResult.Error("目标不存在") else repository.updateGoal(id, draft)
            } else {
                repository.createGoal(editor.level, editor.parentId, draft)
            }

            when (result) {
                is GoalResult.Ok -> localState.update {
                    it.copy(editor = GoalEditorState(), message = if (editor.isEdit) "目标已更新" else "目标已创建")
                }

                is GoalResult.Error -> localState.update {
                    it.copy(editor = it.editor.copy(saving = false, error = result.message))
                }
            }
        }
    }

    private fun saveCheckin() {
        val state = localState.value.checkin
        val goalId = state.goalId ?: return
        if (!state.open || state.saving) return

        GoalRules.validateCheckinNote(state.note)?.let { error ->
            localState.update { it.copy(checkin = it.checkin.copy(error = error)) }
            return
        }
        GoalRules.validateMetric(state.metric)?.let { error ->
            localState.update { it.copy(checkin = it.checkin.copy(error = error)) }
            return
        }

        localState.update { it.copy(checkin = it.checkin.copy(saving = true, error = null)) }

        viewModelScope.launch {
            val result = repository.addCheckin(
                goalId = goalId,
                note = state.note,
                // 文本 → Double 只在这一处发生；非有限数由仓库层再次拒绝。
                metricValue = GoalRules.parseMetric(state.metric),
                metricUnit = state.unit,
            )
            when (result) {
                is GoalResult.Ok -> localState.update {
                    it.copy(checkin = CheckinDraftState(), message = "进展已记录")
                }

                is GoalResult.Error -> localState.update {
                    it.copy(checkin = it.checkin.copy(saving = false, error = result.message))
                }
            }
        }
    }

    private fun confirmDelete() {
        val target = localState.value.delete.target ?: return
        localState.update { it.copy(delete = it.delete.copy(assessing = true)) }
        viewModelScope.launch {
            when (val result = repository.deleteGoalTree(target.id)) {
                is GoalResult.Ok -> localState.update {
                    it.copy(
                        delete = DeleteConfirmState(),
                        message = "已删除「${target.title}」及 ${result.value.descendantCount} 个关联目标",
                    )
                }
                // 删除失败：保留弹窗与上下文，把错误显示出来（Gate 2A 的缺陷）
                is GoalResult.Error -> localState.update {
                    it.copy(
                        delete = it.delete.copy(
                            assessing = false,
                            impact = it.delete.impact,
                        ),
                        message = "删除失败：${result.message}",
                    )
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MengyaApplication
                GoalsViewModel(app.container.goalRepository)
            }
        }
    }
}

/** 供 [GoalsUiState] 之外使用的小工具：历史列表里取最近一条。 */
fun List<GoalCheckinEntity>.latest(): GoalCheckinEntity? = firstOrNull()

/** 目标状态是否算「活跃」。 */
val GoalStatus.isActive: Boolean
    get() = this == GoalStatus.IN_PROGRESS || this == GoalStatus.NOT_STARTED
