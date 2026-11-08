package com.mengyalife.app.data.repository

import com.mengyalife.app.data.local.goal.GoalCheckinEntity
import com.mengyalife.app.data.local.goal.GoalCheckinStat
import com.mengyalife.app.data.local.goal.GoalDao
import com.mengyalife.app.data.restore.RestoreWriteBarrier
import com.mengyalife.app.data.local.goal.GoalEntity
import com.mengyalife.app.domain.goals.CheckinSource
import com.mengyalife.app.domain.goals.ProgressKind
import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalRef
import com.mengyalife.app.domain.goals.GoalRules
import com.mengyalife.app.domain.advisor.AdvisorContextSource
import com.mengyalife.app.domain.advisor.AdvisorGoalChoice
import com.mengyalife.app.domain.advisor.AdvisorGoalContext
import com.mengyalife.app.domain.goals.GoalStatus
import com.mengyalife.app.domain.goals.GoalTrends
import com.mengyalife.app.util.MetricFormat
import com.mengyalife.app.util.Timestamps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** 树上一个节点 + 它的直接子节点 + 进展统计。 */
data class GoalNode(
    val goal: GoalEntity,
    val children: List<GoalNode>,
    val checkinCount: Int,
    val lastCheckinAt: Long?,
    val lastCheckinNote: String? = null,
    val lastCheckinValue: Double? = null,
    val lastCheckinUnit: String? = null,
) {
    val hasCheckins: Boolean get() = checkinCount > 0
}

/**
 * 目标页 / 总览需要的只读快照。
 *
 * 统计口径（Gate 2C 起明确写死，避免「进行中」到底算哪一层说不清）：
 * - [longTermCount]：长期目标数量（层级 LONG_TERM）；
 * - [stageCount] / [actionCount]：阶段目标 / 短期行动数量；
 * - [inProgressCount]：**全部层级**中状态为「进行中」的目标数量；
 * - [longTermInProgressCount]：其中长期目标里状态为「进行中」的数量；
 * - [doneCount]：**全部层级**中状态为「已完成」的目标数量。
 * 「未开始」「已暂停」「已取消」都不计入进行中。
 */
data class GoalSnapshot(
    val roots: List<GoalNode> = emptyList(),
    val totalGoals: Int = 0,
    val longTermCount: Int = 0,
    val stageCount: Int = 0,
    val actionCount: Int = 0,
    val longTermInProgressCount: Int = 0,
    val inProgressCount: Int = 0,
    val doneCount: Int = 0,
    val lastCheckinAt: Long? = null,
)

/** 单个目标的数值趋势（同一目标、同一单位一条）。 */
data class GoalTrend(
    val goalId: Long,
    val goalTitle: String,
    val series: GoalTrends.TrendSeries,
) {
    val unitLabel: String get() = series.unitLabel
    val points: List<GoalTrends.TrendPoint> get() = series.points
    val latest: GoalTrends.TrendPoint get() = series.latest
}

/** 趋势页读取的真实数据快照。 */
data class TrendSnapshot(
    val trends: List<GoalTrend> = emptyList(),
    val numericCheckinCount: Int = 0,
    val goalsWithNumeric: Int = 0,
) {
    val isEmpty: Boolean get() = trends.isEmpty()
}

/** 删除前的影响评估：删这个目标会连带删掉多少东西。 */
data class DeleteImpact(
    val goalCount: Int,
    val stageCount: Int,
    val actionCount: Int,
    val checkinCount: Int,
) {
    val isEmpty: Boolean get() = goalCount == 0
    val descendantCount: Int get() = (goalCount - 1).coerceAtLeast(0)
}

/** 新建/编辑目标的输入。 */
data class GoalDraft(
    val title: String,
    val description: String?,
    val domain: GoalDomain,
    val status: GoalStatus,
    val startDate: Long?,
    val dueDate: Long?,
)

