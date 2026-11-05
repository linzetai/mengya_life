package com.mengyalife.app.data.qa

import androidx.room.withTransaction
import com.mengyalife.app.data.local.JournalEntryEntity
import com.mengyalife.app.data.local.MengyaDatabase
import com.mengyalife.app.data.local.advisor.AdvisorMessageEntity
import com.mengyalife.app.data.local.goal.GoalCheckinEntity
import com.mengyalife.app.data.local.goal.GoalEntity
import com.mengyalife.app.data.local.growth.GrowthMemoryEntity
import com.mengyalife.app.data.local.growth.GrowthSignalEntity
import com.mengyalife.app.data.local.insight.InsightReportEntity
import com.mengyalife.app.data.local.proposal.GoalProposalEntity
import com.mengyalife.app.data.local.weekly.WeeklyAiEntity
import com.mengyalife.app.data.local.weekly.WeeklyReviewEntity
import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalStatus

/**
 * G4A-1.1 QA-only 全表夹具。
 *
 * 不读取任何真实用户数据；只在 QA 构建、清空 QA 数据后由测试入口调用。
 * 覆盖 10 表非空、120 列、可空字段、Unicode/换行/引号、JSON TEXT、
 * 三级目标/打卡外键、AI 对话/草案、用户纠正/软删/STALE、旧/新 factIds。
 */
object QaBackupFixtureSeeder {
    private const val T = 1_793_900_000_000L
    private const val UNICODE = "标题 \"引号\" / 换行\n第二行 😀"
    private const val BODY = "正文 line1\nline2 \"quoted\" \\\\ slash \t tab 中文"

