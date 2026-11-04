package com.mengyalife.app.domain.advisor

import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalStatus
import com.mengyalife.app.util.MetricFormat
import com.mengyalife.app.util.Timestamps

enum class AdvisorSourceType(val label: String) {
    GOAL("目标"),
    PROGRESS("进展"),
    JOURNAL("日记"),
    GROWTH("成长线索"),
    MEMORY("成长记忆"),
}

data class AdvisorSource(
    val type: AdvisorSourceType,
    val recordId: String,
    val label: String,
)

data class ContextGoalRecord(
    val id: Long,
    val title: String,
    val description: String?,
    val level: GoalLevel,
    val parentId: Long?,
    val domain: GoalDomain,
    val status: GoalStatus,
    val createdAt: Long,
)

data class ContextCheckinRecord(
    val id: Long,
    val goalId: Long,
    val recordedAt: Long,
    val note: String,
    val metricValue: Double?,
    val metricUnit: String?,
)

data class ContextJournalRecord(
    val id: Long,
    val title: String?,
    val body: String,
    val updatedAt: Long,
)

data class ContextGrowthMemory(
    val id: String,
    val theme: String,
    val content: String,
    val isInference: Boolean,
    val corrected: Boolean,
    val insufficient: Boolean,
    val recentChange: String?,
    val sourceDiaryIds: List<Long>,
)

data class ContextGrowthSignal(
    val id: String,
    val diaryId: Long,
    val diaryTitle: String?,
    val category: String,
    val content: String,
    val evidence: String,
    val isInference: Boolean,
    val corrected: Boolean,
)

data class AdvisorContextSnapshot(
    val goals: List<ContextGoalRecord> = emptyList(),
    val checkins: List<ContextCheckinRecord> = emptyList(),
    val journals: List<ContextJournalRecord> = emptyList(),
    val growthSignals: List<ContextGrowthSignal> = emptyList(),
    val growthMemories: List<ContextGrowthMemory> = emptyList(),
    val history: List<AdvisorMessage> = emptyList(),
)

data class AdvisorContextStats(
    val goals: Int = 0,
    val progress: Int = 0,
    val journals: Int = 0,
    val growthSignals: Int = 0,
    val memories: Int = 0,
    val historyMessages: Int = 0,
    val chars: Int = 0,
    val droppedByBudget: Int = 0,
)

/** 一条可被请求预算单独取舍的上下文片段；来源只属于真正进入请求的片段。 */
data class AdvisorContextEntry(
    val text: String,
    val source: AdvisorSource? = null,
)

data class AssembledAdvisorContext(
    val entries: List<AdvisorContextEntry>,
    val history: List<AdvisorMessage>,
    val stats: AdvisorContextStats,
) {
    /** 仅用于调试/旧测试展示；真正发送以 entries + 请求预算为准。 */
    val systemContext: String? = entries.joinToString("\n") { it.text }.trim().takeIf { it.isNotBlank() }
    val sources: List<AdvisorSource> = entries.mapNotNull { it.source }
}

/**
 * 规则式上下文检索（V1，不做向量/Embedding）。
 *
 * - 只读取已授权类别；
 * - 根据问题关键词选择相关目标/进展/日记；
 * - 无关问题不附加日记；
 * - 严格限制条数、正文长度与字符预算；
 * - 不返回完整数据库，也不修改任何数据。
 *
 * 纯函数 + 纯数据，可在 JVM 单测中完整覆盖。
 */
