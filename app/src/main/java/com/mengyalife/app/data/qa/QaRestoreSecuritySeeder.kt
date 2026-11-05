package com.mengyalife.app.data.qa

import com.mengyalife.app.di.AppContainer
import com.mengyalife.app.domain.advisor.AiConsentRules

/** QA-only：种下可被恢复删除的 API Key/consent/自动发送状态。 */
object QaRestoreSecuritySeeder {
    suspend fun seed(container: AppContainer) {
        val endpoint = "https://api.example.com/v1"
        container.advisorConfigRepository.save(endpoint, "qa-fake", "qa-restore-key-123456")
        container.aiConsentRepository.grant(endpoint, AiConsentRules.allCategories)
        container.weeklyReviewPreferences.setAiEnabled(true)
    }
}
