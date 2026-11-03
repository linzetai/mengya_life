package com.mengyalife.app.data

import com.mengyalife.app.data.local.goal.GoalCheckinEntity
import com.mengyalife.app.data.local.goal.GoalCheckinStat
import com.mengyalife.app.data.local.goal.GoalDao
import com.mengyalife.app.data.local.goal.GoalEntity
import com.mengyalife.app.data.repository.GoalDraft
import com.mengyalife.app.data.repository.GoalRepository
import com.mengyalife.app.data.repository.GoalResult
import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalRules
import com.mengyalife.app.domain.goals.GoalStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 内存版 GoalDao：模拟 SQL 的排序、级联删除与统计语义。 */
private class FakeGoalDao : GoalDao {
    private val goals = MutableStateFlow<List<GoalEntity>>(emptyList())
    private val checkins = MutableStateFlow<List<GoalCheckinEntity>>(emptyList())
    private var nextGoalId = 1L
    private var nextCheckinId = 1L

    override fun observeGoals(): Flow<List<GoalEntity>> =
        goals.map { list -> list.sortedWith(compareBy<GoalEntity> { it.level.ordinal }.thenBy { it.createdAt }) }

    override fun observeCheckinStats(): Flow<List<GoalCheckinStat>> = checkins.map { list ->
        list.groupBy { it.goalId }.map { (goalId, items) ->
            GoalCheckinStat(goalId, items.size, items.maxOfOrNull { it.recordedAt })
        }
    }

    override fun observeCheckins(goalId: Long): Flow<List<GoalCheckinEntity>> =
        checkins.map { list -> list.filter { it.goalId == goalId }.sortedByDescending { it.recordedAt } }

    override fun observeRecentCheckins(limit: Int): Flow<List<GoalCheckinEntity>> =
        checkins.map { list -> list.sortedByDescending { it.recordedAt }.take(limit) }

    override suspend fun findGoal(id: Long): GoalEntity? = goals.value.firstOrNull { it.id == id }

    override suspend fun loadAllGoals(): List<GoalEntity> = goals.value

    override suspend fun insertGoal(goal: GoalEntity): Long {
        val id = nextGoalId++
        goals.update { it + goal.copy(id = id) }
        return id
    }

    override suspend fun updateGoal(goal: GoalEntity): Int {
        if (goals.value.none { it.id == goal.id }) return 0
        goals.update { list -> list.map { if (it.id == goal.id) goal else it } }
        return 1
    }

    override suspend fun deleteGoalsByIds(ids: List<Long>): Int {
        val before = goals.value.size
        goals.update { list -> list.filterNot { it.id in ids } }
        return before - goals.value.size
    }

    override suspend fun insertCheckin(checkin: GoalCheckinEntity): Long {
        val id = nextCheckinId++
        checkins.update { it + checkin.copy(id = id) }
        return id
    }

    override suspend fun deleteCheckinsForGoals(goalIds: List<Long>): Int {
        val before = checkins.value.size
        checkins.update { list -> list.filterNot { it.goalId in goalIds } }
        return before - checkins.value.size
    }

    override suspend fun countCheckinsForGoals(goalIds: List<Long>): Int =
        checkins.value.count { it.goalId in goalIds }
}

class GoalRepositoryTest {

    private var clock = 1_700_000_000_000L
    private fun repo(dao: GoalDao) = GoalRepository(dao) { clock }

    private fun draft(
        title: String = "目标",
        status: GoalStatus = GoalStatus.NOT_STARTED,
    ) = GoalDraft(
        title = title,
        description = null,
        domain = GoalDomain.HEALTH,
        status = status,
        startDate = null,
        dueDate = null,
    )

    @Test
    fun `长期目标可以独立创建`() = runTest {
        val dao = FakeGoalDao()
        val result = repo(dao).createGoal(GoalLevel.LONG_TERM, null, draft("长期"))
        assertTrue(result is GoalResult.Ok)
        val id = (result as GoalResult.Ok).value
        val saved = dao.findGoal(id)!!
        assertEquals(GoalLevel.LONG_TERM, saved.level)
        assertEquals(null, saved.parentId)
        assertEquals(saved.createdAt, saved.updatedAt)
    }

    @Test
    fun `阶段目标必须挂长期目标`() = runTest {
        val dao = FakeGoalDao()
        val repo = repo(dao)

        val orphan = repo.createGoal(GoalLevel.STAGE, null, draft())
        assertEquals(GoalRules.ERROR_PARENT_REQUIRED, (orphan as GoalResult.Error).message)

        val parentId = (repo.createGoal(GoalLevel.LONG_TERM, null, draft("长期")) as GoalResult.Ok).value
        val ok = repo.createGoal(GoalLevel.STAGE, parentId, draft("阶段"))
        assertTrue(ok is GoalResult.Ok)
    }

    @Test
    fun `行动不能直接挂在长期目标下`() = runTest {
        val dao = FakeGoalDao()
        val repo = repo(dao)
        val longId = (repo.createGoal(GoalLevel.LONG_TERM, null, draft()) as GoalResult.Ok).value

        val result = repo.createGoal(GoalLevel.ACTION, longId, draft("行动"))
        assertEquals(GoalRules.ERROR_PARENT_LEVEL_MISMATCH, (result as GoalResult.Error).message)
    }

