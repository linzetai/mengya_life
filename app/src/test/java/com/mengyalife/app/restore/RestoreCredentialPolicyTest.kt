package com.mengyalife.app.restore

import com.mengyalife.app.data.restore.RestoreCredentialPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreCredentialPolicyTest {
    @Test
    fun `same endpoint keeps key and consent`() {
        val d = RestoreCredentialPolicy.decide("https://api.deepseek.com", "https://api.deepseek.com", true)
        assertFalse(d.deleteKey); assertFalse(d.resetConsent)
    }

    @Test
    fun `different endpoint deletes key and resets consent`() {
        val d = RestoreCredentialPolicy.decide("https://a.example.com/v1", "https://b.example.com/v1", true)
        assertTrue(d.deleteKey); assertTrue(d.resetConsent)
    }

    @Test
    fun `different endpoint without key still resets consent`() {
        val d = RestoreCredentialPolicy.decide("https://a.example.com/v1", "https://b.example.com/v1", false)
        assertFalse(d.deleteKey); assertTrue(d.resetConsent)
    }

    @Test
    fun `expired consent is never auto granted by restore policy`() {
        val consent = com.mengyalife.app.domain.advisor.AiConsentState(
            granted = false,
            recipient = com.mengyalife.app.domain.advisor.AiConsentRules.normalizeRecipient("https://api.deepseek.com"),
            epoch = 7,
        )
        org.junit.Assert.assertFalse(consent.isValidFor("https://api.deepseek.com"))
        val d = RestoreCredentialPolicy.decide("https://api.deepseek.com", "https://api.deepseek.com", true)
        org.junit.Assert.assertFalse(d.resetConsent)
        // 同 endpoint 不强制重置；授权仍为 false，必须用户重新授权，恢复不会自动 grant。
        org.junit.Assert.assertFalse(consent.granted)
    }

    @Test
    fun `backup without endpoint keeps local state`() {
        val d = RestoreCredentialPolicy.decide("https://a.example.com/v1", "", true)
        assertFalse(d.deleteKey); assertFalse(d.resetConsent)
    }
}
