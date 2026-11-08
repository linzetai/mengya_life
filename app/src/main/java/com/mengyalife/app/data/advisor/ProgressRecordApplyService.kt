package com.mengyalife.app.data.advisor

import androidx.room.withTransaction
import com.mengyalife.app.data.local.MengyaDatabase
import com.mengyalife.app.data.local.proposal.RecordProposalDao
import com.mengyalife.app.data.repository.GoalRepository
import com.mengyalife.app.data.repository.GoalResult
import com.mengyalife.app.data.restore.RestoreWriteBarrier
import com.mengyalife.app.domain.advisor.RecordAmountKind
import com.mengyalife.app.domain.advisor.RecordProposalDraft
import com.mengyalife.app.domain.advisor.RecordProposalStatus
import com.mengyalife.app.domain.goals.CheckinSource
import com.mengyalife.app.domain.goals.GoalRules
import com.mengyalife.app.domain.goals.GoalStatus
import com.mengyalife.app.domain.goals.ProgressKind
import com.mengyalife.app.domain.goals.ProgressNotes
import com.mengyalife.app.domain.goals.ProgressUnits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface RecordApplyResult {
    data class Success(val checkinId: Long, val goalTitle: String) : RecordApplyResult
    data class Failure(val message: String) : RecordApplyResult
}

sealed interface RecordUndoResult {
    data class Success(val alreadyUndone: Boolean = false) : RecordUndoResult
    data class Failure(val message: String) : RecordUndoResult
}

interface ProgressRecordApply {
    suspend fun apply(proposalId: String, draft: RecordProposalDraft): RecordApplyResult
    suspend fun editApplied(proposalId: String, draft: RecordProposalDraft): RecordApplyResult
    suspend fun undo(proposalId: String): RecordUndoResult
}

/**
 * 唯一允许把「记录候选」写入 goal_checkins 的服务。
 *
 * - 模型只产生候选，写入只在这里发生；
 * - 目标存在性、数值、单位、说明再次校验（不信任模型）；
 * - DRAFT→APPLIED 与实际插入在同一事务，重复点击/重试不会重复写；
 * - 通过 GoalRepository 写入，保持「AI 不直连数据库」的边界；
 * - 来源写为 [CheckinSource.ADVISOR]，可追溯；取消/撤销不删除已有对话。
 */
