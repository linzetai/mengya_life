package com.mengyalife.app.advisor

import com.mengyalife.app.data.advisor.AdvisorConfigRepository
import com.mengyalife.app.data.advisor.AdvisorEngine
import com.mengyalife.app.data.advisor.AiConfigSaveResult
import com.mengyalife.app.data.advisor.AiConfigStorage
import com.mengyalife.app.data.advisor.AdvisorDataRepository
import com.mengyalife.app.data.advisor.ConversationRepository
import com.mengyalife.app.data.advisor.GoalProposalApply
import com.mengyalife.app.data.advisor.GoalProposalRepository
import com.mengyalife.app.data.advisor.ProposalApplyResult
import com.mengyalife.app.data.advisor.ProposalUndoResult
import com.mengyalife.app.data.advisor.StoredGoalProposal
import com.mengyalife.app.data.consent.AiConsentRepository
import com.mengyalife.app.data.advisor.SecretCipher
import com.mengyalife.app.data.advisor.StoredAiConfig
import com.mengyalife.app.data.local.advisor.AdvisorMessageDao
import com.mengyalife.app.data.local.advisor.AdvisorMessageEntity
import com.mengyalife.app.domain.advisor.AdvisorChatMessage
import com.mengyalife.app.domain.advisor.AdvisorChatResult
import com.mengyalife.app.domain.advisor.AdvisorContextSnapshot
import com.mengyalife.app.domain.advisor.AiConsentRules
import com.mengyalife.app.domain.advisor.AiConsentState
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.advisor.GoalProposalDraft
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

    var runtimeEndpointOverride: String? = null

    override suspend fun runtimeConfig(): AdvisorRuntimeConfig? =
        real.runtimeConfig()?.let { config ->
            runtimeEndpointOverride?.let { config.copy(endpoint = it) } ?: config
        }

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


class FakeConsentRepository(
    initial: AiConsentState = AiConsentState(),
) : AiConsentRepository {
    private val flow = MutableStateFlow(initial)
    override val state: Flow<AiConsentState> = flow
    override suspend fun current(): AiConsentState = flow.value
    override suspend fun grant(recipient: String, categories: Set<ConsentCategory>) {
        flow.value = AiConsentState(
            granted = true,
            textVersion = AiConsentRules.TEXT_VERSION,
            categoriesVersion = AiConsentRules.CATEGORIES_VERSION,
            grantedAt = 1L,
            recipient = AiConsentRules.normalizeRecipient(recipient),
            categories = categories,
            epoch = flow.value.epoch + 1,
        )
    }
    override suspend fun setCategory(category: ConsentCategory, enabled: Boolean) {
        val current = flow.value.categories.toMutableSet()
        if (enabled) current.add(category) else current.remove(category)
        flow.value = flow.value.copy(categories = current, epoch = flow.value.epoch + 1)
    }
    override suspend fun resetAfterRestore(generation: Long): com.mengyalife.app.data.consent.ConsentResetResult {
        return com.mengyalife.app.data.consent.ConsentResetResult.RESET
    }

    override suspend fun withdraw() {
        flow.value = AiConsentState(epoch = flow.value.epoch + 1)
    }
}

class FakeAdvisorDataRepository(var snapshot: AdvisorContextSnapshot = AdvisorContextSnapshot()) :
    AdvisorDataRepository {
    override suspend fun snapshot(consent: AiConsentState): AdvisorContextSnapshot = snapshot
}

class FakeGoalProposalRepository : GoalProposalRepository {
    private val flow = MutableStateFlow<List<StoredGoalProposal>>(emptyList())
    override fun observeAll(): Flow<List<StoredGoalProposal>> = flow
    override suspend fun save(messageId: String, draft: GoalProposalDraft, expectedRestoreGeneration: Long?): StoredGoalProposal {
        val proposal = StoredGoalProposal(
            "p-$messageId", messageId, 1L, draft, "DRAFT",
            selectedTempIds = draft.nodes.map { it.tempId }.toSet(),
        )
        flow.value = flow.value + proposal
        return proposal
    }
    override suspend fun findForMessage(messageId: String): StoredGoalProposal? =
        flow.value.firstOrNull { it.messageId == messageId }
    override suspend fun findById(id: String): StoredGoalProposal? =
        flow.value.firstOrNull { it.id == id }
    override suspend fun updateDraft(
        id: String,
        draft: GoalProposalDraft,
        selected: Set<String>,
    ): Boolean {
        val current = flow.value.firstOrNull { it.id == id && it.isDraft } ?: return false
        flow.value = flow.value.map {
            if (it.id == id) it.copy(draft = draft, selectedTempIds = selected) else it
        }
        return current.isDraft
    }
}

class FakeGoalProposalApply : GoalProposalApply {
    var applyCalls = 0
    var undoCalls = 0
    var applyResult: ProposalApplyResult = ProposalApplyResult.Success(
        listOf(com.mengyalife.app.data.advisor.AppliedGoal("g1", 1L, "健康", com.mengyalife.app.domain.goals.GoalLevel.LONG_TERM)),
    )
    var undoResult: ProposalUndoResult = ProposalUndoResult.Success()
    override suspend fun apply(proposalId: String, selectedTempIds: Set<String>): ProposalApplyResult {
        applyCalls++
        return applyResult
    }
    override suspend fun undo(proposalId: String): ProposalUndoResult {
        undoCalls++
        return undoResult
    }
}
