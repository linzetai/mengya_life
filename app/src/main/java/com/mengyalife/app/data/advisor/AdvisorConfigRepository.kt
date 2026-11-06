package com.mengyalife.app.data.advisor

import com.mengyalife.app.BuildConfig
import com.mengyalife.app.domain.advisor.AdvisorRuntimeConfig
import com.mengyalife.app.domain.advisor.AdvisorRules
import com.mengyalife.app.domain.advisor.AiConfigState
import com.mengyalife.app.data.restore.RestoreWriteBarrier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AiConfigSaveResult(
    val ok: Boolean,
    val error: String? = null,
)

enum class KeyDeleteResult { DELETED, ALREADY_ABSENT, FAILED }

interface AdvisorConfigRepository {
    val state: Flow<AiConfigState>
    suspend fun save(endpoint: String, model: String, apiKey: String?): AiConfigSaveResult
    suspend fun deleteKey()

    /** 删除并确认 alias 不存在；默认 fake 走旧实现，生产覆盖。 */
    suspend fun deleteKeyConfirmed(): KeyDeleteResult {
        deleteKey()
        return KeyDeleteResult.DELETED
    }

    /** 恢复非秘密 endpoint/model，不写 Key；默认 no-op。 */
    suspend fun restoreEndpointAndModel(endpoint: String, model: String) = Unit

    suspend fun runtimeConfig(): AdvisorRuntimeConfig?
    suspend fun setUseFakeEngine(enabled: Boolean)
}

/**
 * BYOK 配置仓库：校验、加密、删除都在这里，UI 只拿配置状态（不含密钥）。
 */
class DataStoreAdvisorConfigRepository(
    private val storage: AiConfigStorage,
    private val cipher: SecretCipher,
    private val allowFakeEngine: Boolean = BuildConfig.QA_BUILD,
    private val writeBarrier: RestoreWriteBarrier? = null,
) : AdvisorConfigRepository {

    override val state: Flow<AiConfigState> = storage.observe().map {
        AiConfigState(
            endpoint = it.endpoint,
            model = it.model,
            hasKey = !it.encryptedKey.isNullOrBlank(),
            useFakeEngine = allowFakeEngine && it.useFakeEngine,
        )
    }

    override suspend fun save(endpoint: String, model: String, apiKey: String?): AiConfigSaveResult =
        guarded { saveUnlocked(endpoint, model, apiKey) }

    private suspend fun saveUnlocked(
        endpoint: String,
        model: String,
        apiKey: String?,
    ): AiConfigSaveResult {
        AdvisorRules.validateEndpoint(endpoint)?.let { return AiConfigSaveResult(false, it) }
        AdvisorRules.validateModel(model)?.let { return AiConfigSaveResult(false, it) }

        val existing = storage.load()
        val trimmedKey = apiKey?.trim()
        if (!trimmedKey.isNullOrEmpty()) {
            AdvisorRules.validateApiKey(trimmedKey)?.let { return AiConfigSaveResult(false, it) }
        }
        if (trimmedKey.isNullOrEmpty() && existing.encryptedKey.isNullOrBlank()) {
            return AiConfigSaveResult(false, AdvisorRules.ERROR_KEY_EMPTY)
        }

        val encrypted = if (!trimmedKey.isNullOrEmpty()) {
            runCatching { cipher.encrypt(trimmedKey) }
                .getOrElse { return AiConfigSaveResult(false, "无法在本机加密保存密钥") }
        } else {
            existing.encryptedKey
        }

        storage.save(
            existing.copy(
                endpoint = endpoint.trim(),
                model = model.trim(),
                encryptedKey = encrypted,
            ),
        )
        return AiConfigSaveResult(true)
    }

    override suspend fun deleteKey() = guarded {
        val existing = storage.load()
        storage.save(existing.copy(encryptedKey = null))
        // 同时删除 Keystore 条目：旧密文即使被恢复也无法再解。
        cipher.deleteKey()
    }

    override suspend fun deleteKeyConfirmed(): KeyDeleteResult = guarded {
        val existing = storage.load()
        val confirmed = cipher.deleteKeyConfirmed()
        if (!confirmed) return@guarded KeyDeleteResult.FAILED
        storage.save(existing.copy(encryptedKey = null))
        if (existing.encryptedKey.isNullOrBlank()) KeyDeleteResult.ALREADY_ABSENT else KeyDeleteResult.DELETED
    }

    override suspend fun restoreEndpointAndModel(endpoint: String, model: String) = guarded {
        val existing = storage.load()
        storage.save(existing.copy(endpoint = endpoint.trim(), model = model.trim(), encryptedKey = null))
    }

    override suspend fun runtimeConfig(): AdvisorRuntimeConfig? {
        val stored = storage.load()
        val endpoint = stored.endpoint.trim()
        val model = stored.model.trim()
        val blob = stored.encryptedKey?.takeIf { it.isNotBlank() } ?: return null
        if (endpoint.isEmpty() || model.isEmpty()) return null
        val key = cipher.decrypt(blob)?.takeIf { it.isNotBlank() } ?: return null
        return AdvisorRuntimeConfig(endpoint = endpoint, model = model, apiKey = key)
    }

    override suspend fun setUseFakeEngine(enabled: Boolean) = guarded {
        if (!allowFakeEngine) return@guarded
        val existing = storage.load()
        storage.save(existing.copy(useFakeEngine = enabled))
    }

    private suspend fun <T> guarded(block: suspend () -> T): T =
        writeBarrier?.withWrite { block() } ?: block()
}