class AdvisorContextAssembler(
    private val maxGoals: Int = 2,
    private val maxStages: Int = 6,
    private val maxActions: Int = 8,
    private val maxProgress: Int = 6,
    private val maxJournals: Int = 2,
    private val maxJournalChars: Int = 300,
    private val maxContextChars: Int = 3_500,
) {

    fun assemble(
        query: String,
        consent: AiConsentState,
        snapshot: AdvisorContextSnapshot,
    ): AssembledAdvisorContext {
        val tokens = tokenize(query)
        val planningIntent = hasAny(query, PLANNING_WORDS)
        val journalIntent = hasAny(query, JOURNAL_WORDS)

        val history = if (consent.enabled(ConsentCategory.CONVERSATION)) {
            snapshot.history.filter {
                it.status == AdvisorMessageStatus.SENT && it.contextEpoch == consent.epoch
            }
        } else {
            emptyList()
        }

        val entries = mutableListOf<AdvisorContextEntry>()
        val sources = mutableListOf<AdvisorSource>()
        var goalsUsed = 0
        var stagesUsed = 0
        var actionsUsed = 0

        if (consent.enabled(ConsentCategory.GOALS) && snapshot.goals.isNotEmpty()) {
            val selectedLongTerm = selectGoals(query, tokens, planningIntent, snapshot.goals)
            val selectedIds = mutableSetOf<Long>()
            selectedLongTerm.forEach { goal ->
                selectedIds.add(goal.id)
                val desc = goal.description?.takeIf { it.isNotBlank() }
                    ?.let { " —— " + it.take(160) }
                    .orEmpty()
                entries.add(AdvisorContextEntry(
                    "- [目标#${goal.id}] ${goal.level.label}：${goal.title}$desc",
                    AdvisorSource(AdvisorSourceType.GOAL, goal.id.toString(), goal.title),
                ))
                sources.add(AdvisorSource(AdvisorSourceType.GOAL, goal.id.toString(), goal.title))
                goalsUsed++
                val children = snapshot.goals
                    .filter { it.parentId == goal.id }
                    .sortedBy { it.createdAt }
                children.filter { it.level == GoalLevel.STAGE }.take(maxStages).forEach { stage ->
                    if (stagesUsed >= maxStages) return@forEach
                    selectedIds.add(stage.id)
                    entries.add(AdvisorContextEntry("  - [阶段#${stage.id}] ${stage.title}"))
                    sources.add(AdvisorSource(AdvisorSourceType.GOAL, stage.id.toString(), stage.title))
                    goalsUsed++
                    stagesUsed++
                    snapshot.goals.filter { it.parentId == stage.id && it.level == GoalLevel.ACTION }
                        .sortedBy { it.createdAt }
                        .take(maxActions - actionsUsed)
                        .forEach { action ->
                            selectedIds.add(action.id)
                            entries.add(AdvisorContextEntry("    - [行动#${action.id}] ${action.title}"))
                            sources.add(AdvisorSource(AdvisorSourceType.GOAL, action.id.toString(), action.title))
                            goalsUsed++
                            actionsUsed++
                        }
                }
            }

        }

        var progressUsed = 0
        if (consent.enabled(ConsentCategory.PROGRESS) && snapshot.checkins.isNotEmpty()) {
            val selectedIds = sources.filter { it.type == AdvisorSourceType.GOAL }
                .mapNotNull { it.recordId.toLongOrNull() }
                .toSet()
            val scored = snapshot.checkins
                .map { it to scoreCheckin(it, tokens) }
                .filter { (checkin, score) ->
                    checkin.goalId in selectedIds || score >= 1 || planningIntent
                }
                .sortedWith(compareByDescending<Pair<ContextCheckinRecord, Int>> { it.second }
                    .thenByDescending { it.first.recordedAt })
                .take(maxProgress)
            if (scored.isNotEmpty()) {
                entries.add(AdvisorContextEntry("进展："))
                scored.forEach { (checkin, _) ->
                    val line = buildString {
                        append("- [进展#${checkin.id}] ")
                        append(Timestamps.formatDate(checkin.recordedAt))
                        append(" · ")
                        append(checkin.note.replace('\n', ' ').take(120))
                        checkin.metricValue?.let {
                            append("（").append(MetricFormat.withUnit(it, checkin.metricUnit)).append("）")
                        }
                    }
                    val source = AdvisorSource(
                        AdvisorSourceType.PROGRESS,
                        checkin.id.toString(),
                        checkin.note.replace('\n', ' ').take(40),
                    )
                    entries.add(AdvisorContextEntry(line, source))
                    sources.add(source)
                    progressUsed++
                }
            }
        }
        var journalsUsed = 0
        if (consent.enabled(ConsentCategory.JOURNAL) && snapshot.journals.isNotEmpty()) {
            val scored = snapshot.journals
                .map { journal -> journal to scoreJournal(journal, tokens) }
                .filter { (_, score) -> score >= 2 || (journalIntent && score >= 1) }
                .sortedWith(compareByDescending<Pair<ContextJournalRecord, Int>> { it.second }
                    .thenByDescending { it.first.updatedAt })
                .take(maxJournals)
            if (scored.isNotEmpty()) {
                entries.add(AdvisorContextEntry("相关日记摘录："))
                scored.forEach { (journal, _) ->
                    val title = journal.title?.takeIf { it.isNotBlank() } ?: "无标题"
                    val excerpt = journal.body.replace('\n', ' ').take(maxJournalChars)
                    val source = AdvisorSource(AdvisorSourceType.JOURNAL, journal.id.toString(), title)
                    entries.add(AdvisorContextEntry("- [日记#${journal.id}] $title：$excerpt", source))
                    sources.add(source)
                    journalsUsed++
                }
            }
        }

        var memoriesUsed = 0
        val memoryDiaryIds = mutableSetOf<Long>()
        if (consent.enabled(ConsentCategory.JOURNAL) && snapshot.growthMemories.isNotEmpty()) {
            val selectedJournalIds = sources
                .filter { it.type == AdvisorSourceType.JOURNAL }
                .mapNotNull { it.recordId.toLongOrNull() }
                .toSet()
            val candidates = snapshot.growthMemories
                .filter { memory -> memory.sourceDiaryIds.none { it in selectedJournalIds } }
                .map { memory -> memory to scoreMemory(memory, tokens) }
                .filter { (_, score) -> score >= 1 || journalIntent || planningIntent }
                .take(2)
            candidates.forEach { (memory, _) ->
                memoryDiaryIds.addAll(memory.sourceDiaryIds)
                val kind = when {
                    memory.corrected -> "用户纠正"
                    memory.isInference -> "含推测"
                    else -> "事实"
                }
                val source = AdvisorSource(
                    AdvisorSourceType.MEMORY,
                    memory.id,
                    "${memory.theme}（记忆）",
                )
                entries.add(
                    AdvisorContextEntry(
                        "- [成长记忆#${memory.id}] ${memory.theme} · $kind：${memory.content}" +
                            (memory.recentChange?.let { "（$it）" } ?: "") +
                            (if (memory.insufficient) "（信息不足，请表达不确定性）" else ""),
                        source,
                    ),
                )
                sources.add(source)
                memoriesUsed++
            }
        }

        var signalsUsed = 0
        if (consent.enabled(ConsentCategory.JOURNAL) && snapshot.growthSignals.isNotEmpty()) {
            val selectedJournalIds = sources
                .filter { it.type == AdvisorSourceType.JOURNAL }
                .mapNotNull { it.recordId.toLongOrNull() }
                .toSet()
            val correctedDiaries = snapshot.growthSignals
                .filter { it.corrected }
                .map { it.diaryId }
                .toSet()
            val candidates = snapshot.growthSignals
                .filter { it.diaryId !in selectedJournalIds && it.diaryId !in memoryDiaryIds }
                // 用户纠正过的日记：只使用纠正结果，不再重复发送旧 AI 推测
                .filter { it.diaryId !in correctedDiaries || it.corrected }
                .map { signal -> signal to scoreSignal(signal, tokens) }
                .filter { (_, score) -> score >= 1 || journalIntent || planningIntent }
                .sortedWith(
                    compareByDescending<Pair<ContextGrowthSignal, Int>> { it.first.corrected }
                        .thenByDescending { it.second },
                )
                .take(4)
            if (candidates.isNotEmpty()) {
                entries.add(AdvisorContextEntry("成长线索（来自日记，事实/推测已标记）："))
                candidates.forEach { (signal, _) ->
                    val kind = if (signal.corrected) "用户纠正" else if (signal.isInference) "推测" else "事实"
                    val source = AdvisorSource(
                        AdvisorSourceType.GROWTH,
                        signal.id,
                        "${signal.category}（日记#${signal.diaryId}）",
                    )
                    entries.add(
                        AdvisorContextEntry(
                            "- [线索#${signal.id}] ${signal.category} · $kind：${signal.content}（依据：${signal.evidence.take(120)}）",
                            source,
                        ),
                    )
                    sources.add(source)
                    signalsUsed++
                }
            }
        }

        val finalEntries = if (entries.isEmpty()) emptyList() else {
            listOf(AdvisorContextEntry("【系统按授权自动检索的个人数据（非完整数据库）】")) + entries
        }
        return AssembledAdvisorContext(
            entries = finalEntries,
            history = history,
            stats = AdvisorContextStats(
                goals = goalsUsed,
                progress = progressUsed,
                journals = journalsUsed,
                growthSignals = signalsUsed,
                memories = memoriesUsed,
                historyMessages = history.size,
                chars = finalEntries.sumOf { it.text.length },
                droppedByBudget = 0,
            ),
        )
    }

    private fun selectGoals(
        query: String,
        tokens: Set<String>,
        planningIntent: Boolean,
        goals: List<ContextGoalRecord>,
    ): List<ContextGoalRecord> {
        val longTerm = goals.filter { it.level == GoalLevel.LONG_TERM }
        val candidates = if (longTerm.isNotEmpty()) longTerm else goals
        val scored = candidates.map { goal -> goal to scoreGoal(goal, tokens) }
        val matched = scored.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<ContextGoalRecord, Int>> { it.second }
                .thenByDescending { it.first.createdAt })
        if (matched.isNotEmpty()) return matched.take(maxGoals).map { it.first }
        if (!planningIntent) return emptyList()
        // 规划类问题且没有关键词命中：优先进行中的长期目标，最多 2 个
        return candidates
            .sortedWith(
                compareByDescending<ContextGoalRecord> { it.status == GoalStatus.IN_PROGRESS }
                    .thenByDescending { it.createdAt },
            )
            .take(maxGoals)
    }

    private fun scoreGoal(goal: ContextGoalRecord, tokens: Set<String>): Int {
        val text = (goal.title + " " + goal.description.orEmpty()).lowercase()
        return tokens.count { it.isNotBlank() && text.contains(it) }
    }

    private fun scoreMemory(memory: ContextGrowthMemory, tokens: Set<String>): Int {
        val text = (memory.theme + " " + memory.content).lowercase()
        return tokens.count { it.length >= 2 && text.contains(it) }
    }

    private fun scoreSignal(signal: ContextGrowthSignal, tokens: Set<String>): Int {
        val text = (signal.content + " " + signal.evidence).lowercase()
        return tokens.count { it.length >= 2 && text.contains(it) }
    }

    private fun scoreCheckin(checkin: ContextCheckinRecord, tokens: Set<String>): Int {
        val text = checkin.note.lowercase()
        return tokens.count { it.length >= 2 && text.contains(it) }
    }

    private fun scoreJournal(journal: ContextJournalRecord, tokens: Set<String>): Int {
        val text = ((journal.title.orEmpty()) + " " + journal.body).lowercase()
        return tokens.count { it.length >= 2 && text.contains(it) }
    }

    private fun tokenize(query: String): Set<String> {
        val lower = query.lowercase()
        val result = mutableSetOf<String>()
        Regex("[a-z0-9]{2,}").findAll(lower).forEach { result.add(it.value) }
        val chinese = Regex("[\\u4e00-\\u9fff]+").findAll(lower).map { it.value }.toList()
        chinese.forEach { run ->
            if (run.length <= 3) result.add(run)
            else run.windowed(2).forEach { result.add(it) }
        }
        return result.filterNot { it in STOP_WORDS }.toSet()
    }

    private fun hasAny(query: String, words: List<String>): Boolean {
        val lower = query.lowercase()
        return words.any { lower.contains(it) }
    }

    private companion object {
        val PLANNING_WORDS = listOf("目标", "规划", "计划", "方向", "阶段", "行动", "长期", "人生", "拆")
        val JOURNAL_WORDS = listOf("日记", "记录", "情绪", "睡眠", "焦虑", "复盘", "状态", "感受", "成长")
        val STOP_WORDS = setOf(
            "the", "and", "how", "what", "why", "can", "you", "help", "with", "for",
            "我", "我们", "我的", "最近", "怎么", "如何", "可以", "帮我", "一下", "现在",
            "应该", "什么", "一个", "这个", "那个", "需要", "想要", "是否", "以及",
        )
    }
}
