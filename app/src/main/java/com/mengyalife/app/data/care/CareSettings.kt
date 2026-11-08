package com.mengyalife.app.data.care

import android.content.Context
import com.mengyalife.app.domain.care.CarePolicy

/** 用户可控的主动关心设置（本地 SharedPreferences，不含敏感正文）。 */
class CareSettings(context: Context) {
    private val prefs = context.getSharedPreferences("mengya_care", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean("notifications", false)
        set(value) = prefs.edit().putBoolean("notifications", value).apply()

    /** CONSERVATIVE / NORMAL */
    var level: String
        get() = prefs.getString("level", "NORMAL") ?: "NORMAL"
        set(value) = prefs.edit().putString("level", value).apply()

    var dndStartHour: Int
        get() = prefs.getInt("dnd_start", 22)
        set(value) = prefs.edit().putInt("dnd_start", value).apply()

    var dndEndHour: Int
        get() = prefs.getInt("dnd_end", 8)
        set(value) = prefs.edit().putInt("dnd_end", value).apply()

    var lockScreenDetail: Boolean
        get() = prefs.getBoolean("lock_detail", false)
        set(value) = prefs.edit().putBoolean("lock_detail", value).apply()

    /** QA 专用可控时钟（epoch millis）；生产构建忽略。 */
    var testNow: Long
        get() = prefs.getLong("test_now", 0L)
        set(value) = prefs.edit().putLong("test_now", value).apply()

    /** QA 专用日发送上限覆盖；-1 表示不覆盖。 */
    var qaDailySendLimit: Int
        get() = prefs.getInt("qa_daily_send_limit", -1)
        set(value) = prefs.edit().putInt("qa_daily_send_limit", value).apply()

    fun policyPrefs(): CarePolicy.Prefs = CarePolicy.Prefs(
        enabled = enabled,
        dndStartHour = dndStartHour,
        dndEndHour = dndEndHour,
        dailySendLimit = qaDailySendLimit.takeIf { it >= 0 }
            ?: if (level == "CONSERVATIVE") 1 else 1,
        topicGapMillis = if (level == "CONSERVATIVE") 7L * 24 * 3600_000 else 72L * 3600_000,
        dailyDecisionBudget = 5,
    )
}
