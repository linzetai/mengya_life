package com.mengyalife.app.advisor

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mengyalife.app.data.advisor.PlanApplyResult
import com.mengyalife.app.data.advisor.PlanRecordApplyService
import com.mengyalife.app.data.advisor.RoomRecordProposalRepository
import com.mengyalife.app.data.local.MengyaDatabase
import com.mengyalife.app.data.repository.SmallPlanRepository
import com.mengyalife.app.data.restore.RestoreWriteBarrier
import com.mengyalife.app.domain.advisor.PlanChangeDraft
import com.mengyalife.app.domain.advisor.PlanChangeKind
import com.mengyalife.app.domain.advisor.RecordAmountKind
import com.mengyalife.app.domain.advisor.RecordIntent
import com.mengyalife.app.domain.advisor.RecordKind
import com.mengyalife.app.domain.advisor.RecordProposalDraft
import com.mengyalife.app.domain.insight.InsightReference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanRecordApplyServiceTest {
    private lateinit var db: MengyaDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MengyaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insightProvenanceSurvivesExistingSourceIdsColumnAndDraftEditing() = runBlocking {
        val barrier = RestoreWriteBarrier(initiallyReady = true)
        val proposals = RoomRecordProposalRepository(db.recordProposalDao(), writeBarrier = barrier)
        val reference = InsightReference("report-stable", "source-hash|2026-11-13", "insight-stable")
        val original = planDraft("有来源的计划", "这周").copy(
            sourceMessageIds = listOf("handoff-user", "action-user"),
            sourceLinkKnown = true,
            sourceInsightReference = reference,
        )
        val stored = proposals.save("assistant-linked-plan", original, barrier.generation())
        assertEquals(reference, stored.draft.sourceInsightReference)
        assertEquals(listOf("handoff-user", "action-user"), stored.draft.sourceMessageIds)

        val edited = original.copy(planChange = original.planChange!!.copy(title = "编辑后的计划"))
        assertTrue(proposals.updateDraft(stored.id, edited))
        val reloaded = proposals.findById(stored.id)!!
        assertEquals(reference, reloaded.draft.sourceInsightReference)
        assertEquals(listOf("handoff-user", "action-user"), reloaded.draft.sourceMessageIds)
    }

    @Test
    fun directApplyCannotBypassInsightProvenanceValidation() = runBlocking {
        val barrier = RestoreWriteBarrier(initiallyReady = true)
        val proposals = RoomRecordProposalRepository(db.recordProposalDao(), writeBarrier = barrier)
        val reference = InsightReference("report-direct", "source-hash|2026-11-13", "insight-direct")
        val draft = planDraft("不得绕过来源校验", "这周").copy(
            sourceMessageIds = listOf("insight-handoff"),
            sourceLinkKnown = true,
            sourceInsightReference = reference,
        )
        val saved = proposals.save("assistant-direct", draft, barrier.generation())
        val apply = PlanRecordApplyService(
            database = db,
            recordDao = db.recordProposalDao(),
            planRepository = SmallPlanRepository(db.smallPlanDao()),
            writeBarrier = barrier,
        )

        val result = apply.apply(saved.id)
        assertTrue(result is PlanApplyResult.Failure)
        assertTrue((result as PlanApplyResult.Failure).message.contains("必须经过 Insight"))
        assertTrue(db.smallPlanDao().loadAll().isEmpty())
        assertTrue(proposals.findById(saved.id)!!.isDraft)
    }

    @Test
    fun sourceValidatorRunsInsideRoomTransactionAndLeavesDraftUntouchedWhenStale() = runBlocking {
        val barrier = RestoreWriteBarrier(initiallyReady = true)
        val proposals = RoomRecordProposalRepository(db.recordProposalDao(), writeBarrier = barrier)
        val saved = proposals.save("assistant-stale-source", planDraft("新计划", "这周"), barrier.generation())
        val apply = PlanRecordApplyService(
            database = db,
            recordDao = db.recordProposalDao(),
            planRepository = SmallPlanRepository(db.smallPlanDao()),
            writeBarrier = barrier,
        )
        var validatorRanInTransaction = false

        val result = apply.applyValidated(saved.id, expectedRestoreGeneration = barrier.generation()) {
            validatorRanInTransaction = db.inTransaction()
            "洞察来源版本已变化"
        }

        assertTrue(validatorRanInTransaction)
        assertTrue(result is PlanApplyResult.Failure)
        assertTrue((result as PlanApplyResult.Failure).message.contains("来源版本已变化"))
        assertTrue(db.smallPlanDao().loadAll().isEmpty())
        assertTrue(proposals.findById(saved.id)!!.isDraft)
    }

    @Test
    fun editedPlanDraftIsSavedOnlyOnConfirmationAndConfirmationIsIdempotent() = runBlocking {
        val barrier = RestoreWriteBarrier(initiallyReady = true)
        val proposals = RoomRecordProposalRepository(db.recordProposalDao(), writeBarrier = barrier)
        val original = planDraft("这周读三次书", "这周")
        val saved = proposals.save("assistant-plan-draft", original, barrier.generation())

        assertTrue(db.smallPlanDao().loadAll().isEmpty()) // draft alone is not business data

        val edited = original.copy(
            planChange = original.planChange!!.copy(title = "这周读四次书", timeScope = "这周"),
            note = "这周读四次书",
        )
        assertTrue(proposals.updateDraft(saved.id, edited))
        assertEquals("这周读四次书", proposals.findById(saved.id)!!.draft.planChange!!.title)
        assertTrue(db.smallPlanDao().loadAll().isEmpty())

        val apply = PlanRecordApplyService(
            database = db,
            recordDao = db.recordProposalDao(),
            planRepository = SmallPlanRepository(db.smallPlanDao()),
            writeBarrier = barrier,
        )
        val first = apply.apply(saved.id) as PlanApplyResult.Success
        val plans = db.smallPlanDao().loadAll()
        assertEquals(1, plans.size)
        assertEquals("这周读四次书", plans.single().title)
        assertEquals("这周", plans.single().timeScope)
        assertEquals("assistant-plan-draft", plans.single().sourceMessageId)
        assertEquals(1, db.smallPlanDao().loadAllEvents().size)
        assertTrue(proposals.findById(saved.id)!!.isApplied)
        assertEquals(first.planId, proposals.findById(saved.id)!!.appliedPlanId)

        val repeated = apply.apply(saved.id) as PlanApplyResult.Success
        assertEquals(first.planId, repeated.planId)
        assertEquals(1, db.smallPlanDao().loadAll().size)
        assertEquals(1, db.smallPlanDao().loadAllEvents().size)
    }

    private fun planDraft(title: String, timeScope: String) = RecordProposalDraft(
        kind = RecordKind.PLAN,
        goalId = null,
        goalTitle = null,
        goalCandidates = emptyList(),
        amountKind = RecordAmountKind.NONE,
        metricValue = null,
        metricUnit = null,
        note = title,
        occurredAt = null,
        pendingFields = emptyList(),
        intent = RecordIntent.SUGGESTED,
        originalText = title,
        planChange = PlanChangeDraft(
            change = PlanChangeKind.CREATE,
            title = title,
            timeScope = timeScope,
        ),
    )
}
