package com.ledger.app.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Money {
    /** 1234 -> "¥12.34"；负数 -> "-¥12.34" */
    fun format(cents: Long): String {
        val abs = kotlin.math.abs(cents)
        val s = "¥%d.%02d".format(abs / 100, abs % 100)
        return if (cents < 0) "-$s" else s
    }

    /** 带正负号：+¥12.34 / -¥12.34 */
    fun formatSigned(cents: Long): String = if (cents > 0) "+" + format(cents) else format(cents)

    /** 简洁格式，用于图表坐标轴 */
    fun formatShort(cents: Long): String {
        val abs = kotlin.math.abs(cents)
        val sign = if (cents < 0) "-" else ""
        return when {
            abs >= 1_000_000L -> sign + "¥%.1f万".format(abs / 1_000_000.0)
            else -> sign + "¥%d".format(abs / 100)
        }
    }

    /** 用户输入 "12.3" -> 1230；非法返回 null */
    fun parseInput(s: String): Long? {
        val t = s.trim().replace("¥", "").replace("￥", "").replace(",", "")
        if (t.isEmpty()) return null
        val bd = t.toBigDecimalOrNull() ?: return null
        if (bd.signum() < 0 || bd.scale() > 2) return null
        return try {
            bd.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
        } catch (e: ArithmeticException) {
            null
        }
    }

    fun toInput(cents: Long): String = BigDecimal.valueOf(cents).movePointLeft(2).toPlainString()
}

object Dates {
    val zone: ZoneId get() = ZoneId.systemDefault()
    private val dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val date = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val shortDate = DateTimeFormatter.ofPattern("yy/MM/dd")
    private val monthDaySlash = DateTimeFormatter.ofPattern("MM/dd")
    private val monthDayTime = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    private val month = DateTimeFormatter.ofPattern("yyyy年M月")

    fun dateTime(ts: Long): String = Instant.ofEpochMilli(ts).atZone(zone).format(dateTime)
    fun date(ts: Long): String = Instant.ofEpochMilli(ts).atZone(zone).format(date)
    fun monthDayTime(ts: Long): String = Instant.ofEpochMilli(ts).atZone(zone).format(monthDayTime)
    fun epochDay(ts: Long): Long = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate().toEpochDay()
    fun dayLabel(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(shortDate)
    /** 图表查值 chip 用：MM/dd */
    fun monthDayLabel(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(monthDaySlash)
    fun monthKey(ts: Long): Int {
        val d = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
        return d.year * 100 + d.monthValue
    }
    fun monthLabel(ts: Long): String = Instant.ofEpochMilli(ts).atZone(zone).format(month)
}
