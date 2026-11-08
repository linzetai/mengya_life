package com.mengyalife.app.data

import com.mengyalife.app.data.export.DataExportRepository
import com.mengyalife.app.data.export.ExportJson
import com.mengyalife.app.data.local.JournalEntryDao
import com.mengyalife.app.data.local.JournalEntryEntity
import com.mengyalife.app.data.local.goal.GoalCheckinEntity
import com.mengyalife.app.data.local.goal.GoalCheckinStat
import com.mengyalife.app.data.local.goal.GoalDao
import com.mengyalife.app.data.local.goal.GoalEntity
import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** 导出测试用内存 DAO（只实现导出/校验需要的读方法）。 */
private class ExportGoalDao(
    private val goals: List<GoalEntity>,
    private val checkins: List<GoalCheckinEntity>,
) : GoalDao {
    override fun observeGoals(): Flow<List<GoalEntity>> = flowOf(goals)
    override fun observeCheckinStats(): Flow<List<GoalCheckinStat>> = flowOf(emptyList())
    override fun observeCheckins(goalId: Long): Flow<List<GoalCheckinEntity>> =
        flowOf(checkins.filter { it.goalId == goalId })
    override fun observeRecentCheckins(limit: Int): Flow<List<GoalCheckinEntity>> =
        flowOf(checkins.take(limit))
    override fun observeAllCheckins(): Flow<List<GoalCheckinEntity>> = flowOf(checkins)
    override suspend fun findGoal(id: Long): GoalEntity? = goals.firstOrNull { it.id == id }
    override suspend fun loadAllGoals(): List<GoalEntity> = goals
    override suspend fun loadAllCheckins(): List<GoalCheckinEntity> = checkins
    override suspend fun insertGoal(goal: GoalEntity): Long = 0
    override suspend fun updateGoal(goal: GoalEntity): Int = 0
    override suspend fun deleteGoalsByIds(ids: List<Long>): Int = 0
    override suspend fun insertCheckin(checkin: GoalCheckinEntity): Long = 0
    override suspend fun deleteCheckinsForGoals(goalIds: List<Long>): Int = 0
    override suspend fun countCheckinsForGoals(goalIds: List<Long>): Int = 0
    override suspend fun loadCheckinsForGoals(goalIds: List<Long>): List<GoalCheckinEntity> =
        checkins.filter { it.goalId in goalIds }
    override suspend fun findCheckin(id: Long): GoalCheckinEntity? = checkins.firstOrNull { it.id == id }
    override suspend fun updateCheckin(
        id: Long,
        note: String,
        metricValue: Double?,
        metricUnit: String?,
        amountKind: String,
        normalizedValue: Double?,
        normalizedUnit: String?,
    ): Int = 0
    override suspend fun deleteCheckin(id: Long): Int = 0
}

private class ExportJournalDao(private val entries: List<JournalEntryEntity>) : JournalEntryDao {
    override fun observeAll(): Flow<List<JournalEntryEntity>> = flowOf(entries)
    override suspend fun findById(id: Long): JournalEntryEntity? = entries.firstOrNull { it.id == id }
    override suspend fun count(): Int = entries.size
    override suspend fun markInterruptedAnalyses(): Int = 0
    override suspend fun loadAll(): List<JournalEntryEntity> = entries
    override suspend fun insert(entry: JournalEntryEntity): Long = 0
    override suspend fun update(entry: JournalEntryEntity): Int = 0
    override suspend fun deleteById(id: Long): Int = 0
    override suspend fun markAnalyzing(id: Long, expectedUpdatedAt: Long, hash: String, now: Long): Int = 0
    override suspend fun markCompleted(id: Long, hash: String, now: Long): Int = 0
    override suspend fun markFailed(id: Long, hash: String, error: String, now: Long): Int = 0
}

class ExportTest {

    private val t0 = Instant.parse("2026-11-01T00:00:00Z").toEpochMilli()

    private val journals = listOf(
        JournalEntryEntity(1, "第一天", "今天开始记录。", t0, t0),
        JournalEntryEntity(2, null, "第二条，没有标题。", t0 + 1000, t0 + 2000),
    )

