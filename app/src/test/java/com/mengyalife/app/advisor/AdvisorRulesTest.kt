package com.mengyalife.app.advisor

import com.mengyalife.app.domain.advisor.AdvisorMessage
import com.mengyalife.app.domain.advisor.AdvisorMessageStatus
import com.mengyalife.app.domain.advisor.AdvisorRole
import com.mengyalife.app.domain.advisor.AdvisorRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorRulesTest {

    @Test
    fun `只接受 HTTPS 服务地址`() {
        assertNull(AdvisorRules.validateEndpoint("https://api.openai.com/v1"))
        assertNotNull(AdvisorRules.validateEndpoint("http://api.openai.com/v1"))
        assertNotNull(AdvisorRules.validateEndpoint("ftp://api.openai.com"))
        assertNotNull(AdvisorRules.validateEndpoint(""))
        assertNotNull(AdvisorRules.validateEndpoint("https://user:pass@api.example.com/v1"))
        assertNotNull(AdvisorRules.validateEndpoint("https://api.example.com/v1?x=1"))
    }

    @Test
    fun `服务地址规范成 chat completions`() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            AdvisorRules.chatCompletionsUrl("https://api.example.com/v1"),
        )
        assertEquals(
            "https://api.example.com/openai/v1/chat/completions",
            AdvisorRules.chatCompletionsUrl("https://api.example.com/openai/v1/"),
        )
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            AdvisorRules.chatCompletionsUrl("https://api.example.com/v1/chat/completions"),
        )
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            AdvisorRules.chatCompletionsUrl("https://api.example.com"),
        )
    }

    @Test
    fun `模型与 key 校验`() {
        assertNotNull(AdvisorRules.validateModel(" "))
        assertNull(AdvisorRules.validateModel("deepseek-chat"))
        assertNotNull(AdvisorRules.validateApiKey(""))
        assertNull(AdvisorRules.validateApiKey("sk-test"))
    }

    @Test
    fun `系统提示禁止直接写库且不生成人生评分`() {
        val prompt = AdvisorRules.systemPrompt()
        // 0.16：应用支持对话式记录进展，但模型本身仍不能直接改数据库。
        assertTrue(prompt.contains("不能直接改数据库"))
        assertTrue(prompt.contains("对话式记录进展"))
        assertTrue(prompt.contains("不要声称是你本人直接写入了数据"))
        assertTrue(prompt.contains("人生综合评分"))
        assertTrue(prompt.contains("中文"))
    }

    @Test
    fun `上下文预算只保留最近消息且当前问题在最后`() {
        val history = (1..30).map { index ->
            AdvisorMessage(
                id = index.toString(),
                role = if (index % 2 == 0) AdvisorRole.ASSISTANT else AdvisorRole.USER,
                content = "消息$index-" + "x".repeat(50),
                createdAt = index.toLong(),
                status = AdvisorMessageStatus.SENT,
            )
        }
        val messages = AdvisorRules.buildRequestMessages(history, "当前问题")
        assertEquals("system", messages.first().role)
        assertEquals("当前问题", messages.last().content)
        val historyInRequest = messages.drop(1).dropLast(1)
        assertTrue("历史条数应受限", historyInRequest.size <= AdvisorRules.MAX_HISTORY_MESSAGES)
        val total = messages.sumOf { it.content.length }
        assertTrue("总字符数应受限", total <= AdvisorRules.MAX_CONTEXT_CHARS + 500)
        // 最新的历史优先保留
        assertTrue(historyInRequest.last().content.startsWith("消息30"))
    }

    @Test
    fun `失败与取消消息不会进入上下文`() {
        val history = listOf(
            AdvisorMessage("1", AdvisorRole.USER, "正常问题", 1, AdvisorMessageStatus.SENT),
            AdvisorMessage("2", AdvisorRole.ASSISTANT, "", 2, AdvisorMessageStatus.FAILED),
            AdvisorMessage("3", AdvisorRole.ASSISTANT, "", 3, AdvisorMessageStatus.CANCELLED),
        )
        val messages = AdvisorRules.buildRequestMessages(history, "新问题")
        assertTrue(messages.none { it.content.isEmpty() })
        assertEquals(3, messages.size)
    }

    @Test
    fun `目标上下文在确认后才进入请求且不含日记标记`() {
        val context = sampleContext()
        val withContext = AdvisorRules.buildRequestMessages(
            history = emptyList(),
            currentInput = "帮我看下一步",
            goalContext = context,
        )
        assertEquals("system", withContext[0].role)
        assertEquals("system", withContext[1].role)
        assertTrue(withContext[1].content.contains("健康"))
        assertTrue(withContext[1].content.contains("最近进展"))
        assertTrue(withContext[1].content.contains("未包含日记正文"))

        val without = AdvisorRules.buildRequestMessages(emptyList(), "帮我看下一步")
        assertTrue(without.none { it.content.contains("健康") })
    }

    @Test
    fun `授权摘要列出范围并明确不发送日记`() {
        val lines = AdvisorRules.consentLines(sampleContext())
        val joined = lines.joinToString("\n")
        assertTrue(joined.contains("目标：健康"))
        assertTrue(joined.contains("阶段目标：1 个"))
        assertTrue(joined.contains("短期行动：1 个"))
        assertTrue(joined.contains("不发送：日记正文"))
    }

    @Test
    fun `输入过长被拒绝`() {
        assertNotNull(AdvisorRules.validateInput("字".repeat(AdvisorRules.MAX_INPUT_CHARS + 1)))
        assertNull(AdvisorRules.validateInput("普通问题"))
    }

    @Test
    fun `运行时配置的 toString 不泄露 key`() {
        val text = com.mengyalife.app.domain.advisor.AdvisorRuntimeConfig(
            endpoint = "https://api.example.com/v1",
            model = "m",
            apiKey = "sk-super-secret",
        ).toString()
        assertFalse(text.contains("sk-super-secret"))
        assertTrue(text.contains("***"))
    }
}
