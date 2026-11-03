package com.mengyalife.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
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

    /**
     * **只有日期、没有时刻**的字段（例如目标截止日期）统一按 UTC 渲染。
     *
     * Material3 的 DatePicker 返回的是「所选日期在 UTC 零点」的毫秒数；
     * 若再用本地时区格式化，在 UTC-x 时区会显示成前一天。
     * 因此日期类字段固定用 UTC，和 DatePicker 的语义保持一致。
     */
    fun formatDateUtc(epochMillis: Long): String =
        dateFormatter.withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(epochMillis))

    fun formatTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        timeFormatter.withZone(zone).format(Instant.ofEpochMilli(epochMillis))
}
