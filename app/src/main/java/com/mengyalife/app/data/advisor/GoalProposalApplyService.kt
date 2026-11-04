package com.mengyalife.app.data.advisor

import androidx.room.withTransaction
import com.mengyalife.app.data.local.MengyaDatabase
import com.mengyalife.app.data.local.proposal.GoalProposalDao
import com.mengyalife.app.data.repository.GoalDraft
import com.mengyalife.app.data.repository.GoalRepository
import com.mengyalife.app.data.repository.GoalResult
import com.mengyalife.app.domain.advisor.GoalProposalDraft
import com.mengyalife.app.domain.advisor.GoalProposalNode
import com.mengyalife.app.domain.advisor.GoalProposalParser
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalRef
import com.mengyalife.app.domain.goals.GoalRules
import com.mengyalife.app.domain.goals.GoalStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.ZoneId

data class AppliedGoal(
    val tempId: String,
    val goalId: Long,
    val title: String,
    val level: GoalLevel,
)

sealed interface ProposalApplyResult {
    data class Success(val created: List<AppliedGoal>) : ProposalApplyResult
    data class Failure(val message: String) : ProposalApplyResult
}

sealed interface ProposalUndoResult {
    data class Success(val alreadyUndone: Boolean = false) : ProposalUndoResult
    data class Failure(val message: String) : ProposalUndoResult
}

interface GoalProposalApply {
    suspend fun apply(proposalId: String, selectedTempIds: Set<String>): ProposalApplyResult
    suspend fun undo(proposalId: String): ProposalUndoResult
}

/**
 * 唯一允许把草案写入正式目标的服务。
 *
 * - 只有用户确认后由 ViewModel 调用；
 * - 用户选择 + schema + 层级 + 父子 + 日期 + 已有父目标全部重新校验；
 * - [MengyaDatabase.withTransaction]：任意节点失败整体回滚；
 * - DRAFT→APPLIED 与实际写入同一事务，重复点击/重启后重复提交不会重复创建；
 * - 撤销只删本次创建且未被修改/挂接数据的目标，幂等。
 */