class ProgressRecordApplyService(
    private val database: MengyaDatabase,
    private val recordDao: RecordProposalDao,
    private val goalRepository: GoalRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val canWrite: () -> Boolean = { true },
    private val writeBarrier: RestoreWriteBarrier? = null,
) : ProgressRecordApply {

    private val mutex = Mutex()

    override suspend fun apply(proposalId: String, draft: RecordProposalDraft): RecordApplyResult =
        if (writeBarrier != null) writeBarrier.withWrite { applyLocked(proposalId, draft) }
        else applyLocked(proposalId, draft)

    private suspend fun applyLocked(proposalId: String, draft: RecordProposalDraft): RecordApplyResult =
        mutex.withLock {
            if (!canWrite()) return@withLock RecordApplyResult.Failure("恢复维护中，禁止写入进展")
            try {
                database.withTransaction {
                    val entity = recordDao.findById(proposalId)
                        ?: return@withTransaction RecordApplyResult.Failure("记录候选不存在")
                    if (entity.status != RecordProposalStatus.DRAFT) {
                        return@withTransaction RecordApplyResult.Failure(
                            when (entity.status) {
                                RecordProposalStatus.APPLIED -> "这条进展已经记录过，不能重复保存"
                                RecordProposalStatus.CANCELLED -> "这条候选已取消"
                                else -> "候选状态不允许保存"
                            },
                        )
                    }
                    val goalId = draft.goalId
                        ?: return@withTransaction RecordApplyResult.Failure("请先选择要记录到哪个目标")
                    val goal = database.goalDao().findGoal(goalId)
                        ?: return@withTransaction RecordApplyResult.Failure("目标不存在，可能已被删除")
                    validate(draft)?.let { return@withTransaction RecordApplyResult.Failure(it) }

                    val recordedAt = draft.occurredAt ?: now()
                    val existingUnits = database.goalDao().loadCheckinsForGoals(listOf(goal.id))
                        .mapNotNull { it.metricUnit?.trim()?.takeIf(String::isNotEmpty) }
                        .toSet()
                    val resolved = ProgressUnits.resolveAgainstExisting(
                        value = draft.metricValue,
                        unit = draft.metricUnit,
                        existingUnits = existingUnits,
                    ) ?: return@withTransaction RecordApplyResult.Failure(
                        "这条进展缺少单位；请补充单位（如元/小时）后再记录",
                    )
                    val kind = draft.amountKind.toProgressKind()
                    when (
                        val result = goalRepository.addCheckin(
                            goalId = goal.id,
                            note = draft.note,
                            metricValue = draft.metricValue,
                            metricUnit = draft.metricUnit,
                            source = CheckinSource.ADVISOR,
                            recordedAt = recordedAt,
                            amountKind = kind,
                            originalText = entity.originalText,
                            normalizedValue = resolved.value.takeIf { resolved.normalized },
                            normalizedUnit = resolved.unit.takeIf { resolved.normalized },
                        )
                    ) {
                        is GoalResult.Error -> return@withTransaction RecordApplyResult.Failure(result.message)
                        is GoalResult.Ok -> {
                            val changed = recordDao.markAppliedIfDraft(
                                id = proposalId,
                                appliedAt = now(),
                                checkinId = result.value,
                                goalId = goal.id,
                                goalTitle = goal.title,
                                amountKind = draft.amountKind.name,
                                metricValue = draft.metricValue,
                                metricUnit = draft.metricUnit,
                                note = draft.note,
                                occurredAt = recordedAt,
                            )
                            if (changed == 0) {
                                // 并发下已被另一次确认写入：回滚本次插入，避免重复记录。
                                throw RecordAbort("这条候选已经被记录过")
                            }
                            // 记录进展后，未开始的目标自动进入进行中（与手动记录一致）。
                            if (goal.status == GoalStatus.NOT_STARTED) {
                                database.goalDao().updateGoal(
                                    goal.copy(status = GoalStatus.IN_PROGRESS, updatedAt = now()),
                                )
                            }
                            RecordApplyResult.Success(result.value, goal.title)
                        }
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (abort: RecordAbort) {
                RecordApplyResult.Failure(abort.message ?: "保存失败，已回滚")
            } catch (error: Throwable) {
                RecordApplyResult.Failure("保存失败，已回滚：${error.message ?: "数据库错误"}")
            }
        }

    override suspend fun editApplied(proposalId: String, draft: RecordProposalDraft): RecordApplyResult =
        if (writeBarrier != null) writeBarrier.withWrite { editLocked(proposalId, draft) }
        else editLocked(proposalId, draft)

    private suspend fun editLocked(proposalId: String, draft: RecordProposalDraft): RecordApplyResult =
        mutex.withLock {
            if (!canWrite()) return@withLock RecordApplyResult.Failure("恢复维护中，禁止修改进展")
            try {
                database.withTransaction {
                    val entity = recordDao.findById(proposalId)
                        ?: return@withTransaction RecordApplyResult.Failure("记录候选不存在")
                    if (entity.status != RecordProposalStatus.APPLIED) {
                        return@withTransaction RecordApplyResult.Failure("只有已记录的进展才能修改")
                    }
                    val checkinId = entity.appliedCheckinId
                        ?: return@withTransaction RecordApplyResult.Failure("找不到对应的进展记录")
                    val checkin = database.goalDao().findCheckin(checkinId)
                        ?: return@withTransaction RecordApplyResult.Failure("进展记录已被删除，请撤销后重新记录")
                    if (draft.goalId != null && draft.goalId != entity.goalId) {
                        return@withTransaction RecordApplyResult.Failure("已记录的进展不能改目标；可撤销后重新记录")
                    }
                    validate(draft)?.let { return@withTransaction RecordApplyResult.Failure(it) }
                    val existingUnits = database.goalDao().loadCheckinsForGoals(listOf(checkin.goalId))
                        .mapNotNull { it.metricUnit?.trim()?.takeIf(String::isNotEmpty) }
                        .toSet()
                    val resolved = ProgressUnits.resolveAgainstExisting(
                        value = draft.metricValue,
                        unit = draft.metricUnit,
                        existingUnits = existingUnits + listOfNotNull(checkin.metricUnit?.takeIf { it.isNotBlank() }),
                    ) ?: return@withTransaction RecordApplyResult.Failure(
                        "这条进展缺少单位；请补充单位后再保存",
                    )
                    // 只改了数值、说明仍是旧原话时，同步改写说明里的旧数字，避免“记录是 1500、说明写 2000”。
                    val note = if (draft.note == entity.note) {
                        ProgressNotes.rewriteValue(entity.note, checkin.metricValue, draft.metricValue)
                    } else {
                        draft.note
                    }
                    when (
                        val result = goalRepository.updateCheckin(
                            checkinId = checkin.id,
                            note = note,
                            metricValue = draft.metricValue,
                            metricUnit = draft.metricUnit,
                            amountKind = draft.amountKind.toProgressKind(),
                            normalizedValue = resolved.value.takeIf { resolved.normalized },
                            normalizedUnit = resolved.unit.takeIf { resolved.normalized },
                        )
                    ) {
                        is GoalResult.Error -> return@withTransaction RecordApplyResult.Failure(result.message)
                        is GoalResult.Ok -> {
                            recordDao.updateAppliedFields(
                                id = proposalId,
                                goalId = checkin.goalId,
                                goalTitle = entity.goalTitle,
                                amountKind = draft.amountKind.name,
                                metricValue = draft.metricValue,
                                metricUnit = draft.metricUnit,
                                note = note,
                                occurredAt = draft.occurredAt ?: checkin.recordedAt,
                            )
                            RecordApplyResult.Success(checkin.id, entity.goalTitle.orEmpty())
                        }
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (abort: RecordAbort) {
                RecordApplyResult.Failure(abort.message ?: "修改失败，已回滚")
            } catch (error: Throwable) {
                RecordApplyResult.Failure("修改失败，已回滚：${error.message ?: "数据库错误"}")
            }
        }

    override suspend fun undo(proposalId: String): RecordUndoResult =
        if (writeBarrier != null) writeBarrier.withWrite { undoLocked(proposalId) }
        else undoLocked(proposalId)

    private suspend fun undoLocked(proposalId: String): RecordUndoResult = mutex.withLock {
        if (!canWrite()) return@withLock RecordUndoResult.Failure("恢复维护中，禁止撤销进展")
        try {
            database.withTransaction {
                val entity = recordDao.findById(proposalId)
                    ?: return@withTransaction RecordUndoResult.Failure("记录候选不存在")
                if (entity.status == RecordProposalStatus.UNDONE) {
                    return@withTransaction RecordUndoResult.Success(alreadyUndone = true)
                }
                if (entity.status != RecordProposalStatus.APPLIED) {
                    return@withTransaction RecordUndoResult.Failure("这条候选还没有记录，无需撤销")
                }
                entity.appliedCheckinId?.let { checkinId ->
                    database.goalDao().findCheckin(checkinId)?.let {
                        database.goalDao().deleteCheckin(checkinId)
                    }
                }
                val changed = recordDao.markUndoneIfApplied(proposalId, now())
                if (changed == 0) throw RecordAbort("候选状态已变化，撤销已回滚")
                RecordUndoResult.Success()
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (abort: RecordAbort) {
            RecordUndoResult.Failure(abort.message ?: "撤销失败，已回滚")
        } catch (error: Throwable) {
            RecordUndoResult.Failure("撤销失败，已回滚：${error.message ?: "数据库错误"}")
        }
    }

    private fun validate(draft: RecordProposalDraft): String? {
        if (draft.note.isBlank()) return "记录说明不能为空"
        GoalRules.validateCheckinNote(draft.note)?.let { return it }
        GoalRules.validateMetricValue(draft.metricValue)?.let { return it }
        GoalRules.validateUnit(draft.metricUnit)?.let { return it }
        if (draft.amountKind != RecordAmountKind.NONE && draft.metricValue == null) {
            return "增量或累计记录必须有数值"
        }
        return null
    }

    private fun RecordAmountKind.toProgressKind(): String = when (this) {
        RecordAmountKind.DELTA -> ProgressKind.DELTA
        RecordAmountKind.SNAPSHOT -> ProgressKind.SNAPSHOT
        RecordAmountKind.NONE -> ProgressKind.NONE
    }

    private class RecordAbort(message: String) : Exception(message)
}
