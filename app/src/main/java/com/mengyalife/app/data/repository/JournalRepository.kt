package com.mengyalife.app.data.repository

import com.mengyalife.app.data.local.JournalEntryDao
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
        val updated = existing.copy(
            title = normalizeTitle(rawTitle),
            body = body,
            updatedAt = now(),
        )
        return dao.update(updated) > 0
    }

    /** @return 是否真的删除了记录（false 表示记录已不存在）。 */
    suspend fun deleteEntry(id: Long): Boolean = dao.deleteById(id) > 0

    private fun normalizeTitle(rawTitle: String?): String? =
        rawTitle?.trim()?.takeIf { it.isNotEmpty() }
}
