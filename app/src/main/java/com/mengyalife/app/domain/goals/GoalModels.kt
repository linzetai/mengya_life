package com.mengyalife.app.domain.goals

/** 目标层级。三层严格嵌套：长期 ← 阶段 ← 行动。 */
enum class GoalLevel(val label: String, val depth: Int) {
    LONG_TERM("长期目标", 0),
    STAGE("阶段目标", 1),
    ACTION("短期行动", 2),
}

/** 领域分类。本轮为固定枚举，不做自定义分类。 */
enum class GoalDomain(val label: String) {
    HEALTH("健康"),
    CAREER("职业"),
    WEALTH("财富"),
    LIFE("生活"),
    LEARNING("学习"),
}

/** 目标状态。 */
enum class GoalStatus(val label: String) {
    NOT_STARTED("未开始"),
    IN_PROGRESS("进行中"),
    DONE("已完成"),
    PAUSED("已暂停"),
    CANCELLED("已取消"),
}

/** 进展记录来源。本轮仅手动录入，保留字段以便以后区分。 */
object CheckinSource {
    const val MANUAL = "manual"

    /** 由 AI 顾问在用户确认（或明确指令）后写入的进展，保留来源便于追溯。 */
    const val ADVISOR = "advisor"
}

/**
 * 进展语义。0.16A 起正式记录保留类型：
 * - DELTA：本次新增（存了 2000 / 学了 2 小时），用于“新增贡献”；
 * - SNAPSHOT：当时累计状态（现在有 14.7 万），用于累计曲线；
 * - NONE：无数值定性进展。
 *
 * 0.16 及更早的手动记录没有该字段，迁移时按 SNAPSHOT 处理，保持原有“累计曲线”含义。
 */
object ProgressKind {
    const val DELTA = "delta"
    const val SNAPSHOT = "snapshot"
    const val NONE = "none"

    fun label(kind: String): String = when (kind) {
        DELTA -> "新增"
        SNAPSHOT -> "累计快照"
        else -> "定性进展"
    }

    fun isIncrement(kind: String): Boolean = kind == DELTA
}
