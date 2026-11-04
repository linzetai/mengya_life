package com.mengyalife.app.advisor

import com.mengyalife.app.domain.advisor.AiConsentRules
import com.mengyalife.app.domain.advisor.AiConsentState
import com.mengyalife.app.domain.advisor.ConsentCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiConsentRulesTest {

    private val endpoint = "https://api.example.com/v1/"

    @Test
    fun `endpoint 规范化去掉末尾斜杠`() {
        assertEquals(
            "https://api.example.com/v1",
            AiConsentRules.normalizeRecipient(endpoint),
        )
    }

    @Test
    fun `未授权默认无效`() {
        assertFalse(AiConsentState().isValidFor(endpoint))
    }

    @Test
    fun `授权文案或类别版本变化后必须重新授权`() {
        val base = AiConsentState(
            granted = true,
            textVersion = AiConsentRules.TEXT_VERSION,
            categoriesVersion = AiConsentRules.CATEGORIES_VERSION,
            recipient = AiConsentRules.normalizeRecipient(endpoint),
            categories = AiConsentRules.allCategories,
            epoch = 1,
        )
        assertTrue(base.isValidFor(endpoint))
        assertFalse(base.copy(textVersion = 0).isValidFor(endpoint))
        assertFalse(base.copy(categoriesVersion = 0).isValidFor(endpoint))
        assertFalse(base.copy(recipient = "https://other.example.com").isValidFor(endpoint))
        assertFalse(base.copy(granted = false).isValidFor(endpoint))
    }

    @Test
    fun `四类类别默认全部允许且可独立关闭`() {
        assertEquals(4, AiConsentRules.allCategories.size)
        val base = AiConsentState(
            granted = true,
            textVersion = AiConsentRules.TEXT_VERSION,
            categoriesVersion = AiConsentRules.CATEGORIES_VERSION,
            recipient = AiConsentRules.normalizeRecipient(endpoint),
            categories = AiConsentRules.allCategories,
            epoch = 1,
        )
        assertTrue(base.enabled(ConsentCategory.JOURNAL))
        val off = base.copy(categories = base.categories - ConsentCategory.JOURNAL)
        assertFalse(off.enabled(ConsentCategory.JOURNAL))
        assertTrue(off.enabled(ConsentCategory.GOALS))
    }
}
