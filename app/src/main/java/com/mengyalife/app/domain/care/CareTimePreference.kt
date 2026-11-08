package com.mengyalife.app.domain.care

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** "SUN 20:00" 的严格编解码；非法值返回 null，不默认放行。 */
data class CareTimeWindow(val day: DayOfWeek, val hour: Int) {
    fun contains(now: Long, zone: ZoneId, slackHours: Int = 2): Boolean {
        val zoned = Instant.ofEpochMilli(now).atZone(zone)
        if (zoned.dayOfWeek != day) return false
        return kotlin.math.abs(zoned.hour - hour) <= slackHours
    }
}

object CareTimePreferenceCodec {
    fun decode(note: String?): CareTimeWindow? {
        val parts = note?.trim()?.split(" ") ?: return null
        if (parts.size != 2) return null
        val day = when (parts[0]) {
            "MON" -> DayOfWeek.MONDAY
            "TUE" -> DayOfWeek.TUESDAY
            "WED" -> DayOfWeek.WEDNESDAY
            "THU" -> DayOfWeek.THURSDAY
            "FRI" -> DayOfWeek.FRIDAY
            "SAT" -> DayOfWeek.SATURDAY
            "SUN" -> DayOfWeek.SUNDAY
            else -> return null
        }
        val time = parts[1].split(":")
        if (time.size != 2) return null
        val hour = time[0].toIntOrNull() ?: return null
        val minute = time[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return CareTimeWindow(day, hour)
    }
}