    private val goals = listOf(
        GoalEntity(
            id = 10, title = "长期：健康", description = "十年尺度",
            level = GoalLevel.LONG_TERM, parentId = null,
            domain = GoalDomain.HEALTH, status = GoalStatus.IN_PROGRESS,
            startDate = null, dueDate = null, createdAt = t0, updatedAt = t0,
        ),
        GoalEntity(
            id = 11, title = "阶段：每周三次", description = null,
            level = GoalLevel.STAGE, parentId = 10,
            domain = GoalDomain.HEALTH, status = GoalStatus.IN_PROGRESS,
            startDate = null, dueDate = null, createdAt = t0 + 10, updatedAt = t0 + 10,
        ),
        GoalEntity(
            id = 12, title = "行动：晨跑", description = null,
            level = GoalLevel.ACTION, parentId = 11,
            domain = GoalDomain.HEALTH, status = GoalStatus.NOT_STARTED,
            startDate = null, dueDate = null, createdAt = t0 + 20, updatedAt = t0 + 20,
        ),
    )

    private val checkins = listOf(
        GoalCheckinEntity(
            id = 100, goalId = 11, recordedAt = t0 + 100,
            note = "跑了 3 次", metricValue = 3.0, metricUnit = "次/周",
            source = "manual",
        ),
        GoalCheckinEntity(
            id = 101, goalId = 10, recordedAt = t0 + 200,
            note = "状态不错", metricValue = null, metricUnit = null,
            source = "manual",
        ),
    )

    private fun bundle() = ExportJson.Bundle(
        exportedAt = t0 + 500,
        appPackage = "com.mengyalife.app",
        appVersionName = "0.14.1-g3e2-1",
        appVersionCode = 15,
        databaseVersion = 11,
        journalEntries = journals,
        goals = goals,
        goalCheckins = checkins,
    )

    private fun repository(
        goals: List<GoalEntity> = this.goals,
        checkins: List<GoalCheckinEntity> = this.checkins,
        journals: List<JournalEntryEntity> = this.journals,
    ) = DataExportRepository(
        journalDao = ExportJournalDao(journals),
        goalDao = ExportGoalDao(goals, checkins),
        appPackage = "com.mengyalife.app",
        appVersionName = "0.14.1-g3e2-1",
        appVersionCode = 15,
        databaseVersion = 11,
        now = { t0 + 500 },
    )

    @Test
    fun `导出内容完整且可被同一校验器解析`() {
        val text = ExportJson.encode(bundle())
        val verification = ExportJson.verify(text, bundle())

        assertTrue(verification.problems.toString(), verification.ok)
        assertEquals(ExportJson.Counts(journalEntries = 2, goals = 3, goalCheckins = 2), verification.counts)
    }

    @Test
    fun `导出文件包含 schemaVersion 与版本信息`() {
        val text = ExportJson.encode(bundle())
        assertTrue(text.contains("\"schemaVersion\": 1"))
        assertTrue(text.contains("\"versionName\": \"0.14.1-g3e2-1\""))
        assertTrue(text.contains("\"versionCode\": 15"))
        assertTrue(text.contains("\"schemaVersion\": 11")) // database.schemaVersion
        assertTrue(text.contains("\"format\": \"mengyalife-backup\""))
    }

    @Test
    fun `父子关系与进展关联逐项保留`() {
        val text = ExportJson.encode(bundle())
        assertTrue(text.contains("\"parentId\": null"))
        assertTrue(text.contains("\"parentId\": 10"))
        assertTrue(text.contains("\"parentId\": 11"))
        assertTrue(text.contains("\"goalId\": 11"))
        assertTrue(text.contains("\"goalId\": 10"))
        assertTrue(text.contains("\"recordedAt\""))
    }

    @Test
    fun `导出不包含任何密钥字段`() {
        val text = ExportJson.encode(bundle()).lowercase()
        listOf("apikey", "api_key", "secret", "token", "password").forEach {
            assertFalse("导出文件不应包含 $it", text.contains(it))
        }
    }

