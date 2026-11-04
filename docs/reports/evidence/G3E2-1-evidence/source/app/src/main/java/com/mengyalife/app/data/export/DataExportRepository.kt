package com.mengyalife.app.data.export

import com.mengyalife.app.data.local.JournalEntryDao
import com.mengyalife.app.data.local.goal.GoalCheckinEntity
import com.mengyalife.app.data.local.goal.GoalDao
import com.mengyalife.app.data.local.goal.GoalEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 导出仓库：在**一个数据库事务**里读出日记、目标、进展三张表。
 *
 * 为什么不是直接复制 `.db` 文件：
 * - 数据库使用 WAL 模式，最新数据可能还在 `-wal` 里，
 *   单拷 `.db` 会漏数据甚至拿到半写状态；
 * - 直接读文件需要额外处理 `-wal` / `-shm` 与 checkpoint，风险高；
 * - 这里改用 SQL 查询 + Room 事务，拿到的是**一致快照**：
 *   三条查询看到的是同一个数据库版本，不会出现「目标有了、进展没了」的撕裂。
 *
 * 事务执行器由调用方注入（生产用 `RoomDatabase.withTransaction`，
 * 单元测试用直通实现），因此本类可以在 JVM 上被完整测试。
 */
class DataExportRepository(
    private val journalDao: JournalEntryDao,
    private val goalDao: GoalDao,
    private val appPackage: String,
    private val appVersionName: String,
    private val appVersionCode: Long,
    private val databaseVersion: Int,
    private val now: () -> Long = System::currentTimeMillis,
    private val transaction: suspend (suspend () -> ExportJson.Bundle) -> ExportJson.Bundle =
        { block -> block() },
) {

    /**
     * 读取一份一致性快照。
     * @return 导出所需的全部数据（此时还没有序列化）。
     */
    suspend fun buildBundle(): ExportJson.Bundle = withContext(Dispatchers.IO) {
        transaction {
            ExportJson.Bundle(
                exportedAt = now(),
                appPackage = appPackage,
                appVersionName = appVersionName,
                appVersionCode = appVersionCode,
                databaseVersion = databaseVersion,
                journalEntries = journalDao.loadAll().sortedBy { it.id },
                goals = goalDao.loadAllGoals().sortedBy { it.id },
                goalCheckins = goalDao.loadAllCheckins().sortedBy { it.id },
            )
        }
    }

    /** 序列化 + 自校验（结构自检，不依赖设备）。 */
    fun encode(bundle: ExportJson.Bundle): String = ExportJson.encode(bundle)

    fun verify(text: String, expected: ExportJson.Bundle): ExportJson.Verification =
        ExportJson.verify(text, expected)

    /** 仅供单元测试/统计：检查快照里的进展是否都能找到目标。 */
    fun orphanCheckins(bundle: ExportJson.Bundle): List<GoalCheckinEntity> {
        val ids = bundle.goals.map(GoalEntity::id).toSet()
        return bundle.goalCheckins.filterNot { it.goalId in ids }
    }
}
