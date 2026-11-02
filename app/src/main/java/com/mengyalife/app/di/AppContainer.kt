package com.mengyalife.app.di

import android.content.Context
import com.mengyalife.app.data.local.MengyaDatabase
import com.mengyalife.app.data.preferences.ThemePreferences
import com.mengyalife.app.data.repository.JournalRepository

/**
 * 手工依赖容器（本轮刻意不引入 Hilt：少一个编译器插件、少一层构建风险）。
 *
 * 用 `by lazy` 保证数据库与 DataStore 都是首次使用时才创建。
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    private val database: MengyaDatabase by lazy { MengyaDatabase.get(appContext) }

    val journalRepository: JournalRepository by lazy {
        JournalRepository(database.journalEntryDao())
    }

    val themePreferences: ThemePreferences by lazy {
        ThemePreferences(appContext)
    }
}
