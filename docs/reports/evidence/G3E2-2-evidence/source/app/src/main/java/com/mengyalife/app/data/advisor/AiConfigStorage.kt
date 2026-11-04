package com.mengyalife.app.data.advisor

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mengyalife.app.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/** 只描述「存在磁盘上的东西」：endpoint / model / 加密后的 key / QA 假引擎开关。 */
data class StoredAiConfig(
    val endpoint: String = "",
    val model: String = "",
    val encryptedKey: String? = null,
    val useFakeEngine: Boolean = false,
)

interface AiConfigStorage {
    fun observe(): Flow<StoredAiConfig>
    suspend fun load(): StoredAiConfig
    suspend fun save(config: StoredAiConfig)
}

private val Context.aiDataStore: DataStore<Preferences> by preferencesDataStore(
    name = BuildConfig.AI_PREFS_NAME,
)

/**
 * AI 配置存储（DataStore）。
 *
 * 这里**只存密文**；密钥明文只在设置页输入框与请求瞬间存在于内存。
 */
class DataStoreAiConfigStorage(private val context: Context) : AiConfigStorage {

    private val endpointKey = stringPreferencesKey("endpoint")
    private val modelKey = stringPreferencesKey("model")
    private val encryptedKeyKey = stringPreferencesKey("encrypted_api_key")
    private val fakeEngineKey = booleanPreferencesKey("use_fake_engine")

    override fun observe(): Flow<StoredAiConfig> = context.aiDataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs ->
            StoredAiConfig(
                endpoint = prefs[endpointKey].orEmpty(),
                model = prefs[modelKey].orEmpty(),
                encryptedKey = prefs[encryptedKeyKey]?.takeIf { it.isNotBlank() },
                useFakeEngine = prefs[fakeEngineKey] ?: false,
            )
        }

    override suspend fun load(): StoredAiConfig {
        val prefs = context.aiDataStore.data
            .catch { throwable ->
                if (throwable is IOException) emit(emptyPreferences()) else throw throwable
            }
            .first()
        return StoredAiConfig(
            endpoint = prefs[endpointKey].orEmpty(),
            model = prefs[modelKey].orEmpty(),
            encryptedKey = prefs[encryptedKeyKey]?.takeIf { it.isNotBlank() },
            useFakeEngine = prefs[fakeEngineKey] ?: false,
        )
    }

    override suspend fun save(config: StoredAiConfig) {
        context.aiDataStore.edit { prefs ->
            prefs[endpointKey] = config.endpoint.trim()
            prefs[modelKey] = config.model.trim()
            val encrypted = config.encryptedKey?.takeIf { it.isNotBlank() }
            if (encrypted == null) {
                prefs.remove(encryptedKeyKey)
            } else {
                prefs[encryptedKeyKey] = encrypted
            }
            prefs[fakeEngineKey] = config.useFakeEngine
        }
    }
}
