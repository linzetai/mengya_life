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
    fun `backup without endpoint keeps local state`() {
        val d = RestoreCredentialPolicy.decide("https://a.example.com/v1", "", true)
        assertFalse(d.deleteKey); assertFalse(d.resetConsent)
    }
}
