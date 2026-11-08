package com.mengyalife.app.data.advisor

import androidx.room.withTransaction
import com.mengyalife.app.data.local.MengyaDatabase
import com.mengyalife.app.data.local.proposal.RecordProposalDao
import com.mengyalife.app.data.repository.SmallPlanRepository
import com.mengyalife.app.data.repository.SmallPlanStatus
import com.mengyalife.app.data.restore.RestoreWriteBarrier
import com.mengyalife.app.domain.advisor.PlanChangeKind
import com.mengyalife.app.domain.advisor.RecordProposalStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface PlanApplyResult {
    data class Success(val planId: Long) : PlanApplyResult
    data class Failure(val message: String) : PlanApplyResult
}

interface PlanRecordApply {
    suspend fun apply(proposalId: String): PlanApplyResult
    suspend fun applyProgress(proposalId: String): PlanApplyResult
}

/**
 * 小计划的正式写入：只经 SmallPlanRepository，创建与状态更新都保留历史事件。
 * 状态变更只改 small_plans.status，不删除计划。
 */
class PlanRecordApplyService(
    private val database: MengyaDatabase,
    private val recordDao: RecordProposalDao,
    private val planRepository: SmallPlanRepository,
    private val canWrite: () -> Boolean = { true },
    private val writeBarrier: RestoreWriteBarrier? = null,
) : PlanRecordApply {
    private val mutex = Mutex()

    override suspend fun apply(proposalId: String): PlanApplyResult = mutex.withLock {
        val entity = recordDao.findById(proposalId)
            ?: return@withLock PlanApplyResult.Failure("这条计划候选不存在")
        if (entity.status == RecordProposalStatus.APPLIED) {
            return@withLock entity.appliedPlanId?.let { PlanApplyResult.Success(it) }
                ?: PlanApplyResult.Failure("这条计划已经处理过")
        }
        if (entity.status != RecordProposalStatus.DRAFT) {
            return@withLock PlanApplyResult.Failure("这条计划候选已经取消")
        }
        val change = PlanChangeCodec.decode(entity.planChangeJson)
            ?: return@withLock PlanApplyResult.Failure("计划草稿不完整")
        if (!canWrite()) return@withLock PlanApplyResult.Failure("恢复维护中，暂不能保存计划")

        val write: suspend () -> PlanApplyResult = {
            try {
                database.withTransaction {
                    val planId = when (change.change) {
                        PlanChangeKind.CREATE -> {
                            val plan = planRepository.create(
                                title = change.title.orEmpty(),
                                timeScope = change.timeScope,
                                goalId = change.goalId,
                                sourceMessageId = entity.messageId,
                            )
                            plan.id
                        }
                        PlanChangeKind.STATUS -> {
                            val target = change.targetPlanId
                                ?: throw PlanAbort("没有定位到要更新的计划")
                            val status = change.newStatus ?: SmallPlanStatus.ACTIVE
                            planRepository.changeStatus(target, status, change.note)
                                ?: throw PlanAbort("这条计划已经不存在")
                            target
                        }
                    }
                    val moved = recordDao.markPlanAppliedIfDraft(
                        id = proposalId,
                        appliedAt = System.currentTimeMillis(),
                        planId = planId,
                        planChangeJson = entity.planChangeJson,
                    )
                    if (moved == 0) throw PlanAbort("候选状态已变化")
                    PlanApplyResult.Success(planId)
                }
            } catch (abort: PlanAbort) {
                PlanApplyResult.Failure(abort.message ?: "保存计划失败")
            } catch (error: Exception) {
                PlanApplyResult.Failure(error.message ?: "保存计划失败")
            }
        }
        writeBarrier?.withWrite(writeBarrier.generation()) { write() } ?: write()
    }

    override suspend fun applyProgress(proposalId: String): PlanApplyResult = mutex.withLock {
        val entity = recordDao.findById(proposalId)
            ?: return@withLock PlanApplyResult.Failure("这条执行候选不存在")
        if (entity.status == RecordProposalStatus.APPLIED) {
            return@withLock entity.appliedPlanId?.let { PlanApplyResult.Success(it) }
                ?: PlanApplyResult.Failure("这条执行已经处理过")
        }
        if (entity.status != RecordProposalStatus.DRAFT) {
            return@withLock PlanApplyResult.Failure("这条执行候选已经取消")
        }
        val progress = PlanChangeCodec.decodeProgress(entity.planProgressJson)
            ?: return@withLock PlanApplyResult.Failure("执行草稿不完整")
        val planId = progress.planId ?: return@withLock PlanApplyResult.Failure("没有定位到对应计划")
        if (!canWrite()) return@withLock PlanApplyResult.Failure("恢复维护中，暂不能记录执行")
        val write: suspend () -> PlanApplyResult = {
            try {
                database.withTransaction {
                    // 唯一 sourceMessageId：重复提交不会重复计数。
                    planRepository.addProgress(planId, progress.note, entity.messageId)
                    val moved = recordDao.markPlanAppliedIfDraft(
                        id = proposalId,
                        appliedAt = System.currentTimeMillis(),
                        planId = planId,
                        planChangeJson = entity.planProgressJson,
                    )
                    if (moved == 0) throw PlanAbort("候选状态已变化")
                    PlanApplyResult.Success(planId)
                }
            } catch (abort: PlanAbort) {
                PlanApplyResult.Failure("这条执行已经处理过")
            } catch (error: Exception) {
                PlanApplyResult.Failure(error.message ?: "记录执行失败")
            }
        }
        writeBarrier?.withWrite(writeBarrier.generation()) { write() } ?: write()
    }

    private class PlanAbort(override val message: String) : Exception(message)
}
