package com.mengyalife.app.domain.goals

/**
 * 目标层级与父子关系规则（纯函数，可在 JVM 单元测试里直接验证）。
 *
 * 三条硬约束：
 * 1. 层级必须逐级嵌套（长期 → 阶段 → 行动），不能跳级；
 * 2. 根目标（长期）不能有父目标，非根目标必须有父目标；
 * 3. 父子关系不能形成环（把目标挂到自己的后代下面）。
 */
object GoalRules {

    const val ERROR_TITLE_REQUIRED = "标题不能为空"
    const val ERROR_TITLE_TOO_LONG = "标题过长，请控制在 60 字以内"
    const val ERROR_DESCRIPTION_TOO_LONG = "描述过长，请控制在 1000 字以内"
    const val ERROR_ROOT_HAS_PARENT = "长期目标不能有上级目标"
    const val ERROR_PARENT_REQUIRED = "阶段目标必须挂在某个长期目标下"
    const val ERROR_ACTION_PARENT_REQUIRED = "短期行动必须挂在某个阶段目标下"
    const val ERROR_PARENT_LEVEL_MISMATCH = "层级不合法：阶段目标只能挂在长期目标下，短期行动只能挂在阶段目标下"
    const val ERROR_CYCLE = "不能把目标挂到它自己或它的子目标下"
    const val ERROR_PARENT_MISSING = "上级目标不存在"
    const val ERROR_DUE_BEFORE_START = "截止时间不能早于开始时间"
    const val ERROR_NOTE_REQUIRED = "进展说明不能为空"
    const val ERROR_METRIC_INVALID = "指标数值不合法"

    const val MAX_TITLE = 60
    const val MAX_DESCRIPTION = 1000
    const val MAX_NOTE = 500

    /** 某个层级允许的父层级（null 表示不允许有父）。 */
    fun requiredParentLevel(level: GoalLevel): GoalLevel? = when (level) {
        GoalLevel.LONG_TERM -> null
        GoalLevel.STAGE -> GoalLevel.LONG_TERM
        GoalLevel.ACTION -> GoalLevel.STAGE
    }

    fun validateTitle(raw: String): String? {
        val title = raw.trim()
        return when {
            title.isEmpty() -> ERROR_TITLE_REQUIRED
            title.length > MAX_TITLE -> ERROR_TITLE_TOO_LONG
            else -> null
        }
    }

    fun validateDescription(raw: String?): String? =
        if ((raw?.trim()?.length ?: 0) > MAX_DESCRIPTION) ERROR_DESCRIPTION_TOO_LONG else null

    fun validateDates(startDate: Long?, dueDate: Long?): String? =
        if (startDate != null && dueDate != null && dueDate < startDate) ERROR_DUE_BEFORE_START else null

    /**
     * 校验「层级 + 父目标」是否合法。
     * @param parent 父目标（根目标传 null）
     */
    fun validateParent(level: GoalLevel, parent: GoalRef?): String? {
        val required = requiredParentLevel(level)
        if (required == null) {
            return if (parent != null) ERROR_ROOT_HAS_PARENT else null
        }
        if (parent == null) {
            return when (level) {
                GoalLevel.STAGE -> ERROR_PARENT_REQUIRED
                GoalLevel.ACTION -> ERROR_ACTION_PARENT_REQUIRED
                GoalLevel.LONG_TERM -> ERROR_ROOT_HAS_PARENT
            }
        }
        return if (parent.level != required) ERROR_PARENT_LEVEL_MISMATCH else null
    }

    /**
     * 是否会产生环：把 [goalId] 挂到 [newParentId] 下时，
     * 若 newParent 是 goalId 自己或它的后代，则非法。
     *
     * @param allGoals 全量目标（本轮目标数量很小，直接内存计算）
     */
    fun wouldCreateCycle(goalId: Long, newParentId: Long?, allGoals: List<GoalRef>): Boolean {
        if (newParentId == null) return false
        if (newParentId == goalId) return true
        val byId = allGoals.associateBy { it.id }
        var cursor: Long? = newParentId
        var guard = 0
        while (cursor != null && guard < 64) {
            if (cursor == goalId) return true
            cursor = byId[cursor]?.parentId
            guard++
        }
        return false
    }

    fun validateCheckinNote(raw: String): String? {
        val note = raw.trim()
        return when {
            note.isEmpty() -> ERROR_NOTE_REQUIRED
            note.length > MAX_NOTE -> "进展说明过长，请控制在 $MAX_NOTE 字以内"
            else -> null
        }
    }

    fun validateMetric(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return if (raw.trim().toDoubleOrNull() == null) ERROR_METRIC_INVALID else null
    }
}

/** 只带校验所需字段的轻量投影，避免领域层依赖 Room 实体。 */
data class GoalRef(
    val id: Long,
    val level: GoalLevel,
    val parentId: Long?,
)
