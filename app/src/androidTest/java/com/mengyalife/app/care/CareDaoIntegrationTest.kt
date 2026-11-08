package com.mengyalife.app.care

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mengyalife.app.data.local.MengyaDatabase
import com.mengyalife.app.data.local.care.CareDecisionEntity
import com.mengyalife.app.data.local.care.CareOpportunityEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CareDaoIntegrationTest {
    private lateinit var db: MengyaDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MengyaDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun concurrentClaimOnlyOneWins() = runBlocking {
        val dao = db.careDao()
        val now = System.currentTimeMillis()
        val oppId = dao.insertOpportunity(
            CareOpportunityEntity(
                type = "TEST", sourceId = 2, planId = null, goalId = null, title = "并发",
                discoveredAt = now, windowStart = now, windowEnd = now + 1000,
                expiresAt = now + 2000, sensitivity = "NORMAL", status = "SENDING",
                dedupeKey = "test:2", createdAt = now, updatedAt = now,
            ),
        )
        val decisionId = dao.insertDecision(
            CareDecisionEntity(
                opportunityId = oppId, action = "SEND", reason = "test", sourceRefs = "test",
                suggestedText = "hi", avoidJson = null, nextWindowStart = null, nextWindowEnd = null,
                modelOk = true, createdAt = now,
            ),
        )
        val results = coroutineScope {
            val a = async(Dispatchers.IO) { dao.claimNotification(decisionId, now) }
            val b = async(Dispatchers.IO) { dao.claimNotification(decisionId, now) }
            listOf(a.await(), b.await())
        }
        assertEquals(1, results.count { it == 1 })
    }

    @Test
    fun notificationClaimIsIdempotentAndSendingReconciles() = runBlocking {
        val dao = db.careDao()
        val now = System.currentTimeMillis()
        val oppId = dao.insertOpportunity(
            CareOpportunityEntity(
                type = "TEST", sourceId = 1, planId = null, goalId = null, title = "测试",
                discoveredAt = now, windowStart = now, windowEnd = now + 1000,
                expiresAt = now + 2000, sensitivity = "NORMAL", status = "SENDING",
                dedupeKey = "test:1", createdAt = now, updatedAt = now,
            ),
        )
        val decisionId = dao.insertDecision(
            CareDecisionEntity(
                opportunityId = oppId, action = "SEND", reason = "test", sourceRefs = "test",
                suggestedText = "hi", avoidJson = null, nextWindowStart = null, nextWindowEnd = null,
                modelOk = true, createdAt = now,
            ),
        )
        assertTrue(dao.claimNotification(decisionId, now) == 1)
        assertFalse(dao.claimNotification(decisionId, now + 1) == 1)
        assertEquals(1, db.carePreferenceDao().reconcileSending(now + 2))
        assertEquals("UNKNOWN", dao.findOpportunity(oppId)!!.status)
    }
}
