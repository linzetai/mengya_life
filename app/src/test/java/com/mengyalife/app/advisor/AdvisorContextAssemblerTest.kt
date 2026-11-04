package com.mengyalife.app.advisor

import com.mengyalife.app.domain.advisor.AdvisorContextAssembler
import com.mengyalife.app.domain.advisor.AdvisorContextSnapshot
import com.mengyalife.app.domain.advisor.AdvisorMessage
import com.mengyalife.app.domain.advisor.AdvisorMessageStatus
import com.mengyalife.app.domain.advisor.AdvisorRole
import com.mengyalife.app.domain.advisor.AdvisorSourceType
import com.mengyalife.app.domain.advisor.AiConsentRules
import com.mengyalife.app.domain.advisor.AiConsentState
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.advisor.ContextCheckinRecord
import com.mengyalife.app.domain.advisor.ContextGoalRecord
import com.mengyalife.app.domain.advisor.ContextJournalRecord
import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorContextAssemblerTest {

    private val assembler = AdvisorContextAssembler()
    private val recipient = "https://api.example.com/v1"

    private fun consent(
        categories: Set<ConsentCategory> = AiConsentRules.allCategories,
        epoch: Long = 3,
    ) = AiConsentState(
        granted = true,
        textVersion = AiConsentRules.TEXT_VERSION,
        categoriesVersion = AiConsentRules.CATEGORIES_VERSION,
        grantedAt = 1L,
        recipient = AiConsentRules.normalizeRecipient(recipient),
        categories = categories,
        epoch = epoch,
    )

    private val snapshot = AdvisorContextSnapshot(
        goals = listOf(
            ContextGoalRecord(1, "健康", "长期运动", GoalLevel.LONG_TERM, null, GoalDomain.HEALTH, GoalStatus.IN_PROGRESS, 1),
            ContextGoalRecord(2, "每周三次", null, GoalLevel.STAGE, 1, GoalDomain.HEALTH, GoalStatus.IN_PROGRESS, 2),
            ContextGoalRecord(3, "晨跑", null, GoalLevel.ACTION, 2, GoalDomain.HEALTH, GoalStatus.NOT_STARTED, 3),
        ),
        checkins = listOf(ContextCheckinRecord(10, 1, 1_000, "完成三次训练", 3.0, "次/周")),
        journals = listOf(
            ContextJournalRecord(20, "睡眠", "最近睡眠不好，开始早睡", 1_000),
            ContextJournalRecord(21, "旅行", "周末去了山里", 2_000),
        ),
        history = listOf(
            AdvisorMessage("h1", AdvisorRole.USER, "旧问题", 1, AdvisorMessageStatus.SENT, contextEpoch = 3),
            AdvisorMessage("h2", AdvisorRole.ASSISTANT, "旧回答", 2, AdvisorMessageStatus.SENT, contextEpoch = 3),
            AdvisorMessage("h3", AdvisorRole.ASSISTANT, "旧 epoch 回答", 3, AdvisorMessageStatus.SENT, contextEpoch = 2),
        ),
    )

    @Test
    fun `相关日记会被选中，无关日记不会`() {
        val result = assembler.assemble("最近睡眠不好，目标怎么调整", consent(), snapshot)
        val text = result.systemContext.orEmpty()
        assertTrue(text.contains("睡眠不好"))
        assertFalse(text.contains("周末去了山里"))
        assertTrue(result.sources.any { it.type == AdvisorSourceType.JOURNAL && it.recordId == "20" })
    }

    @Test
    fun `普通问题不附加无关日记`() {
        val result = assembler.assemble("hello", consent(), snapshot)
        val text = result.systemContext.orEmpty()
        assertFalse(text.contains("睡眠"))
        assertFalse(text.contains("旅行"))
        assertTrue(result.sources.none { it.type == AdvisorSourceType.JOURNAL })
    }

    @Test
    fun `关闭日记类别后完全不读取日记`() {
        val result = assembler.assemble(
            "最近睡眠不好",
            consent(categories = setOf(ConsentCategory.GOALS, ConsentCategory.PROGRESS, ConsentCategory.CONVERSATION)),
            snapshot,
        )
        assertFalse(result.systemContext.orEmpty().contains("睡眠"))
        assertTrue(result.sources.none { it.type == AdvisorSourceType.JOURNAL })
    }

    @Test
    fun `目标与进展按授权加入并记录来源`() {
        val result = assembler.assemble("健康目标下一步怎么做", consent(), snapshot)
        val text = result.systemContext.orEmpty()
        assertTrue(text.contains("长期运动"))
        assertTrue(text.contains("每周三次"))
        assertTrue(text.contains("完成三次训练"))
        assertTrue(result.sources.any { it.type == AdvisorSourceType.GOAL && it.recordId == "1" })
        assertTrue(result.sources.any { it.type == AdvisorSourceType.PROGRESS && it.recordId == "10" })
    }

    @Test
    fun `关闭目标后不附带目标与进展`() {
        val result = assembler.assemble(
            "健康目标下一步怎么做",
            consent(categories = setOf(ConsentCategory.JOURNAL, ConsentCategory.CONVERSATION)),
            snapshot,
        )
        assertFalse(result.systemContext.orEmpty().contains("长期运动"))
        assertTrue(result.sources.none { it.type == AdvisorSourceType.GOAL })
        assertTrue(result.sources.none { it.type == AdvisorSourceType.PROGRESS })
    }

    @Test
    fun `历史只使用当前 epoch 且受 CONVERSATION 开关控制`() {
        val with = assembler.assemble("健康", consent(), snapshot)
        assertEquals(listOf("h1", "h2"), with.history.map { it.id })

        val without = assembler.assemble(
            "健康",
            consent(categories = AiConsentRules.allCategories - ConsentCategory.CONVERSATION),
            snapshot,
        )
        assertTrue(without.history.isEmpty())
    }

    @Test
    fun `字符预算截断且标记 dropped`() {
        val huge = snapshot.copy(
            journals = listOf(ContextJournalRecord(99, "睡眠", "睡眠".repeat(5_000), 1_000)),
        )
        val result = assembler.assemble("睡眠", consent(), huge)
        assertTrue((result.systemContext?.length ?: 0) <= 3_600)
        assertTrue(result.stats.droppedByBudget >= 0)
    }

    @Test
    fun `未授权时返回空上下文`() {
        val result = assembler.assemble("健康 睡眠", AiConsentState(), snapshot)
        assertNull(result.systemContext)
        assertTrue(result.sources.isEmpty())
        assertTrue(result.history.isEmpty())
    }
}
