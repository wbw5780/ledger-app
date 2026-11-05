package com.ledger.app.csv

import kotlin.math.abs

/**
 * 微信 PDF 账单的「坐标分列」表格解析（主路径；纯 Kotlin，输入为 [PdfTextExtractor.extractWords] 的结果）。
 *
 * 面向微信「交易电子凭证」：表头「交易单号 交易时间 交易类型 收支类型 交易方式 金额(元) 交易对方 当前状态 收/付款说明 商户单号」，
 * 窄列导致每笔交易折成 3~5 个物理行（单号折 3 段、日期/时间分两行、交易方式/商户单号折行）。
 * 也兼容「个人对账」类表头（交易时间 … 商品 收/支 金额(元) 支付方式 当前状态 交易单号 …）。
 *
 * 算法：
 *  1. 按基线 y 聚簇成视觉行（|Δy| < 字高一半视为同一行）。
 *  2. 表头行 = 含「交易时间」「金额」「收支类型/收/支」且识别出 ≥4 个列名的行；
 *     列边界 = 相邻两个表头词之间空隙的中点（表头居中、数据比表头宽 1~2pt，直接用表头 X 起点会把日期/商户单号分错列），
 *     词按其水平中心落在哪两个边界之间归列。后续页没有表头时沿用上一页的列。
 *  3. 交易时间列出现日期（yyyy-MM-dd）的行 = 一笔交易的开始；其后的行是折行续行，直到下一笔开始。
 *     续行遇到以下情况结束当前交易（该行本身丢弃）：与上一行间距 > 3 倍字高（同页内）、
 *     出现在收支/金额列（合计行等）、某个词横跨 ≥2 个列边界（表尾说明文字）、以「说明/合计/总计」开头。
 *     交易跨页时自动续接。
 *  4. 每笔交易按列把各行片段按顺序直接拼接（去空格）：交易单号 = 单号列全部数字段；时间 = 日期 + 时间；
 *     收支类型必须是 收入/支出（否则整笔丢弃，计入跳过）；金额 = 金额列数字（分）。
 */
object PdfTableParser {

    private enum class Col { ID, TIME, TYPE, DIRECTION, METHOD, AMOUNT, COUNTERPARTY, STATUS, DESC, MERCHANT_ID, GOODS, NOTE, UNKNOWN }

    private val headerNames: Map<String, Col> = mapOf(
        "交易单号" to Col.ID,
        "交易时间" to Col.TIME,
        "交易类型" to Col.TYPE,
        "收支类型" to Col.DIRECTION,
        "收/支" to Col.DIRECTION,
        "收/支/其他" to Col.DIRECTION,
        "交易方式" to Col.METHOD,
        "支付方式" to Col.METHOD,
        "金额(元)" to Col.AMOUNT,
        "金额" to Col.AMOUNT,
        "交易对方" to Col.COUNTERPARTY,
        "当前状态" to Col.STATUS,
        "交易状态" to Col.STATUS,
        "收/付款说明" to Col.DESC,
        "收付款说明" to Col.DESC,
        "商户单号" to Col.MERCHANT_ID,
        "商品" to Col.GOODS,
        "商品名称" to Col.GOODS,
        "备注" to Col.NOTE,
    )

    private val dateRe = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val clockRe = Regex("""^\d{2}:\d{2}:\d{2}$""")
    private val dateTimeRe = Regex("""^(\d{4}-\d{2}-\d{2})(\d{2}:\d{2}:\d{2})$""")
    private val digitsRe = Regex("""^\d+$""")
    private val tableEndRe = Regex("""^(说明|合计|总计|小计)""")

    private data class Line(val y: Float, val height: Float, val words: List<PdfWord>)

    private data class Column(val col: Col, val left: Float, val right: Float)

    private class Txn {
        val parts = HashMap<Col, MutableList<String>>()
        fun add(c: Col, s: String) { parts.getOrPut(c) { ArrayList() }.add(s) }
        fun joined(c: Col): String = parts[c].orEmpty().joinToString("")
    }

