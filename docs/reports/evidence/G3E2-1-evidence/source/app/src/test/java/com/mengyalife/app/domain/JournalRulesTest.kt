package com.mengyalife.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JournalRulesTest {

    @Test
    fun `空正文被拒绝`() {
        assertEquals(JournalRules.ERROR_BODY_REQUIRED, JournalRules.validateBody(""))
    }

    @Test
    fun `纯空白正文被拒绝`() {
        assertEquals(JournalRules.ERROR_BODY_REQUIRED, JournalRules.validateBody("   \n\t  "))
    }

    @Test
    fun `正常正文通过校验`() {
        assertNull(JournalRules.validateBody("今天把节奏调慢了一档。"))
    }

    @Test
    fun `超长正文被拒绝`() {
        val tooLong = "字".repeat(JournalRules.MAX_BODY_LENGTH + 1)
        assertEquals(JournalRules.ERROR_BODY_TOO_LONG, JournalRules.validateBody(tooLong))
    }

    @Test
    fun `摘要压缩换行与多余空格`() {
        assertEquals("第一行 第二行", JournalRules.excerpt("第一行\n\n   第二行", 40))
    }

    @Test
    fun `摘要按长度截断并追加省略号`() {
        val text = "字".repeat(80)
        val result = JournalRules.excerpt(text, 60)
        assertEquals(61, result.length)
        assertEquals("…", result.takeLast(1))
    }
}
