package com.mengyalife.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mengyalife.app.BuildConfig
import com.mengyalife.app.data.local.advisor.AdvisorMessageDao
import com.mengyalife.app.data.local.advisor.AdvisorMessageEntity
import com.mengyalife.app.data.local.goal.GoalCheckinEntity
import com.mengyalife.app.data.local.goal.GoalDao
import com.mengyalife.app.data.local.goal.GoalEntity
import com.mengyalife.app.data.local.growth.GrowthSignalDao
import com.mengyalife.app.data.local.growth.GrowthSignalEntity
import com.mengyalife.app.data.local.proposal.GoalProposalDao
import com.mengyalife.app.data.local.proposal.GoalProposalEntity

/**
 * 本地数据库。
 *
 * 版本历史：
 * - **v1**：`journal_entries`（日记）
 * - **v2**（Gate 2B）：新增 `goals`、`goal_checkins`；**不改动** `journal_entries`
 * - **v3**（Gate 3A）：新增 `advisor_messages`；**不改动** v1/v2 的三张表
 * - **v4**（Gate 3B-1）：advisor_messages 增加 epoch/来源/草案错误列；
 *   新增 `goal_proposals`（草案）
 * - **v5**（Gate 3B-2）：advisor_messages 增加 proposal_mode；
 *   goal_proposals 增加应用/撤销/选择字段
 * - **v6**（Gate 3C-1）：journal_entries 增加分析状态列；
 *   新增 `growth_signals`（日记成长线索）
 *
 * 迁移策略：
 * 1. 每次改表都必须 version + 1 并提供 `Migration`；
 * 2. **不使用** `fallbackToDestructiveMigration()` —— 缺迁移时抛异常，绝不清库；
 * 3. schema 导出到 `app/schemas/`，历史版本 JSON 一并保留，用于 diff；
 * 4. 迁移必须在真机升级安装上验证（本轮已做 v1→v2 实测，见报告）。
 */
/** 数据库结构版本。导出文件里的 `databaseVersion` 与它保持一致。 */
const val DB_SCHEMA_VERSION = 6

@Database(
    entities = [
        JournalEntryEntity::class,
        GoalEntity::class,
        GoalCheckinEntity::class,
        AdvisorMessageEntity::class,
        GoalProposalEntity::class,
        GrowthSignalEntity::class,
    ],
    version = DB_SCHEMA_VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MengyaDatabase : RoomDatabase() {

    abstract fun journalEntryDao(): JournalEntryDao

    abstract fun goalDao(): GoalDao

    abstract fun advisorMessageDao(): AdvisorMessageDao

    abstract fun goalProposalDao(): GoalProposalDao

    abstract fun growthSignalDao(): GrowthSignalDao

    companion object {
        // 正式版与 QA 测试构建使用不同的物理库文件（见 app/build.gradle 的 buildConfigField）。
        private const val DATABASE_NAME = BuildConfig.DATABASE_NAME

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

        /**
         * v2 → v3：只新增 `advisor_messages` 表与索引，不触碰日记/目标/进展。
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `advisor_messages` (
                        `id` TEXT PRIMARY KEY NOT NULL,
                        `role` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `error_code` TEXT,
                        `error_message` TEXT,
                        `context_goal_id` INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_advisor_messages_created_at` " +
                        "ON `advisor_messages` (`created_at`)",
                )
            }
        }

        /**
         * v3 → v4：advisor_messages 增加 3 列 + 草案表；不动业务表。
         * 列的 DEFAULT 保证旧消息可读，但仍不会进入新的上下文（epoch=0）。
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `advisor_messages` ADD COLUMN `context_epoch` INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("ALTER TABLE `advisor_messages` ADD COLUMN `sources_json` TEXT")
                db.execSQL("ALTER TABLE `advisor_messages` ADD COLUMN `proposal_error` TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `goal_proposals` (
                        `id` TEXT PRIMARY KEY NOT NULL,
                        `message_id` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `schema_version` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `assumptions_json` TEXT NOT NULL,
                        `risks_json` TEXT NOT NULL,
                        `nodes_json` TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_goal_proposals_message_id` " +
                        "ON `goal_proposals` (`message_id`)",
                )
            }
        }


        /**
         * v4 → v5：草案应用/撤销状态 + 消息重试模式；不动业务表。
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `advisor_messages` ADD COLUMN `proposal_mode` INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("ALTER TABLE `goal_proposals` ADD COLUMN `selected_temp_ids_json` TEXT")
                db.execSQL("ALTER TABLE `goal_proposals` ADD COLUMN `applied_at` INTEGER")
                db.execSQL("ALTER TABLE `goal_proposals` ADD COLUMN `applied_goal_ids_json` TEXT")
                db.execSQL("ALTER TABLE `goal_proposals` ADD COLUMN `undone_at` INTEGER")
            }
        }


        /**
         * v5 → v6：日记分析状态 + 成长线索表；不动业务内容。
         */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `analysis_status` TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `analysis_hash` TEXT")
                db.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `analysis_error` TEXT")
                db.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `analysis_attempts` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `analyzed_at` INTEGER")
                db.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `analysis_last_attempt_at` INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `growth_signals` (
                        `id` TEXT PRIMARY KEY NOT NULL,
                        `source_type` TEXT NOT NULL,
                        `source_id` INTEGER NOT NULL,
                        `source_version` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `evidence` TEXT NOT NULL,
                        `is_inference` INTEGER NOT NULL,
                        `confidence` REAL,
                        `extracted_at` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `origin` TEXT NOT NULL,
                        `source_title` TEXT,
                        `content_hash` TEXT NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_growth_signals_source_type_source_id` ON `growth_signals` (`source_type`, `source_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_growth_signals_source_id_status` ON `growth_signals` (`source_id`, `status`)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_growth_signals_source_type_source_id_source_version_content_hash` " +
                        "ON `growth_signals` (`source_type`, `source_id`, `source_version`, `content_hash`)",
                )
            }
        }

        @Volatile
        private var instance: MengyaDatabase? = null

        /** QA 构建专用判断：破坏性测试只允许发生在它上面。 */
        val isTestBuild: Boolean get() = BuildConfig.QA_BUILD

        fun get(context: Context): MengyaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MengyaDatabase::class.java,
                    DATABASE_NAME,
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { instance = it }
            }
    }
}
