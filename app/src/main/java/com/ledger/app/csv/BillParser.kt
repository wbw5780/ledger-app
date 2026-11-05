package com.ledger.app.csv

import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.LocalDateTime
import java.time.ZoneId

/** 解析出的单条账单 */
data class ImportRow(
    val source: String,          // wechat / alipay
    val sourceId: String,
    val isIncome: Boolean,
    val amountCents: Long,
    val timestamp: Long,
    val counterparty: String,
    val goods: String,
    val note: String,
    val status: String,
) {
    /** 合并后的对方/商品名展示 */
    val title: String
        get() = listOf(counterparty, goods).filter { it.isNotBlank() && it != "/" }
            .distinct().joinToString(" · ")
}

data class ParseResult(
    val source: String?,         // null 表示无法识别
    val rows: List<ImportRow>,
    val skippedLines: Int,
    val encoding: String,
    val error: String? = null,
)

object BillParser {

    const val WECHAT = "wechat"
    const val ALIPAY = "alipay"

    // ---------------- 编码识别 ----------------

    fun decode(bytes: ByteArray): Pair<String, String> {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8) to "UTF-8 (BOM)"
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE) to "UTF-16LE"
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE) to "UTF-16BE"
        }
        try {
            val dec = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return dec.decode(ByteBuffer.wrap(bytes)).toString() to "UTF-8"
        } catch (_: CharacterCodingException) {
        }
        val gbk = runCatching { Charset.forName("GBK") }.getOrNull()
            ?: runCatching { Charset.forName("GB18030") }.getOrNull()
        return if (gbk != null) String(bytes, gbk) to "GBK"
        else String(bytes, Charsets.ISO_8859_1) to "ISO-8859-1"
    }

    // ---------------- CSV 拆分（支持引号、引号内逗号/换行、"" 转义）----------------

    fun parseCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text[i + 1] == '"') {
                        sb.append('"'); i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    sb.append(c)
                }
            } else {
                when (c) {
                    '"' -> if (sb.isBlank()) { sb.setLength(0); inQuotes = true } else sb.append(c)
                    ',' -> { row.add(sb.toString()); sb.setLength(0) }
                    '\r' -> {}
                    '\n' -> {
                        row.add(sb.toString()); sb.setLength(0)
                        rows.add(row); row = ArrayList()
                    }
                    else -> sb.append(c)
                }
            }
            i++
        }
        if (sb.isNotEmpty() || row.isNotEmpty()) {
            row.add(sb.toString())
            rows.add(row)
        }
        return rows
    }

    private fun clean(s: String?): String =
        s?.replace("﻿", "")?.trim { it.isWhitespace() || it == '\t' || it == ' ' } ?: ""

    // ---------------- 主入口 ----------------

    fun parse(bytes: ByteArray, fileName: String? = null): ParseResult {
        val (text, enc) = try {
            decode(bytes)
        } catch (e: Exception) {
            return ParseResult(null, emptyList(), 0, "?", "无法解码文件：${e.message}")
        }
        return try {
            parseText(text, enc, fileName)
        } catch (e: Exception) {
            ParseResult(null, emptyList(), 0, enc, "解析失败：${e.message}")
        }
    }

    fun parseText(text: String, encoding: String = "UTF-8", fileName: String? = null): ParseResult {
        val table = parseCsv(text).map { r -> r.map { clean(it) } }

        // 1. 找表头
        var headerIdx = -1
        for ((idx, r) in table.withIndex()) {
            if (isHeader(r)) { headerIdx = idx; break }
        }
        if (headerIdx < 0) {
            return ParseResult(null, emptyList(), 0, encoding, "未找到微信/支付宝账单表头（需包含「交易时间」「收/支」等列）")
        }
        val header = table[headerIdx]
        val preamble = table.take(headerIdx).joinToString("\n") { it.joinToString(",") }
        val source = detectSource(header, preamble, fileName)

        val cols = Columns.from(header, source)
        if (cols.time < 0 || cols.amount < 0 || cols.direction < 0) {
            return ParseResult(source, emptyList(), 0, encoding, "表头缺少必要列（时间/金额/收支）")
        }

        val out = ArrayList<ImportRow>()
        var skipped = 0
        for (r in table.drop(headerIdx + 1)) {
            if (r.all { it.isEmpty() }) continue
            val row = runCatching { parseRow(r, cols, source) }.getOrNull()
            if (row == null) skipped++ else out.add(row)
        }
        return ParseResult(source, out, skipped, encoding)
    }

    private fun isHeader(r: List<String>): Boolean {
        val set = r.toSet()
        if ("收/支" !in set) return false
        return "交易时间" in set || "交易号" in set || "交易创建时间" in set
    }

    private fun detectSource(header: List<String>, preamble: String, fileName: String?): String {
        val set = header.toSet()
        if ("交易号" in set || "交易创建时间" in set || "交易订单号" in set ||
            "商品名称" in set || "商品说明" in set || "交易分类" in set || "资金状态" in set
        ) return ALIPAY
        if ("交易单号" in set || "交易类型" in set || "当前状态" in set) return WECHAT
        if (preamble.contains("支付宝") || fileName?.contains("alipay", true) == true) return ALIPAY
        return WECHAT
    }

    private class Columns(
        val time: Int,
        val amount: Int,
        val direction: Int,
        val counterparty: Int,
        val goods: Int,
        val id: Int,
        val status: Int,
        val note: Int,
    ) {
        companion object {
            fun from(h: List<String>, source: String): Columns {
                fun find(vararg names: String): Int {
                    for (n in names) {
                        val i = h.indexOf(n)
                        if (i >= 0) return i
                    }
                    return -1
                }
                val amount = find("金额(元)", "金额（元）", "金额").let { i ->
                    if (i >= 0) i else h.indexOfFirst { it.startsWith("金额") }
                }
                return Columns(
                    time = find("交易时间", "交易创建时间", "付款时间", "最近修改时间"),
                    amount = amount,
                    direction = find("收/支"),
                    counterparty = find("交易对方"),
                    goods = find("商品", "商品名称", "商品说明"),
                    id = if (source == WECHAT) find("交易单号", "交易订单号", "交易号")
                    else find("交易号", "交易订单号", "交易单号"),
                    status = find("交易状态", "当前状态"),
                    note = find("备注"),
                )
            }
        }
    }

    private fun parseRow(r: List<String>, c: Columns, source: String): ImportRow? {
        fun get(i: Int) = if (i in r.indices) r[i] else ""

        val dir = get(c.direction)
        val isIncome = when (dir) {
            "收入" -> true
            "支出" -> false
            else -> return null // "/"、"不计收支" 等跳过
        }
        val status = get(c.status)
        if (source == ALIPAY && c.status >= 0 && !status.contains("成功")) return null

        val ts = parseTime(get(c.time)) ?: return null
        val cents = parseAmount(get(c.amount)) ?: return null
        if (cents <= 0L) return null

        val counterparty = get(c.counterparty).let { if (it == "/") "" else it }
        val goods = get(c.goods).let { if (it == "/") "" else it }
        val note = get(c.note).let { if (it == "/") "" else it }
        var id = get(c.id)
        if (id.isEmpty() || id == "/") {
            id = "h:" + (get(c.time) + "|" + cents + "|" + counterparty + "|" + goods).hashCode().toUInt().toString(16)
        }
        return ImportRow(source, id, isIncome, cents, ts, counterparty, goods, note, status)
    }

    private val timeRegex =
        Regex("""(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})日?(?:[ T]+(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?)?""")

    fun parseTime(s: String): Long? {
        val m = timeRegex.find(s) ?: return null
        val g = m.groupValues
        val dt = LocalDateTime.of(
            g[1].toInt(), g[2].toInt(), g[3].toInt(),
            g[4].toIntOrNull() ?: 0, g[5].toIntOrNull() ?: 0, g[6].toIntOrNull() ?: 0,
        )
        return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun parseAmount(s: String): Long? {
        val t = s.replace("¥", "").replace("￥", "").replace(",", "").replace("元", "").trim()
        if (t.isEmpty()) return null
        val bd = t.toBigDecimalOrNull() ?: return null
        return bd.abs().setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    }

    @Suppress("unused")
    private fun BigDecimal.cents(): Long = setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()
}