    @Test
    fun `修改计数会被校验器发现`() {
        val text = ExportJson.encode(bundle()).replace("\"goals\": 3", "\"goals\": 4")
        val verification = ExportJson.verify(text)
        assertFalse(verification.ok)
        assertTrue(verification.problems.any { it.contains("counts.goals") })
    }

    @Test
    fun `孤立的进展记录会被校验器发现`() {
        val broken = bundle().copy(
            goalCheckins = checkins + GoalCheckinEntity(
                id = 999, goalId = 12345, recordedAt = t0,
                note = "孤立", metricValue = 1.0, metricUnit = "次", source = "manual",
            ),
        )
        val verification = ExportJson.verify(ExportJson.encode(broken))
        assertFalse(verification.ok)
        assertTrue(verification.problems.any { it.contains("关联的目标") })
    }

    @Test
    fun `层级与父子关系不一致会被校验器发现`() {
        val brokenGoals = goals.map {
            if (it.id == 11L) it.copy(parentId = null) else it // 阶段目标没有父
        }
        val broken = bundle().copy(goals = brokenGoals)
        val verification = ExportJson.verify(ExportJson.encode(broken))
        assertFalse(verification.ok)
        assertTrue(verification.problems.any { it.contains("没有父目标") })
    }

    @Test
    fun `历史脏数据里的 NaN 不会写出非法 JSON`() {
        val dirty = checkins + GoalCheckinEntity(
            id = 102, goalId = 11, recordedAt = t0 + 300,
            note = "旧版本脏数据", metricValue = Double.NaN,
            metricUnit = "次", source = "manual",
        )
        val text = ExportJson.encode(bundle().copy(goalCheckins = dirty))
        val verification = ExportJson.verify(text)
        assertTrue(verification.problems.toString(), verification.ok)
        // 非有限数被写成 null，而不是 NaN/Infinity 字面量
        assertFalse(text.contains("NaN"))
        assertFalse(text.contains("Infinity"))
    }

    @Test
    fun `仓库在事务里读取三张表并生成快照`() = runTest {
        var transactions = 0
        val repo = DataExportRepository(
            journalDao = ExportJournalDao(journals),
            goalDao = ExportGoalDao(goals, checkins),
            appPackage = "com.mengyalife.app",
            appVersionName = "0.14.1-g3e2-1",
            appVersionCode = 15,
            databaseVersion = 11,
            now = { t0 + 500 },
            transaction = { block ->
                transactions++
                block()
            },
        )

        val snapshot = repo.buildBundle()
        assertEquals(1, transactions)
        assertEquals(2, snapshot.counts.journalEntries)
        assertEquals(3, snapshot.counts.goals)
        assertEquals(2, snapshot.counts.goalCheckins)
        assertTrue(repo.orphanCheckins(snapshot).isEmpty())

        val verification = repo.verify(repo.encode(snapshot), snapshot)
        assertTrue(verification.problems.toString(), verification.ok)
    }

    @Test
    fun `没有数据时也能导出一份合法的空备份`() = runTest {
        val repo = repository(goals = emptyList(), checkins = emptyList(), journals = emptyList())
        val snapshot = repo.buildBundle()
        val verification = repo.verify(repo.encode(snapshot), snapshot)
        assertTrue(verification.problems.toString(), verification.ok)
        assertEquals(ExportJson.Counts(0, 0, 0), verification.counts)
    }

    @Test
    fun `建议文件名包含导出时间且以 json 结尾`() {
        val name = ExportJson.suggestedFileName(
            Instant.parse("2026-11-03T03:25:00Z").toEpochMilli(),
            ZoneId.of("Asia/Shanghai"),
        )
        assertEquals("mengya-life-export-20261103-112500.json", name)
        assertNotNull(name)
    }

    @Test
    fun `非 JSON 文本会被拒绝而不是崩溃`() {
        val verification = ExportJson.verify("not a json")
        assertFalse(verification.ok)
        assertTrue(verification.problems.first().contains("不是合法的 JSON"))
    }
}