    suspend fun seed(database: MengyaDatabase) {
        database.withTransaction {
            // QA 夹具必须可重复；只允许在 QA 包调用（入口已做 BuildConfig 检查）。
            val db = database.openHelper.writableDatabase
            listOf(
                "weekly_ai_reviews", "weekly_reviews", "insight_reports", "growth_memories",
                "growth_signals", "goal_proposals", "advisor_messages", "goal_checkins",
                "goals", "journal_entries",
            ).forEach { db.execSQL("DELETE FROM `$it`") }

            val journalDao = database.journalEntryDao()
            val goalDao = database.goalDao()
            val journalId = journalDao.insert(
                JournalEntryEntity(
                    title = UNICODE, body = BODY, createdAt = T, updatedAt = T + 10,
                    analysisStatus = "COMPLETED", analysisHash = "hash-qa-1", analysisError = null,
                    analysisAttempts = 2, analyzedAt = T + 11, analysisLastAttemptAt = T + 12,
                ),
            )
            val failedJournalId = journalDao.insert(
                JournalEntryEntity(
                    title = null, body = "失败分析的日记\n带 \\ \" 特殊字符", createdAt = T + 20, updatedAt = T + 21,
                    analysisStatus = "FAILED", analysisHash = null, analysisError = "AI 超时\n请重试",
                    analysisAttempts = 1, analyzedAt = null, analysisLastAttemptAt = T + 22,
                ),
            )

            val longGoalId = goalDao.insertGoal(
                GoalEntity(
                    title = "长期健康 $UNICODE", description = "根目标\n多行", level = GoalLevel.LONG_TERM,
                    parentId = null, domain = GoalDomain.HEALTH, status = GoalStatus.IN_PROGRESS,
                    startDate = T, dueDate = T + 30L * 86400_000, createdAt = T, updatedAt = T + 1,
                ),
            )
            val stageGoalId = goalDao.insertGoal(
                GoalEntity(
                    title = "阶段：每周运动", description = null, level = GoalLevel.STAGE,
                    parentId = longGoalId, domain = GoalDomain.HEALTH, status = GoalStatus.IN_PROGRESS,
                    startDate = null, dueDate = T + 14L * 86400_000, createdAt = T + 2, updatedAt = T + 3,
                ),
            )
            val actionGoalId = goalDao.insertGoal(
                GoalEntity(
                    title = "行动：三次 30 分钟", description = "带 \"引号\" 的描述", level = GoalLevel.ACTION,
                    parentId = stageGoalId, domain = GoalDomain.HEALTH, status = GoalStatus.NOT_STARTED,
                    startDate = T + 4, dueDate = null, createdAt = T + 4, updatedAt = T + 5,
                ),
            )
            goalDao.insertCheckin(
                GoalCheckinEntity(
                    goalId = actionGoalId, recordedAt = T + 100, note = "第一次打卡\n状态不错",
                    metricValue = 3.5, metricUnit = "次/周", source = "MANUAL",
                ),
            )
            goalDao.insertCheckin(
                GoalCheckinEntity(
                    goalId = stageGoalId, recordedAt = T + 200, note = "无数值打卡 \"quoted\"",
                    metricValue = null, metricUnit = null, source = "MANUAL",
                ),
            )

            val messageDao = database.advisorMessageDao()
            messageDao.insert(
                AdvisorMessageEntity(
                    id = "qa-msg-user-1", role = "user", content = "我想改进健康\n\"从睡眠开始\"",
                    createdAt = T + 300, status = "SENT", errorCode = null, errorMessage = null,
                    contextGoalId = longGoalId, contextEpoch = 7, sourcesJson = null,
                    proposalError = null, proposalMode = false,
                ),
            )
            messageDao.insert(
                AdvisorMessageEntity(
                    id = "qa-msg-assistant-1", role = "assistant",
                    content = "建议先记录一周睡眠 😀\n再设阶段目标", createdAt = T + 301, status = "SENT",
                    errorCode = null, errorMessage = null, contextGoalId = longGoalId, contextEpoch = 7,
                    sourcesJson = """[{"type":"GOAL","id":"$longGoalId","title":"长期健康"}]""",
                    proposalError = null, proposalMode = true,
                ),
            )
            messageDao.insert(
                AdvisorMessageEntity(
                    id = "qa-msg-failed-1", role = "assistant", content = "",
                    createdAt = T + 302, status = "FAILED", errorCode = "TIMEOUT",
                    errorMessage = "请求超时，请重试", contextGoalId = null, contextEpoch = 7,
                    sourcesJson = null, proposalError = "无法解析草案", proposalMode = true,
                ),
            )

            val proposalDao = database.goalProposalDao()
            proposalDao.insert(
                GoalProposalEntity(
                    id = "qa-proposal-draft", messageId = "qa-msg-assistant-1", createdAt = T + 303,
                    schemaVersion = 1, status = "DRAFT", summary = "睡眠与运动草案",
                    assumptionsJson = """["用户希望改善健康"]""",
                    risksJson = """[{"level":"LOW","text":"时间不足"}]""",
                    nodesJson = """[{"tempId":"n1","title":"阶段：睡眠","children":[{"tempId":"n2","title":"行动：23点前睡"}]}]""",
                    selectedTempIdsJson = null, appliedAt = null, appliedGoalIdsJson = null, undoneAt = null,
                ),
            )
            proposalDao.insert(
                GoalProposalEntity(
                    id = "qa-proposal-applied", messageId = "qa-msg-assistant-1", createdAt = T + 304,
                    schemaVersion = 1, status = "APPLIED", summary = "已应用草案",
                    assumptionsJson = "[]", risksJson = "[]",
                    nodesJson = """[{"tempId":"n3","title":"行动：拉伸"}]""",
                    selectedTempIdsJson = """["n3"]""", appliedAt = T + 305,
                    appliedGoalIdsJson = """[$actionGoalId]""", undoneAt = null,
                ),
            )

            val signalDao = database.growthSignalDao()
            signalDao.insertIgnore(
                GrowthSignalEntity(
                    id = "qa-signal-active", sourceType = "DIARY", sourceId = journalId,
                    sourceVersion = "v1", category = "HABIT", content = "开始记录睡眠\n有意识改变",
                    evidence = "日记提到睡眠", isInference = false, confidence = 0.85,
                    extractedAt = T + 400, status = "ACTIVE", origin = "AI", sourceTitle = UNICODE,
                    contentHash = "qa-signal-hash-active", updatedAt = T + 401,
                ),
            )
            signalDao.insertIgnore(
                GrowthSignalEntity(
                    id = "qa-signal-corrected", sourceType = "DIARY", sourceId = failedJournalId,
                    sourceVersion = "v1", category = "EMOTION", content = "用户纠正过的线索 \"quoted\"",
                    evidence = "用户纠正", isInference = true, confidence = null,
                    extractedAt = T + 410, status = "CORRECTED", origin = "USER", sourceTitle = null,
                    contentHash = "qa-signal-hash-corrected", updatedAt = T + 411,
                ),
            )
            signalDao.insertIgnore(
                GrowthSignalEntity(
                    id = "qa-signal-stale", sourceType = "DIARY", sourceId = journalId,
                    sourceVersion = "v0", category = "HABIT", content = "旧版本线索",
                    evidence = "旧日记版本", isInference = false, confidence = 0.5,
                    extractedAt = T + 420, status = "STALE", origin = "AI", sourceTitle = "旧标题",
                    contentHash = "qa-signal-hash-stale", updatedAt = T + 421,
                ),
            )

            val memoryDao = database.growthMemoryDao()
            memoryDao.insert(
                GrowthMemoryEntity(
                    id = "qa-memory-active", theme = "健康与习惯", content = "用户重视睡眠\n希望可持续",
                    isInference = false, status = "ACTIVE", createdAt = T + 500, updatedAt = T + 501,
                    signalIdsJson = """["qa-signal-active"]""", sourceDiaryIdsJson = "[$journalId]",
                    userCorrected = true, version = 2, confidence = 0.9, insufficient = false,
                    recentChange = "用户补充了睡眠目标", fingerprint = "qa-memory-fp-active", deletedAt = null,
                ),
            )
            memoryDao.insert(
                GrowthMemoryEntity(
                    id = "qa-memory-deleted", theme = "职业", content = "已删除的旧记忆",
                    isInference = true, status = "DELETED", createdAt = T + 510, updatedAt = T + 511,
                    signalIdsJson = "[]", sourceDiaryIdsJson = "[]", userCorrected = false,
                    version = 1, confidence = null, insufficient = true, recentChange = null,
                    fingerprint = "qa-memory-fp-deleted", deletedAt = T + 512,
                ),
            )

            database.insightReportDao().insert(
                InsightReportEntity(
                    id = "qa-insight-ready", dataVersion = "dv-full-1", status = "READY",
                    summary = "洞察摘要\n带 \"引号\" 和 😀", factsJson = """[{"key":"sleep","value":3.5}]""",
                    insightsJson = """[{"title":"睡眠","body":"有改善"}]""",
                    periodStart = T - 7L * 86400_000, periodEnd = T, generatedAt = T + 600,
                    error = null, model = "qa-fake",
                ),
            )
            database.insightReportDao().insert(
                InsightReportEntity(
                    id = "qa-insight-failed", dataVersion = "dv-full-2", status = "FAILED",
                    summary = "失败摘要", factsJson = "[]", insightsJson = "[]",
                    periodStart = T - 14L * 86400_000, periodEnd = T - 7L * 86400_000,
                    generatedAt = T + 601, error = "AI 失败\n可重试", model = null,
                ),
            )

            val weeklyDao = database.weeklyReviewDao()
            weeklyDao.upsert(
                WeeklyReviewEntity(
                    id = "qa-weekly-45", periodKey = "2026-W45", periodStart = T, periodEnd = T + 7L * 86400_000,
                    generatedAt = T + 700, version = 1, dataVersion = "dv-full-1", aiConsentVersion = "cv-full-1",
                    factsJson = """[{"key":"week-counts","value":"3"}]""",
                    sectionsJson = """{"sections":[{"title":"本周","body":"有进展"}]}""",
                    notifiedAt = T + 701,
                ),
            )
            weeklyDao.upsert(
                WeeklyReviewEntity(
                    id = "qa-weekly-44", periodKey = "2026-W44", periodStart = T - 7L * 86400_000, periodEnd = T,
                    generatedAt = T + 710, version = 1, dataVersion = "dv-full-0", aiConsentVersion = "cv-full-1",
                    factsJson = "[]", sectionsJson = """{"sections":[]}""", notifiedAt = null,
                ),
            )

            val weeklyAiDao = database.weeklyAiDao()
            weeklyAiDao.insertIfAbsent(
                WeeklyAiEntity(
                    periodKey = "2026-W45", localDataVersion = "dv-full-1", aiConsentVersion = "cv-full-1",
                    status = "READY", generatedAt = T + 800, summary = "旧格式 AI 周报\n带 \"引号\"",
                    highlightsJson = """[{"type":"POSITIVE","title":"睡眠","body":"有改善"}]""",
                    suggestion = "继续保持", factIdsJson = "week-counts\u001fcheckin-done",
                    categories = "GOALS", error = null, model = "qa-fake", configHash = "cfg-1",
                ),
            )
            weeklyAiDao.insertIfAbsent(
                WeeklyAiEntity(
                    periodKey = "2026-W44", localDataVersion = "dv-full-0", aiConsentVersion = "cv-full-1",
                    status = "STALE", generatedAt = T + 810, summary = "新格式 AI 周报",
                    highlightsJson = "[]", suggestion = "下周再试", factIdsJson = """["week-counts","goal-progress"]""",
                    categories = "GOALS", error = "旧周报已过期", model = null, configHash = "cfg-2",
                ),
            )
        }
    }

    /** B 变体：与 A 同为 10 表非空，但日记/目标内容不同，用于恢复后比对。 */
    suspend fun seedVariantB(database: MengyaDatabase) {
        seed(database)
        database.withTransaction {
            val db = database.openHelper.writableDatabase
            db.execSQL("UPDATE journal_entries SET title='B 变体日记', body='B body after export' WHERE id=(SELECT MIN(id) FROM journal_entries)")
            db.execSQL("UPDATE goals SET title='B 变体目标' WHERE level='LONG_TERM'")
            db.execSQL("UPDATE growth_memories SET content='B 变体记忆' WHERE status='ACTIVE'")
        }
    }
}
