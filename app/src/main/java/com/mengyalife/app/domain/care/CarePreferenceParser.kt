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

        // 反问/假设/引用不产生不可逆偏好。
        if (lower.contains("吗") || lower.contains("？") || lower.contains("?") ||
            lower.contains("你觉得") || lower.contains("合不合理") || lower.contains("是否")) {
            return null
        }
        val endOfWeek = endOfWeek(now, zone)
        val topicWord = extractTopicWord(lower)
        val matched = matchTopic(topicWord, plans, goals)

        // 撤销：恢复某主题提醒。
        if ((lower.contains("恢复") || lower.contains("撤销") || lower.contains("取消")) &&
            (lower.contains("提醒") || lower.contains("关心"))
        ) {
            val revokeWord = lower.substringAfter("恢复", "").trim('，', '。', '！', '!', '吧', '了', '呀', '啊', ' ')
                .removeSuffix("提醒").removeSuffix("关心").trim()
            val revokeMatch = matchTopic(revokeWord.takeIf { it.length in 1..12 }, plans, goals)
            val label = matched?.second ?: revokeMatch?.second ?: topicWord ?: revokeWord.takeIf { it.length in 1..12 }
            if (label == null) return null
            return CarePreferenceInstruction(
                kind = "REVOKE", value = "RESUME",
                topicKey = matched?.first ?: revokeMatch?.first ?: "topic:$label", topicLabel = label,
                sourceExcerpt = clean,
                reply = "好，恢复「$label」的主动关心；之后会重新参与正常判断。",
            )
        }

        // 正向反馈只对明确评价生效。
        if (lower.contains("有用") && (lower.contains("提醒") || lower.contains("关心"))) {
            return CarePreferenceInstruction(
                kind = "FEEDBACK", value = "WELCOME", topicKey = null, topicLabel = null,
                sourceExcerpt = clean, reply = "记下了，这次关心对你有用。",
            )
        }

        // 永久关闭：必须带明确主题；未知主题要求澄清。
        if (lower.contains("以后别") || lower.contains("不再提醒") || lower.contains("别再提醒")) {
            val label = matched?.second ?: topicWord
            if (label.isNullOrBlank() || label == "这个话题") {
                return CarePreferenceInstruction(
                    kind = "CLARIFY", value = "CLARIFY", topicKey = null, topicLabel = null,
                    sourceExcerpt = clean, reply = "你想关闭哪个话题的提醒？可以说具体一点，比如“简历”或“读书”。",
                )
            }
            return CarePreferenceInstruction(
                kind = "TOPIC", value = "SILENCED",
                topicKey = matched?.first ?: "topic:$label", topicLabel = label,
                sourceExcerpt = clean,
                reply = "好，以后不再主动提「$label」。可在设置 → 关心偏好里恢复。",
            )
        }

        // 时间偏好：否定句不得写成“喜欢”。
        val timeMention = lower.contains("周日") && (lower.contains("晚上") || lower.contains("复盘") || lower.contains("回顾"))
        if (timeMention) {
            if (lower.contains("不喜欢") || lower.contains("不要") || lower.contains("别在") || lower.contains("不想")) {
                return null
            }
            return CarePreferenceInstruction(
                kind = "TIME", value = "PREFER_WINDOW", topicKey = null, topicLabel = "周回顾",
                windowNote = "SUN 20:00", expiresAt = null, sourceExcerpt = clean,
                reply = "记住了：你更喜欢周日晚上回顾。之后我会尽量在这个时段找机会，但不承诺精确 20:00。",
            )
        }

        // 忙碌 / 暂停：有具体主题则只作用于该主题。
        val busy = lower.contains("忙") && (lower.contains("这周") || lower.contains("最近") || lower.contains("暂时") || lower.contains("先别"))
        val pause = (lower.contains("先别") || lower.contains("暂停")) && (lower.contains("催") || lower.contains("提醒"))
        if (busy || pause) {
            val label = matched?.second ?: topicWord
            if (label == null) {
                if (busy) return CarePreferenceInstruction(
                    kind = "BUSY", value = "BUSY", topicKey = null, topicLabel = null,
                    expiresAt = endOfWeek, sourceExcerpt = clean,
                    reply = "好，这周先少打扰你，有重要的事我才开口。",
                )
                return CarePreferenceInstruction(
                    kind = "CLARIFY", value = "CLARIFY", topicKey = null, topicLabel = null,
                    sourceExcerpt = clean,
                    reply = "你想先暂停哪个话题？可以说具体一点，我再记下来。",
                )
            }
            return CarePreferenceInstruction(
                kind = "BUSY", value = if (busy) "BUSY" else "PAUSED",
                topicKey = matched?.first ?: "topic:$label", topicLabel = label,
                expiresAt = endOfWeek, sourceExcerpt = clean,
                reply = if (busy) "好，这周先少提「$label」，有重要的事我才开口。"
                else "好，这周先不主动提「$label」，周日之后自动恢复。",
            )
        }
        return null
    }

    private fun extractTopicWord(text: String): String? {
        val markers = listOf("别提醒我", "提醒我", "别提醒", "不再提醒", "别再提醒", "先别催我", "先别催", "别催我", "别催", "暂停")
        markers.forEach { marker ->
            val i = text.indexOf(marker)
            if (i >= 0) {
                val raw = text.substring(i + marker.length)
                    .trim('，', '。', '！', '!', '吧', '了', '呀', '啊', ' ', '的', '我')
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
