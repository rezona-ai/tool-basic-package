/** 文件大小、复数、日期的格式化，所有品牌共用 */
package com.appbase.core.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024
        i++
    }
    val pattern = if (value >= 100) "%.0f %s" else "%.1f %s"
    return String.format(Locale.US, pattern, value, units[i])
}

/** 1 item / 2 items */
fun plural(count: Int, word: String): String = if (count == 1) "1 $word" else "$count ${word}s"

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** 每次按当前时区格式化，系统时区变化后也正确 */
fun formatDate(millis: Long): String =
    dateFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
