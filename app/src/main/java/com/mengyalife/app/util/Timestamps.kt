package com.mengyalife.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 时间戳展示工具。
 *
 * 数据库里存的是 **Epoch 毫秒（UTC）**，这里负责按**设备当前时区**渲染，
 * 因此同一份数据在不同时区下显示不同本地时间，这是正确行为。
 */
object Timestamps {

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.getDefault())
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.getDefault())
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    fun formatDateTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dateTimeFormatter.withZone(zone).format(Instant.ofEpochMilli(epochMillis))

    fun formatDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dateFormatter.withZone(zone).format(Instant.ofEpochMilli(epochMillis))

    fun formatTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        timeFormatter.withZone(zone).format(Instant.ofEpochMilli(epochMillis))
}