sealed interface GoalResult<out T> {
    data class Ok<T>(val value: T) : GoalResult<T>
    data class Error(val message: String) : GoalResult<Nothing>
}

/**
 * 目标仓库：**唯一**允许写目标表的地方。
 *
 * 负责：层级校验、循环检测、时间戳统一、删除影响评估、进展记录写入。
 * 数据库本身只是存储；业务约束都在这里（并由 `GoalRules` 纯函数单测覆盖）。
 */
class GoalRepository(
    private val dao: GoalDao,
    private val canWrite: () -> Boolean = { true },
    private val writeBarrier: RestoreWriteBarrier? = null,
    private val now: () -> Long = System::currentTimeMillis,
) : AdvisorContextSource {
    private fun ensureWritable() {
        if (!canWrite()) throw IllegalStateException("恢复维护中，禁止写入目标")
    }

    private suspend fun <T> guarded(expectedGeneration: Long? = writeBarrier?.generation(), block: suspend () -> T): T =
        writeBarrier?.withWrite(expectedGeneration) { ensureWritable(); block() } ?: run { ensureWritable(); block() }

    fun observeSnapshot(): Flow<GoalSnapshot> =
        combine(dao.observeGoals(), dao.observeCheckinStats()) { goals, stats ->
            buildSnapshot(goals, stats)
        }

    fun observeCheckins(goalId: Long): Flow<List<GoalCheckinEntity>> = dao.observeCheckins(goalId)

    /**
     * 真实数值趋势：数据只来自 `goal_checkins` 里用户手填的数值。
     *
     * 按「目标 + 单位」分组（不同单位不混画）；非有限数被 [GoalTrends] 过滤。
     */
    fun observeTrends(): Flow<TrendSnapshot> =
        combine(dao.observeGoals(), dao.observeAllCheckins()) { goals, checkins ->
            buildTrends(goals, checkins)
        }

    fun observeRecentCheckins(limit: Int = 3): Flow<List<GoalCheckinEntity>> =
        dao.observeRecentCheckins(limit)

    suspend fun findGoal(id: Long): GoalEntity? = dao.findGoal(id)

    // ---------------------------------------------------------------- 新建

    suspend fun createGoal(
        level: GoalLevel,
        parentId: Long?,
        draft: GoalDraft,
    ): GoalResult<Long> = guarded {
        val parent = parentId?.let { dao.findGoal(it) }
        if (parentId != null && parent == null) return@guarded GoalResult.Error(GoalRules.ERROR_PARENT_MISSING)

        GoalRules.validateParent(
            level = level,
            parent = parent?.let { GoalRef(it.id, it.level, it.parentId) },
        )?.let { return@guarded GoalResult.Error(it) }

        validateDraft(draft)?.let { return@guarded GoalResult.Error(it) }

        val timestamp = now()
        val id = dao.insertGoal(
            GoalEntity(
                title = draft.title.trim(),
                description = draft.description?.trim()?.takeIf { it.isNotEmpty() },
                level = level,
                parentId = parentId,
                domain = draft.domain,
                status = draft.status,
                startDate = draft.startDate,
                dueDate = draft.dueDate,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
        return@guarded GoalResult.Ok(id)

    }

    // ---------------------------------------------------------------- 编辑

    suspend fun updateGoal(id: Long, draft: GoalDraft): GoalResult<Unit> = guarded {
        val existing = dao.findGoal(id) ?: return@guarded GoalResult.Error("目标不存在")
        validateDraft(draft)?.let { return@guarded GoalResult.Error(it) }

        dao.updateGoal(
            existing.copy(
                title = draft.title.trim(),
                description = draft.description?.trim()?.takeIf { it.isNotEmpty() },
                domain = draft.domain,
                status = draft.status,
                startDate = draft.startDate,
                dueDate = draft.dueDate,
                updatedAt = now(),
            ),
        )
        return@guarded GoalResult.Ok(Unit)

    }

    suspend fun changeStatus(id: Long, status: GoalStatus): GoalResult<Unit> = guarded {
        val existing = dao.findGoal(id) ?: return@guarded GoalResult.Error("目标不存在")
        dao.updateGoal(existing.copy(status = status, updatedAt = now()))
        return@guarded GoalResult.Ok(Unit)

    }

    // ---------------------------------------------------------------- 删除

    /** 计算删除 [id] 会连带影响多少目标与进展记录。 */
    suspend fun assessDeleteImpact(id: Long): DeleteImpact {
        val all = dao.loadAllGoals()
        val subtree = collectSubtreeIds(id, all)
        val checkins = if (subtree.isEmpty()) 0 else dao.countCheckinsForGoals(subtree)
        val byId = all.associateBy { it.id }
        return DeleteImpact(
            goalCount = subtree.size,
            stageCount = subtree.count { byId[it]?.level == GoalLevel.STAGE },
            actionCount = subtree.count { byId[it]?.level == GoalLevel.ACTION },
            checkinCount = checkins,
        )
    }

    /**
     * 删除目标及其全部子目标与进展记录。
     * 只在用户**明确确认影响范围**后调用（UI 会先展示 [assessDeleteImpact] 的结果）。
     */
    suspend fun deleteGoalTree(id: Long): GoalResult<DeleteImpact> = guarded {
        val all = dao.loadAllGoals()
        val subtree = collectSubtreeIds(id, all)
        if (subtree.isEmpty()) return@guarded GoalResult.Error("目标不存在")
        val impact = assessDeleteImpact(id)
        dao.deleteGoalTree(subtree)
        return@guarded GoalResult.Ok(impact)

    }

    // ---------------------------------------------------------------- 进展

    suspend fun addCheckin(
        goalId: Long,
        note: String,
        metricValue: Double?,
        metricUnit: String?,
        source: String = CheckinSource.MANUAL,
        recordedAt: Long? = null,
        amountKind: String = ProgressKind.SNAPSHOT,
        originalText: String? = null,
        normalizedValue: Double? = null,
        normalizedUnit: String? = null,
    ): GoalResult<Long> = guarded {
        dao.findGoal(goalId) ?: return@guarded GoalResult.Error("目标不存在")
        GoalRules.validateCheckinNote(note)?.let { return@guarded GoalResult.Error(it) }
        // 数据层兜底：NaN / Infinity / 超出范围一律拒绝，不依赖界面先校验。
        // 即使以后接导入、脚本或其它入口，也无法把非法数值写进 goal_checkins。
        GoalRules.validateMetricValue(metricValue)?.let { return@guarded GoalResult.Error(it) }
        GoalRules.validateUnit(metricUnit)?.let { return@guarded GoalResult.Error(it) }

        val id = dao.insertCheckin(
            GoalCheckinEntity(
                goalId = goalId,
                recordedAt = recordedAt ?: now(),
                note = note.trim(),
                metricValue = metricValue,
                metricUnit = GoalRules.normalizeUnit(metricUnit),
                source = source,
                amountKind = amountKind,
                originalText = originalText?.takeIf { it.isNotBlank() },
                normalizedValue = normalizedValue,
                normalizedUnit = normalizedUnit?.takeIf { it.isNotBlank() },
            ),
        )
        // 记录进展时，若目标还没开始，自动置为「进行中」更符合直觉
        val goal = dao.findGoal(goalId)
        if (goal != null && goal.status == GoalStatus.NOT_STARTED) {
            dao.updateGoal(goal.copy(status = GoalStatus.IN_PROGRESS, updatedAt = now()))
        }
        return@guarded GoalResult.Ok(id)

    }

    /**
     * 修改已经写入的进展记录（顾问记录卡片里的「修改」与后续纠错）。
     * 只改数值/单位/说明，不改变目标与来源；复用同一套校验。
     */
    suspend fun updateCheckin(
        checkinId: Long,
        note: String,
        metricValue: Double?,
        metricUnit: String?,
        amountKind: String? = null,
        normalizedValue: Double? = null,
        normalizedUnit: String? = null,
    ): GoalResult<Unit> = guarded {
        val existing = dao.findCheckin(checkinId) ?: return@guarded GoalResult.Error("进展记录不存在")
        GoalRules.validateCheckinNote(note)?.let { return@guarded GoalResult.Error(it) }
        GoalRules.validateMetricValue(metricValue)?.let { return@guarded GoalResult.Error(it) }
        GoalRules.validateUnit(metricUnit)?.let { return@guarded GoalResult.Error(it) }
        dao.updateCheckin(
            id = checkinId,
            note = note.trim(),
            metricValue = metricValue,
            metricUnit = GoalRules.normalizeUnit(metricUnit),
            amountKind = amountKind ?: existing.amountKind,
            normalizedValue = normalizedValue,
            normalizedUnit = normalizedUnit?.takeIf { it.isNotBlank() },
        )
        return@guarded GoalResult.Ok(Unit)
    }

    /** 撤销一条进展记录（只用于撤销顾问刚写入且用户确认撤销的记录）。 */
    suspend fun deleteCheckin(checkinId: Long): GoalResult<Unit> = guarded {
        dao.findCheckin(checkinId) ?: return@guarded GoalResult.Error("进展记录不存在")
        dao.deleteCheckin(checkinId)
        return@guarded GoalResult.Ok(Unit)
    }

    // ---------------------------------------------------------------- 顾问上下文

    /**
     * 为「用户明确选择的目标」构造可发送摘要。
     *
     * 只读，且只包含这一个目标子树；**不读取日记**，不包含其它目标。
     * 由 AdvisorViewModel 在用户确认后调用。
     */
    override fun observeGoalChoices(): Flow<List<AdvisorGoalChoice>> =
        observeSnapshot().map { snapshot ->
            snapshot.roots.map { AdvisorGoalChoice(id = it.goal.id, title = it.goal.title) }
        }

    override suspend fun buildContext(goalId: Long): AdvisorGoalContext? {
        val goal = dao.findGoal(goalId) ?: return null
        val all = dao.loadAllGoals()
        val subtree = collectSubtreeIds(goalId, all)
        if (subtree.isEmpty()) return null
        val byId = all.associateBy { it.id }
        val stages = subtree
            .mapNotNull { byId[it] }
            .filter { it.level == GoalLevel.STAGE }
            .sortedBy { it.createdAt }
            .take(8)
        val stageIds = stages.map { it.id }.toSet()
        val actions = subtree
            .mapNotNull { byId[it] }
            .filter { it.level == GoalLevel.ACTION && it.parentId in stageIds }
            .sortedBy { it.createdAt }
            .take(12)
        val checkins = dao.loadCheckinsForGoals(subtree)
            .sortedWith(compareByDescending<GoalCheckinEntity> { it.recordedAt }.thenByDescending { it.id })
        val recent = checkins.take(3).map { checkin ->
            buildString {
                append(Timestamps.formatDate(checkin.recordedAt))
                append(" · ")
                append(checkin.note.replace('\n', ' ').take(80))
                checkin.metricValue?.let { value ->
                    append("（")
                    append(MetricFormat.withUnit(value, checkin.metricUnit))
                    append("）")
                }
            }
        }
        return AdvisorGoalContext(
            goalId = goal.id,
            title = goal.title,
            description = goal.description,
            stageTitles = stages.map { it.title },
            actionTitles = actions.map { it.title },
            recentCheckinLines = recent,
            totalCheckins = checkins.size,
        )
    }

    // ---------------------------------------------------------------- 内部

    private fun validateDraft(draft: GoalDraft): String? =
        GoalRules.validateTitle(draft.title)
            ?: GoalRules.validateDescription(draft.description)
            ?: GoalRules.validateDates(draft.startDate, draft.dueDate)

    /** 收集 id 自身 + 全部后代（内存计算，目标量级很小）。 */
    private fun collectSubtreeIds(rootId: Long, all: List<GoalEntity>): List<Long> {
        if (all.none { it.id == rootId }) return emptyList()
        val childrenOf = all.groupBy { it.parentId }
        val result = mutableListOf<Long>()
        val queue = ArrayDeque<Long>()
        queue.add(rootId)
        val seen = mutableSetOf<Long>()
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!seen.add(current)) continue
            result.add(current)
            childrenOf[current]?.forEach { queue.add(it.id) }
        }
        return result
    }

    private fun buildSnapshot(goals: List<GoalEntity>, stats: List<GoalCheckinStat>): GoalSnapshot {
        val statByGoal = stats.associateBy { it.goalId }
        val childrenOf = goals.groupBy { it.parentId }

        fun node(goal: GoalEntity): GoalNode {
            val children = childrenOf[goal.id]
                ?.sortedWith(compareBy<GoalEntity> { it.level.ordinal }.thenBy { it.createdAt })
                ?.map { node(it) }
                ?: emptyList()
            val stat = statByGoal[goal.id]
            return GoalNode(
                goal = goal,
                children = children,
                checkinCount = stat?.checkinCount ?: 0,
                lastCheckinAt = stat?.lastAt,
                lastCheckinNote = stat?.lastNote,
                lastCheckinValue = stat?.lastValue,
                lastCheckinUnit = stat?.lastUnit,
            )
        }

        val roots = childrenOf[null]
            ?.filter { it.level == GoalLevel.LONG_TERM }
            ?.sortedBy { it.createdAt }
            ?.map { node(it) }
            ?: emptyList()

        return GoalSnapshot(
            roots = roots,
            totalGoals = goals.size,
            longTermCount = goals.count { it.level == GoalLevel.LONG_TERM },
            stageCount = goals.count { it.level == GoalLevel.STAGE },
            actionCount = goals.count { it.level == GoalLevel.ACTION },
            longTermInProgressCount = goals.count {
                it.level == GoalLevel.LONG_TERM && it.status == GoalStatus.IN_PROGRESS
            },
            inProgressCount = goals.count { it.status == GoalStatus.IN_PROGRESS },
            doneCount = goals.count { it.status == GoalStatus.DONE },
            lastCheckinAt = stats.mapNotNull { it.lastAt }.maxOrNull(),
        )
    }

    /** 把数据库行整理成「目标 + 单位」的趋势序列，过滤掉非法数值。 */
    private fun buildTrends(
        goals: List<GoalEntity>,
        checkins: List<GoalCheckinEntity>,
    ): TrendSnapshot {
        if (checkins.isEmpty()) return TrendSnapshot()

        val titleById = goals.associate { it.id to it.title }
        val trends = checkins
            .filter { it.goalId in titleById }
            .groupBy { it.goalId }
            .flatMap { (goalId, rows) ->
                val metrics = rows.map {
                    GoalTrends.MetricRecord(
                        checkinId = it.id,
                        recordedAt = it.recordedAt,
                        value = it.normalizedValue ?: it.metricValue,
                        unit = it.normalizedUnit ?: it.metricUnit,
                        kind = it.amountKind,
                    )
                }
                GoalTrends.seriesFrom(metrics).map { series ->
                    GoalTrend(
                        goalId = goalId,
                        goalTitle = titleById[goalId].orEmpty(),
                        series = series,
                    )
                }
            }
            .sortedWith(
                compareByDescending<GoalTrend> { it.latest.recordedAt }
                    .thenBy { it.goalTitle }
                    .thenBy { it.unitLabel },
            )

        return TrendSnapshot(
            trends = trends,
            numericCheckinCount = trends.sumOf { it.points.size },
            goalsWithNumeric = trends.map { it.goalId }.distinct().size,
        )
    }
}
