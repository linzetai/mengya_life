package com.mengyalife.app.data.care

import com.mengyalife.app.data.local.care.CareDao
import com.mengyalife.app.data.local.care.CareDecisionEntity
import com.mengyalife.app.data.local.care.CareFeedbackEntity
import com.mengyalife.app.data.local.care.CareOpportunityEntity
import com.mengyalife.app.domain.care.CareOpportunityFactory

class CareRepository(private val dao: CareDao, private val now: () -> Long = System::currentTimeMillis) {

    suspend fun ensure(drafts: List<CareOpportunityFactory.Draft>): List<CareOpportunityEntity> {
        val ts = now()
        drafts.forEach { d ->
            dao.insertOpportunity(
                CareOpportunityEntity(
                    type = d.type, sourceId = d.sourceId, planId = d.planId, goalId = d.goalId,
                    title = d.title, discoveredAt = ts, windowStart = d.windowStart,
                    windowEnd = d.windowEnd, expiresAt = d.expiresAt,
                    sensitivity = "NORMAL", status = "PENDING", dedupeKey = d.dedupeKey,
                    createdAt = ts, updatedAt = ts,
                ),
            )
        }
        return dao.pendingOpportunities()
    }

    suspend fun pending(): List<CareOpportunityEntity> = dao.pendingOpportunities()

    suspend fun find(id: Long): CareOpportunityEntity? = dao.findOpportunity(id)

    suspend fun markStatus(id: Long, status: String) = dao.updateOpportunityStatus(id, status, now())

    suspend fun claimSending(id: Long): Boolean = dao.claimSending(id, now()) > 0

    suspend fun markDecisionDelivered(decisionId: Long, messageId: String?) =
        dao.markDecisionDelivered(decisionId, messageId, now())

    suspend fun saveDecision(
        opportunityId: Long,
        action: String,
        reason: String,
        sourceRefs: String,
        suggestedText: String?,
        avoidJson: String?,
        nextStart: Long?,
        nextEnd: Long?,
        modelOk: Boolean,
    ): Long {
        return dao.insertDecision(
            CareDecisionEntity(
                opportunityId = opportunityId, action = action, reason = reason,
                sourceRefs = sourceRefs, suggestedText = suggestedText, avoidJson = avoidJson,
                nextWindowStart = nextStart, nextWindowEnd = nextEnd, modelOk = modelOk,
                createdAt = now(),
            ),
        )
    }

    suspend fun decisionsSince(since: Long): Int = dao.decisionCountSince(since)

    suspend fun sentSince(since: Long): List<CareDecisionEntity> = dao.sentSince(since)

    suspend fun latestSentWithoutFeedback(): CareDecisionEntity? {
        val decisions = dao.sentSince(0)
        return decisions.firstOrNull { d -> dao.latestFeedback(d.opportunityId) == null }
    }

    suspend fun latestDecision(id: Long) = dao.latestDecision(id)

    suspend fun addFeedback(opportunityId: Long, kind: String) {
        dao.insertFeedback(CareFeedbackEntity(opportunityId = opportunityId, kind = kind, createdAt = now()))
    }

    suspend fun silencedTopics(): Set<String> {
        val silenced = dao.silencedFeedback()
        val keys = mutableSetOf<String>()
        silenced.forEach { fb ->
            val opp = dao.findOpportunity(fb.opportunityId) ?: return@forEach
            opp.planId?.let { keys += "plan:$it" }
            opp.goalId?.let { keys += "goal:$it" }
        }
        return keys
    }

    suspend fun latestDecisionFor(id: Long) = dao.latestDecision(id)

    suspend fun latestSentDecision(): CareDecisionEntity? = dao.latestSentDecision()

    /** 系统通知最多一次；返回 true 才允许调用 NotificationManager。 */
    suspend fun claimNotification(decisionId: Long): Boolean = dao.claimNotification(decisionId, now()) == 1
}
