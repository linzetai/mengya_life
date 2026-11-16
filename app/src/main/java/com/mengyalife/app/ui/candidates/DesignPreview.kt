package com.mengyalife.app.ui.candidates

import android.content.Context
import android.content.Intent
import com.mengyalife.app.BuildConfig
import com.mengyalife.app.MengyaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class UiDesignCandidate { NONE, A_FOCUS, B_COMPANION }

/** QA-only layout switch. Never changes production navigation, data, authorization or model. */
object DesignPreview {
    private val mutableMode = MutableStateFlow(UiDesignCandidate.NONE)
    val mode = mutableMode.asStateFlow()
    private val mutablePreparation = MutableStateFlow<String?>(null)
    val preparation = mutablePreparation.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun handleIntent(context: Context, intent: Intent?) {
        val isIsolatedReview = BuildConfig.QA_BUILD &&
            (BuildConfig.APPLICATION_ID.endsWith(".qa0254") || BuildConfig.APPLICATION_ID.endsWith(".qa0254.empty"))
        if (!isIsolatedReview) return
        val isEmptyVariant = BuildConfig.APPLICATION_ID.endsWith(".qa0254.empty")
        val prefs = context.getSharedPreferences(BuildConfig.PREFS_NAME, Context.MODE_PRIVATE)
        val raw = intent?.getStringExtra("qa_ui_candidate")
            ?: prefs.getString("ui_design_candidate", if (isEmptyVariant) "none" else "a")
        mutableMode.value = when (raw?.lowercase()) {
            "a", "focus" -> UiDesignCandidate.A_FOCUS
            "b", "companion" -> UiDesignCandidate.B_COMPANION
            else -> UiDesignCandidate.NONE
        }
        if (intent?.hasExtra("qa_ui_candidate") == true) {
            prefs.edit().putString("ui_design_candidate", raw).apply()
        }
        intent?.getStringExtra("qa_ui_theme")?.let { requested ->
            scope.launch {
                val container = (context.applicationContext as MengyaApplication).container
                if (container.restoreCoordinator.awaitReady()) {
                    val theme = when (requested.lowercase()) {
                        "dark" -> com.mengyalife.app.ui.theme.ThemeMode.DARK
                        "light" -> com.mengyalife.app.ui.theme.ThemeMode.LIGHT
                        else -> com.mengyalife.app.ui.theme.ThemeMode.SYSTEM
                    }
                    container.themePreferences.setThemeMode(theme)
                }
            }
        }
        if (intent?.getBooleanExtra("qa_ui_seed", false) == true) {
            mutablePreparation.value = "正在准备隔离的 QA 数据"
            scope.launch {
                runCatching {
                    val app = context.applicationContext as MengyaApplication
                    val container = app.container
                    check(container.restoreCoordinator.awaitReady())
                    // Explicit synthetic authorization fixture in the separately installed design QA app.
                    val endpoint = "https://qa-ui.invalid/v1"
                    val config = container.advisorConfigRepository.state.first()
                    if (!config.configured || config.endpoint != endpoint || config.model != "qa-ui-offline") {
                        check(container.advisorConfigRepository.save(endpoint, "qa-ui-offline", "qa-placeholder-key").ok)
                    }
                    val consent = container.aiConsentRepository.current()
                    val categories = com.mengyalife.app.domain.advisor.AiConsentRules.allCategories
                    if (!consent.granted || !consent.isValidFor(endpoint) || consent.categories != categories) {
                        container.aiConsentRepository.grant(endpoint, categories)
                    }
                    if (!config.useFakeEngine) container.advisorConfigRepository.setUseFakeEngine(true)
                    // Keep A/B snapshots identical; Care gets its own explicit acceptance scenario later.
                    com.mengyalife.app.data.care.CareSettings(context).apply {
                        enabled = false
                        notificationsEnabled = false
                    }
                    val fixture = com.mengyalife.app.data.qa.QaUiDesignFixtureSeeder.seed(container)
                    android.util.Log.i("UiDesignQA", "fixture ready; goals=${listOf(fixture.rootGoalId, fixture.stageGoalId, fixture.actionGoalId, fixture.independentGoalId)} plans=${listOf(fixture.linkedPlanId, fixture.independentPlanId)} checkins=${fixture.checkinIds} messages=${fixture.messageIds.size} report=${fixture.reportId} version=${fixture.dataVersion}")
                }.onSuccess { mutablePreparation.value = null }
                    .onFailure {
                        mutablePreparation.value = "QA 数据准备失败，未开始视觉验收"
                        android.util.Log.e("UiDesignQA", "fixture preparation failed", it)
                    }
            }
        }
    }
}
