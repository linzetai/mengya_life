package com.mengyalife.app.data.advisor

import com.mengyalife.app.data.local.advisor.AdvisorMessageDao
import com.mengyalife.app.data.local.advisor.AdvisorMessageEntity
import com.mengyalife.app.domain.advisor.AdvisorError
import com.mengyalife.app.domain.advisor.AdvisorMessage
import com.mengyalife.app.domain.advisor.AdvisorMessageStatus
import com.mengyalife.app.domain.advisor.AdvisorRole
import com.mengyalife.app.domain.advisor.AdvisorSource
import com.mengyalife.app.domain.advisor.AdvisorSourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

interface ConversationRepository {
    fun observeMessages(): Flow<List<AdvisorMessage>>
    suspend fun appendUser(
        content: String,
        contextGoalId: Long?,
        contextEpoch: Long = 0,
        expectedRestoreGeneration: Long? = null,
        sources: List<AdvisorSource> = emptyList(),
    ): AdvisorMessage
    suspend fun appendAssistant(
        content: String,
        contextEpoch: Long,
        sources: List<AdvisorSource> = emptyList(),
        proposalError: String? = null,
        proposalMode: Boolean = false,
        expectedRestoreGeneration: Long? = null,
    ): AdvisorMessage

    suspend fun appendFailure(
        error: AdvisorError,
        cancelled: Boolean,
        contextEpoch: Long = 0,
        proposalMode: Boolean = false,
        expectedRestoreGeneration: Long? = null,
    ): AdvisorMessage
    suspend fun deleteMessage(id: String)
    suspend fun loadAll(): List<AdvisorMessage>
}

/** 单会话的本地消息仓库。只写 advisor_messages，不接触目标/日记。 */
class RoomConversationRepository(
    private val dao: AdvisorMessageDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val canWrite: () -> Boolean = { true },
    private val writeBarrier: com.mengyalife.app.data.restore.RestoreWriteBarrier? = null,
) : ConversationRepository {

    override fun observeMessages(): Flow<List<AdvisorMessage>> =
        dao.observeAll().map { rows -> rows.map(::toDomain) }

    override suspend fun appendUser(
        content: String,
        contextGoalId: Long?,
        contextEpoch: Long,
        expectedRestoreGeneration: Long?,
        sources: List<AdvisorSource>,
    ): AdvisorMessage =
        insert(
            AdvisorMessageEntity(
                id = newId(),
                role = AdvisorRole.USER.name,
                content = content.trim(),
                createdAt = nextTimestamp(),
                status = AdvisorMessageStatus.SENT.name,
                contextGoalId = contextGoalId,
                contextEpoch = contextEpoch,
                sourcesJson = encodeSources(sources),
            ), expectedRestoreGeneration,
        )

    override suspend fun appendAssistant(
        content: String,
        contextEpoch: Long,
        sources: List<AdvisorSource>,
        proposalError: String?,
        proposalMode: Boolean,
        expectedRestoreGeneration: Long?,
    ): AdvisorMessage =
        insert(
            AdvisorMessageEntity(
                id = newId(),
                role = AdvisorRole.ASSISTANT.name,
                content = content.trim(),
                createdAt = nextTimestamp(),
                status = AdvisorMessageStatus.SENT.name,
                contextEpoch = contextEpoch,
                sourcesJson = encodeSources(sources),
                proposalError = proposalError,
                proposalMode = proposalMode,
            ), expectedRestoreGeneration,
        )

    override suspend fun appendFailure(
        error: AdvisorError,
        cancelled: Boolean,
        contextEpoch: Long,
        proposalMode: Boolean,
        expectedRestoreGeneration: Long?,
    ): AdvisorMessage =
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
                contextEpoch = contextEpoch,
                proposalMode = proposalMode,
            ), expectedRestoreGeneration,
        )

    override suspend fun deleteMessage(id: String) {
        val write: suspend () -> Unit = { dao.deleteById(id); Unit }
        if (!canWrite()) throw IllegalStateException("恢复维护中，禁止删除对话")
        if (writeBarrier != null) writeBarrier.withWrite { write() } else write()
    }

    override suspend fun loadAll(): List<AdvisorMessage> = dao.loadAll().map(::toDomain)

    /** 同一毫秒内连续写入时也保证严格递增，避免 UUID 排序打乱对话顺序。 */
    private suspend fun nextTimestamp(): Long {
        val max = dao.maxCreatedAt() ?: 0L
        return maxOf(now(), max + 1)
    }

    private suspend fun insert(entity: AdvisorMessageEntity, expectedGeneration: Long?): AdvisorMessage {
        if (!canWrite()) throw IllegalStateException("恢复维护中，禁止写入对话")
        val write: suspend () -> AdvisorMessage = {
            if (!canWrite()) throw IllegalStateException("恢复维护中，禁止写入对话")
            dao.insert(entity)
            toDomain(entity)
        }
        return writeBarrier?.withWrite(expectedGeneration) { write() } ?: write()
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
        contextEpoch = entity.contextEpoch,
        sources = decodeSources(entity.sourcesJson),
        proposalError = entity.proposalError,
        proposalMode = entity.proposalMode,
    )

    private fun encodeSources(sources: List<AdvisorSource>): String? {
        if (sources.isEmpty()) return null
        return buildJsonArray {
            sources.forEach { source ->
                add(
                    buildJsonObject {
                        put("type", source.type.name)
                        put("id", source.recordId)
                        put("label", source.label)
                        if (source.metadata.isNotEmpty()) {
                            put("metadata", kotlinx.serialization.json.buildJsonObject {
                                source.metadata.forEach { (key, value) -> put(key, value) }
                            })
                        }
                    },
                )
            }
        }.toString()
    }

    private fun decodeSources(raw: String?): List<AdvisorSource> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            Json.parseToJsonElement(raw).jsonArray.mapNotNull { element ->
                val obj = element.jsonObject
                val type = AdvisorSourceType.entries.firstOrNull {
                    it.name == obj["type"]?.jsonPrimitive?.contentOrNull
                } ?: return@mapNotNull null
                val metadata = obj["metadata"]?.jsonObject?.mapNotNull { (key, value) ->
                    value.jsonPrimitive.contentOrNull?.let { key to it }
                }?.toMap().orEmpty()
                AdvisorSource(
                    type = type,
                    recordId = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                    label = obj["label"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    metadata = metadata,
                )
            }
        }.getOrDefault(emptyList())
    }
}
