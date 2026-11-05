package com.mengyalife.app.backup

import com.mengyalife.app.data.backup.BackupAppInfo
import com.mengyalife.app.data.backup.BackupSettingsSnapshot
import com.mengyalife.app.data.backup.BackupSnapshot
import com.mengyalife.app.data.local.JournalEntryEntity
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

object BackupTestFixtures {
    const val T = 1_793_827_200_000L

    fun app(qa: Boolean = true) = BackupAppInfo(
        appPackage = if (qa) "com.mengyalife.app.qa" else "com.mengyalife.app",
        qaBuild = qa,
        versionName = "0.14.1-g3e2-1",
        versionCode = 15,
        androidSdk = 35,
    )

    fun settings() = BackupSettingsSnapshot(
        themeMode = "SYSTEM",
        weeklyReviewEnabled = true,
        weeklyAiEnabled = false,
        notificationAsked = false,
        aiEndpoint = "https://api.example.com/v1",
        aiModel = "qa-fake",
    )

    fun empty(): BackupSnapshot = BackupSnapshot(
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
    )

    fun snapshot(analyzing: Boolean = false): BackupSnapshot = BackupSnapshot(
        journalEntries = listOf(
            JournalEntryEntity(
                id = 1, title = "日记一", body = "正文一", createdAt = T, updatedAt = T,
                analysisStatus = if (analyzing) "ANALYZING" else "COMPLETED",
                analysisHash = "hash", analysisError = null, analysisAttempts = 1,
                analyzedAt = T, analysisLastAttemptAt = T,
            ),
        ),
        goals = listOf(
            GoalEntity(
                id = 1, title = "长期", description = "desc", level = GoalLevel.LONG_TERM,
                parentId = null, domain = GoalDomain.HEALTH, status = GoalStatus.IN_PROGRESS,
                startDate = null, dueDate = null, createdAt = T, updatedAt = T,
            ),
            GoalEntity(
                id = 2, title = "阶段", description = null, level = GoalLevel.STAGE,
                parentId = 1, domain = GoalDomain.HEALTH, status = GoalStatus.IN_PROGRESS,
                startDate = null, dueDate = null, createdAt = T, updatedAt = T,
            ),
        ),
        goalCheckins = listOf(
            GoalCheckinEntity(
                id = 1, goalId = 2, recordedAt = T, note = "checkin",
                metricValue = 3.0, metricUnit = "min", source = "MANUAL",
            ),
        ),
        advisorMessages = listOf(
            AdvisorMessageEntity(
                id = "m1", role = "user", content = "hello", createdAt = T, status = "SENT",
                errorCode = null, errorMessage = null, contextGoalId = 1, contextEpoch = 1,
                sourcesJson = null, proposalError = null, proposalMode = false,
            ),
            AdvisorMessageEntity(
                id = "m2", role = "assistant", content = "reply", createdAt = T + 1, status = "SENT",
                errorCode = null, errorMessage = null, contextGoalId = null, contextEpoch = 1,
                sourcesJson = "[]", proposalError = null, proposalMode = false,
            ),
        ),
        goalProposals = listOf(
            GoalProposalEntity(
                id = "p1", messageId = "m2", createdAt = T, schemaVersion = 1, status = "DRAFT",
                summary = "summary", assumptionsJson = "[]", risksJson = "[]", nodesJson = "[]",
                selectedTempIdsJson = null, appliedAt = null, appliedGoalIdsJson = null, undoneAt = null,
            ),
        ),
        growthSignals = listOf(
            GrowthSignalEntity(
                id = "s1", sourceType = "DIARY", sourceId = 1, sourceVersion = "v1",
                category = "EVENT", content = "signal", evidence = "evidence", isInference = false,
                confidence = 0.9, extractedAt = T, status = "ACTIVE", origin = "AI",
                sourceTitle = "日记一", contentHash = "hash", updatedAt = T,
            ),
        ),
        growthMemories = listOf(
            GrowthMemoryEntity(
                id = "mem1", theme = "健康与习惯", content = "memory", isInference = false,
                status = "ACTIVE", createdAt = T, updatedAt = T,
                signalIdsJson = "[\"s1\"]", sourceDiaryIdsJson = "[1]", userCorrected = false,
                version = 1, confidence = 0.8, insufficient = false, recentChange = null,
                fingerprint = "fp", deletedAt = null,
            ),
        ),
        insightReports = listOf(
            InsightReportEntity(
                id = "i1", dataVersion = "dv1", status = "READY", summary = "summary",
                factsJson = "[]", insightsJson = "[]", periodStart = T, periodEnd = T + 1,
                generatedAt = T, error = null, model = "qa-fake",
            ),
        ),
        weeklyReviews = listOf(
            WeeklyReviewEntity(
                id = "w1", periodKey = "2026-W45", periodStart = T, periodEnd = T + 1,
                generatedAt = T, version = 1, dataVersion = "dv1", aiConsentVersion = "cv1",
                factsJson = "[]", sectionsJson = "{}", notifiedAt = T + 2,
            ),
        ),
        weeklyAiReviews = listOf(
            WeeklyAiEntity(
                periodKey = "2026-W45", localDataVersion = "dv1", aiConsentVersion = "cv1",
                status = "READY", generatedAt = T, summary = "weekly ai", highlightsJson = "[]",
                suggestion = "suggestion", factIdsJson = "week-counts\u001fwins-progress",
                categories = "GOALS", error = null, model = "qa-fake", configHash = "cfg",
            ),
        ),
    )
}
