package com.mengyalife.app

import android.app.Application
import com.mengyalife.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 应用入口：持有一个手工依赖容器（不引入 Hilt/Dagger）。
 *
 * 只有这里知道「数据库 / DataStore 在哪里」，ViewModel 通过工厂从中取依赖。
 */
class MengyaApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // WorkManager 自身持久化；这里只保证「已开启但任务被系统清理」时恢复唯一任务。
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { container.restoreCoordinator.reconcileOnStartup() }
            runCatching { container.weeklyReviewScheduler.sync(container.weeklyReviewPreferences) }
            runCatching { container.carePreferenceRepository.reconcileSending() }
            runCatching { com.mengyalife.app.data.care.CareScheduler.schedule(this@MengyaApplication) }
        }
    }
}
