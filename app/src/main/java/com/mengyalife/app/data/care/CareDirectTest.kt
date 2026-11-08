package com.mengyalife.app.data.care

import com.mengyalife.app.MengyaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** QA-only：在进程内存活的后台延迟执行一次真实 CareRunner，验证通知投递链路。 */
object CareDirectTest {
    fun runDelayed(app: MengyaApplication, delayMs: Long = 6_000) {
        CoroutineScope(Dispatchers.IO).launch {
            delay(delayMs)
            val settings = CareSettings(app)
            if (!settings.enabled || !settings.notificationsEnabled) return@launch
            val c = app.container
            val runner = CareRunner(
                careRepository = c.careRepository,
                planRepository = c.smallPlanRepository,
                dataRepository = c.advisorDataRepository,
                consentRepository = c.aiConsentRepository,
                configRepository = c.advisorConfigRepository,
                conversations = c.conversationRepository,
                engine = c.networkAdvisorEngine,
                settings = settings,
                preferences = c.carePreferenceRepository,
            )
            val card = runCatching { runner.runOnce() }.getOrNull() ?: return@launch
            CareNotification.post(app, card.opportunityId, card.text, settings.lockScreenDetail)
        }
    }
}
