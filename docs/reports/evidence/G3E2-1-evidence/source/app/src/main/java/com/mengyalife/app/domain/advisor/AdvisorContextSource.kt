package com.mengyalife.app.domain.advisor

import kotlinx.coroutines.flow.Flow

/**
 * 顾问可读取的「目标上下文」边界。
 *
 * 实现方只允许返回用户明确选择的那一个目标的摘要；
 * 顾问层没有日记 DAO，也没有修改目标的能力。
 */
interface AdvisorContextSource {
    fun observeGoalChoices(): Flow<List<AdvisorGoalChoice>>
    suspend fun buildContext(goalId: Long): AdvisorGoalContext?
}
