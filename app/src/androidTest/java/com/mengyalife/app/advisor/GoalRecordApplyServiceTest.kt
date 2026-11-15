package com.mengyalife.app.advisor

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mengyalife.app.data.advisor.GoalApplyResult
import com.mengyalife.app.data.advisor.GoalRecordApplyService
import com.mengyalife.app.data.advisor.RoomRecordProposalRepository
import com.mengyalife.app.data.local.MengyaDatabase
import com.mengyalife.app.data.local.advisor.AdvisorMessageEntity
import com.mengyalife.app.data.repository.GoalRepository
import com.mengyalife.app.data.restore.RestoreWriteBarrier
import com.mengyalife.app.domain.advisor.GoalBreakdownSuggestion
import com.mengyalife.app.domain.advisor.GoalChangeDraft
import com.mengyalife.app.domain.advisor.GoalChangeKind
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
class GoalRecordApplyServiceTest {
    private lateinit var db: MengyaDatabase
    private lateinit var barrier: RestoreWriteBarrier
    private lateinit var proposals: RoomRecordProposalRepository
    private lateinit var apply: GoalRecordApplyService

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), MengyaDatabase::class.java,
        ).allowMainThreadQueries().build()
        barrier = RestoreWriteBarrier(initiallyReady = true)
        proposals = RoomRecordProposalRepository(db.recordProposalDao(), writeBarrier = barrier)
        apply = GoalRecordApplyService(
            db, db.recordProposalDao(), GoalRepository(db.goalDao(), writeBarrier = barrier),
            writeBarrier = barrier,
        )
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun directApplyCannotBypassDurableInsightReference() = runBlocking {
        val draft = goalDraft().copy(
            sourceLinkKnown = true,
            sourceInsightReference = InsightReference("report", "version", "insight"),
        )
        val saved = proposals.save("assistant-reference", draft, barrier.generation())
        assertRejectedAndUnchanged(saved.id)
    }

    @Test
    fun directApplyRejectsLegacyInsightMarkersAndMissingSourceMessages() = runBlocking {
        for ((index, marker) in listOf("INSIGHT", "INSIGHT_FACT", "{invalid").withIndex()) {
            val sourceId = "source-$index"
            db.advisorMessageDao().insert(AdvisorMessageEntity(
                id = sourceId, role = "user", content = "source", createdAt = index.toLong(), status = "SENT",
                sourcesJson = if (marker.startsWith("{")) marker else "[{\"type\":\"$marker\"}]",
            ))
            val saved = proposals.save("assistant-legacy-$index", goalDraft().copy(
                sourceMessageIds = listOf(sourceId),
            ), barrier.generation())
            // Old rows used an ID array rather than the durable provenance envelope.
            db.openHelper.writableDatabase.execSQL(
                "UPDATE record_proposals SET source_message_ids_json = ? WHERE id = ?",
                arrayOf("[\"$sourceId\"]", saved.id),
            )
            assertRejectedAndUnchanged(saved.id)
        }
        val missing = proposals.save("assistant-missing", goalDraft().copy(
            sourceMessageIds = listOf("deleted-source"),
        ), barrier.generation())
        assertRejectedAndUnchanged(missing.id)
    }

    @Test
    fun assistantMarkerCannotBypassValidationWithEmptySourceIds() = runBlocking {
        db.advisorMessageDao().insert(AdvisorMessageEntity(
            id = "assistant-marker", role = "assistant", content = "source", createdAt = 1L,
            status = "SENT", sourcesJson = "[{\"type\":\"INSIGHT\"}]",
        ))
        val saved = proposals.save("assistant-marker", goalDraft(), barrier.generation())
        assertRejectedAndUnchanged(saved.id)
    }

    @Test
    fun corruptProvenanceCannotBecomeAnUnrelatedDraft() = runBlocking {
        val saved = proposals.save("assistant-corrupt", goalDraft().copy(
            sourceLinkKnown = true, sourceLinkInvalid = true,
        ), barrier.generation())
        assertRejectedAndUnchanged(saved.id)
        db.openHelper.writableDatabase.execSQL(
            "UPDATE record_proposals SET source_message_ids_json = ? WHERE id = ?",
            arrayOf("{broken", saved.id),
        )
        assertRejectedAndUnchanged(saved.id)
    }

    @Test
    fun staleValidatorRunsInsideTransactionBeforeAnyMutation() = runBlocking {
        val seed = proposals.save("seed", goalDraft(), barrier.generation())
        val target = (apply.apply(seed.id) as GoalApplyResult.Success).goalIds.single()
        for (kind in GoalChangeKind.entries) {
            val draft = goalDraft().copy(goalChange = GoalChangeDraft(
                change = kind, targetGoalId = target, title = "不得保存",
                breakdown = listOf(GoalBreakdownSuggestion("不得创建子目标", "STAGE")),
            ))
            val saved = proposals.save("stale-$kind", draft, barrier.generation())
            val before = db.goalDao().loadAllGoals()
            val proposalBefore = db.recordProposalDao().findById(saved.id)
            var inTransaction = false
            val result = apply.applyValidated(saved.id, barrier.generation()) {
                inTransaction = db.inTransaction()
                assertEquals(before, db.goalDao().loadAllGoals())
                "洞察来源版本已变化"
            }
            assertTrue(inTransaction)
            assertTrue(result is GoalApplyResult.Failure)
            assertTrue((result as GoalApplyResult.Failure).message.contains("来源版本已变化"))
            assertEquals(before, db.goalDao().loadAllGoals())
            assertEquals(proposalBefore, db.recordProposalDao().findById(saved.id))
        }
    }

    @Test
    fun validatedConfirmationCreatesSelectedChildrenAndIsIdempotent() = runBlocking {
        val reference = InsightReference("report", "version", "insight")
        val draft = goalDraft().copy(
            sourceLinkKnown = true, sourceInsightReference = reference,
            goalChange = goalDraft().goalChange!!.copy(breakdown = listOf(
                GoalBreakdownSuggestion("选中的阶段", "STAGE"),
                GoalBreakdownSuggestion("不选的阶段", "STAGE", selected = false),
            )),
        )
        val saved = proposals.save("assistant-confirm", draft, barrier.generation())
        assertTrue(db.goalDao().loadAllGoals().isEmpty())
        var validations = 0
        val first = apply.applyValidated(saved.id, barrier.generation()) {
            assertTrue(db.inTransaction())
            validations++
            null
        } as GoalApplyResult.Success
        assertEquals(2, first.goalIds.size)
        assertEquals(2, db.goalDao().loadAllGoals().size)
        assertTrue(proposals.findById(saved.id)!!.isApplied)
        assertEquals(reference, proposals.findById(saved.id)!!.draft.sourceInsightReference)
        val repeated = apply.applyValidated(saved.id, barrier.generation()) {
            error("Applied retry must not mutate or revalidate")
        } as GoalApplyResult.Success
        assertEquals(first.goalIds, repeated.goalIds)
        assertEquals(first, apply.apply(saved.id))
        assertEquals(1, validations)
        assertEquals(2, db.goalDao().loadAllGoals().size)
    }

    @Test
    fun failedChildCreationRollsBackParentAndDraftStatus() = runBlocking {
        val saved = proposals.save("assistant-rollback", goalDraft().copy(
            goalChange = goalDraft().goalChange!!.copy(breakdown = listOf(
                GoalBreakdownSuggestion("合法子目标", "STAGE"),
                GoalBreakdownSuggestion("长".repeat(com.mengyalife.app.domain.goals.GoalRules.MAX_TITLE + 1), "STAGE"),
            )),
        ), barrier.generation())
        val before = db.recordProposalDao().findById(saved.id)
        val result = apply.applyValidated(saved.id, barrier.generation()) { null }
        assertTrue(result is GoalApplyResult.Failure)
        assertTrue(db.goalDao().loadAllGoals().isEmpty())
        assertEquals(before, db.recordProposalDao().findById(saved.id))
    }

    @Test
    fun staleRestoreGenerationAndMaintenanceFailWithoutInvokingValidator() = runBlocking {
        val saved = proposals.save("assistant-generation", goalDraft(), barrier.generation())
        val oldGeneration = barrier.generation()
        barrier.withExclusive { }
        barrier.setReady(true)
        var validations = 0
        val stale = apply.applyValidated(saved.id, oldGeneration) { validations++; null }
        assertTrue(stale is GoalApplyResult.Failure)
        assertTrue((stale as GoalApplyResult.Failure).message.contains("旧请求已过期"))
        barrier.setReady(false)
        assertTrue(apply.apply(saved.id) is GoalApplyResult.Failure)
        assertEquals(0, validations)
        assertTrue(db.goalDao().loadAllGoals().isEmpty())
        assertTrue(proposals.findById(saved.id)!!.isDraft)
    }

    @Test
    fun missingBarrierFailsClosedAndPlainDraftStillAppliesWithBarrier() = runBlocking {
        val saved = proposals.save("assistant-plain", goalDraft(), barrier.generation())
        val unguarded = GoalRecordApplyService(db, db.recordProposalDao(), GoalRepository(db.goalDao()))
        assertTrue(unguarded.apply(saved.id) is GoalApplyResult.Failure)
        assertTrue(unguarded.applyValidated(saved.id, barrier.generation()) { null } is GoalApplyResult.Failure)
        assertTrue(db.goalDao().loadAllGoals().isEmpty())
        val first = apply.apply(saved.id) as GoalApplyResult.Success
        assertEquals(first, apply.apply(saved.id))
        assertEquals(1, db.goalDao().loadAllGoals().size)
    }

    private suspend fun assertRejectedAndUnchanged(proposalId: String) {
        val before = db.recordProposalDao().findById(proposalId)
        val goalsBefore = db.goalDao().loadAllGoals()
        val result = apply.apply(proposalId)
        assertTrue(result is GoalApplyResult.Failure)
        assertTrue((result as GoalApplyResult.Failure).message.contains("必须经过 Insight"))
        assertEquals(goalsBefore, db.goalDao().loadAllGoals())
        assertEquals(before, db.recordProposalDao().findById(proposalId))
    }

    private fun goalDraft() = RecordProposalDraft(
        kind = RecordKind.GOAL, goalId = null, goalTitle = null, goalCandidates = emptyList(),
        amountKind = RecordAmountKind.NONE, metricValue = null, metricUnit = null,
        note = "读书目标", occurredAt = null, pendingFields = emptyList(),
        intent = RecordIntent.SUGGESTED, originalText = "读书目标",
        goalChange = GoalChangeDraft(change = GoalChangeKind.CREATE, title = "读书目标"),
    )
}
