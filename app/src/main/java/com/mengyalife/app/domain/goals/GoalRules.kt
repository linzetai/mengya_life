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
    const val ERROR_METRIC_INVALID = "指标数值不合法：请填写有限数字（不能是 NaN 或 Infinity）"
    const val ERROR_METRIC_TOO_LARGE = "指标数值超出可记录范围（绝对值不能超过 1,000,000,000）"
    const val ERROR_UNIT_TOO_LONG = "单位过长，请控制在 16 个字以内"

    const val MAX_TITLE = 60
    const val MAX_DESCRIPTION = 1000
    const val MAX_NOTE = 500
    const val MAX_UNIT = 16

    /** 允许记录的最大绝对值：既挡住溢出，也保证图表坐标不会失真。 */
    const val MAX_ABS_METRIC = 1_000_000_000.0

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

    /**
     * 校验用户输入的指标文本。
     *
     * 注意 `String.toDoubleOrNull()` 会接受 `NaN` / `Infinity` / `-Infinity`
     * （它们都是合法的 Java Double 字面量），所以**不能**只判断「能不能转成 Double」。
     * 这里额外要求结果是有限数，并由 [validateMetricValue] 统一兜底。
     */
    fun validateMetric(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val parsed = raw.trim().toDoubleOrNull() ?: return ERROR_METRIC_INVALID
        return validateMetricValue(parsed)
    }

    /**
     * 校验**已经解析成 Double** 的指标值。
     *
     * 这是数据层的最后一道闸门：[com.mengyalife.app.data.repository.GoalRepository]
     * 在写库前必须调用它，因此即使绕过界面（例如以后接导入）也无法写入非法值。
     */
    fun validateMetricValue(value: Double?): String? {
        if (value == null) return null
        if (!value.isFinite()) return ERROR_METRIC_INVALID
        if (kotlin.math.abs(value) > MAX_ABS_METRIC) return ERROR_METRIC_TOO_LARGE
        return null
    }

    /** 日期/指标等文本输入 → 可用于入库的有限 Double（非法或空返回 null）。 */
    fun parseMetric(raw: String?): Double? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { it.isFinite() }

    fun validateUnit(raw: String?): String? {
        val unit = raw?.trim().orEmpty()
        return if (unit.length > MAX_UNIT) ERROR_UNIT_TOO_LONG else null
    }

    /** 单位归一：空串一律存 NULL，避免「有单位但空白」的脏数据。 */
    fun normalizeUnit(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }
}

/** 只带校验所需字段的轻量投影，避免领域层依赖 Room 实体。 */
data class GoalRef(
    val id: Long,
    val level: GoalLevel,
    val parentId: Long?,
)