    private fun normHeader(s: String): String =
        s.replace(" ", "").replace('（', '(').replace('）', ')').replace('／', '/')

    /** 按基线聚簇成视觉行 */
    private fun clusterLines(words: List<PdfWord>): List<Line> {
        val sorted = words.filter { it.text.isNotBlank() }.sortedWith(compareBy({ it.y }, { it.x }))
        val lines = ArrayList<Line>()
        var cur = ArrayList<PdfWord>()
        var curY = 0f
        var curH = 0f
        for (w in sorted) {
            if (cur.isNotEmpty() && abs(w.y - curY) < maxOf(curH, w.height) / 2f) {
                cur.add(w)
                curH = maxOf(curH, w.height)
            } else {
                if (cur.isNotEmpty()) lines.add(Line(curY, curH, cur.sortedBy { it.x }))
                cur = arrayListOf(w)
                curY = w.y
                curH = w.height
            }
        }
        if (cur.isNotEmpty()) lines.add(Line(curY, curH, cur.sortedBy { it.x }))
        return lines
    }

    /** 若该行是表头，返回列定义 */
    private fun detectHeader(line: Line): List<Column>? {
        // 识别表头词；相邻的未知碎片（如被拆开的「(元)」）并入前一个表头词
        data class H(var col: Col, var text: String, var x: Float, var endX: Float)
        val hs = ArrayList<H>()
        for (w in line.words) {
            val t = normHeader(w.text)
            val c = headerNames[t]
            val prev = hs.lastOrNull()
            if (c == null && prev != null && w.x - prev.endX < line.height) {
                prev.text += t; prev.endX = w.endX
                headerNames[normHeader(prev.text)]?.let { prev.col = it }
                continue
            }
            hs.add(H(c ?: Col.UNKNOWN, t, w.x, w.endX))
        }
        val cols = hs.map { it.col }.filter { it != Col.UNKNOWN }
        if (cols.size < 4 || Col.TIME !in cols || Col.AMOUNT !in cols || Col.DIRECTION !in cols) return null
        if (cols.size != cols.toSet().size) return null
        return hs.mapIndexed { i, h ->
            val left = if (i == 0) Float.NEGATIVE_INFINITY else (hs[i - 1].endX + h.x) / 2f
            val right = if (i == hs.lastIndex) Float.POSITIVE_INFINITY else (h.endX + hs[i + 1].x) / 2f
            Column(h.col, left, right)
        }
    }

    private fun columnOf(w: PdfWord, columns: List<Column>): Column {
        val cx = (w.x + w.endX) / 2f
        return columns.firstOrNull { cx >= it.left && cx < it.right } ?: columns.last()
    }

    /** 词横跨 ≥2 个内部列边界（整段说明文字之类） */
    private fun spansColumns(w: PdfWord, columns: List<Column>): Boolean =
        columns.drop(1).count { it.left > w.x && it.left < w.endX } >= 2

