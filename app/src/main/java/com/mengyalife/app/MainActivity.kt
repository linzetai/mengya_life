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
        }
        enableEdgeToEdge()
        setContent {
            MengyaLifeApp()
        }
    }
}
