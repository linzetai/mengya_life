package com.mengyalife.app

import android.app.Application
import com.mengyalife.app.di.AppContainer

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
    }
}
