package com.mengyalife.app.data.advisor

import com.mengyalife.app.data.local.proposal.GoalProposalDao
import com.mengyalife.app.data.local.proposal.GoalProposalEntity
import com.mengyalife.app.domain.advisor.GoalProposalDraft
import com.mengyalife.app.domain.advisor.GoalProposalIds
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
    val selectedTempIds: Set<String> = emptySet(),
    val appliedAt: Long? = null,
    val appliedGoalIds: List<Long> = emptyList(),
    val undoneAt: Long? = null,
) {
    val isDraft: Boolean get() = status == "DRAFT"
    val isApplied: Boolean get() = status == "APPLIED"
    val isUndone: Boolean get() = status == "UNDONE"
}

interface GoalProposalRepository {
    fun observeAll(): Flow<List<StoredGoalProposal>>
    suspend fun save(messageId: String, draft: GoalProposalDraft): StoredGoalProposal
    suspend fun findForMessage(messageId: String): StoredGoalProposal?
    suspend fun findById(id: String): StoredGoalProposal?
    /** 审核期间编辑/勾选：只更新 DRAFT。 */
    suspend fun updateDraft(id: String, draft: GoalProposalDraft, selected: Set<String>): Boolean
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
        val selected = draft.nodes.map { it.tempId }.toSet()
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
                selectedTempIdsJson = GoalProposalIds.encodeSelected(selected),
            ),
        )
        return StoredGoalProposal(
            id = id,
            messageId = messageId,
            createdAt = createdAt,
            draft = draft,
            status = "DRAFT",
            selectedTempIds = selected,
        )
    }

    override suspend fun findForMessage(messageId: String): StoredGoalProposal? =
        dao.findByMessage(messageId)?.let(::toDomain)

    override suspend fun findById(id: String): StoredGoalProposal? =
        dao.findById(id)?.let(::toDomain)

    override suspend fun updateDraft(
        id: String,
        draft: GoalProposalDraft,
        selected: Set<String>,
    ): Boolean {
        val encoded = GoalProposalParser.encode(draft)
        return dao.updateDraft(id, encoded.nodesJson, GoalProposalIds.encodeSelected(selected)) > 0
    }

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
            selectedTempIds = GoalProposalIds.decodeSelected(entity.selectedTempIdsJson),
            appliedAt = entity.appliedAt,
            appliedGoalIds = GoalProposalIds.decodeMapping(entity.appliedGoalIdsJson).values.toList(),
            undoneAt = entity.undoneAt,
        )
    }

}
