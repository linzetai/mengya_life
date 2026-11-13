package com.mengyalife.app.data.advisor

import androidx.room.withTransaction
import com.mengyalife.app.data.local.MengyaDatabase
import com.mengyalife.app.data.local.proposal.RecordProposalDao
import com.mengyalife.app.data.repository.SmallPlanRepository
import com.mengyalife.app.data.repository.SmallPlanStatus
import com.mengyalife.app.data.restore.RestoreException
import com.mengyalife.app.data.restore.RestoreWriteBarrier
import com.mengyalife.app.domain.advisor.PlanChangeKind
import com.mengyalife.app.domain.advisor.RecordProposalStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface PlanApplyResult {
    data class Success(val planId: Long) : PlanApplyResult
    data class Failure(val message: String) : PlanApplyResult
}

interface PlanRecordApply {
    suspend fun apply(proposalId: String): PlanApplyResult
    suspend fun applyProgress(proposalId: String): PlanApplyResult

    /** Validator runs inside the same Room transaction immediately before plan mutation. */
    suspend fun applyValidated(
        proposalId: String,
        expectedRestoreGeneration: Long,
        validateSource: suspend () -> String?,
    ): PlanApplyResult = apply(proposalId)

    suspend fun applyProgressValidated(
        proposalId: String,
        expectedRestoreGeneration: Long,
        validateSource: suspend () -> String?,
    ): PlanApplyResult = applyProgress(proposalId)
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

    override suspend fun apply(proposalId: String): PlanApplyResult {
        val expectedGeneration = writeBarrier?.generation()
            ?: return PlanApplyResult.Failure("恢复安全边界不可用，计划未写入")
        return applyInternal(proposalId, expectedGeneration, null)
    }

    override suspend fun applyValidated(
        proposalId: String,
        expectedRestoreGeneration: Long,
        validateSource: suspend () -> String?,
    ): PlanApplyResult = applyInternal(proposalId, expectedRestoreGeneration, validateSource)

    private suspend fun applyInternal(
        proposalId: String,
        expectedRestoreGeneration: Long,
        validateSource: (suspend () -> String?)?,
    ): PlanApplyResult = mutex.withLock {
        val barrier = writeBarrier ?: return@withLock PlanApplyResult.Failure("恢复安全边界不可用，计划未写入")
        try {
            barrier.withWrite(expectedRestoreGeneration) {
                if (!canWrite()) return@withWrite PlanApplyResult.Failure("恢复维护中，暂不能保存计划")
                val entity = recordDao.findById(proposalId)
                    ?: return@withWrite PlanApplyResult.Failure("这条计划候选不存在")
                if (entity.status == RecordProposalStatus.APPLIED) {
                    return@withWrite entity.appliedPlanId?.let { PlanApplyResult.Success(it) }
                        ?: PlanApplyResult.Failure("这条计划已经处理过")
                }
                if (entity.status != RecordProposalStatus.DRAFT) {
                    return@withWrite PlanApplyResult.Failure("这条计划候选已经取消")
                }
                if (validateSource == null && requiresInsightValidator(entity)) {
                    return@withWrite PlanApplyResult.Failure("此来源相关草案必须经过 Insight 来源与 generation 验证")
                }
                val change = PlanChangeCodec.decode(entity.planChangeJson)
                    ?: return@withWrite PlanApplyResult.Failure("计划草稿不完整")
                try {
                    database.withTransaction {
                        validateSource?.invoke()?.let { throw PlanAbort(it) }
                        val planId = when (change.change) {
                            PlanChangeKind.CREATE -> planRepository.create(
                                title = change.title.orEmpty(),
                                timeScope = change.timeScope,
                                goalId = change.goalId,
                                sourceMessageId = entity.messageId,
                            ).id
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
        } catch (error: RestoreException) {
            PlanApplyResult.Failure("${error.message ?: "恢复 generation 已变化"}；计划未写入")
        }
    }

    override suspend fun applyProgress(proposalId: String): PlanApplyResult {
        val expectedGeneration = writeBarrier?.generation()
            ?: return PlanApplyResult.Failure("恢复安全边界不可用，执行未写入")
        return applyProgressInternal(proposalId, expectedGeneration, null)
    }

    override suspend fun applyProgressValidated(
        proposalId: String,
        expectedRestoreGeneration: Long,
        validateSource: suspend () -> String?,
    ): PlanApplyResult = applyProgressInternal(proposalId, expectedRestoreGeneration, validateSource)

    private suspend fun applyProgressInternal(
        proposalId: String,
        expectedRestoreGeneration: Long,
        validateSource: (suspend () -> String?)?,
    ): PlanApplyResult = mutex.withLock {
        val barrier = writeBarrier ?: return@withLock PlanApplyResult.Failure("恢复安全边界不可用，执行未写入")
        try {
            barrier.withWrite(expectedRestoreGeneration) {
                if (!canWrite()) return@withWrite PlanApplyResult.Failure("恢复维护中，暂不能记录执行")
                val entity = recordDao.findById(proposalId)
                    ?: return@withWrite PlanApplyResult.Failure("这条执行候选不存在")
                if (entity.status == RecordProposalStatus.APPLIED) {
                    return@withWrite entity.appliedPlanId?.let { PlanApplyResult.Success(it) }
                        ?: PlanApplyResult.Failure("这条执行已经处理过")
                }
                if (entity.status != RecordProposalStatus.DRAFT) {
                    return@withWrite PlanApplyResult.Failure("这条执行候选已经取消")
                }
                if (validateSource == null && requiresInsightValidator(entity)) {
                    return@withWrite PlanApplyResult.Failure("此来源相关草案必须经过 Insight 来源与 generation 验证")
                }
                val progress = PlanChangeCodec.decodeProgress(entity.planProgressJson)
                    ?: return@withWrite PlanApplyResult.Failure("执行草稿不完整")
                val planId = progress.planId
                    ?: return@withWrite PlanApplyResult.Failure("没有定位到对应计划")
                try {
                    database.withTransaction {
                        validateSource?.invoke()?.let { throw PlanAbort(it) }
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
                    PlanApplyResult.Failure(abort.message ?: "这条执行已经处理过")
                } catch (error: Exception) {
                    PlanApplyResult.Failure(error.message ?: "记录执行失败")
                }
            }
        } catch (error: RestoreException) {
            PlanApplyResult.Failure("${error.message ?: "恢复 generation 已变化"}；执行未写入")
        }
    }

    private suspend fun requiresInsightValidator(entity: com.mengyalife.app.data.local.proposal.RecordProposalEntity): Boolean {
        val source = RecordProposalSourceCodec.decode(entity.sourceMessageIdsJson)
        if (source.invalid || source.insightReference != null) return true
        if (source.messageIds.isEmpty()) return false
        val allowed = source.messageIds.toSet() + entity.messageId
        val messages = database.advisorMessageDao().loadAll().filter { it.id in allowed }
        if (source.messageIds.any { id -> messages.none { it.id == id } }) return true
        return messages.any { message -> hasInsightSourceMarker(message.sourcesJson) }
    }

    private fun hasInsightSourceMarker(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        return runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonArray.any { item ->
                val type = item.jsonObject["type"]?.jsonPrimitive?.content
                type == "INSIGHT" || type == "INSIGHT_FACT"
            }
        }.getOrElse { true }
    }

    private class PlanAbort(override val message: String) : Exception(message)
}
