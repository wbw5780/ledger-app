package com.ledger.app.csv

/**
 * 微信「个人对账」PDF 账单的文本解析（纯 Kotlin，不依赖 PDFBox，便于单元测试）。
 *
 * 输入：PDFTextStripper 逐页提取出的文本；输出：与 CSV 相同的 [ParseResult]/[ImportRow]。
 *
 * 防御式策略：
 *  - 每行按空白切成"词"并分类为锚点（时间/收支/金额/交易单号）或普通文本。
 *    只含普通文本的行视为一个单元格（兼容"每格一行"）；含锚点的行每个词各算一格（兼容"整行一条"）。
 *  - 以「yyyy-MM-dd HH:mm:ss」（同一行或日期、时间分两行）作为一条记录的起点，收集到下一个时间为止。
 *  - 记录内：方向 = 第一个「收入」/「支出」；金额 = 方向之后第一个 `¥?\d+\.\d{2}`（没有则取第一个）；
 *    交易单号 = 纯数字且长度 ≥ 20（→ sourceId，与 CSV 一致，跨格式去重天然生效）；
 *    其余文本按表头列序（找不到表头时按默认列序）尽力填入 交易类型/对方/商品/状态，允许为空。
 *  - 封面/说明页（没有任何交易时间的页）、页眉页脚、表头行丢弃；解析不出方向或金额的块整体丢弃。
 */
object PdfBillParser {

    const val ENCODING_LABEL = "PDF"

    // ---------------- 列定义 ----------------

    private enum class Col { TIME, TYPE, COUNTERPARTY, GOODS, DIRECTION, AMOUNT, METHOD, STATUS, ID, MERCHANT_ID, NOTE }

    /** 表头文字 → 列 */
    private val headerNames: Map<String, Col> = mapOf(
        "交易时间" to Col.TIME,
        "交易类型" to Col.TYPE,
        "交易对方" to Col.COUNTERPARTY,
        "商品" to Col.GOODS,
        "商品名称" to Col.GOODS,
        "收/支" to Col.DIRECTION,
        "收/支/其他" to Col.DIRECTION,
        "金额(元)" to Col.AMOUNT,
        "金额（元）" to Col.AMOUNT,
        "金额" to Col.AMOUNT,
        "支付方式" to Col.METHOD,
        "交易方式" to Col.METHOD,
        "当前状态" to Col.STATUS,
        "交易状态" to Col.STATUS,
        "交易单号" to Col.ID,
        "商户单号" to Col.MERCHANT_ID,
        "备注" to Col.NOTE,
    )

    /** SPEC 给出的默认列序（找不到表头时使用） */
    private val defaultOrder = listOf(
        Col.TIME, Col.TYPE, Col.COUNTERPARTY, Col.GOODS, Col.DIRECTION, Col.AMOUNT,
        Col.METHOD, Col.STATUS, Col.ID, Col.MERCHANT_ID, Col.NOTE,
    )

    private val anchorCols = setOf(Col.TIME, Col.DIRECTION, Col.AMOUNT, Col.ID)

    // ---------------- 词法 ----------------

