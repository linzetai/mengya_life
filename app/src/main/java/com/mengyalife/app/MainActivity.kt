package com.mengyalife.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
                    com.mengyalife.app.data.qa.QaRestoreSecuritySeeder.seed(container)
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
        setContent {
            MengyaLifeApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(com.mengyalife.app.data.weekly.WeeklyReviewNotifier.EXTRA_WEEKLY_REVIEW, false)) {
            com.mengyalife.app.data.weekly.WeeklyReviewRouter.requestOpen()
        }
    }
}
