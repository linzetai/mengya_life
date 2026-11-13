package com.mengyalife.app.data.advisor

import com.mengyalife.app.data.local.proposal.RecordProposalDao
import com.mengyalife.app.data.local.proposal.RecordProposalEntity
import com.mengyalife.app.data.restore.RestoreWriteBarrier
import com.mengyalife.app.domain.advisor.RecordGoalCandidate
import com.mengyalife.app.domain.advisor.RecordProposalDraft
import com.mengyalife.app.domain.advisor.RecordProposalStatus
import com.mengyalife.app.domain.insight.InsightReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.UUID

data class StoredRecordProposal(
    val id: String,
    val messageId: String,
    val createdAt: Long,
    val draft: RecordProposalDraft,
    val status: String,
    val appliedCheckinId: Long? = null,
    val diaryEntryId: Long? = null,
    val appliedGoalIds: List<Long> = emptyList(),
    val appliedPlanId: Long? = null,
    val appliedAt: Long? = null,
    val undoneAt: Long? = null,
) {
    val isDraft: Boolean get() = status == RecordProposalStatus.DRAFT
    val isApplied: Boolean get() = status == RecordProposalStatus.APPLIED
    val isCancelled: Boolean get() = status == RecordProposalStatus.CANCELLED
    val isUndone: Boolean get() = status == RecordProposalStatus.UNDONE
    val isPending: Boolean get() = isDraft
}

interface RecordProposalRepository {
    fun observeAll(): Flow<List<StoredRecordProposal>>
    suspend fun findById(id: String): StoredRecordProposal?

    /** 同一 message_id 只保存一条；重复调用返回既有记录（幂等）。 */
    suspend fun save(
        messageId: String,
        draft: RecordProposalDraft,
        expectedRestoreGeneration: Long? = null,
    ): StoredRecordProposal

    suspend fun updateDraft(id: String, draft: RecordProposalDraft): Boolean
    suspend fun cancel(id: String): Boolean

    /** 日记正式保存：绑定 journal_entries.id；只有 DRAFT 可迁移。 */
    suspend fun markDiaryApplied(proposalId: String, entryId: Long, title: String?, body: String): Boolean

    /** 修改已保存的日记候选字段（正文以 journal_entries 为准，同步更新）。 */
    suspend fun updateDiaryApplied(proposalId: String, title: String?, body: String): Boolean

    /** 目标正式写入：记录创建/更新的目标 id。 */
    suspend fun markGoalApplied(proposalId: String, goalIds: List<Long>): Boolean

    suspend fun markPlanApplied(proposalId: String, planId: Long): Boolean
}

