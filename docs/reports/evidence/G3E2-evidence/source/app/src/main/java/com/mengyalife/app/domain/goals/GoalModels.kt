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
}
