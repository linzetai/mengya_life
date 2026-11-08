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
import com.mengyalife.app.data.local.growth.GrowthMemoryDao
import com.mengyalife.app.data.local.insight.InsightReportDao
import com.mengyalife.app.data.local.insight.InsightReportEntity
import com.mengyalife.app.data.local.weekly.WeeklyReviewDao
import com.mengyalife.app.data.local.weekly.WeeklyReviewEntity
import com.mengyalife.app.data.local.weekly.WeeklyAiDao
import com.mengyalife.app.data.local.weekly.WeeklyAiEntity
import com.mengyalife.app.data.local.growth.GrowthMemoryEntity
import com.mengyalife.app.data.local.growth.GrowthSignalDao
import com.mengyalife.app.data.local.growth.GrowthSignalEntity
import com.mengyalife.app.data.local.proposal.GoalProposalDao
import com.mengyalife.app.data.local.proposal.GoalProposalEntity
import com.mengyalife.app.data.local.proposal.RecordProposalDao
import com.mengyalife.app.data.local.proposal.RecordProposalEntity

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
 * - **v7**（Gate 3C-2）：新增 `growth_memories`（跨日记成长记忆）
 * - **v8**（Gate 3D-1）：新增 `insight_reports`（规则事实 + AI 洞察缓存）
 * - **v9**（Gate 3E-1）：新增 `weekly_reviews`（本地每周回顾历史）
 * - **v10**（Gate 3E-2）：周回顾加入 dataVersion / AI 授权版本
 * - **v11**（Gate 3E-2.1）：AI 请求绑定配置哈希
 * - **v13**（0.16）：新增 `record_proposals`（对话式记录候选）；不动业务表
 * - **v14**（0.16A）：goal_checkins 增加进展语义/原话/归一化字段；旧行按 snapshot 处理
 * - **v15**（0.17）：record_proposals 增加日记草稿字段（不新增日记表）
 * - **v16**（0.18）：record_proposals 增加目标变更草稿与已创建目标 id（不新增目标表）
 * - **v17**（0.20）：新增 small_plans / plan_events（对话确认的小计划与状态历史）
 *
 * 迁移策略：
 * 1. 每次改表都必须 version + 1 并提供 `Migration`；
 * 2. **不使用** `fallbackToDestructiveMigration()` —— 缺迁移时抛异常，绝不清库；
 * 3. schema 导出到 `app/schemas/`，历史版本 JSON 一并保留，用于 diff；
 * 4. 迁移必须在真机升级安装上验证（本轮已做 v1→v2 实测，见报告）。
 */
/** 数据库结构版本。导出文件里的 `databaseVersion` 与它保持一致。 */
const val DB_SCHEMA_VERSION = 22

