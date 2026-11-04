package com.mengyalife.app.data.consent

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mengyalife.app.BuildConfig
import com.mengyalife.app.domain.advisor.AiConsentRules
import com.mengyalife.app.domain.advisor.AiConsentState
import com.mengyalife.app.domain.advisor.ConsentCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

interface AiConsentRepository {
    val state: Flow<AiConsentState>
    suspend fun current(): AiConsentState
    suspend fun grant(recipient: String, categories: Set<ConsentCategory>)
    suspend fun setCategory(category: ConsentCategory, enabled: Boolean)
    suspend fun withdraw()
}

private val Context.consentDataStore: DataStore<Preferences> by preferencesDataStore(
    name = BuildConfig.CONSENT_PREFS_NAME,
)

/**
 * 持续授权存储（DataStore）。
 *
 * 默认未授权；Gate 3A 用户升级后没有这个文件，因此必须重新同意。
 */
class DataStoreAiConsentRepository(
    context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) : AiConsentRepository {

    private val appContext = context.applicationContext

    private val grantedKey = intPreferencesKey("granted")
    private val textVersionKey = intPreferencesKey("text_version")
    private val categoriesVersionKey = intPreferencesKey("categories_version")
    private val grantedAtKey = longPreferencesKey("granted_at")
    private val recipientKey = stringPreferencesKey("recipient")
    private val categoriesKey = stringPreferencesKey("categories")
    private val epochKey = longPreferencesKey("epoch")

    override val state: Flow<AiConsentState> = appContext.consentDataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs ->
            AiConsentState(
                granted = (prefs[grantedKey] ?: 0) == 1,
                textVersion = prefs[textVersionKey] ?: 0,
                categoriesVersion = prefs[categoriesVersionKey] ?: 0,
                grantedAt = prefs[grantedAtKey],
                recipient = prefs[recipientKey].orEmpty(),
                categories = parseCategories(prefs[categoriesKey]),
                epoch = prefs[epochKey] ?: 0L,
            )
        }

    override suspend fun current(): AiConsentState = state.first()

    override suspend fun grant(recipient: String, categories: Set<ConsentCategory>) {
        appContext.consentDataStore.edit { prefs ->
            val epoch = (prefs[epochKey] ?: 0L) + 1
            prefs[grantedKey] = 1
            prefs[textVersionKey] = AiConsentRules.TEXT_VERSION
            prefs[categoriesVersionKey] = AiConsentRules.CATEGORIES_VERSION
            prefs[grantedAtKey] = now()
            prefs[recipientKey] = AiConsentRules.normalizeRecipient(recipient)
            prefs[categoriesKey] = encodeCategories(categories)
            prefs[epochKey] = epoch
        }
    }

    override suspend fun setCategory(category: ConsentCategory, enabled: Boolean) {
        appContext.consentDataStore.edit { prefs ->
            val granted = (prefs[grantedKey] ?: 0) == 1
            if (!granted) return@edit
            val current = parseCategories(prefs[categoriesKey]).toMutableSet()
            if (enabled) current.add(category) else current.remove(category)
            prefs[categoriesKey] = encodeCategories(current)
            prefs[epochKey] = (prefs[epochKey] ?: 0L) + 1
        }
    }

    override suspend fun withdraw() {
        appContext.consentDataStore.edit { prefs ->
            prefs[grantedKey] = 0
            prefs[textVersionKey] = 0
            prefs[categoriesVersionKey] = 0
            prefs[grantedAtKey] = 0L
            prefs[categoriesKey] = ""
            // recipient 保留用于展示「上次授权给谁」，但 granted=false 不会再发送
            prefs[epochKey] = (prefs[epochKey] ?: 0L) + 1
        }
    }

    private fun parseCategories(raw: String?): Set<ConsentCategory> =
        raw.orEmpty()
            .split(",")
            .mapNotNull { name -> ConsentCategory.entries.firstOrNull { it.name == name } }
            .toSet()

    private fun encodeCategories(categories: Set<ConsentCategory>): String =
        categories.sortedBy { it.ordinal }.joinToString(",") { it.name }
}
