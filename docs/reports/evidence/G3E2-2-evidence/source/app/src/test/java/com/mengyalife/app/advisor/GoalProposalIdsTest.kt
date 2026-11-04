package com.mengyalife.app.advisor

import com.mengyalife.app.domain.advisor.GoalProposalIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalProposalIdsTest {

    @Test
    fun `tempId 格式严格限制`() {
        assertTrue(GoalProposalIds.isValidTempId("g1"))
        assertTrue(GoalProposalIds.isValidTempId("stage_1-a"))
        assertFalse(GoalProposalIds.isValidTempId(""))
        assertFalse(GoalProposalIds.isValidTempId("1g"))
        assertFalse(GoalProposalIds.isValidTempId("a/b"))
        assertFalse(GoalProposalIds.isValidTempId("a\"b"))
        assertFalse(GoalProposalIds.isValidTempId("g".repeat(49)))
    }

    @Test
    fun `映射往返不丢失 appliedGoalIds`() {
        val mapping = mapOf("g1" to 22L, "s1" to 23L, "a\\\"1" to 24L)
        // 非法 tempId 应被拒绝，不静默写出坏 JSON
        val bad = runCatching { GoalProposalIds.encodeMapping(mapping) }
        assertTrue(bad.isFailure)

        val good = mapOf("g1" to 22L, "s_1" to 23L, "a-1" to 24L)
        val encoded = GoalProposalIds.encodeMapping(good)
        assertEquals(good, GoalProposalIds.decodeMapping(encoded))
        assertEquals(setOf(22L, 23L, 24L), GoalProposalIds.decodeMapping(encoded).values.toSet())
    }

    @Test
    fun `selected 往返且过滤非法项`() {
        val selected = setOf("g1", "s1", "a1")
        val encoded = GoalProposalIds.encodeSelected(selected)
        assertEquals(selected, GoalProposalIds.decodeSelected(encoded))
        assertEquals(setOf("ok"), GoalProposalIds.decodeSelected("[\"1bad\",\"ok\"]"))
    }

    @Test
    fun `旧错误数组格式不会伪造映射`() {
        assertTrue(GoalProposalIds.decodeMapping("[22,23]").isEmpty())
        assertTrue(GoalProposalIds.decodeMapping(null).isEmpty())
    }
}