@Database(
    entities = [
        JournalEntryEntity::class,
        GoalEntity::class,
        GoalCheckinEntity::class,
        AdvisorMessageEntity::class,
        GoalProposalEntity::class,
        RecordProposalEntity::class,
        GrowthSignalEntity::class,
        GrowthMemoryEntity::class,
        InsightReportEntity::class,
        WeeklyReviewEntity::class,
        WeeklyAiEntity::class,
        AppMetaEntity::class,
        com.mengyalife.app.data.local.plan.SmallPlanEntity::class,
        com.mengyalife.app.data.local.plan.PlanEventEntity::class,
        com.mengyalife.app.data.local.plan.PlanProgressEntity::class,
        com.mengyalife.app.data.local.care.CareOpportunityEntity::class,
        com.mengyalife.app.data.local.care.CareDecisionEntity::class,
        com.mengyalife.app.data.local.care.CareFeedbackEntity::class,
        com.mengyalife.app.data.local.care.CarePreferenceEntity::class,
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

    abstract fun smallPlanDao(): com.mengyalife.app.data.local.plan.SmallPlanDao

    abstract fun careDao(): com.mengyalife.app.data.local.care.CareDao

    abstract fun carePreferenceDao(): com.mengyalife.app.data.local.care.CarePreferenceDao

    abstract fun recordProposalDao(): RecordProposalDao

    abstract fun growthSignalDao(): GrowthSignalDao

    abstract fun growthMemoryDao(): GrowthMemoryDao

    abstract fun insightReportDao(): InsightReportDao

    abstract fun weeklyReviewDao(): WeeklyReviewDao

    abstract fun weeklyAiDao(): WeeklyAiDao

    abstract fun appMetaDao(): AppMetaDao

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


        /**
         * v6 → v7：成长记忆表；不改动日记/线索/业务表。
         */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `growth_memories` (
                        `id` TEXT PRIMARY KEY NOT NULL,
                        `theme` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `is_inference` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        `signal_ids_json` TEXT NOT NULL,
                        `source_diary_ids_json` TEXT NOT NULL,
                        `user_corrected` INTEGER NOT NULL,
                        `version` INTEGER NOT NULL,
                        `confidence` REAL,
                        `insufficient` INTEGER NOT NULL,
                        `recent_change` TEXT,
                        `fingerprint` TEXT NOT NULL,
                        `deleted_at` INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_growth_memories_theme` ON `growth_memories` (`theme`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_growth_memories_status` ON `growth_memories` (`status`)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_growth_memories_theme_fingerprint` " +
                        "ON `growth_memories` (`theme`, `fingerprint`)",
                )
            }
        }


        /** v7 → v8：洞察报告缓存；不动业务表。 */
        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `insight_reports` (
                        `id` TEXT PRIMARY KEY NOT NULL,
                        `data_version` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `facts_json` TEXT NOT NULL,
                        `insights_json` TEXT NOT NULL,
                        `period_start` INTEGER NOT NULL,
                        `period_end` INTEGER NOT NULL,
                        `generated_at` INTEGER NOT NULL,
                        `error` TEXT,
                        `model` TEXT
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_insight_reports_data_version` ON `insight_reports` (`data_version`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_insight_reports_generated_at` ON `insight_reports` (`generated_at`)")
            }
        }


        /** v8 → v9：本地每周回顾历史；不动业务表。 */
        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `weekly_reviews` (
                        `id` TEXT PRIMARY KEY NOT NULL,
                        `period_key` TEXT NOT NULL,
                        `period_start` INTEGER NOT NULL,
                        `period_end` INTEGER NOT NULL,
                        `generated_at` INTEGER NOT NULL,
                        `version` INTEGER NOT NULL,
                        `facts_json` TEXT NOT NULL,
                        `sections_json` TEXT NOT NULL,
                        `notified_at` INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_weekly_reviews_period_key` ON `weekly_reviews` (`period_key`)")
            }
        }


        /** v9 → v10：周回顾缓存版本字段。 */
        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `weekly_reviews` ADD COLUMN `data_version` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `weekly_reviews` ADD COLUMN `ai_consent_version` TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE TABLE IF NOT EXISTS `weekly_ai_reviews` (`period_key` TEXT PRIMARY KEY NOT NULL, `local_data_version` TEXT NOT NULL, `ai_consent_version` TEXT NOT NULL, `status` TEXT NOT NULL, `generated_at` INTEGER NOT NULL, `summary` TEXT NOT NULL, `highlights_json` TEXT NOT NULL, `suggestion` TEXT NOT NULL, `fact_ids_json` TEXT NOT NULL, `categories` TEXT NOT NULL, `error` TEXT, `model` TEXT)")
            }
        }


        /** v10 → v11：AI 结果绑定模型配置。 */
        val MIGRATION_10_11: Migration = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `weekly_ai_reviews` ADD COLUMN `config_hash` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v11 → v12：恢复单行元数据；不触碰业务表。 */
        val MIGRATION_11_12: Migration = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_meta` (
                        `id` INTEGER PRIMARY KEY NOT NULL,
                        `restore_attempt_id` TEXT,
                        `restore_backup_id` TEXT,
                        `restore_generation` INTEGER NOT NULL DEFAULT 0,
                        `restore_settings_json` TEXT,
                        `restore_completed_at` INTEGER,
                        `restore_expected_sha256` TEXT,
                        `restore_payload_sha256` TEXT,
                        `restore_converged_at` INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL("INSERT OR IGNORE INTO `app_meta` (`id`, `restore_generation`) VALUES (1, 0)")
            }
        }

        /** v13 → v14：正式进展保留类型与来源原话，并保存可选的归一化数值。 */
        val MIGRATION_13_14: Migration = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `goal_checkins` ADD COLUMN `amount_kind` TEXT NOT NULL DEFAULT 'snapshot'",
                )
                db.execSQL("ALTER TABLE `goal_checkins` ADD COLUMN `original_text` TEXT")
                db.execSQL("ALTER TABLE `goal_checkins` ADD COLUMN `normalized_value` REAL")
                db.execSQL("ALTER TABLE `goal_checkins` ADD COLUMN `normalized_unit` TEXT")
                // 旧候选表里 amount_kind 的写法可能是大写；统一成枚举名，避免读取时被丢弃。
                db.execSQL("UPDATE `record_proposals` SET `amount_kind` = UPPER(`amount_kind`)")
                // 0.16 已写入的顾问进展：从候选表回填真实语义与原话，避免历史增量被当成“累计总额”。
                db.execSQL(
                    """
                    UPDATE `goal_checkins`
                    SET `amount_kind` = COALESCE(
                        (SELECT LOWER(rp.`amount_kind`) FROM `record_proposals` rp
                         WHERE rp.`applied_checkin_id` = `goal_checkins`.`id` LIMIT 1),
                        `amount_kind`
                    ),
                    `original_text` = COALESCE(
                        (SELECT rp.`original_text` FROM `record_proposals` rp
                         WHERE rp.`applied_checkin_id` = `goal_checkins`.`id` LIMIT 1),
                        `original_text`
                    )
                    WHERE EXISTS (
                        SELECT 1 FROM `record_proposals` rp
                        WHERE rp.`applied_checkin_id` = `goal_checkins`.`id`
                    )
                    """.trimIndent(),
                )
                // 没有候选可回溯的旧顾问记录：按“新增”处理更安全（避免被读成累计下降）；
                // 手动记录保持 snapshot，延续原有“累计曲线”含义。
                db.execSQL(
                    "UPDATE `goal_checkins` SET `amount_kind` = 'delta' " +
                        "WHERE `source` = 'advisor' AND `amount_kind` = 'snapshot'",
                )
            }
        }

        /** v14 → v15：日记候选复用同一张候选表；正式日记仍写 journal_entries。 */
        val MIGRATION_14_15: Migration = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `record_proposals` ADD COLUMN `diary_title` TEXT")
                db.execSQL("ALTER TABLE `record_proposals` ADD COLUMN `diary_body` TEXT")
                db.execSQL("ALTER TABLE `record_proposals` ADD COLUMN `diary_entry_id` INTEGER")
                db.execSQL("ALTER TABLE `record_proposals` ADD COLUMN `source_message_ids_json` TEXT")
            }
        }

        /** v15 → v16：目标变更复用候选表；正式目标仍写 goals。 */
        val MIGRATION_15_16: Migration = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `record_proposals` ADD COLUMN `goal_change_json` TEXT")
                db.execSQL("ALTER TABLE `record_proposals` ADD COLUMN `applied_goal_ids_json` TEXT")
            }
        }

        /** v16 → v17：对话确认的小计划；正式写入只经 SmallPlanRepository。 */
        val MIGRATION_16_17: Migration = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `small_plans` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                        "`time_scope` TEXT, `goal_id` INTEGER, `status` TEXT NOT NULL, " +
                        "`source_message_id` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_small_plans_status` ON `small_plans` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_small_plans_updated_at` ON `small_plans` (`updated_at`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plan_events` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `plan_id` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, `note` TEXT, `created_at` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_events_plan_id` ON `plan_events` (`plan_id`)")
                db.execSQL("ALTER TABLE `record_proposals` ADD COLUMN `plan_change_json` TEXT")
                db.execSQL("ALTER TABLE `record_proposals` ADD COLUMN `applied_plan_id` INTEGER")
            }
        }

        /** v17 → v18：计划执行记录；来源消息唯一，防重复提交。 */
        val MIGRATION_17_18: Migration = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plan_progress` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `plan_id` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, `recorded_at` INTEGER NOT NULL, `source_message_id` TEXT)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_progress_plan_id` ON `plan_progress` (`plan_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_plan_progress_source_message_id` ON `plan_progress` (`source_message_id`)")
                db.execSQL("ALTER TABLE `record_proposals` ADD COLUMN `plan_progress_json` TEXT")
            }
        }

        /** v18 → v19：Care Engine 机会、决策、反馈。 */
        val MIGRATION_18_19: Migration = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `care_opportunities` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, " +
                        "`source_id` INTEGER NOT NULL, `plan_id` INTEGER, `goal_id` INTEGER, `title` TEXT NOT NULL, " +
                        "`discovered_at` INTEGER NOT NULL, `window_start` INTEGER NOT NULL, `window_end` INTEGER NOT NULL, " +
                        "`expires_at` INTEGER NOT NULL, `sensitivity` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                        "`dedupe_key` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_care_opportunities_dedupe_key` ON `care_opportunities` (`dedupe_key`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_opportunities_status` ON `care_opportunities` (`status`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `care_decisions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `opportunity_id` INTEGER NOT NULL, " +
                        "`action` TEXT NOT NULL, `reason` TEXT NOT NULL, `source_refs` TEXT NOT NULL, " +
                        "`suggested_text` TEXT, `avoid_json` TEXT, `next_window_start` INTEGER, `next_window_end` INTEGER, " +
                        "`model_ok` INTEGER NOT NULL, `created_at` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_decisions_opportunity_id` ON `care_decisions` (`opportunity_id`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `care_feedback` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `opportunity_id` INTEGER NOT NULL, " +
                        "`kind` TEXT NOT NULL, `created_at` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_feedback_opportunity_id` ON `care_feedback` (`opportunity_id`)")
            }
        }

        /** v19 → v20：Care 决策关联顾问消息，通知与站内共享同一投递。 */
        val MIGRATION_19_20: Migration = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `care_decisions` ADD COLUMN `message_id` TEXT")
                db.execSQL("ALTER TABLE `care_decisions` ADD COLUMN `delivered_at` INTEGER")
            }
        }

        /** v20 → v21：0.24 关心偏好；SENDING 可被安全收敛为 UNKNOWN。 */
        val MIGRATION_20_21: Migration = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `care_preferences` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, " +
                        "`topic_key` TEXT, `topic_label` TEXT, `value` TEXT NOT NULL, `source` TEXT NOT NULL, " +
                        "`source_message_id` TEXT, `source_excerpt` TEXT, `window_note` TEXT, " +
                        "`effective_at` INTEGER NOT NULL, `expires_at` INTEGER, `revoked_at` INTEGER, " +
                        "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_preferences_kind` ON `care_preferences` (`kind`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_preferences_topic_key` ON `care_preferences` (`topic_key`)")
            }
        }

        /** v21 → v22：系统通知幂等标记。 */
        val MIGRATION_21_22: Migration = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `care_decisions` ADD COLUMN `notified_at` INTEGER")
            }
        }

        /** v12 → v13：对话式记录候选表；不改动目标/进展/对话等既有表。 */
        val MIGRATION_12_13: Migration = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `record_proposals` (
                        `id` TEXT PRIMARY KEY NOT NULL,
                        `message_id` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `goal_id` INTEGER,
                        `goal_title` TEXT,
                        `goal_candidates_json` TEXT,
                        `amount_kind` TEXT NOT NULL,
                        `metric_value` REAL,
                        `metric_unit` TEXT,
                        `note` TEXT NOT NULL,
                        `occurred_at` INTEGER,
                        `pending_fields_json` TEXT,
                        `intent` TEXT NOT NULL,
                        `original_text` TEXT NOT NULL,
                        `applied_checkin_id` INTEGER,
                        `applied_at` INTEGER,
                        `undone_at` INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_record_proposals_message_id` " +
                        "ON `record_proposals` (`message_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_record_proposals_status` " +
                        "ON `record_proposals` (`status`)",
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Fresh v12 installations do not execute MIGRATION_11_12.
                            db.execSQL("INSERT OR IGNORE INTO `app_meta` (`id`, `restore_generation`) VALUES (1, 0)")
                        }
                    })
                    .build()
                    .also { instance = it }
            }
    }
}
