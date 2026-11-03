package com.mengyalife.app.advisor

import com.mengyalife.app.data.advisor.AdvisorConfigRepository
import com.mengyalife.app.data.advisor.AdvisorEngine
import com.mengyalife.app.data.advisor.AiConfigSaveResult
import com.mengyalife.app.data.advisor.AiConfigStorage
import com.mengyalife.app.data.advisor.ConversationRepository
import com.mengyalife.app.data.advisor.SecretCipher
import com.mengyalife.app.data.advisor.StoredAiConfig
import com.mengyalife.app.data.local.advisor.AdvisorMessageDao
import com.mengyalife.app.data.local.advisor.AdvisorMessageEntity
import com.mengyalife.app.domain.advisor.AdvisorChatMessage
import com.mengyalife.app.domain.advisor.AdvisorChatResult
import com.mengyalife.app.domain.advisor.AdvisorContextSource
import com.mengyalife.app.domain.advisor.AdvisorError
import com.mengyalife.app.domain.advisor.AdvisorGoalChoice
import com.mengyalife.app.domain.advisor.AdvisorGoalContext
import com.mengyalife.app.domain.advisor.AdvisorRuntimeConfig
import com.mengyalife.app.domain.advisor.AdvisorTestResult
import com.mengyalife.app.domain.advisor.AiConfigState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSecretCipher : SecretCipher {
    var deleted = 0
    val plaintexts = mutableListOf<String>()

    override fun encrypt(plaintext: String): String {
        plaintexts.add(plaintext)
        return "enc:" + plaintext.reversed()
    }

    override fun decrypt(blob: String): String? =
        if (blob.startsWith("enc:")) blob.removePrefix("enc:").reversed() else null

    override fun deleteKey() {
        deleted++
    }
}

class FakeAiConfigStorage(initial: StoredAiConfig = StoredAiConfig()) : AiConfigStorage {
    val state = MutableStateFlow(initial)

    override fun observe(): Flow<StoredAiConfig> = state

    override suspend fun load(): StoredAiConfig = state.value

    override suspend fun save(config: StoredAiConfig) {
        state.value = config
    }
}

/** 真实配置仓库 + 假存储/假加密：验证加密、删除与状态，不依赖 Android。 */
class FakeConfigRepository(
    val storage: FakeAiConfigStorage = FakeAiConfigStorage(),
    val cipher: FakeSecretCipher = FakeSecretCipher(),
) : AdvisorConfigRepository {
    private val real = com.mengyalife.app.data.advisor.DataStoreAdvisorConfigRepository(
        storage = storage,
        cipher = cipher,
        allowFakeEngine = true,
    )

    override val state: Flow<AiConfigState> = real.state

    override suspend fun save(endpoint: String, model: String, apiKey: String?): AiConfigSaveResult =
        real.save(endpoint, model, apiKey)

    override suspend fun deleteKey() = real.deleteKey()

    override suspend fun runtimeConfig(): AdvisorRuntimeConfig? = real.runtimeConfig()

    override suspend fun setUseFakeEngine(enabled: Boolean) = real.setUseFakeEngine(enabled)
}

class FakeConversationDao : AdvisorMessageDao {
    private val rows = MutableStateFlow<List<AdvisorMessageEntity>>(emptyList())

    override fun observeAll(): Flow<List<AdvisorMessageEntity>> =
        rows.map { it.sortedWith(compareBy<AdvisorMessageEntity> { m -> m.createdAt }.thenBy { m -> m.id }) }

    override suspend fun loadAll(): List<AdvisorMessageEntity> =
        rows.value.sortedWith(compareBy<AdvisorMessageEntity> { it.createdAt }.thenBy { it.id })

    override suspend fun insert(message: AdvisorMessageEntity) {
        rows.value = rows.value + message
    }

    override suspend fun maxCreatedAt(): Long? = rows.value.maxOfOrNull { it.createdAt }

    override suspend fun deleteById(id: String): Int {
        val before = rows.value.size
        rows.value = rows.value.filterNot { it.id == id }
        return before - rows.value.size
    }

    override suspend fun deleteAll(): Int {
        val n = rows.value.size
        rows.value = emptyList()
        return n
    }
}

/** 记录调用的假引擎；按脚本返回结果。 */
class ScriptedAdvisorEngine(
    private val results: MutableList<AdvisorChatResult> = mutableListOf(),
) : AdvisorEngine {
    val calls = mutableListOf<List<AdvisorChatMessage>>()
    var testResult: AdvisorTestResult = AdvisorTestResult(true, "ok")
    var blockUntilCancelled: Boolean = false

    fun enqueue(result: AdvisorChatResult) {
        results.add(result)
    }

    override suspend fun chat(
        config: AdvisorRuntimeConfig,
        messages: List<AdvisorChatMessage>,
        maxTokens: Int,
    ): AdvisorChatResult {
        calls.add(messages)
        if (blockUntilCancelled) {
            kotlinx.coroutines.awaitCancellation()
        }
        return results.removeFirstOrNull() ?: AdvisorChatResult.Success("default reply")
    }

    override suspend fun testConnection(config: AdvisorRuntimeConfig): AdvisorTestResult = testResult
}

class FakeContextSource(
    private val goals: List<AdvisorGoalChoice> = emptyList(),
    private val contexts: Map<Long, AdvisorGoalContext> = emptyMap(),
) : AdvisorContextSource {
    override fun observeGoalChoices(): Flow<List<AdvisorGoalChoice>> =
        MutableStateFlow(goals)

    override suspend fun buildContext(goalId: Long): AdvisorGoalContext? = contexts[goalId]
}

fun sampleContext(goalId: Long = 1L) = AdvisorGoalContext(
    goalId = goalId,
    title = "健康",
    description = "长期保持规律运动",
    stageTitles = listOf("每周三次"),
    actionTitles = listOf("晨跑"),
    recentCheckinLines = listOf("2026年11月3日 · 完成 3 次（3 次/周）"),
    totalCheckins = 1,
)

fun sampleGoal(id: Long = 1L, title: String = "健康") = AdvisorGoalChoice(id, title)
