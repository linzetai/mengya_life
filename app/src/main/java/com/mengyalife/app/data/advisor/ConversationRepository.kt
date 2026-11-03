package com.mengyalife.app.data.advisor

import com.mengyalife.app.data.local.advisor.AdvisorMessageDao
import com.mengyalife.app.data.local.advisor.AdvisorMessageEntity
import com.mengyalife.app.domain.advisor.AdvisorError
import com.mengyalife.app.domain.advisor.AdvisorMessage
import com.mengyalife.app.domain.advisor.AdvisorMessageStatus
import com.mengyalife.app.domain.advisor.AdvisorRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

interface ConversationRepository {
    fun observeMessages(): Flow<List<AdvisorMessage>>
    suspend fun appendUser(content: String, contextGoalId: Long?): AdvisorMessage
    suspend fun appendAssistant(content: String): AdvisorMessage
    suspend fun appendFailure(error: AdvisorError, cancelled: Boolean): AdvisorMessage
    suspend fun deleteMessage(id: String)
    suspend fun loadAll(): List<AdvisorMessage>
}

/** 单会话的本地消息仓库。只写 advisor_messages，不接触目标/日记。 */
class RoomConversationRepository(
    private val dao: AdvisorMessageDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ConversationRepository {

    override fun observeMessages(): Flow<List<AdvisorMessage>> =
        dao.observeAll().map { rows -> rows.map(::toDomain) }

    override suspend fun appendUser(content: String, contextGoalId: Long?): AdvisorMessage =
        insert(
            AdvisorMessageEntity(
                id = newId(),
                role = AdvisorRole.USER.name,
                content = content.trim(),
                createdAt = nextTimestamp(),
                status = AdvisorMessageStatus.SENT.name,
                contextGoalId = contextGoalId,
            ),
        )

    override suspend fun appendAssistant(content: String): AdvisorMessage =
        insert(
            AdvisorMessageEntity(
                id = newId(),
                role = AdvisorRole.ASSISTANT.name,
                content = content.trim(),
                createdAt = nextTimestamp(),
                status = AdvisorMessageStatus.SENT.name,
            ),
        )

    override suspend fun appendFailure(error: AdvisorError, cancelled: Boolean): AdvisorMessage =
        insert(
            AdvisorMessageEntity(
                id = newId(),
                role = AdvisorRole.ASSISTANT.name,
                content = "",
                createdAt = nextTimestamp(),
                status = if (cancelled) AdvisorMessageStatus.CANCELLED.name
                else AdvisorMessageStatus.FAILED.name,
                errorCode = error.code,
                errorMessage = error.userMessage,
            ),
        )

    override suspend fun deleteMessage(id: String) {
        dao.deleteById(id)
    }

    override suspend fun loadAll(): List<AdvisorMessage> = dao.loadAll().map(::toDomain)

    /** 同一毫秒内连续写入时也保证严格递增，避免 UUID 排序打乱对话顺序。 */
    private suspend fun nextTimestamp(): Long {
        val max = dao.maxCreatedAt() ?: 0L
        return maxOf(now(), max + 1)
    }

    private suspend fun insert(entity: AdvisorMessageEntity): AdvisorMessage {
        dao.insert(entity)
        return toDomain(entity)
    }

    private fun toDomain(entity: AdvisorMessageEntity): AdvisorMessage = AdvisorMessage(
        id = entity.id,
        role = AdvisorRole.entries.firstOrNull { it.name == entity.role } ?: AdvisorRole.ASSISTANT,
        content = entity.content,
        createdAt = entity.createdAt,
        status = AdvisorMessageStatus.entries.firstOrNull { it.name == entity.status }
            ?: AdvisorMessageStatus.SENT,
        errorCode = entity.errorCode,
        errorMessage = entity.errorMessage,
        contextGoalId = entity.contextGoalId,
    )
}
