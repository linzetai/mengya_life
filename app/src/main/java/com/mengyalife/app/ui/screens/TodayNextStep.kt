package com.mengyalife.app.ui.screens

import com.mengyalife.app.data.local.JournalEntryEntity
import com.mengyalife.app.data.repository.GoalNode
import com.mengyalife.app.data.repository.GoalSnapshot
import com.mengyalife.app.domain.goals.GoalStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 今天页的近期计划条目（由 App 按时间锚定后传入）。 */
data class PlanTodayItem(
    val planId: Long,
    val title: String,
    val timeScope: String?,
    val progressCount: Int,
)

/** 「下一步」的种类：决定按钮接去哪里。 */
enum class TodayNextStepKind { CARE, PLAN, CREATE_GOAL, STALE_GOAL, WRITE_DIARY, TALK }

/**
 * 由真实状态推导出的唯一「下一步」。
 *
 * 这里保持纯函数：不读时间以外的全局状态、不访问数据库，方便单测。
 */
data class TodayNextStep(
    val kind: TodayNextStepKind,
    val eyebrow: String,
    val title: String,
    val body: String,
    val cta: String,
    val planTitle: String? = null,
    val planId: Long? = null,
    val goalId: Long? = null,
)

/**
 * 优先级：真实的主动关心 > 本周未记录的小计划 > 没有方向 > 长期未更新的方向 >
 * 今天还没写日记 > 和顾问聊聊。
 */
fun decideTodayNextStep(
    careText: String?,
    careSourceType: String?,
    plans: List<PlanTodayItem>,
    goals: GoalSnapshot,
    entries: List<JournalEntryEntity>,
    today: LocalDate,
    now: Long = System.currentTimeMillis(),
): TodayNextStep {
    if (!careText.isNullOrBlank()) {
        return TodayNextStep(
            kind = TodayNextStepKind.CARE,
            eyebrow = "MengYa 想和你聊聊",
            title = careText,
            body = if (careSourceType == "PLAN_CHECKIN") "来自你的小计划" else "来自你的长期目标",
            cta = "现在聊聊",
        )
    }
    val stalePlan = plans.firstOrNull { it.progressCount == 0 }
    if (stalePlan != null) {
        return TodayNextStep(
            kind = TodayNextStepKind.PLAN,
            eyebrow = "这周的小计划",
            title = stalePlan.title,
            body = "还没有记录进展 · ${stalePlan.timeScope?.takeIf { it.isNotBlank() } ?: "时间未定"}",
            cta = "和 MengYa 聊聊进展",
            planTitle = stalePlan.title,
            planId = stalePlan.planId,
        )
    }
    if (goals.roots.isEmpty()) {
        return TodayNextStep(
            kind = TodayNextStepKind.CREATE_GOAL,
            eyebrow = "下一步",
            title = "写下一个想去的方向",
            body = "不用先想清楚十年后。先写下此刻最在意的那件事。",
            cta = "新增长期方向",
        )
    }
    val allNodes = goals.roots.flatMap { flattenNodes(it) }
    val latestCheckin = allNodes.maxOfOrNull { it.lastCheckinAt ?: 0L } ?: 0L
    // 只有「记录过进展、但停了很久」的方向才提示回头看看；
    // 刚建立、还没记录过的方向不在这里打扰。
    val staleNode = allNodes
        .filter { it.goal.status != GoalStatus.DONE && (it.lastCheckinAt ?: 0L) > 0L }
        .minByOrNull { it.lastCheckinAt ?: 0L }
        ?.takeIf { node -> now - (node.lastCheckinAt ?: 0L) > STALE_AFTER_MILLIS }
    if (staleNode != null && latestCheckin != 0L) {
        return TodayNextStep(
            kind = TodayNextStepKind.STALE_GOAL,
            eyebrow = "回头看看",
            title = "「${staleNode.goal.title}」有一阵没更新了",
            body = staleNode.lastCheckinAt?.let { "最近一次进展是 ${formatDay(it)}" }
                ?: "还没有记录过进展",
            cta = "看看这个方向",
            goalId = staleNode.goal.id,
        )
    }
    val wroteToday = entries.any {
        Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).toLocalDate() == today
    }
    if (!wroteToday) {
        return TodayNextStep(
            kind = TodayNextStepKind.WRITE_DIARY,
            eyebrow = "下一步",
            title = "今天还没有留下记录",
            body = "一句话就够。写下来的那一刻，以后会成为线索。",
            cta = "写一篇日记",
        )
    }
    return TodayNextStep(
        kind = TodayNextStepKind.TALK,
        eyebrow = "下一步",
        title = "把最近的变化聊清楚",
        body = "说说这段时间注意到了什么，MengYa 会一起理一理。",
        cta = "和 MengYa 聊聊",
    )
}

/** 十天没有更新就算「有一阵没看了」；只在有进展史时提示。 */
const val STALE_AFTER_MILLIS: Long = 10L * 24 * 60 * 60 * 1000

private fun flattenNodes(node: GoalNode): List<GoalNode> =
    listOf(node) + node.children.flatMap { flattenNodes(it) }

private fun formatDay(at: Long): String =
    Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("M月d日"))
