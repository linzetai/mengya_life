package com.mengyalife.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 唯一 Activity。页面结构、导航与主题状态都在 [MengyaLifeApp] 里，
 * 这里只负责窗口与挂载，避免把业务 UI 堆进 Activity。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.QA_BUILD) {
            com.mengyalife.app.data.backup.QaBackupFaultInjector.reset()
            com.mengyalife.app.data.restore.QaRestoreFaultInjector.reset()
            if (intent?.getBooleanExtra("qa_restore_fail_transaction", false) == true) {
                com.mengyalife.app.data.restore.QaRestoreFaultInjector.failTransaction = true
            }
            if (intent?.getBooleanExtra("qa_restore_fail_key_delete", false) == true) {
                com.mengyalife.app.data.restore.QaRestoreFaultInjector.failKeyDelete = true
            }
            if (intent?.getBooleanExtra("qa_restore_fail_settings", false) == true) {
                com.mengyalife.app.data.restore.QaRestoreFaultInjector.failSettings = true
            }
            if (intent?.hasExtra("qa_restore_pause_after_commit_ms") == true) {
                com.mengyalife.app.data.restore.QaRestoreFaultInjector.pauseAfterCommitMs =
                    intent.getLongExtra("qa_restore_pause_after_commit_ms", 0L)
            }
            if (intent?.hasExtra("qa_restore_pause_after_consent_ms") == true) {
                com.mengyalife.app.data.restore.QaRestoreFaultInjector.pauseAfterConsentMs =
                    intent.getLongExtra("qa_restore_pause_after_consent_ms", 0L)
            }
            if (intent?.getBooleanExtra("qa_backup_fail_write", false) == true) {
                com.mengyalife.app.data.backup.QaBackupFaultInjector.failTargetWrite = true
            }
            if (intent?.getBooleanExtra("qa_backup_truncate_write", false) == true) {
                com.mengyalife.app.data.backup.QaBackupFaultInjector.truncateTargetWrite = true
            }
            if (intent?.getBooleanExtra("qa_backup_fail_read", false) == true) {
                com.mengyalife.app.data.backup.QaBackupFaultInjector.failTargetRead = true
            }
            if (intent?.getBooleanExtra("qa_backup_fail_delete", false) == true) {
                com.mengyalife.app.data.backup.QaBackupFaultInjector.failTargetDelete = true
            }
            if (intent?.getBooleanExtra("qa_backup_fail_temp_cleanup", false) == true) {
                com.mengyalife.app.data.backup.QaBackupFaultInjector.failTempCleanup = true
            }
            com.mengyalife.app.data.advisor.QaFaultInjector.reset()
            if (intent?.hasExtra("qa_fail_create_at") == true) {
                com.mengyalife.app.data.advisor.QaFaultInjector.failCreateAt =
                    intent.getIntExtra("qa_fail_create_at", -1).takeIf { it > 0 }
            }
            if (intent?.hasExtra("qa_fail_undo_at") == true) {
                com.mengyalife.app.data.advisor.QaFaultInjector.failUndoAt =
                    intent.getIntExtra("qa_fail_undo_at", -1).takeIf { it > 0 }
            }
            if (intent?.hasExtra("qa_fail_signal_insert_at") == true) {
                com.mengyalife.app.data.advisor.QaFaultInjector.failSignalInsertAt =
                    intent.getIntExtra("qa_fail_signal_insert_at", -1).takeIf { it > 0 }
            }
            com.mengyalife.app.data.growth.QaExtractionGate.reset()
            if (intent?.hasExtra("qa_extract_withdraw_before_request") == true) {
                com.mengyalife.app.data.growth.QaExtractionGate.withdrawBeforeRequest = true
            }
            if (intent?.hasExtra("qa_extract_delay_ms") == true) {
                com.mengyalife.app.data.growth.QaExtractionGate.delayBeforeRequestMs =
                    intent.getIntExtra("qa_extract_delay_ms", 0).toLong().coerceAtLeast(0)
            }
            // QA 真机验收：ADB 无法输入中文，允许用 intent extra 注入一句顾问输入。
            intent?.getStringExtra("qa_advisor_prompt")?.let {
                com.mengyalife.app.ui.advisor.AdvisorTestBus.offer(it)
            }
            if (intent?.getBooleanExtra("qa_care_now", false) == true) {
                com.mengyalife.app.data.care.CareScheduler.runNow(this)
            }
            if (intent?.getBooleanExtra("qa_endpoint_change_restore", false) == true) {
                val app = application as MengyaApplication
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    runCatching {
                        app.container.restoreCoordinator.awaitReady()
                        // 固定基线：确保备份里的 endpoint 是 A，避免上次失败把本机改成 B 后污染 fixture。
                        app.container.advisorConfigRepository.restoreEndpointAndModel(
                            "https://api.deepseek.com", "deepseek-flash",
                        )
                        val pass = "qa-endpoint".toCharArray()
                        val artifact = app.container.backupExporter.build(pass)
                        // 让本机 endpoint 与备份中的 endpoint 不同，再把备份恢复回来。
                        app.container.advisorConfigRepository.restoreEndpointAndModel(
                            "https://other.example.com/v1", "other-model",
                        )
                        val result = app.container.restoreCoordinator.execute(
                            artifact.bytes, pass, artifact.payloadSha256, artifact.backupId,
                        )
                        val config = app.container.advisorConfigRepository.state.first()
                        val consent = app.container.aiConsentRepository.current()
                        android.util.Log.i(
                            "CareRoundtrip",
                            "endpointRestore=$result hasKey=${config.hasKey} consent=${consent.granted} epoch=${consent.epoch}",
                        )
                    }.onFailure { android.util.Log.e("CareRoundtrip", "endpointRestore failed", it) }
                }
            }
            if (intent?.getBooleanExtra("qa_legacy_roundtrip", false) == true) {
                val app = application as MengyaApplication
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    runCatching {
                        app.container.restoreCoordinator.awaitReady()
                        val pass = "qa-legacy".toCharArray()
                        // 版本固定 fixture：真实编码器，但不含 0.24.1 的 recordProposals。
                        val artifact = app.container.backupExporter.build(pass, includeRecordProposals = false)
                        val result = app.container.restoreCoordinator.execute(
                            artifact.bytes, pass, artifact.payloadSha256, artifact.backupId,
                        )
                        android.util.Log.i("CareRoundtrip", "legacy=$result")
                    }.onFailure { android.util.Log.e("CareRoundtrip", "legacy failed", it) }
                }
            }
            if (intent?.getBooleanExtra("qa_care_roundtrip", false) == true) {
                val app = application as MengyaApplication
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    runCatching {
                        app.container.restoreCoordinator.awaitReady()
                        val pass = "qa-roundtrip".toCharArray()
                        val artifact = app.container.backupExporter.build(pass)
                        val result = app.container.restoreCoordinator.execute(
                            artifact.bytes, pass, artifact.payloadSha256, artifact.backupId,
                        )
                        android.util.Log.i("CareRoundtrip", "result=$result")
                    }.onFailure { android.util.Log.e("CareRoundtrip", "failed", it) }
                }
            }
            if (intent?.getBooleanExtra("qa_care_direct", false) == true) {
                com.mengyalife.app.data.care.CareDirectTest.runDelayed(application as MengyaApplication)
            }
            intent?.getLongExtra("qa_republish_care", -1L)?.takeIf { it > 0 }?.let { careId ->
                val app = application as MengyaApplication
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val opp = app.container.careRepository.find(careId) ?: return@launch
                    val decision = app.container.careRepository.latestDecisionFor(careId) ?: return@launch
                    com.mengyalife.app.data.care.CareNotification.post(
                        app, careId, decision.suggestedText ?: "最近怎么样？",
                        com.mengyalife.app.data.care.CareSettings(app).lockScreenDetail,
                    )
                }
            }

            if (intent?.getBooleanExtra("qa_seed_backup_fixture", false) == true) {
                val container = (application as MengyaApplication).container
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    container.qaSeedBackupFixture()
                }
            }
            if (intent?.getBooleanExtra("qa_seed_backup_fixture_b", false) == true) {
                val container = (application as MengyaApplication).container
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    container.qaSeedBackupFixtureB()
                }
            }
            if (intent?.getBooleanExtra("qa_seed_restore_security", false) == true) {
                val container = (application as MengyaApplication).container
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    container.qaSeedRestoreSecurity()
                }
            }
            if (intent?.hasExtra("qa_run_weekly_review") == true) {
                (application as MengyaApplication).container.weeklyReviewScheduler.runNowForQa()
            }
            // 仅 QA：清空本周 AI 结果行，供状态机用例独立重跑；不触碰日记/目标/授权/配置。
            if (intent?.hasExtra("qa_clear_weekly_ai") == true) {
                val container = (application as MengyaApplication).container
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    container.qaClearWeeklyAi()
                }
            }
        }
        if (intent?.getBooleanExtra(com.mengyalife.app.data.weekly.WeeklyReviewNotifier.EXTRA_WEEKLY_REVIEW, false) == true) {
            com.mengyalife.app.data.weekly.WeeklyReviewRouter.requestOpen()
        }
        enableEdgeToEdge()
        intent?.getLongExtra(com.mengyalife.app.data.care.CareNotification.EXTRA_CARE_ID, -1L)
            ?.takeIf { it > 0 }
            ?.let { com.mengyalife.app.data.care.CareOpenBus.offer(it) }
        setContent {
            MengyaLifeApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (BuildConfig.QA_BUILD) {
            intent.getStringExtra("qa_advisor_prompt")?.let {
                com.mengyalife.app.ui.advisor.AdvisorTestBus.offer(it)
            }
        }
        intent.getLongExtra(com.mengyalife.app.data.care.CareNotification.EXTRA_CARE_ID, -1L)
            .takeIf { it > 0 }
            ?.let { com.mengyalife.app.data.care.CareOpenBus.offer(it) }
        if (intent.getBooleanExtra(com.mengyalife.app.data.weekly.WeeklyReviewNotifier.EXTRA_WEEKLY_REVIEW, false)) {
            com.mengyalife.app.data.weekly.WeeklyReviewRouter.requestOpen()
        }
    }
}
