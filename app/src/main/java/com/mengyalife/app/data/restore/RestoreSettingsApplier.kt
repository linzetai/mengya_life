package com.mengyalife.app.data.restore

import com.mengyalife.app.data.advisor.AdvisorConfigRepository
import com.mengyalife.app.data.preferences.ThemePreferences
import com.mengyalife.app.data.weekly.WeeklyReviewPreferences
import com.mengyalife.app.ui.theme.ThemeMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 只恢复非秘密设置；AI 自动发送强制关闭，Key/consent 不在此处。 */
class RestoreSettingsApplier(
    private val themePreferences: ThemePreferences,
    private val weeklyPreferences: WeeklyReviewPreferences,
    private val configRepository: AdvisorConfigRepository,
) {
    private val json = Json

    suspend fun apply(settingsJson: String?) {
        if (com.mengyalife.app.BuildConfig.QA_BUILD && com.mengyalife.app.data.restore.QaRestoreFaultInjector.failSettings) {
            throw RestoreException("QA_SETTINGS_FAIL", "QA injected settings failure")
        }
        if (settingsJson.isNullOrBlank()) return
        val root = runCatching { json.parseToJsonElement(settingsJson).jsonObject }
            .getOrElse { throw RestoreException("SETTINGS_INVALID", "恢复设置 JSON 无效") }
        val theme = root["themeMode"]?.jsonPrimitive?.contentOrNull
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.SYSTEM
        val weekly = root["weeklyReview"] as? JsonObject
        val ai = root["ai"] as? JsonObject
        themePreferences.setThemeMode(theme)
        weeklyPreferences.setEnabled(weekly?.get("enabled")?.jsonPrimitive?.content == "true")
        weeklyPreferences.setAiEnabled(false) // F/E: 绝不恢复旧自动发送许可
        val endpoint = ai?.get("endpoint")?.jsonPrimitive?.contentOrNull.orEmpty()
        val model = ai?.get("model")?.jsonPrimitive?.contentOrNull.orEmpty()
        if (endpoint.isNotBlank() && model.isNotBlank()) {
            configRepository.restoreEndpointAndModel(endpoint, model)
        }
    }
}
