package com.mengyalife.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * 唯一 Activity。页面结构、导航与主题状态都在 [MengyaLifeApp] 里，
 * 这里只负责窗口与挂载，避免把业务 UI 堆进 Activity。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.QA_BUILD) {
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
        }
        enableEdgeToEdge()
        setContent {
            MengyaLifeApp()
        }
    }
}
