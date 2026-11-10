package com.mengyalife.app.domain.care

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** 硬策略门：AI 只能被否决，不能绕过。 */
object CarePolicy {

    data class Prefs(
        val enabled: Boolean = true,
        val dndStartHour: Int = 22,
        val dndEndHour: Int = 8,
        val dailySendLimit: Int = 1,
        val topicGapMillis: Long = 72L * 3600_000,
        val dailyDecisionBudget: Int = 5,
    )

    sealed interface Result { data object Allow : Result; data class Deny(val reason: String) : Result }

    fun inDnd(now: Long, prefs: Prefs, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val hour = Instant.ofEpochMilli(now).atZone(zone).toLocalTime().hour
        return if (prefs.dndStartHour > prefs.dndEndHour) {
            hour >= prefs.dndStartHour || hour < prefs.dndEndHour
        } else {
            hour in prefs.dndStartHour until prefs.dndEndHour
        }
    }

    fun allowSend(
        now: Long,
        prefs: Prefs,
        granted: Boolean,
        candidate: CareCandidate,
        sentToday: Int,
        lastSentAt: Long?,
        lastTopicSentAt: Long?,
        silencedTopic: Boolean,
        decisionsToday: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Result {
        if (!prefs.enabled) return Result.Deny("主动关心已关闭")
        if (!granted) return Result.Deny("授权已撤销")
        if (silencedTopic) return Result.Deny("用户已要求不再提醒这个话题")
        if (candidate.expiresAt < now) return Result.Deny("机会已过期")
        if (now > candidate.windowEnd) return Result.Deny("不在合适时间窗口")
        if (inDnd(now, prefs, zone)) return Result.Deny("免打扰时段")
        if (sentToday >= prefs.dailySendLimit) return Result.Deny("今日主动关心已达上限")
        if (lastSentAt != null && now - lastSentAt < 24L * 3600_000) return Result.Deny("距上次打扰不足 24 小时")
        if (lastTopicSentAt != null && now - lastTopicSentAt < prefs.topicGapMillis) {
            return Result.Deny("同一话题间隔不足 72 小时")
        }
        if (decisionsToday >= prefs.dailyDecisionBudget) return Result.Deny("今日决策预算已用尽")
        return Result.Allow
    }
}