    /**
     * 解析；找不到表头时返回 null（交给旧启发式兜底）。
     * 找到表头但没有任何交易时返回带 error 的结果。
     */
    fun parse(pages: List<List<PdfWord>>): ParseResult? {
        var columns: List<Column>? = null
        val txns = ArrayList<Txn>()
        var cur: Txn? = null
        var headerFound = false

        for (pageWords in pages) {
            var prevY: Float? = null // 同页上一条表格行的基线；换页后为 null（允许跨页续接）
            for (line in clusterLines(pageWords)) {
                val header = detectHeader(line)
                if (header != null) {
                    columns = header; headerFound = true
                    cur = null; prevY = line.y
                    continue
                }
                val cols = columns ?: continue // 表头之前（文件头、申请方信息、编号）全部忽略

                val assigned = line.words.map { it to columnOf(it, cols).col }
                val timeTexts = assigned.filter { it.second == Col.TIME }.map { it.first.text }
                val startsTxn = timeTexts.any { dateRe.matches(it) || dateTimeRe.matches(it.replace(" ", "")) }

                if (startsTxn) {
                    cur = Txn().also { txns.add(it) }
                } else {
                    val c = cur
                    val ends = c == null ||
                        (prevY != null && line.y - prevY > 3f * line.height) ||
                        assigned.any { it.second == Col.DIRECTION || it.second == Col.AMOUNT } ||
                        line.words.any { spansColumns(it, cols) } ||
                        tableEndRe.containsMatchIn(line.words.first().text)
                    if (ends) {
                        cur = null
                        continue
                    }
                }
                for ((w, c) in assigned) cur!!.add(c, w.text.replace(" ", ""))
                prevY = line.y
            }
        }
        if (!headerFound) return null

        val rows = ArrayList<ImportRow>()
        var skipped = 0
        for (t in txns) {
            val r = runCatching { toRow(t, columns.orEmpty().map { it.col }.toSet()) }.getOrNull()
            if (r == null) skipped++ else rows.add(r)
        }
        if (rows.isEmpty()) {
            return ParseResult(
                null, emptyList(), skipped, PdfBillParser.ENCODING_LABEL,
                "无法识别的账单格式：找到了微信账单表头，但没有解析出收入/支出交易",
            )
        }
        return ParseResult(BillParser.WECHAT, rows, skipped, PdfBillParser.ENCODING_LABEL)
    }

    private fun clean(s: String): String = s.trim().let { if (it == "/" || it == "-") "" else it }

    private fun toRow(t: Txn, present: Set<Col>): ImportRow? {
        // 交易时间：日期段 + 时间段
        val timeParts = t.parts[Col.TIME].orEmpty()
        val joinedTime = timeParts.joinToString("")
        val date = timeParts.firstOrNull { dateRe.matches(it) }
            ?: dateTimeRe.find(joinedTime)?.groupValues?.get(1) ?: return null
        val clock = timeParts.firstOrNull { clockRe.matches(it) }
            ?: dateTimeRe.find(joinedTime)?.groupValues?.get(2) ?: return null
        val timeStr = "$date $clock"
        val ts = BillParser.parseTime(timeStr) ?: return null

        val isIncome = when (t.joined(Col.DIRECTION)) {
            "收入" -> true
            "支出" -> false
            else -> return null // 「/」「不计收支」、合计行等整笔丢弃
        }
        val cents = BillParser.parseAmount(t.joined(Col.AMOUNT)) ?: return null
        if (cents <= 0L) return null

        val id = t.parts[Col.ID].orEmpty().filter { digitsRe.matches(it) }.joinToString("")
        val counterparty = clean(t.joined(Col.COUNTERPARTY))
        val status = clean(t.joined(Col.STATUS))
        val goods: String
        val note: String
        if (Col.GOODS in present) {
            // 「个人对账」版式：与 CSV 字段一致
            goods = clean(t.joined(Col.GOODS))
            note = clean(t.joined(Col.NOTE))
        } else {
            // 交易电子凭证：商品 = 交易类型 + 交易方式；备注 = 当前状态 + 收/付款说明
            goods = listOf(t.joined(Col.TYPE), t.joined(Col.METHOD)).map(::clean).filter { it.isNotEmpty() }.joinToString(" ")
            note = listOf(t.joined(Col.STATUS), t.joined(Col.DESC), t.joined(Col.NOTE)).map(::clean)
                .filter { it.isNotEmpty() }.joinToString(" ")
        }
        val sourceId = id.ifEmpty { "h:" + "$timeStr|$cents|$counterparty|$goods".hashCode().toUInt().toString(16) }
        return ImportRow(
            source = BillParser.WECHAT,
            sourceId = sourceId,
            isIncome = isIncome,
            amountCents = cents,
            timestamp = ts,
            counterparty = counterparty,
            goods = goods,
            note = note,
            status = status,
        )
    }
}
