package com.mengyalife.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mengyalife.app.data.local.goal.GoalCheckinEntity
import com.mengyalife.app.data.local.goal.GoalDao
import com.mengyalife.app.data.local.goal.GoalEntity

/**
 * 本地数据库。
 *
 * 版本历史：
 * - **v1**：`journal_entries`（日记）
 * - **v2**（Gate 2B）：新增 `goals`、`goal_checkins`；**不改动** `journal_entries`
 *
 * 迁移策略：
 * 1. 每次改表都必须 version + 1 并提供 `Migration`；
 * 2. **不使用** `fallbackToDestructiveMigration()` —— 缺迁移时抛异常，绝不清库；
 * 3. schema 导出到 `app/schemas/`，历史版本 JSON 一并保留，用于 diff；
 * 4. 迁移必须在真机升级安装上验证（本轮已做 v1→v2 实测，见报告）。
 */
@Database(
    entities = [
        JournalEntryEntity::class,
        GoalEntity::class,
        GoalCheckinEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MengyaDatabase : RoomDatabase() {

    abstract fun journalEntryDao(): JournalEntryDao

    abstract fun goalDao(): GoalDao

    companion object {
        private const val DATABASE_NAME = "mengya-life.db"

        /**
         * v1 → v2：只**新增**两张表与索引，不触碰 `journal_entries`。
         * 因此升级后日记的 id / 正文 / 时间戳必须逐字节不变。
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `goals` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT,
                        `level` TEXT NOT NULL,
                        `parent_id` INTEGER,
                        `domain` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `start_date` INTEGER,
                        `due_date` INTEGER,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        FOREIGN KEY(`parent_id`) REFERENCES `goals`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_goals_parent_id` ON `goals` (`parent_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_goals_level` ON `goals` (`level`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_goals_status` ON `goals` (`status`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `goal_checkins` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `goal_id` INTEGER NOT NULL,
                        `recorded_at` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `metric_value` REAL,
                        `metric_unit` TEXT,
                        `source` TEXT NOT NULL,
                        FOREIGN KEY(`goal_id`) REFERENCES `goals`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_goal_checkins_goal_id` ON `goal_checkins` (`goal_id`)",
                )
            }
        }

        @Volatile
        private var instance: MengyaDatabase? = null

        fun get(context: Context): MengyaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MengyaDatabase::class.java,
                    DATABASE_NAME,
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
