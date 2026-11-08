package com.mengyalife.app.data.advisor

import androidx.room.withTransaction
import com.mengyalife.app.data.local.MengyaDatabase
import com.mengyalife.app.data.local.proposal.RecordProposalDao
import com.mengyalife.app.data.repository.GoalDraft
import com.mengyalife.app.data.repository.GoalRepository
import com.mengyalife.app.data.repository.GoalResult
import com.mengyalife.app.data.restore.RestoreWriteBarrier
import com.mengyalife.app.domain.advisor.GoalChangeDraft
import com.mengyalife.app.domain.advisor.GoalChangeKind
import com.mengyalife.app.domain.advisor.RecordProposalStatus
import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface GoalApplyResult {
    data class Success(val goalIds: List<Long>) : GoalApplyResult
    data class Failure(val message: String) : GoalApplyResult
}

interface GoalRecordApply {
    suspend fun apply(proposalId: String): GoalApplyResult
}

/**
 * 目标候选的正式写入。
 *
 * 约束：
 * - 只经 [GoalRepository] 的 create/update，不直接写 goals 表，也不删除重建；
 * - 修改已有目标保留 id、状态与全部进展/日记关联；
 * - 拆解只创建用户勾选的少量子目标；
 * - 重复点击/重试幂等，返回已创建的目标 id。
 */
class GoalRecordApplyService(
    private val database: MengyaDatabase,
    private val recordDao: RecordProposalDao,
    private val goalRepository: GoalRepository,
    private val canWrite: () -> Boolean = { true },
    private val writeBarrier: RestoreWriteBarrier? = null,
) : GoalRecordApply {

    private val mutex = Mutex()

    override suspend fun apply(proposalId: String): GoalApplyResult = mutex.withLock {
        val entity = recordDao.findById(proposalId)
            ?: return@withLock GoalApplyResult.Failure("这条目标候选不存在")
        val existingIds = entity.appliedGoalIdsJson
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            .orEmpty()
        if (entity.status == RecordProposalStatus.APPLIED) {
            return@withLock GoalApplyResult.Success(existingIds)
        }
        if (entity.status != RecordProposalStatus.DRAFT) {
            return@withLock GoalApplyResult.Failure("这条目标候选已经取消")
        }
        val change = GoalChangeCodec.decode(entity.goalChangeJson)
            ?: return@withLock GoalApplyResult.Failure("目标草稿不完整")
        if (!canWrite()) return@withLock GoalApplyResult.Failure("恢复维护中，暂不能修改目标")

        val write: suspend () -> GoalApplyResult = {
            try {
                database.withTransaction {
                    val created = mutableListOf<Long>()
                    when (change.change) {
                        GoalChangeKind.CREATE -> {
                            val parentId = change.parentId
                            val level = parseLevel(change.levelName) ?: GoalLevel.LONG_TERM
                            val title = change.title?.trim().orEmpty()
                            if (title.isEmpty()) throw GoalAbort("目标标题不能为空")
                            if (level != GoalLevel.LONG_TERM && parentId == null) {
                                throw GoalAbort("阶段目标/行动需要先有一个上级目标")
                            }
                            val id = createGoal(
                                title = title,
                                description = change.description,
                                domain = parseDomain(change.domainName) ?: GoalDomain.LIFE,
                                level = level,
                                parentId = parentId,
                                dueAt = change.dueAt,
                            )
                            created += id
                            createBreakdown(change, parentId = id, parentLevel = level, into = created)
                        }

                        GoalChangeKind.UPDATE -> {
                            val targetId = change.targetGoalId
                                ?: throw GoalAbort("没有定位到要修改的目标")
                            val existing = goalRepository.findGoal(targetId)
                                ?: throw GoalAbort("目标已经不存在")
                            val merged = GoalDraft(
                                title = change.title?.trim()?.takeIf { it.isNotEmpty() } ?: existing.title,
                                description = change.description ?: existing.description,
                                domain = parseDomain(change.domainName) ?: existing.domain,
                                status = existing.status,
                                startDate = existing.startDate,
                                dueDate = change.dueAt ?: existing.dueDate,
                            )
                            when (val result = goalRepository.updateGoal(existing.id, merged)) {
                                is GoalResult.Ok -> created += existing.id
                                is GoalResult.Error -> throw GoalAbort(result.message)
                            }
                            // 修改时若同时给出拆解建议，也挂到同一个目标下。
                            createBreakdown(change, parentId = existing.id, parentLevel = existing.level, into = created)
                        }

                        GoalChangeKind.BREAKDOWN -> {
                            val targetId = change.targetGoalId
                                ?: throw GoalAbort("没有定位到要拆解的目标")
                            val existing = goalRepository.findGoal(targetId)
                                ?: throw GoalAbort("目标已经不存在")
                            created += existing.id
                            createBreakdown(change, parentId = existing.id, parentLevel = existing.level, into = created)
                        }
                    }
                    val moved = recordDao.markGoalAppliedIfDraft(
                        id = proposalId,
                        appliedAt = System.currentTimeMillis(),
                        goalIdsJson = created.joinToString(","),
                        goalChangeJson = entity.goalChangeJson,
                    )
                    if (moved == 0) throw GoalAbort("候选状态已变化")
                    GoalApplyResult.Success(created.distinct())
                }
            } catch (abort: GoalAbort) {
                GoalApplyResult.Failure(abort.message ?: "保存目标失败")
            } catch (error: Exception) {
                GoalApplyResult.Failure(error.message ?: "保存目标失败")
            }
        }
        writeBarrier?.withWrite(writeBarrier.generation()) { write() } ?: write()
    }

    private suspend fun createBreakdown(
        change: GoalChangeDraft,
        parentId: Long,
        parentLevel: GoalLevel,
        into: MutableList<Long>,
    ) {
        val selected = change.breakdown.filter { it.selected }
        if (selected.isEmpty()) return
        val childLevel = when (parentLevel) {
            GoalLevel.LONG_TERM -> GoalLevel.STAGE
            GoalLevel.STAGE -> GoalLevel.ACTION
            GoalLevel.ACTION -> return
        }
        selected.take(4).forEach { item ->
            into += createGoal(
                title = item.title,
                description = null,
                domain = parseDomain(change.domainName) ?: GoalDomain.LIFE,
                level = childLevel,
                parentId = parentId,
                dueAt = null,
            )
        }
    }

    private suspend fun createGoal(
        title: String,
        description: String?,
        domain: GoalDomain,
        level: GoalLevel,
        parentId: Long?,
        dueAt: Long?,
    ): Long = when (
        val result = goalRepository.createGoal(
            level = level,
            parentId = parentId,
            draft = GoalDraft(
                title = title,
                description = description,
                domain = domain,
                status = GoalStatus.NOT_STARTED,
                startDate = null,
                dueDate = dueAt,
            ),
        )
    ) {
        is GoalResult.Ok -> result.value
        is GoalResult.Error -> throw GoalAbort(result.message)
    }

    private fun parseLevel(raw: String?): GoalLevel? =
        GoalLevel.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }

    private fun parseDomain(raw: String?): GoalDomain? =
        GoalDomain.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }

    private class GoalAbort(override val message: String) : Exception(message)
}
