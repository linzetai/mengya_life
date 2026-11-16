package com.mengyalife.app.plan

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mengyalife.app.data.local.MengyaDatabase
import com.mengyalife.app.data.local.plan.PlanProgressEntity
import com.mengyalife.app.data.local.plan.SmallPlanEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmallPlanDaoIntegrationTest {
    private lateinit var database: MengyaDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), MengyaDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun observeProgressBetweenReturnsOnlyActualRowsInsideTheWeekWindow() = runBlocking {
        val dao = database.smallPlanDao()
        dao.insert(SmallPlanEntity(id = 1, title = "本周阅读", timeScope = "本周", goalId = null,
            status = "ACTIVE", sourceMessageId = "plan-1", createdAt = 1, updatedAt = 1))
        dao.insert(SmallPlanEntity(id = 2, title = "其他计划", timeScope = "本周", goalId = null,
            status = "ACTIVE", sourceMessageId = "plan-2", createdAt = 1, updatedAt = 1))
        listOf(
            PlanProgressEntity(id = 1, planId = 1, note = "开始", recordedAt = 99, sourceMessageId = "p-1"),
            PlanProgressEntity(id = 2, planId = 1, note = "本周内", recordedAt = 100, sourceMessageId = "p-2"),
            PlanProgressEntity(id = 3, planId = 1, note = "本周内2", recordedAt = 200, sourceMessageId = "p-3"),
            PlanProgressEntity(id = 4, planId = 1, note = "下周", recordedAt = 201, sourceMessageId = "p-4"),
            PlanProgressEntity(id = 5, planId = 2, note = "另一个计划", recordedAt = 150, sourceMessageId = "p-5"),
        ).forEach { dao.insertProgress(it) }

        val inWindow = dao.observeProgressBetween(100, 200).first()
        assertEquals(listOf(2L, 5L, 3L), inWindow.map { it.id })
        assertEquals(2, inWindow.count { it.planId == 1L })
    }
}
