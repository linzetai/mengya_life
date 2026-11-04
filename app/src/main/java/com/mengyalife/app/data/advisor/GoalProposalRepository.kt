package com.mengyalife.app.data.advisor

import com.mengyalife.app.data.local.proposal.GoalProposalDao
import com.mengyalife.app.data.local.proposal.GoalProposalEntity
import com.mengyalife.app.domain.advisor.GoalProposalDraft
import com.mengyalife.app.domain.advisor.GoalProposalParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class StoredGoalProposal(
    val id: String,
    val messageId: String,
    val createdAt: Long,
    val draft: GoalProposalDraft,
    val status: String,
)

interface GoalProposalRepository {
    fun observeAll(): Flow<List<StoredGoalProposal>>
    suspend fun save(messageId: String, draft: GoalProposalDraft): StoredGoalProposal
    suspend fun findForMessage(messageId: String): StoredGoalProposal?
}

class RoomGoalProposalRepository(
    private val dao: GoalProposalDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : GoalProposalRepository {

    override fun observeAll(): Flow<List<StoredGoalProposal>> =
        dao.observeAll().map { rows -> rows.mapNotNull(::toDomain) }

    override suspend fun save(messageId: String, draft: GoalProposalDraft): StoredGoalProposal {
        val encoded = GoalProposalParser.encode(draft)
        val id = newId()
        val createdAt = now()
        dao.insert(
            GoalProposalEntity(
                id = id,
                messageId = messageId,
                createdAt = createdAt,
                schemaVersion = draft.schemaVersion,
                status = "DRAFT",
                summary = encoded.summary,
                assumptionsJson = encoded.assumptionsJson,
                risksJson = encoded.risksJson,
                nodesJson = encoded.nodesJson,
            ),
        )
        return StoredGoalProposal(id, messageId, createdAt, draft, "DRAFT")
    }

    override suspend fun findForMessage(messageId: String): StoredGoalProposal? =
        dao.findByMessage(messageId)?.let(::toDomain)

    private fun toDomain(entity: GoalProposalEntity): StoredGoalProposal? {
        val draft = GoalProposalParser.decode(
            schemaVersion = entity.schemaVersion,
            summary = entity.summary,
            assumptionsJson = entity.assumptionsJson,
            risksJson = entity.risksJson,
            nodesJson = entity.nodesJson,
        ) ?: return null
        return StoredGoalProposal(
            id = entity.id,
            messageId = entity.messageId,
            createdAt = entity.createdAt,
            draft = draft,
            status = entity.status,
        )
    }
}
