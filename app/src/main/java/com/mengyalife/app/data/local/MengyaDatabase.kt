package com.mengyalife.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 本地数据库。**当前版本 v1**，只有 `journal_entries` 一张表。
 *
 * 迁移策略（本轮先把规则定死，避免以后临时决定）：
 * 1. 任何表结构变更都必须 **version + 1**，并提供对应的 `Migration(old, new)`；
 * 2. **不使用** `fallbackToDestructiveMigration()`：缺少迁移时 Room 会抛异常而不是清库，
 *    宁可让问题暴露，也不允许静默丢掉用户数据；
 * 3. 用 `exportSchema = true` 把每个版本的 schema 导出到 `app/schemas/`，
 *    升级时对比两份 JSON 写迁移（该目录随源码一起提交）；
 * 4. 迁移必须在 `androidTest` 或真机上验证过再合入（Gate 2B 起补迁移测试）。
 *
 * 数据库文件名固定为 `mengya-life.db`，不要随意改名（改名等价于「用户数据消失」）。
 */
@Database(
    entities = [JournalEntryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MengyaDatabase : RoomDatabase() {

    abstract fun journalEntryDao(): JournalEntryDao

    companion object {
        private const val DATABASE_NAME = "mengya-life.db"

        @Volatile
        private var instance: MengyaDatabase? = null

        fun get(context: Context): MengyaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MengyaDatabase::class.java,
                    DATABASE_NAME,
                )
                    .build()
                    .also { instance = it }
            }
    }
}
