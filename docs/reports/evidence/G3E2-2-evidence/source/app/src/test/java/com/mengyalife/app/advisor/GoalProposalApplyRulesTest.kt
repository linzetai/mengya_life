package com.mengyalife.app.advisor

import com.mengyalife.app.data.advisor.GoalProposalApplyRules
import com.mengyalife.app.domain.advisor.GoalProposalDraft
import com.mengyalife.app.domain.advisor.GoalProposalNode
import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalRef
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalProposalApplyRulesTest {

    private fun node(
        id: String,
        level: GoalLevel,
        parentTemp: String? = null,
        parentGoal: Long? = null,
        title: String = id,
        start: String? = null,
        due: String? = null,
    ) = GoalProposalNode(
        tempId = id,
        level = level,
        parentTempId = parentTemp,
        parentGoalId = parentGoal,
        title = title,
        description = null,
        domain = GoalDomain.HEALTH,
        startDate = start,
        dueDate = due,
        basis = "basis",
    )

    private fun draft(vararg nodes: GoalProposalNode) = GoalProposalDraft(
        schemaVersion = 1,
        summary = "s",
        assumptions = emptyList(),
        risks = emptyList(),
        nodes = nodes.toList(),
    )

    @Test
    fun `完整三级新建合法`() {
        val d = draft(
            node("g", GoalLevel.LONG_TERM),
            node("s", GoalLevel.STAGE, parentTemp = "g"),
            node("a", GoalLevel.ACTION, parentTemp = "s"),
        )
        assertNull(
            GoalProposalApplyRules.validate(d, setOf("g", "s", "a"), d.nodes.associateBy { it.tempId }, emptyMap()),
        )
    }

    @Test
    fun `只选子节点而父节点未选被拒绝`() {
        val d = draft(
            node("g", GoalLevel.LONG_TERM),
            node("s", GoalLevel.STAGE, parentTemp = "g"),
        )
        val error = GoalProposalApplyRules.validate(d, setOf("s"), d.nodes.associateBy { it.tempId }, emptyMap())
        assertTrue(error.orEmpty().contains("父节点未选择"))
    }

    @Test
    fun `已有父目标在审核期间被删除被拒绝`() {
        val d = draft(node("s", GoalLevel.STAGE, parentGoal = 99))
        val error = GoalProposalApplyRules.validate(d, setOf("s"), d.nodes.associateBy { it.tempId }, emptyMap())
        assertTrue(error.orEmpty().contains("不存在"))
    }

    @Test
    fun `已有父目标层级不匹配被拒绝`() {
        val d = draft(node("a", GoalLevel.ACTION, parentGoal = 1))
        val error = GoalProposalApplyRules.validate(
            d, setOf("a"), d.nodes.associateBy { it.tempId },
            mapOf(1L to GoalRef(1, GoalLevel.LONG_TERM, null)),
        )
        assertTrue(error.orEmpty().contains("层级不合法"))
    }

    @Test
    fun `日期非法或倒置被拒绝`() {
        val bad = draft(node("g", GoalLevel.LONG_TERM, start = "2026-13-40"))
        assertTrue(
            GoalProposalApplyRules.validate(bad, setOf("g"), bad.nodes.associateBy { it.tempId }, emptyMap())
                .orEmpty().contains("日期不合法"),
        )
        val reversed = draft(node("g", GoalLevel.LONG_TERM, start = "2026-12-01", due = "2026-11-01"))
        assertTrue(
            GoalProposalApplyRules.validate(reversed, setOf("g"), reversed.nodes.associateBy { it.tempId }, emptyMap())
                .orEmpty().contains("早于"),
        )
    }

    @Test
    fun `长期目标不能有父级`() {
        val d = draft(node("g", GoalLevel.LONG_TERM, parentGoal = 1))
        assertTrue(
            GoalProposalApplyRules.validate(d, setOf("g"), d.nodes.associateBy { it.tempId }, emptyMap())
                .orEmpty().contains("不能有父级"),
        )
    }
}
