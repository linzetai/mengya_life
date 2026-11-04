package com.mengyalife.app.advisor

import com.mengyalife.app.domain.advisor.AdvisorContextEntry
import com.mengyalife.app.domain.advisor.AdvisorMessage
import com.mengyalife.app.domain.advisor.AdvisorMessageStatus
import com.mengyalife.app.domain.advisor.AdvisorRole
import com.mengyalife.app.domain.advisor.AdvisorRules
import com.mengyalife.app.domain.advisor.AdvisorSource
import com.mengyalife.app.domain.advisor.AdvisorSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorRequestBudgetTest {

    private fun entry(index: Int) = AdvisorContextEntry(
        text = "记录$index：" + "x".repeat(80),
        source = AdvisorSource(AdvisorSourceType.GOAL, index.toString(), "g$index"),
    )

    @Test
    fun `预算截断后来源只包含真正进入请求的记录`() {
        val entries = (1..8).map(::entry)
        val built = AdvisorRules.buildRequest(
            history = emptyList(),
            currentInput = "当前问题",
            contextEntries = entries,
            maxChars = 900,
        )
        assertEquals(entries.size, built.droppedContextEntries + built.usedSources.size)
        assertTrue(built.droppedContextEntries > 0)
        val contextText = built.messages.filter { it.role == "system" }.joinToString("") { it.content }
        built.usedSources.forEach { source ->
            assertTrue("source ${source.recordId} must be in request", contextText.contains("记录${source.recordId}"))
        }
        entries.filter { it.source !in built.usedSources }.forEach { dropped ->
            assertFalse(contextText.contains(dropped.text))
        }
    }

    @Test
    fun `system 当前问题 上下文和历史共同受预算约束`() {
        val history = (1..20).map {
            AdvisorMessage(
                id = "h$it",
                role = if (it % 2 == 0) AdvisorRole.ASSISTANT else AdvisorRole.USER,
                content = "历史$it " + "y".repeat(60),
                createdAt = it.toLong(),
                status = AdvisorMessageStatus.SENT,
            )
        }
        val built = AdvisorRules.buildRequest(
            history = history,
            currentInput = "问题" + "z".repeat(200),
            contextEntries = listOf(entry(1), entry(2)),
            maxChars = 1_200,
        )
        val total = built.messages.sumOf { it.content.length }
        assertTrue("total=$total", total <= 1_200)
        assertTrue(built.messages.first().role == "system")
        assertEquals("user", built.messages.last().role)
        assertTrue(built.messages.last().content.startsWith("问题"))
        assertTrue(built.historyMessages < history.size)
    }

    @Test
    fun `没有上下文时来源为空且仍保留当前问题`() {
        val built = AdvisorRules.buildRequest(
            history = emptyList(),
            currentInput = "你好",
            contextEntries = emptyList(),
            maxChars = 1_000,
        )
        assertTrue(built.usedSources.isEmpty())
        assertEquals("你好", built.messages.last().content)
    }
}
