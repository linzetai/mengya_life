package com.mengyalife.app.ui.goals

import com.mengyalife.app.data.local.goal.GoalCheckinEntity
import com.mengyalife.app.data.local.goal.GoalEntity
import com.mengyalife.app.data.repository.DeleteImpact
import com.mengyalife.app.data.repository.GoalSnapshot
import com.mengyalife.app.data.repository.TrendSnapshot
import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalStatus

enum class EditorMode { CREATE, EDIT }

/** 新建 / 编辑目标的表单状态。 */
data class GoalEditorState(
    val open: Boolean = false,
    val mode: EditorMode = EditorMode.CREATE,
    val level: GoalLevel = GoalLevel.LONG_TERM,
    val parentId: Long? = null,
    val goalId: Long? = null,
    val parentTitle: String? = null,
    val title: String = "",
    val description: String = "",
    val domain: GoalDomain = GoalDomain.LIFE,
    val status: GoalStatus = GoalStatus.NOT_STARTED,
    val dueDate: Long? = null,
    val error: String? = null,
    val saving: Boolean = false,
) {
    val isEdit: Boolean get() = mode == EditorMode.EDIT
}

/** 记录一次进展的表单状态。 */
data class CheckinDraftState(
    val open: Boolean = false,
    val goalId: Long? = null,
    val goalTitle: String = "",
    val note: String = "",
    val metric: String = "",
    val unit: String = "",
    val error: String? = null,
    val saving: Boolean = false,
)

/** 删除确认：必须先把影响范围算出来再展示。 */
data class DeleteConfirmState(
    val target: GoalEntity? = null,
    val impact: DeleteImpact? = null,
    val assessing: Boolean = false,
)

/** 状态选择器。 */
data class StatusPickerState(val target: GoalEntity? = null)

data class GoalsUiState(
    val loading: Boolean = true,
    val snapshot: GoalSnapshot = GoalSnapshot(),
    val trends: TrendSnapshot = TrendSnapshot(),
    val expandedIds: Set<Long> = emptySet(),
    val editor: GoalEditorState = GoalEditorState(),
    val checkin: CheckinDraftState = CheckinDraftState(),
    val historyOpenFor: GoalEntity? = null,
    val history: List<GoalCheckinEntity> = emptyList(),
    val delete: DeleteConfirmState = DeleteConfirmState(),
    val statusPicker: StatusPickerState = StatusPickerState(),
    val message: String? = null,
)

/** Compose 只发意图，不碰数据层。 */
sealed interface GoalAction {
    data class ToggleExpand(val id: Long) : GoalAction
    data class NewGoal(val level: GoalLevel, val parentId: Long?, val parentTitle: String? = null) : GoalAction
    data class EditGoal(val goal: GoalEntity) : GoalAction
    data class TitleChange(val value: String) : GoalAction
    data class DescriptionChange(val value: String) : GoalAction
    data class DomainChange(val value: GoalDomain) : GoalAction
    data class EditorStatusChange(val value: GoalStatus) : GoalAction
    data class DueDateChange(val value: Long?) : GoalAction
    data object SaveEditor : GoalAction
    data object CloseEditor : GoalAction

    data class RequestStatusChange(val goal: GoalEntity) : GoalAction
    data class SetStatus(val goal: GoalEntity, val status: GoalStatus) : GoalAction
    data object DismissStatusPicker : GoalAction

    data class RequestDelete(val goal: GoalEntity) : GoalAction
    data object ConfirmDelete : GoalAction
    data object DismissDelete : GoalAction

    data class OpenCheckin(val goal: GoalEntity) : GoalAction
    data class NoteChange(val value: String) : GoalAction
    data class MetricChange(val value: String) : GoalAction
    data class UnitChange(val value: String) : GoalAction
    data object SaveCheckin : GoalAction
    data object CloseCheckin : GoalAction

    data class OpenHistory(val goal: GoalEntity) : GoalAction
    data object CloseHistory : GoalAction

    data object ConsumeMessage : GoalAction
}
