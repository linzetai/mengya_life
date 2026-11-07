package com.mengyalife.app.ui.components

import com.mengyalife.app.domain.goals.GoalDomain

/** 领域 → 统一线性图标的稳定映射；不再使用汉字占位图标。 */
fun goalDomainIcon(domain: GoalDomain): MorrowIconKind = when (domain) {
    GoalDomain.HEALTH -> MorrowIconKind.HEART
    GoalDomain.CAREER -> MorrowIconKind.BRIEFCASE
    GoalDomain.WEALTH -> MorrowIconKind.WALLET
    GoalDomain.LEARNING -> MorrowIconKind.BOOK
    GoalDomain.LIFE -> MorrowIconKind.LEAF
}
