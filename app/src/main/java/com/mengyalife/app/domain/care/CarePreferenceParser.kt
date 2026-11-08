package com.mengyalife.app.domain.care

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class CarePreferenceInstruction(
    val kind: String,
    val value: String,
    val topicKey: String?,
    val topicLabel: String?,
    val windowNote: String? = null,
    val expiresAt: Long? = null,
    val sourceExcerpt: String,
    val reply: String,
)

/** 只识别明确表达；不做情绪/人格推断。 */
object CarePreferenceParser {
    fun parse(
        text: String,
        plans: List<Pair<Long, String>>,
        goals: List<Pair<Long, String>>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): CarePreferenceInstruction? {
        val clean = text.trim()
        val lower = clean.replace(" ", "")
        val topicWord = extractTopicWord(lower)
        val matched = matchTopic(topicWord, plans, goals)
        val endOfWeek = endOfWeek(now, zone)

        if (lower.contains("有用") && (lower.contains("提醒") || lower.contains("关心"))) {
            return CarePreferenceInstruction(
                kind = "FEEDBACK", value = "WELCOME", topicKey = null, topicLabel = null,
                sourceExcerpt = clean,
                reply = "记下了，这次关心对你有用。",
            )
        }
        if ((lower.contains("以后别") || lower.contains("不再提醒") || lower.contains("别再提醒")) &&
            (lower.contains("提醒") || lower.contains("提"))
        ) {
            val label = matched?.second ?: topicWord ?: "这个话题"
            return CarePreferenceInstruction(
                kind = "TOPIC", value = "SILENCED",
                topicKey = matched?.first ?: "topic:$label", topicLabel = label,
                sourceExcerpt = clean,
                reply = "好，以后不再主动提「$label」。可在设置 → 关心偏好里恢复。",
            )
        }
        if (lower.contains("忙") && (lower.contains("这周") || lower.contains("最近") || lower.contains("暂时") || lower.contains("先别"))) {
            val label = matched?.second ?: topicWord
            return CarePreferenceInstruction(
                kind = "BUSY", value = "BUSY",
                topicKey = matched?.first ?: label?.let { "topic:$it" }, topicLabel = label,
                expiresAt = endOfWeek, sourceExcerpt = clean,
                reply = if (label != null) "好，这周先少提「$label」，有重要的事我才开口。"
                else "好，这周先少打扰你，有重要的事我才开口。",
            )
        }
        if ((lower.contains("先别") || lower.contains("暂停")) && (lower.contains("催") || lower.contains("提醒"))) {
            val label = matched?.second ?: topicWord
            if (label != null) return CarePreferenceInstruction(
                kind = "TOPIC", value = "PAUSED",
                topicKey = matched?.first ?: "topic:$label", topicLabel = label,
                expiresAt = endOfWeek, sourceExcerpt = clean,
                reply = "好，这周先不主动提「$label」，周日之后自动恢复。",
            )
        }
        if (lower.contains("周日") && (lower.contains("晚上") || lower.contains("回顾") || lower.contains("复盘") || lower.contains("周"))) {
            return CarePreferenceInstruction(
                kind = "TIME", value = "PREFER_WINDOW", topicKey = null, topicLabel = "周回顾",
                windowNote = "SUN 20:00", expiresAt = null, sourceExcerpt = clean,
                reply = "记住了：你更喜欢周日晚上回顾。之后我会按这个时间找机会。",
            )
        }
        return null
    }

    private fun extractTopicWord(text: String): String? {
        val markers = listOf("别提醒我", "不再提醒", "别再提醒", "先别催我", "先别催", "别催我", "暂停")
        markers.forEach { marker ->
            val i = text.indexOf(marker)
            if (i >= 0) {
                val raw = text.substring(i + marker.length)
                    .trim('，', '。', '！', '!', '吧', '了', '呀', '啊', ' ', '的')
                if (raw.length in 1..12) return raw
            }
        }
        return null
    }

    private fun matchTopic(word: String?, plans: List<Pair<Long, String>>, goals: List<Pair<Long, String>>): Pair<String, String>? {
        if (word.isNullOrBlank()) return null
        val plan = plans.sortedByDescending { it.second.length }
            .firstOrNull { it.second.contains(word) || word.contains(it.second) }
        if (plan != null) return "plan:${plan.first}" to plan.second
        val goal = goals.sortedByDescending { it.second.length }
            .firstOrNull { it.second.contains(word) || word.contains(it.second) }
        if (goal != null) return "goal:${goal.first}" to goal.second
        return null
    }

    fun endOfWeek(now: Long, zone: ZoneId): Long {
        val date = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
        val days = (7 - date.dayOfWeek.value) % 7
        return date.plusDays(days.toLong()).atTime(LocalTime.of(23, 59)).atZone(zone).toInstant().toEpochMilli()
    }
}