    @Test
    fun `三层结构可以完整创建`() = runTest {
        val dao = FakeGoalDao()
        val repo = repo(dao)
        clock = 1000L
        val longId = (repo.createGoal(GoalLevel.LONG_TERM, null, draft("长期")) as GoalResult.Ok).value
        clock = 2000L
        val stageId = (repo.createGoal(GoalLevel.STAGE, longId, draft("阶段")) as GoalResult.Ok).value
        clock = 3000L
        val actionId = (repo.createGoal(GoalLevel.ACTION, stageId, draft("行动")) as GoalResult.Ok).value

        assertEquals(3, dao.loadAllGoals().size)
        assertEquals(stageId, dao.findGoal(actionId)!!.parentId)
        assertEquals(longId, dao.findGoal(stageId)!!.parentId)
    }

    @Test
    fun `空标题被拒绝且不写库`() = runTest {
        val dao = FakeGoalDao()
        val result = repo(dao).createGoal(GoalLevel.LONG_TERM, null, draft("   "))
        assertEquals(GoalRules.ERROR_TITLE_REQUIRED, (result as GoalResult.Error).message)
        assertEquals(0, dao.loadAllGoals().size)
    }

    @Test
    fun `不存在的父目标被拒绝`() = runTest {
        val dao = FakeGoalDao()
        val result = repo(dao).createGoal(GoalLevel.STAGE, 999L, draft())
        assertEquals(GoalRules.ERROR_PARENT_MISSING, (result as GoalResult.Error).message)
    }

    @Test
    fun `删除影响范围会统计子目标与进展`() = runTest {
        val dao = FakeGoalDao()
        val repo = repo(dao)
        val longId = (repo.createGoal(GoalLevel.LONG_TERM, null, draft("长期")) as GoalResult.Ok).value
        val stageId = (repo.createGoal(GoalLevel.STAGE, longId, draft("阶段")) as GoalResult.Ok).value
        val a1 = (repo.createGoal(GoalLevel.ACTION, stageId, draft("行动1")) as GoalResult.Ok).value
        repo.createGoal(GoalLevel.ACTION, stageId, draft("行动2"))

        repo.addCheckin(longId, "进展1", null, null)
        repo.addCheckin(a1, "进展2", 3.0, "次")

        val impact = repo.assessDeleteImpact(longId)
        assertEquals(4, impact.goalCount)      // 1 长期 + 1 阶段 + 2 行动
        assertEquals(1, impact.stageCount)
        assertEquals(2, impact.actionCount)
        assertEquals(2, impact.checkinCount)
        assertEquals(3, impact.descendantCount)
    }

    @Test
    fun `删除整棵树会同时删掉子目标与进展`() = runTest {
        val dao = FakeGoalDao()
        val repo = repo(dao)
        val longId = (repo.createGoal(GoalLevel.LONG_TERM, null, draft()) as GoalResult.Ok).value
        val stageId = (repo.createGoal(GoalLevel.STAGE, longId, draft()) as GoalResult.Ok).value
        repo.addCheckin(stageId, "进展", null, null)

        val result = repo.deleteGoalTree(longId)
        assertTrue(result is GoalResult.Ok)
        assertEquals(0, dao.loadAllGoals().size)
        assertEquals(0, dao.countCheckinsForGoals(listOf(longId, stageId)))
    }

    @Test
    fun `记录进展会把未开始的目标置为进行中`() = runTest {
        val dao = FakeGoalDao()
        val repo = repo(dao)
        val id = (repo.createGoal(GoalLevel.LONG_TERM, null, draft()) as GoalResult.Ok).value

        repo.addCheckin(id, "第一次推进", null, null)

        assertEquals(GoalStatus.IN_PROGRESS, dao.findGoal(id)!!.status)
    }

    @Test
    fun `快照按树组织并统计`() = runTest {
        val dao = FakeGoalDao()
        val repo = repo(dao)
        val longId = (repo.createGoal(GoalLevel.LONG_TERM, null, draft("长期")) as GoalResult.Ok).value
        val stageId = (repo.createGoal(GoalLevel.STAGE, longId, draft("阶段")) as GoalResult.Ok).value
        repo.createGoal(GoalLevel.ACTION, stageId, draft("行动"))

        val snapshot = repo.observeSnapshot().first()
        assertEquals(1, snapshot.roots.size)
        assertEquals(1, snapshot.roots[0].children.size)
        assertEquals(1, snapshot.roots[0].children[0].children.size)
        assertEquals(3, snapshot.totalGoals)
        assertEquals(1, snapshot.longTermCount)
    }

    @Test
    fun `编辑目标不会改变层级与父级`() = runTest {
        val dao = FakeGoalDao()
        val repo = repo(dao)
        val longId = (repo.createGoal(GoalLevel.LONG_TERM, null, draft("旧标题")) as GoalResult.Ok).value

        clock += 5000
        val updated = repo.updateGoal(longId, draft("新标题", GoalStatus.IN_PROGRESS))
        assertTrue(updated is GoalResult.Ok)

        val saved = dao.findGoal(longId)!!
        assertEquals("新标题", saved.title)
        assertEquals(GoalStatus.IN_PROGRESS, saved.status)
        assertEquals(GoalLevel.LONG_TERM, saved.level)
        assertEquals(null, saved.parentId)
        assertTrue(saved.updatedAt > saved.createdAt)
    }

    @Test
    fun `进展说明为空被拒绝`() = runTest {
        val dao = FakeGoalDao()
        val repo = repo(dao)
        val id = (repo.createGoal(GoalLevel.LONG_TERM, null, draft()) as GoalResult.Ok).value

        val result = repo.addCheckin(id, "   ", null, null)
        assertEquals(GoalRules.ERROR_NOTE_REQUIRED, (result as GoalResult.Error).message)
    }

    @Test
    fun `给不存在的目标记进展会被拒绝`() = runTest {
        val dao = FakeGoalDao()
        val result = repo(dao).addCheckin(12345L, "进展", null, null)
        assertNotNull(result as GoalResult.Error)
    }
}
