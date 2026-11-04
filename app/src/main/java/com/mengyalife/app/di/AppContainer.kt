package com.mengyalife.app.di

import android.content.Context
import androidx.room.withTransaction
import com.mengyalife.app.BuildConfig
import com.mengyalife.app.data.advisor.AdvisorConfigRepository
import com.mengyalife.app.data.advisor.AdvisorDataRepository
import com.mengyalife.app.data.advisor.AndroidKeystoreCipher
import com.mengyalife.app.data.advisor.ConversationRepository
import com.mengyalife.app.data.advisor.DataStoreAdvisorConfigRepository
import com.mengyalife.app.data.advisor.DataStoreAiConfigStorage
import com.mengyalife.app.data.advisor.FakeAdvisorEngine
import com.mengyalife.app.data.advisor.GoalProposalRepository
import com.mengyalife.app.data.advisor.RoomAdvisorDataRepository
import com.mengyalife.app.data.advisor.RoomGoalProposalRepository
import com.mengyalife.app.data.advisor.NetworkAdvisorEngine
import com.mengyalife.app.data.advisor.RoomConversationRepository
import com.mengyalife.app.data.advisor.SecretCipher
import com.mengyalife.app.data.consent.AiConsentRepository
import com.mengyalife.app.data.consent.DataStoreAiConsentRepository
import com.mengyalife.app.data.export.DataExportRepository
import com.mengyalife.app.data.local.DB_SCHEMA_VERSION
import com.mengyalife.app.data.local.MengyaDatabase
import com.mengyalife.app.data.preferences.ThemePreferences
import com.mengyalife.app.data.repository.GoalRepository
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

    val goalRepository: GoalRepository by lazy {
        GoalRepository(database.goalDao())
    }

    val themePreferences: ThemePreferences by lazy {
        ThemePreferences(appContext)
    }

    private val secretCipher: SecretCipher by lazy {
        AndroidKeystoreCipher(BuildConfig.AI_KEY_ALIAS)
    }

    val advisorConfigRepository: AdvisorConfigRepository by lazy {
        DataStoreAdvisorConfigRepository(
            storage = DataStoreAiConfigStorage(appContext),
            cipher = secretCipher,
        )
    }

    val conversationRepository: ConversationRepository by lazy {
        RoomConversationRepository(database.advisorMessageDao())
    }

    val aiConsentRepository: AiConsentRepository by lazy {
        DataStoreAiConsentRepository(appContext)
    }

    val advisorDataRepository: AdvisorDataRepository by lazy {
        RoomAdvisorDataRepository(
            goalDao = database.goalDao(),
            journalDao = database.journalEntryDao(),
        )
    }

    val goalProposalRepository: GoalProposalRepository by lazy {
        RoomGoalProposalRepository(database.goalProposalDao())
    }

    val networkAdvisorEngine: NetworkAdvisorEngine by lazy { NetworkAdvisorEngine() }

    /** 仅 QA 构建提供；正式包永远为 null，避免假回复出现在真实产品里。 */
    val fakeAdvisorEngine: FakeAdvisorEngine? by lazy {
        if (BuildConfig.QA_BUILD) FakeAdvisorEngine() else null
    }

    /**
     * 导出仓库。事务执行器用 Room 的 `withTransaction`：
     * 三张表在同一事务内读取，得到一致快照。
     */
    val dataExportRepository: DataExportRepository by lazy {
        DataExportRepository(
            journalDao = database.journalEntryDao(),
            goalDao = database.goalDao(),
            appPackage = BuildConfig.APPLICATION_ID,
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE.toLong(),
            databaseVersion = DB_SCHEMA_VERSION,
            transaction = { block ->
                database.withTransaction { block() }
            },
        )
    }
}
