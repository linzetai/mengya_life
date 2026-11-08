package com.mengyalife.app.data.advisor

import androidx.room.withTransaction
import com.mengyalife.app.data.local.MengyaDatabase
import com.mengyalife.app.data.local.proposal.RecordProposalDao
import com.mengyalife.app.data.repository.JournalRepository
import com.mengyalife.app.data.restore.RestoreWriteBarrier
import com.mengyalife.app.domain.advisor.RecordProposalDraft
import com.mengyalife.app.domain.advisor.RecordProposalStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface DiaryRecordApply {
    suspend fun apply(proposalId: String): DiaryApplyResult
    suspend fun editApplied(proposalId: String, draft: RecordProposalDraft): DiaryApplyResult
}

sealed interface DiaryApplyResult {
    data class Success(val entryId: Long) : DiaryApplyResult
    data class Failure(val message: String) : DiaryApplyResult
}

/**
 * 对话式日记的正式写入。
 *
 * 约束：
 * - 只写现有 `journal_entries`（通过 [JournalRepository]），不存在第二套日记存储；
 * - 先建日记，再原子迁移候选状态；并发/重复点击只会成功一次；
 * - 已保存的日记再次修改时，改的是**同一篇**，不会产生副本。
 */
class DiaryRecordApplyService(
    private val database: MengyaDatabase,
    private val recordDao: RecordProposalDao,
    private val journalRepository: JournalRepository,
    private val canWrite: () -> Boolean = { true },
    private val writeBarrier: RestoreWriteBarrier? = null,
) : DiaryRecordApply {
    private val mutex = Mutex()

    override suspend fun apply(proposalId: String): DiaryApplyResult = mutex.withLock {
        val entity = recordDao.findById(proposalId)
            ?: return@withLock DiaryApplyResult.Failure("这条日记候选不存在")
        if (entity.status == RecordProposalStatus.APPLIED && entity.diaryEntryId != null) {
            // 重复点击/重试：返回已有日记，不复制。
            return@withLock DiaryApplyResult.Success(entity.diaryEntryId)
        }
        if (entity.status != RecordProposalStatus.DRAFT) {
            return@withLock DiaryApplyResult.Failure("这条日记候选已经取消")
        }
        val body = entity.diaryBody?.trim().orEmpty()
        if (body.isEmpty()) return@withLock DiaryApplyResult.Failure("日记正文不能为空")

        writeBarrier?.withWrite(writeBarrier.generation()) { write(body, entity.diaryTitle, proposalId) }
            ?: write(body, entity.diaryTitle, proposalId)
    }

    /** 修改已保存的日记：同一篇，不新建。 */
    override suspend fun editApplied(proposalId: String, draft: RecordProposalDraft): DiaryApplyResult = mutex.withLock {
        val entity = recordDao.findById(proposalId)
            ?: return@withLock DiaryApplyResult.Failure("这条日记候选不存在")
        val entryId = entity.diaryEntryId
            ?: return@withLock DiaryApplyResult.Failure("这篇日记还没有正式保存")
        if (entity.status != RecordProposalStatus.APPLIED) {
            return@withLock DiaryApplyResult.Failure("这篇日记已不在可编辑状态")
        }
        val body = draft.diaryBody?.trim().orEmpty()
        if (body.isEmpty()) return@withLock DiaryApplyResult.Failure("日记正文不能为空")

        writeBarrier?.withWrite(writeBarrier.generation()) {
            edit(proposalId, entryId, draft.diaryTitle, body)
        } ?: edit(proposalId, entryId, draft.diaryTitle, body)
    }

    private suspend fun write(body: String, title: String?, proposalId: String): DiaryApplyResult {
        if (!canWrite()) return DiaryApplyResult.Failure("恢复维护中，暂不能保存日记")
        return try {
            database.withTransaction {
                val entryId = journalRepository.createEntry(body, title)
                val moved = recordDao.markDiaryAppliedIfDraft(
                    id = proposalId,
                    appliedAt = System.currentTimeMillis(),
                    entryId = entryId,
                    title = title,
                    body = body,
                )
                if (moved == 0) throw DiaryAbort()
                DiaryApplyResult.Success(entryId)
            }
        } catch (abort: DiaryAbort) {
            DiaryApplyResult.Failure("这条日记已经处理过，请刷新后再看")
        } catch (error: Exception) {
            DiaryApplyResult.Failure(error.message ?: "保存日记失败")
        }
    }

    private suspend fun edit(proposalId: String, entryId: Long, title: String?, body: String): DiaryApplyResult {
        if (!canWrite()) return DiaryApplyResult.Failure("恢复维护中，暂不能修改日记")
        return try {
            database.withTransaction {
                val updated = journalRepository.updateEntry(entryId, body, title)
                if (!updated) throw DiaryAbort()
                recordDao.updateDiaryApplied(proposalId, title, body)
                DiaryApplyResult.Success(entryId)
            }
        } catch (abort: DiaryAbort) {
            DiaryApplyResult.Failure("这篇日记已经不存在")
        } catch (error: Exception) {
            DiaryApplyResult.Failure(error.message ?: "修改日记失败")
        }
    }

    private class DiaryAbort : Exception()
}
