package com.mengyalife.app.ui.candidates

import com.mengyalife.app.data.local.goal.GoalEntity
import com.mengyalife.app.data.repository.GoalNode
import com.mengyalife.app.data.repository.GoalSnapshot
import com.mengyalife.app.domain.advisor.AdvisorMessage
import com.mengyalife.app.domain.advisor.AdvisorRole
import com.mengyalife.app.domain.advisor.AdvisorMessageStatus
import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalStatus
import com.mengyalife.app.ui.advisor.AdvisorUiState
import com.mengyalife.app.ui.presentation.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CandidateConversationTest {
    private val goal = GoalEntity(10, "阅读计划", level = GoalLevel.ACTION, domain = GoalDomain.LEARNING,
        status = GoalStatus.IN_PROGRESS, createdAt = 1L, updatedAt = 2L)
    private val plan = InformationPresentation(
        subject = InformationSubject(InformationSubjectType.PLAN, 20, "每周阅读", linkedGoal = InformationGoalIdentity(10, "阅读计划"),
            goalRelation = InformationGoalRelation.LINKED, displayName = "每周阅读 · 阅读计划"),
        observation = emptyList(), suggestion = null, nextAction = InformationNextAction.OpenPlan(20),
        evidence = InformationEvidence(binding = InformationBinding()), state = InformationState.VALID, issues = emptySet(),
    )
    private val snapshot = CandidateSnapshot(
        context = InformationContext(GoalSnapshot(roots = listOf(GoalNode(goal, emptyList(), 0, null))),
            emptyList(), emptyList(), InformationBinding(), now = 100),
        report = null, observations = emptyList(), plans = listOf(plan), care = null,
    )

    @Test fun onlyExplicitPreferenceIsShownAsVerbatimShortQuote() {
        val message = AdvisorMessage(
            id = "m1", role = AdvisorRole.USER,
            content = "阅读累计记录更新到二十分钟。我想先保持每周两次、每次十分钟。",
            createdAt = 10, contextGoalId = 10,
        )
        assertEquals("我想先保持每周两次、每次十分钟。", candidateUserContext(snapshot, AdvisorUiState(messages = listOf(message))))
    }

    @Test fun proseWithoutAnExplicitPreferenceIsNotReframedAsTheUsersReason() {
        val message = AdvisorMessage(id = "m2", role = AdvisorRole.USER, content = "最近读了几页书。", createdAt = 11, contextGoalId = 10)
        assertNull(candidateUserContext(snapshot, AdvisorUiState(messages = listOf(message))))
    }

    @Test fun samePreferenceOnAnotherGoalCannotExplainThisRecommendation() {
        val message = AdvisorMessage(
            id = "m3", role = AdvisorRole.USER, content = "我想先保持每周两次。", createdAt = 12, contextGoalId = 99,
        )
        assertNull(candidateUserContext(snapshot, AdvisorUiState(messages = listOf(message))))
    }

    @Test fun failedOrUnsentMessagesAreNotUserPreferenceEvidence() {
        val message = AdvisorMessage(
            id = "m4", role = AdvisorRole.USER, content = "我想先保持每周两次。", createdAt = 13,
            contextGoalId = 10, status = AdvisorMessageStatus.FAILED,
        )
        assertNull(candidateUserContext(snapshot, AdvisorUiState(messages = listOf(message))))
    }
}
