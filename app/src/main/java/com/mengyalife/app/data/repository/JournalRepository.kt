package com.mengyalife.app.data.repository

import com.mengyalife.app.data.local.JournalEntryDao
import com.mengyalife.app.data.local.growth.GrowthSignalDao
import com.mengyalife.app.data.local.JournalEntryEntity
import com.mengyalife.app.domain.JournalRules
import kotlinx.coroutines.flow.Flow

/**
 * 日记仓库：**唯一**允许写日记表的地方。
 *
 * 职责：
 * - 统一时间戳（都由这里的 [now] 产生，界面与 ViewModel 不自己造时间）；
 * - 统一规整输入（正文 trim、空标题归一为 null）；
 * - 对外只暴露业务方法，不把 DAO 泄给上层。
 *
 * [now] 可注入，便于单元测试用固定时钟验证时间戳行为。
 */
class JournalRepository(
    private val dao: JournalEntryDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val growthSignalDao: GrowthSignalDao? = null,
) {

    fun observeEntries(): Flow<List<JournalEntryEntity>> = dao.observeAll()

    suspend fun getEntry(id: Long): JournalEntryEntity? = dao.findById(id)

    suspend fun count(): Int = dao.count()

    /**
     * 新建日记。
     * @return 新记录的自增 id；正文非法时抛 [IllegalArgumentException]（调用方应先校验）。
     */
    suspend fun createEntry(rawBody: String, rawTitle: String?): Long {
        val body = rawBody.trim()
        require(JournalRules.validateBody(body) == null) { "正文不能为空" }
        val timestamp = now()
        return dao.insert(
            JournalEntryEntity(
                title = normalizeTitle(rawTitle),
                body = body,
                createdAt = timestamp,
                updatedAt = timestamp,
                analysisStatus = "PENDING",
            ),
        )
    }

    /**
     * 更新正文与标题。`createdAt` 保持不变，`updatedAt` 刷新。
     * @return 是否命中并更新了记录。
     */
    suspend fun updateEntry(id: Long, rawBody: String, rawTitle: String?): Boolean {
        val body = rawBody.trim()
        require(JournalRules.validateBody(body) == null) { "正文不能为空" }
        val existing = dao.findById(id) ?: return false
        val timestamp = now()
        val newTitle = normalizeTitle(rawTitle)
        val contentChanged = existing.title != newTitle || existing.body != body
        val updated = if (contentChanged) {
            existing.copy(
                title = newTitle,
                body = body,
                updatedAt = timestamp,
                // 内容变了：旧分析结果立即失效，等待重新提取
                analysisStatus = "PENDING",
                analysisHash = null,
                analysisError = null,
                analyzedAt = null,
            )
        } else {
            // 同内容版本：保留分析状态/哈希/线索，避免重复请求与重复提取
            existing.copy(title = newTitle, body = body, updatedAt = timestamp)
        }
        val changed = dao.update(updated) > 0
        if (changed && contentChanged) {
            growthSignalDao?.markStaleForDiary(id, timestamp)
        }
        return changed
    }

    /** 删除日记时同步移除其线索，避免删除后仍参与未来上下文。 */
    suspend fun deleteEntry(id: Long): Boolean {
        val deleted = dao.deleteById(id) > 0
        if (deleted) growthSignalDao?.deleteForDiary(id)
        return deleted
    }

    fun observeSignals(diaryId: Long) =
        growthSignalDao?.observeForDiary(diaryId) ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun resetInterruptedAnalyses(): Int = dao.markInterruptedAnalyses()

    suspend fun markAnalyzing(id: Long, expectedUpdatedAt: Long, hash: String): Boolean =
        dao.markAnalyzing(id, expectedUpdatedAt, hash, now()) > 0

    suspend fun markAnalysisCompleted(id: Long, hash: String): Boolean =
        dao.markCompleted(id, hash, now()) > 0

    suspend fun markAnalysisFailed(id: Long, hash: String?, error: String): Boolean =
        dao.markFailed(id, hash, error.take(300), now()) > 0

    private fun normalizeTitle(rawTitle: String?): String? =
        rawTitle?.trim()?.takeIf { it.isNotEmpty() }
}