class GoalProposalApplyService(
    private val database: MengyaDatabase,
    private val proposalDao: GoalProposalDao,
    private val goalRepository: GoalRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : GoalProposalApply {
    private val mutex = Mutex()

    override suspend fun apply(proposalId: String, selectedTempIds: Set<String>): ProposalApplyResult =
        mutex.withLock {
            try {
                database.withTransaction {
                    val entity = proposalDao.findById(proposalId)
                        ?: return@withTransaction ProposalApplyResult.Failure("草案不存在")
                    if (entity.status != "DRAFT") {
                        return@withTransaction ProposalApplyResult.Failure(
                            when (entity.status) {
                                "APPLIED" -> "该草案已经创建过目标，不能重复创建"
                                "UNDONE" -> "该草案已撤销，不能再次创建；请重新生成草案"
                                else -> "草案状态不允许创建"
                            },
                        )
                    }
                    val draft = GoalProposalParser.decode(
                        entity.schemaVersion,
                        entity.summary,
                        entity.assumptionsJson,
                        entity.risksJson,
                        entity.nodesJson,
                    ) ?: return@withTransaction ProposalApplyResult.Failure("草案数据损坏，无法创建")

                    val existingGoals = database.goalDao().loadAllGoals()
                    val existingById = existingGoals.associateBy { it.id }
                    val nodesById = draft.nodes.associateBy { it.tempId }
                    val selected = selectedTempIds.intersect(nodesById.keys)
                    if (selected.isEmpty()) {
                        return@withTransaction ProposalApplyResult.Failure("至少选择一个要创建的节点")
                    }

                    val validation = validateSelection(draft, selected, nodesById, existingById)
                    if (validation != null) {
                        return@withTransaction ProposalApplyResult.Failure(validation)
                    }

                    val created = mutableListOf<AppliedGoal>()
                    val tempToReal = mutableMapOf<String, Long>()
                    val ordered = draft.nodes
                        .filter { it.tempId in selected }
                        .sortedWith(compareBy { it.level.ordinal })

                    for (node in ordered) {
                        val parentId = when {
                            node.level == GoalLevel.LONG_TERM -> null
                            node.parentTempId != null -> tempToReal[node.parentTempId]
                                ?: throw ApplyAbort("父节点尚未创建：${node.parentTempId}")
                            else -> node.parentGoalId
                        }
                        val draftGoal = GoalDraft(
                            title = node.title.trim(),
                            description = node.description?.trim(),
                            domain = node.domain,
                            status = GoalStatus.NOT_STARTED,
                            startDate = GoalProposalApplyRules.parseDate(node.startDate),
                            dueDate = GoalProposalApplyRules.parseDate(node.dueDate),
                        )
                        when (val result = goalRepository.createGoal(node.level, parentId, draftGoal)) {
                            is GoalResult.Ok -> {
                                tempToReal[node.tempId] = result.value
                                created.add(AppliedGoal(node.tempId, result.value, node.title, node.level))
                            }
                            is GoalResult.Error -> throw ApplyAbort(result.message)
                        }
                    }

                    // 用对象存 tempId→goalId，便于撤销/审计；不是数组。
                    val mappingJson = created.joinToString(prefix = "{", postfix = "}") {
                        "\"${it.tempId.replace("\"", "\\\"")}\":${it.goalId}"
                    }
                    val selectedJson = selected.sorted().joinToString(prefix = "[", postfix = "]") {
                        "\"${it.replace("\"", "\\\"")}\""
                    }
                    val changed = proposalDao.markAppliedIfDraft(
                        id = proposalId,
                        appliedAt = now(),
                        goalIdsJson = mappingJson,
                        selectedJson = selectedJson,
                    )
                    if (changed == 0) throw ApplyAbort("草案状态已变化，本次创建已回滚")
                    ProposalApplyResult.Success(created)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (abort: ApplyAbort) {
                ProposalApplyResult.Failure(abort.message ?: "创建失败，已回滚")
            } catch (error: Throwable) {
                ProposalApplyResult.Failure("创建失败，已回滚：${error.message ?: "数据库错误"}")
            }
        }

    override suspend fun undo(proposalId: String): ProposalUndoResult = mutex.withLock {
        try {
            database.withTransaction {
                val entity = proposalDao.findById(proposalId)
                    ?: return@withTransaction ProposalUndoResult.Failure("草案不存在")
                if (entity.status == "UNDONE") {
                    return@withTransaction ProposalUndoResult.Success(alreadyUndone = true)
                }
                if (entity.status != "APPLIED") {
                    return@withTransaction ProposalUndoResult.Failure("只有已创建过的草案可以撤销")
                }
                val mapping = decodeMapping(entity.appliedGoalIdsJson)
                if (mapping.isEmpty()) {
                    return@withTransaction ProposalUndoResult.Failure("没有可撤销的创建记录")
                }
                val ids = mapping.values.toSet()
                val all = database.goalDao().loadAllGoals()
                val byId = all.associateBy { it.id }
                for (id in ids) {
                    val goal = byId[id]
                        ?: return@withTransaction ProposalUndoResult.Failure("目标 #$id 已不存在，撤销已阻止")
                    if (goal.updatedAt != goal.createdAt) {
                        return@withTransaction ProposalUndoResult.Failure(
                            "目标「${goal.title}」已被修改；为避免误删，撤销已阻止",
                        )
                    }
                }
                val checkins = database.goalDao().countCheckinsForGoals(ids.toList())
                if (checkins > 0) {
                    return@withTransaction ProposalUndoResult.Failure(
                        "本次创建的目标已有 $checkins 条进展记录；撤销已阻止",
                    )
                }
                val externalChildren = all.filter {
                    it.parentId in ids && it.id !in ids
                }
                if (externalChildren.isNotEmpty()) {
                    return@withTransaction ProposalUndoResult.Failure(
                        "本次创建的目标下已挂接其他目标；撤销已阻止",
                    )
                }
                // 只删本次创建的目标；先删深层，避免外键级联删到外部数据。
                val ordered = ids.sortedByDescending { byId[it]?.level?.ordinal ?: -1 }
                // 已在事务内验证：无进展、无外部子目标、未被修改。
                // 使用 deleteGoalTree 的删除顺序（进展→目标），再回查确认没有残留。
                database.goalDao().deleteGoalTree(ordered)
                val remaining = database.goalDao().loadAllGoals().map { it.id }.toSet()
                if (remaining.any { it in ids }) {
                    throw ApplyAbort("目标删除不完整，撤销已回滚")
                }
                val changed = proposalDao.markUndoneIfApplied(proposalId, now())
                if (changed == 0) throw ApplyAbort("草案状态已变化，撤销已回滚")
                ProposalUndoResult.Success()
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (abort: ApplyAbort) {
            ProposalUndoResult.Failure(abort.message ?: "撤销失败，已回滚")
        } catch (error: Throwable) {
            ProposalUndoResult.Failure("撤销失败，已回滚：${error.message ?: "数据库错误"}")
        }
    }

    private fun validateSelection(
        draft: GoalProposalDraft,
        selected: Set<String>,
        nodesById: Map<String, GoalProposalNode>,
        existing: Map<Long, com.mengyalife.app.data.local.goal.GoalEntity>,
    ): String? {
        val refs = existing.mapValues { (_, goal) -> GoalRef(goal.id, goal.level, goal.parentId) }
        return GoalProposalApplyRules.validate(draft, selected, nodesById, refs)
    }

    private fun decodeMapping(raw: String?): Map<String, Long> = runCatching {
        if (raw.isNullOrBlank()) emptyMap()
        else {
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(raw)
                .jsonObject
            obj.mapValues { it.value.jsonPrimitive.content.toLong() }
        }
    }.getOrDefault(emptyMap())

    private class ApplyAbort(message: String) : Exception(message)
}


/** 纯校验逻辑，便于 JVM 测试；写入服务只做事务与持久化。 */
object GoalProposalApplyRules {
    fun validate(
        draft: GoalProposalDraft,
        selected: Set<String>,
        nodesById: Map<String, GoalProposalNode>,
        existing: Map<Long, GoalRef>,
    ): String? {
        if (selected.isEmpty()) return "至少选择一个要创建的节点"
        for (node in draft.nodes.filter { it.tempId in selected }) {
            if (GoalRules.validateTitle(node.title) != null) return "「${node.title}」标题不合法"
            if (GoalRules.validateDescription(node.description) != null) return "「${node.title}」描述过长"
            if (node.parentTempId != null && node.parentGoalId != null) {
                return "「${node.title}」同时引用临时父级和已有父级"
            }
            val start = parseDate(node.startDate)
            val due = parseDate(node.dueDate)
            if (node.startDate != null && start == null) return "「${node.title}」开始日期不合法"
            if (node.dueDate != null && due == null) return "「${node.title}」截止日期不合法"
            if (GoalRules.validateDates(start, due) != null) {
                return "「${node.title}」截止日期早于开始日期"
            }
            val parentRef: GoalRef? = when {
                node.level == GoalLevel.LONG_TERM -> {
                    if (node.parentTempId != null || node.parentGoalId != null) {
                        return "长期目标「${node.title}」不能有父级"
                    }
                    null
                }
                node.parentTempId != null -> {
                    if (node.parentTempId !in selected) {
                        return "「${node.title}」的父节点未选择，不能只创建子节点"
                    }
                    val parent = nodesById[node.parentTempId]
                        ?: return "「${node.title}」的临时父级不存在"
                    GoalRef(-1, parent.level, null)
                }
                node.parentGoalId != null -> {
                    val parent = existing[node.parentGoalId]
                        ?: return "已有父目标 #${node.parentGoalId} 不存在（可能已被删除）"
                    parent
                }
                else -> return "「${node.title}」缺少父级"
            }
            GoalRules.validateParent(node.level, parentRef)
                ?.let { return "「${node.title}」：$it" }
        }
        return null
    }

    fun parseDate(raw: String?): Long? {
        if (raw == null) return null
        return runCatching {
            LocalDate.parse(raw).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }
}