    private val fullTimeLine = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$""")
    private val dateRe = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val clockRe = Regex("""^\d{2}:\d{2}:\d{2}$""")
    private val amountRe = Regex("""^[¥￥]?\d{1,3}(?:,\d{3})*\.\d{2}$|^[¥￥]?\d+\.\d{2}$""")
    private val idRe = Regex("""^\d{20,}$""")
    private val digitsRe = Regex("""^\d+$""")
    private val pageNoRe = Regex("""第\s*\d+\s*页\s*[/／,，]?\s*共\s*\d+\s*页|第\s*\d+\s*页|共\s*\d+\s*页|\d+\s*/\s*\d+\s*页|Page\s*\d+(\s*of\s*\d+)?""", RegexOption.IGNORE_CASE)

    /** 页眉页脚/标题噪声关键字：不含锚点的行命中即丢弃 */
    private val noiseKeywords = listOf(
        "微信支付", "账单明细", "明细列表", "交易明细", "个人对账", "对账单", "温馨提示", "说明：", "备注：",
        "起始时间", "终止时间", "导出时间", "导出类型", "微信昵称", "共计", "笔记录",
    )

    /** 表头折行产生的碎片 */
    private val noiseLines = setOf("(元)", "（元）", "/其他", "收/支/", "交易", "单号")

    private val spaceRe = Regex("""\s+""")

    private sealed interface Tok {
        data class Time(val text: String) : Tok
        data class Dir(val income: Boolean) : Tok
        data class Amount(val text: String) : Tok
        data class Id(val text: String) : Tok
        data class Text(val text: String) : Tok
    }

    private fun classify(w: String): Tok = when {
        w == "收入" -> Tok.Dir(true)
        w == "支出" -> Tok.Dir(false)
        amountRe.matches(w) -> Tok.Amount(w)
        idRe.matches(w) -> Tok.Id(w)
        else -> Tok.Text(w)
    }

    private fun normalize(s: String): String =
        s.replace(' ', ' ').replace('　', ' ').replace("﻿", "").replace('\t', ' ').trim()

    /** 一行 → 若干词元 */
    private fun tokenizeLine(raw: String): List<Tok> {
        val line = normalize(pageNoRe.replace(normalize(raw), " "))
        if (line.isEmpty()) return emptyList()
        if (fullTimeLine.matches(line)) return listOf(Tok.Time(line))

        val words = line.split(spaceRe).filter { it.isNotEmpty() }
        val out = ArrayList<Tok>()
        var i = 0
        while (i < words.size) {
            val w = words[i]
            if (dateRe.matches(w) && i + 1 < words.size && clockRe.matches(words[i + 1])) {
                out.add(Tok.Time("$w ${words[i + 1]}")); i += 2; continue
            }
            out.add(if (dateRe.matches(w)) Tok.Text(w) else classify(w))
            i++
        }
        // 页眉页脚/标题/表头：整行丢弃。整行一条的数据行（含「收入」/「支出」）即使对方名含「微信支付」也保留
        if (out.none { it is Tok.Dir } && isNoise(line, words)) return emptyList()
        // 纯文本行：整行作为一个单元格（"648元宝 礼包" 不拆开）
        if (out.all { it is Tok.Text }) return listOf(Tok.Text(line))
        return out
    }

    private fun isNoise(line: String, words: List<String>): Boolean {
        if (noiseKeywords.any { line.contains(it) }) return true
        if (line in noiseLines) return true
        // 表头行（全部由列名组成）
        return words.isNotEmpty() && words.all { it in headerNames || it in noiseLines }
    }

    /** 文本中按出现顺序找到的列名 → 列序 */
    private fun detectOrder(lines: List<String>): List<Col>? {
        for ((idx, raw) in lines.withIndex()) {
            val line = normalize(raw)
            if (!line.contains("交易时间")) continue
            // 表头可能整行一条，也可能每列一行：从此处往后最多 20 行收集列名
            val cols = ArrayList<Col>()
            for (j in idx until minOf(lines.size, idx + 20)) {
                val l = normalize(lines[j])
                if (j > idx && (fullTimeLine.matches(l) || l.split(spaceRe).any { dateRe.matches(it) })) break
                for (w in l.split(spaceRe)) {
                    val c = headerNames[w] ?: continue
                    if (c !in cols) cols.add(c)
                }
            }
            if (cols.size >= 4 && Col.TIME in cols && Col.DIRECTION in cols && Col.AMOUNT in cols) return cols
        }
        return null
    }

    // ---------------- 主入口 ----------------

    fun parsePages(pages: List<String>): ParseResult {
        val pageLines = pages.map { p -> p.split('\n', '\r').filter { it.isNotBlank() } }
        val allLines = pageLines.flatten()
        val order = detectOrder(allLines) ?: defaultOrder
        val idBeforeTime = order.indexOf(Col.ID).let { it >= 0 && it < order.indexOf(Col.TIME) }

        // 1. 词元流；跳过不含任何交易时间的页（封面、说明页、结尾声明页）
        val tokens = ArrayList<Tok>()
        for (lines in pageLines) {
            val pageToks = mergeSplitTimes(lines.flatMap { tokenizeLine(it) })
            if (pageToks.none { it is Tok.Time }) continue
            tokens.addAll(pageToks)
        }
        mergeWrappedIds(tokens)

        // 2. 按交易时间切块
        val blocks = ArrayList<MutableList<Tok>>()
        var cur: MutableList<Tok> = ArrayList() // 第一个时间之前的内容（表头等），不成块、最终丢弃
        for (t in tokens) {
            if (t is Tok.Time) {
                val nb = ArrayList<Tok>()
                // 交易单号列在时间列之前（如「交易单号 交易时间 …」）：紧邻时间前的单号属于新记录
                if (idBeforeTime && cur.lastOrNull() is Tok.Id) {
                    nb.add(cur.removeAt(cur.size - 1))
                }
                nb.add(t)
                blocks.add(nb); cur = nb
            } else {
                cur.add(t)
            }
        }

        // 3. 每块解析
        val rows = ArrayList<ImportRow>()
        var skipped = 0
        for (b in blocks) {
            val r = runCatching { parseBlock(b, order) }.getOrNull()
            if (r == null) skipped++ else rows.add(r)
        }
        if (blocks.isEmpty()) {
            return ParseResult(null, emptyList(), 0, ENCODING_LABEL, "PDF 中未找到交易记录（可能不是微信支付账单，或为扫描图片版 PDF）")
        }
        return ParseResult(BillParser.WECHAT, rows, skipped, ENCODING_LABEL)
    }

    /** 窄列中日期、时间被折成两行：「2024-03-01」+「12:33:11」→ 交易时间 */
    private fun mergeSplitTimes(tokens: List<Tok>): List<Tok> {
        val out = ArrayList<Tok>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            val a = (tokens[i] as? Tok.Text)?.text
            val b = (tokens.getOrNull(i + 1) as? Tok.Text)?.text
            if (a != null && b != null && dateRe.matches(a) && clockRe.matches(b)) {
                out.add(Tok.Time("$a $b")); i += 2; continue
            }
            out.add(tokens[i]); i++
        }
        return out
    }

    /** 窄列中被折成两行的长单号：「42000023452024030112」+「34567890」→ 合并 */
    private fun mergeWrappedIds(tokens: MutableList<Tok>) {
        var i = 0
        while (i < tokens.size - 1) {
            val a = tokens[i]
            val b = tokens[i + 1]
            // 只合并"前半段本身还不够 20 位"的情况，避免把交易单号和后面的商户单号拼在一起
            val at = (a as? Tok.Text)?.text
            val bt = (b as? Tok.Text)?.text
            if (at != null && bt != null && digitsRe.matches(at) && digitsRe.matches(bt) &&
                at.length in 15..19 && bt.length <= 16 && at.length + bt.length in 20..34
            ) {
                tokens[i] = Tok.Id(at + bt)
                tokens.removeAt(i + 1)
                continue
            }
            i++
        }
    }

    private fun parseBlock(b: List<Tok>, order: List<Col>): ImportRow? {
        val time = b.firstNotNullOfOrNull { it as? Tok.Time }?.text ?: return null
        val dirIdx = b.indexOfFirst { it is Tok.Dir }
        if (dirIdx < 0) return null // 无方向（「/」「其他」等）整块丢弃
        val isIncome = (b[dirIdx] as Tok.Dir).income

        val amountIdx = b.withIndex().firstOrNull { it.index > dirIdx && it.value is Tok.Amount }?.index
            ?: b.indexOfFirst { it is Tok.Amount }
        if (amountIdx < 0) return null
        val cents = BillParser.parseAmount((b[amountIdx] as Tok.Amount).text) ?: return null
        if (cents <= 0L) return null
        val ts = BillParser.parseTime(time) ?: return null

        val timeIdx = b.indexOfFirst { it is Tok.Time }
        // 交易单号：时间列在前时取第一个，单号列在前时取紧邻时间之前的那个
        val idIdx = if (order.indexOf(Col.ID) in 0 until order.indexOf(Col.TIME)) {
            (timeIdx - 1).takeIf { it >= 0 && b[it] is Tok.Id } ?: b.indexOfFirst { it is Tok.Id }
        } else b.indexOfFirst { it is Tok.Id }

        // 锚点位置（只认采用的那一个），其余锚点类词元降级为文本
        val anchorPos = mutableMapOf(Col.TIME to timeIdx, Col.DIRECTION to dirIdx, Col.AMOUNT to amountIdx)
        if (idIdx >= 0) anchorPos[Col.ID] = idIdx

        // 按锚点把文本分段，再按表头列序填入各段的文本列
        val fields = HashMap<Col, String>()
        val presentAnchors = order.filter { it in anchorCols && anchorPos.containsKey(it) }
        // 段 k = 第 k 个锚点之后、第 k+1 个锚点之前（按词元位置排序）
        val sortedAnchors = presentAnchors.sortedBy { anchorPos[it] }
        val segments = ArrayList<Pair<Col?, MutableList<String>>>() // (段前锚点, 文本)
        segments.add(null to ArrayList())
        val anchorAt = anchorPos.entries.associate { it.value to it.key }
        for ((i, t) in b.withIndex()) {
            val a = anchorAt[i]
            if (a != null) { segments.add(a to ArrayList()); continue }
            val text = when (t) {
                is Tok.Text -> t.text
                is Tok.Id -> t.text
                is Tok.Amount -> t.text
                is Tok.Dir -> if (t.income) "收入" else "支出"
                is Tok.Time -> t.text
            }
            segments.last().second.add(text)
        }
        for ((afterAnchor, texts) in segments) {
            if (texts.isEmpty()) continue
            val cols = textColsAfter(order, afterAnchor, sortedAnchors)
            assign(cols, texts, fields)
        }

        fun f(c: Col) = fields[c].orEmpty().let { if (it == "/") "" else it }
        val counterparty = f(Col.COUNTERPARTY)
        val goods = f(Col.GOODS)
        var id = if (idIdx >= 0) (b[idIdx] as Tok.Id).text else ""
        if (id.isEmpty()) {
            id = "h:" + ("$time|$cents|$counterparty|$goods").hashCode().toUInt().toString(16)
        }
        return ImportRow(
            source = BillParser.WECHAT,
            sourceId = id,
            isIncome = isIncome,
            amountCents = cents,
            timestamp = ts,
            counterparty = counterparty,
            goods = goods,
            note = f(Col.NOTE),
            status = f(Col.STATUS),
        )
    }

    /** 表头列序中位于锚点 [after] 之后、下一个出现的锚点之前的文本列 */
    private fun textColsAfter(order: List<Col>, after: Col?, presentAnchors: List<Col>): List<Col> {
        val start = if (after == null) 0 else order.indexOf(after) + 1
        val res = ArrayList<Col>()
        for (i in start until order.size) {
            val c = order[i]
            if (c in anchorCols) {
                if (c in presentAnchors) break else continue // 缺失的锚点列（如无单号）跳过
            }
            res.add(c)
        }
        return res
    }

    /** 文本按顺序填列；多出来的（长文本折行）并入「商品」/「交易对方」，否则并入最后一列 */
    private fun assign(cols: List<Col>, texts: List<String>, out: MutableMap<Col, String>) {
        if (cols.isEmpty()) return
        if (texts.size <= cols.size) {
            texts.forEachIndexed { i, t -> out[cols[i]] = t }
            return
        }
        val extra = texts.size - cols.size
        val wide = cols.firstOrNull { it == Col.GOODS } ?: cols.firstOrNull { it == Col.COUNTERPARTY } ?: cols.last()
        val wideIdx = cols.indexOf(wide)
        var ti = 0
        for ((ci, c) in cols.withIndex()) {
            val n = if (ci == wideIdx) 1 + extra else 1
            out[c] = texts.subList(ti, ti + n).joinToString("")
            ti += n
        }
    }

    /** 文件头是否为 PDF（允许前导 UTF-8 BOM / 空白） */
    fun isPdf(bytes: ByteArray): Boolean {
        var i = 0
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) i = 3
        while (i < bytes.size && i < 16 && (bytes[i] == ' '.code.toByte() || bytes[i] == '\r'.code.toByte() ||
                bytes[i] == '\n'.code.toByte() || bytes[i] == '\t'.code.toByte())
        ) i++
        val magic = "%PDF-".toByteArray(Charsets.US_ASCII)
        if (bytes.size - i < magic.size) return false
        for (k in magic.indices) if (bytes[i + k] != magic[k]) return false
        return true
    }
}