class RoomRecordProposalRepository(
    private val dao: RecordProposalDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val writeBarrier: RestoreWriteBarrier? = null,
) : RecordProposalRepository {

    override fun observeAll(): Flow<List<StoredRecordProposal>> =
        dao.observeAll().map { rows -> rows.mapNotNull(::toDomain) }

    override suspend fun findById(id: String): StoredRecordProposal? =
        dao.findById(id)?.let(::toDomain)

    override suspend fun save(
        messageId: String,
        draft: RecordProposalDraft,
        expectedRestoreGeneration: Long?,
    ): StoredRecordProposal {
        val existing = dao.findByMessage(messageId)?.let(::toDomain)
        if (existing != null) return existing
        val durableDraft = draft.copy(sourceLinkKnown = true)
        val entity = toEntity(newId(), messageId, now(), durableDraft)
        val write: suspend () -> Unit = { dao.insert(entity) }
        if (writeBarrier != null) writeBarrier.withWrite(expectedRestoreGeneration) { write() } else write()
        return dao.findByMessage(messageId)?.let(::toDomain)
            ?: StoredRecordProposal(
                id = entity.id,
                messageId = entity.messageId,
                createdAt = entity.createdAt,
                draft = durableDraft,
                status = entity.status,
            )
    }

    override suspend fun updateDraft(id: String, draft: RecordProposalDraft): Boolean {
        val generation = writeBarrier?.generation()
        val write: suspend () -> Boolean = {
            dao.updateDraft(
                id = id,
                goalId = draft.goalId,
                goalTitle = draft.goalTitle,
                candidatesJson = encodeCandidates(draft.goalCandidates),
                amountKind = draft.amountKind.name,
                metricValue = draft.metricValue,
                metricUnit = draft.metricUnit,
                note = draft.note,
                occurredAt = draft.occurredAt,
                pendingJson = encodePending(draft.pendingFields),
                diaryTitle = draft.diaryTitle,
                diaryBody = draft.diaryBody,
                goalChangeJson = GoalChangeCodec.encode(draft.goalChange),
                planChangeJson = PlanChangeCodec.encode(draft.planChange),
                planProgressJson = PlanChangeCodec.encodeProgress(draft.planProgress),
            ) > 0
        }
        return writeBarrier?.withWrite(generation) { write() } ?: write()
    }

    override suspend fun markDiaryApplied(proposalId: String, entryId: Long, title: String?, body: String): Boolean {
        val generation = writeBarrier?.generation()
        val write: suspend () -> Boolean = {
            dao.markDiaryAppliedIfDraft(proposalId, now(), entryId, title, body) > 0
        }
        return writeBarrier?.withWrite(generation) { write() } ?: write()
    }

    override suspend fun updateDiaryApplied(proposalId: String, title: String?, body: String): Boolean {
        val generation = writeBarrier?.generation()
        val write: suspend () -> Boolean = { dao.updateDiaryApplied(proposalId, title, body) > 0 }
        return writeBarrier?.withWrite(generation) { write() } ?: write()
    }

    override suspend fun markPlanApplied(proposalId: String, planId: Long): Boolean {
        val entity = dao.findById(proposalId) ?: return false
        val generation = writeBarrier?.generation()
        val write: suspend () -> Boolean = {
            dao.markPlanAppliedIfDraft(proposalId, now(), planId, entity.planChangeJson) > 0
        }
        return writeBarrier?.withWrite(generation) { write() } ?: write()
    }

    override suspend fun markGoalApplied(proposalId: String, goalIds: List<Long>): Boolean {
        val entity = dao.findById(proposalId) ?: return false
        val generation = writeBarrier?.generation()
        val write: suspend () -> Boolean = {
            dao.markGoalAppliedIfDraft(
                id = proposalId,
                appliedAt = now(),
                goalIdsJson = goalIds.joinToString(","),
                goalChangeJson = entity.goalChangeJson,
            ) > 0
        }
        return writeBarrier?.withWrite(generation) { write() } ?: write()
    }

    override suspend fun cancel(id: String): Boolean {
        val generation = writeBarrier?.generation()
        val write: suspend () -> Boolean = { dao.markCancelledIfDraft(id) > 0 }
        return writeBarrier?.withWrite(generation) { write() } ?: write()
    }

    private fun toEntity(id: String, messageId: String, createdAt: Long, draft: RecordProposalDraft) =
        RecordProposalEntity(
            id = id,
            messageId = messageId,
            createdAt = createdAt,
            status = RecordProposalStatus.DRAFT,
            kind = draft.kind.name,
            goalId = draft.goalId,
            goalTitle = draft.goalTitle,
            goalCandidatesJson = encodeCandidates(draft.goalCandidates),
            amountKind = draft.amountKind.name,
            metricValue = draft.metricValue,
            metricUnit = draft.metricUnit,
            note = draft.note,
            occurredAt = draft.occurredAt,
            pendingFieldsJson = encodePending(draft.pendingFields),
            intent = draft.intent.name,
            originalText = draft.originalText,
            diaryTitle = draft.diaryTitle,
            diaryBody = draft.diaryBody,
            sourceMessageIdsJson = RecordProposalSourceCodec.encode(draft),
            goalChangeJson = GoalChangeCodec.encode(draft.goalChange),
            planChangeJson = PlanChangeCodec.encode(draft.planChange),
            planProgressJson = PlanChangeCodec.encodeProgress(draft.planProgress),
        )

    private fun toDomain(entity: RecordProposalEntity): StoredRecordProposal? {
        // 单条坏行不能拖垮整个对话；解析失败直接忽略。
        val draft = decodeDraft(entity) ?: return null
        return StoredRecordProposal(
            id = entity.id,
            messageId = entity.messageId,
            createdAt = entity.createdAt,
            draft = draft,
            status = entity.status,
            appliedCheckinId = entity.appliedCheckinId,
            diaryEntryId = entity.diaryEntryId,
            appliedGoalIds = entity.appliedGoalIdsJson?.split(',')?.mapNotNull { it.trim().toLongOrNull() }.orEmpty(),
            appliedPlanId = entity.appliedPlanId,
            appliedAt = entity.appliedAt,
            undoneAt = entity.undoneAt,
        )
    }

    private fun decodeDraft(entity: RecordProposalEntity): RecordProposalDraft? {
        val candidates = decodeCandidates(entity.goalCandidatesJson)
        val pending = decodePending(entity.pendingFieldsJson)
        val kind = com.mengyalife.app.domain.advisor.RecordKind.entries
            .firstOrNull { it.name.equals(entity.kind, ignoreCase = true) }
            ?: return null
        val amountKind = com.mengyalife.app.domain.advisor.RecordAmountKind.entries
            .firstOrNull { it.name.equals(entity.amountKind, ignoreCase = true) }
            ?: return null
        val intent = com.mengyalife.app.domain.advisor.RecordIntent.entries
            .firstOrNull { it.name.equals(entity.intent, ignoreCase = true) }
            ?: com.mengyalife.app.domain.advisor.RecordIntent.SUGGESTED
        if (entity.note.isBlank()) return null
        val sourceLink = RecordProposalSourceCodec.decode(entity.sourceMessageIdsJson)
        return RecordProposalDraft(
            kind = kind,
            goalId = entity.goalId,
            goalTitle = entity.goalTitle,
            goalCandidates = candidates,
            amountKind = amountKind,
            metricValue = entity.metricValue,
            metricUnit = entity.metricUnit,
            note = entity.note,
            occurredAt = entity.occurredAt,
            pendingFields = pending,
            intent = intent,
            originalText = entity.originalText,
            diaryTitle = entity.diaryTitle,
            diaryBody = entity.diaryBody,
            diaryEntryId = entity.diaryEntryId,
            sourceMessageIds = sourceLink.messageIds,
            sourceLinkKnown = sourceLink.known,
            sourceLinkInvalid = sourceLink.invalid,
            sourceInsightReference = sourceLink.insightReference,
            goalChange = GoalChangeCodec.decode(entity.goalChangeJson),
            appliedGoalIds = entity.appliedGoalIdsJson?.split(',')?.mapNotNull { it.trim().toLongOrNull() }.orEmpty(),
            planChange = PlanChangeCodec.decode(entity.planChangeJson),
            planProgress = PlanChangeCodec.decodeProgress(entity.planProgressJson),
            appliedPlanId = entity.appliedPlanId,
        )
    }

    private fun encodeCandidates(candidates: List<RecordGoalCandidate>): String? {
        if (candidates.isEmpty()) return null
        return kotlinx.serialization.json.buildJsonArray {
            candidates.forEach { candidate ->
                add(
                    kotlinx.serialization.json.buildJsonObject {
                        put("goalId", candidate.goalId)
                        put("title", candidate.title)
                    },
                )
            }
        }.toString()
    }

    private fun decodeCandidates(raw: String?): List<RecordGoalCandidate> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            Json.parseToJsonElement(raw).jsonArray.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["goalId"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                RecordGoalCandidate(
                    goalId = id,
                    title = obj["title"]?.jsonPrimitive?.content.orEmpty(),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun encodePending(fields: List<String>): String? =
        if (fields.isEmpty()) null else
            kotlinx.serialization.json.buildJsonArray {
                fields.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            }.toString()

    private fun decodePending(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            Json.parseToJsonElement(raw).jsonArray.mapNotNull { it.jsonPrimitive.content }
        }.getOrDefault(emptyList())
    }
}

internal data class DecodedRecordSourceLink(
    val messageIds: List<String> = emptyList(),
    val known: Boolean = false,
    val invalid: Boolean = false,
    val insightReference: InsightReference? = null,
)

/** Backward-compatible envelope in the existing source_message_ids_json TEXT column. */
internal object RecordProposalSourceCodec {
    fun encode(draft: RecordProposalDraft): String? {
        if (draft.sourceMessageIds.isEmpty() && !draft.sourceLinkKnown &&
            draft.sourceInsightReference == null && !draft.sourceLinkInvalid
        ) return null
        return kotlinx.serialization.json.buildJsonObject {
            put("format", "record-source-v1")
            put("messageIds", kotlinx.serialization.json.buildJsonArray {
                draft.sourceMessageIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            })
            put("sourceLinkKnown", draft.sourceLinkKnown || draft.sourceInsightReference != null)
            put("sourceLinkInvalid", draft.sourceLinkInvalid)
            put("insightReference", draft.sourceInsightReference?.let { reference ->
                kotlinx.serialization.json.buildJsonObject {
                    put("reportId", reference.reportId)
                    put("dataVersion", reference.dataVersion)
                    put("insightId", reference.insightId)
                }
            } ?: kotlinx.serialization.json.JsonNull)
        }.toString()
    }

    fun decode(raw: String?): DecodedRecordSourceLink {
        if (raw.isNullOrBlank()) return DecodedRecordSourceLink()
        val element = runCatching { Json.parseToJsonElement(raw) }.getOrNull()
            ?: return DecodedRecordSourceLink(invalid = true)
        if (element is kotlinx.serialization.json.JsonArray) {
            return DecodedRecordSourceLink(
                messageIds = element.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() },
            )
        }
        val obj = element as? kotlinx.serialization.json.JsonObject
            ?: return DecodedRecordSourceLink(invalid = true)
        val format = runCatching { obj["format"]?.jsonPrimitive?.content }.getOrNull()
        if (format != "record-source-v1") return DecodedRecordSourceLink(invalid = true)
        val ids = runCatching {
            obj["messageIds"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content }.orEmpty()
        }.getOrDefault(emptyList())
        val known = runCatching { obj["sourceLinkKnown"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() }.getOrNull()
        val markedInvalid = runCatching { obj["sourceLinkInvalid"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() }.getOrNull()
        if (known == null || markedInvalid == null || !known) {
            return DecodedRecordSourceLink(ids, known = known == true, invalid = true)
        }
        if (markedInvalid) return DecodedRecordSourceLink(ids, known = true, invalid = true)
        val linkElement = obj["insightReference"]
        if (linkElement == null) return DecodedRecordSourceLink(ids, known, invalid = true)
        if (linkElement == kotlinx.serialization.json.JsonNull) return DecodedRecordSourceLink(ids, known)
        val link = runCatching {
            val ref = linkElement.jsonObject
            val reportId = ref["reportId"]?.jsonPrimitive?.content ?: return@runCatching null
            val version = ref["dataVersion"]?.jsonPrimitive?.content ?: return@runCatching null
            val insightId = ref["insightId"]?.jsonPrimitive?.content ?: return@runCatching null
            InsightReference(reportId, version, insightId)
        }.getOrNull()
        return if (link == null) DecodedRecordSourceLink(ids, known, invalid = true)
        else DecodedRecordSourceLink(ids, known, insightReference = link)
    }
}

/** 供 UI/测试复用的纯摘要：不改数据库。 */
object RecordProposalFormat {
    fun pendingLabel(field: String): String = when (field) {
        "goal" -> "目标"
        "metricValue" -> "数值"
        "occurredAt" -> "时间"
        else -> field
    }

    fun statusLabel(status: String): String = when (status) {
        RecordProposalStatus.DRAFT -> "待确认"
        RecordProposalStatus.APPLIED -> "已记录"
        RecordProposalStatus.CANCELLED -> "已取消"
        RecordProposalStatus.UNDONE -> "已撤销"
        else -> status
    }
}
