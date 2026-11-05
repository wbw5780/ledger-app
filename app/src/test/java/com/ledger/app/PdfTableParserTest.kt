package com.ledger.app

import com.ledger.app.csv.BillImporter
import com.ledger.app.csv.BillParser
import com.ledger.app.csv.ImportRow
import com.ledger.app.csv.PdfTableParser
import com.ledger.app.csv.PdfWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 坐标分列解析的回归测试。
 * fixture `wechat-voucher-sample.txt` 是真实微信「交易电子凭证」PDF 经 pdfbox-android 提取出的坐标词，已脱敏
 * （姓名/微信号/对方/卡号/金额/单号/编号已替换，日期平移）；期望值 `wechat-voucher-sample.expected.tsv`
 * 由另一个工具（poppler pdftotext -layout）独立解析同一份样本后同样脱敏得到。
 */
class PdfTableParserTest {

    private fun resource(name: String): String =
        javaClass.classLoader!!.getResource(name)!!.readText(Charsets.UTF_8)

    /** fixture → 每页的词 */
    private fun fixturePages(): List<List<PdfWord>> {
        val byPage = sortedMapOf<Int, MutableList<PdfWord>>()
        for (l in resource("wechat-voucher-sample.txt").lines()) {
            if (l.isBlank() || l.startsWith("#")) continue
            val f = l.split('\t', limit = 6)
            byPage.getOrPut(f[0].toInt()) { ArrayList() }
                .add(PdfWord(f[5], f[2].toFloat(), f[3].toFloat(), f[1].toFloat(), f[4].toFloat()))
        }
        return byPage.values.toList()
    }

    private data class Expect(val time: String, val income: Boolean, val amount: String, val id: String)

    private fun expected(): List<Expect> = resource("wechat-voucher-sample.expected.tsv").lines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { val f = it.split('\t'); Expect(f[0], f[1] == "收入", f[2], f[3]) }

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private fun fmtTime(ts: Long) = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(fmt)
    private fun fmtAmount(c: Long) = "%d.%02d".format(c / 100, c % 100)

    private fun assertMatchesExpected(rows: List<ImportRow>) {
        val exp = expected()
        assertEquals(28, exp.size)
        assertEquals("笔数", exp.size, rows.size)
        exp.zip(rows).forEachIndexed { i, (e, r) ->
            assertEquals("第${i + 1}笔 时间", e.time, fmtTime(r.timestamp))
            assertEquals("第${i + 1}笔 收支", e.income, r.isIncome)
            assertEquals("第${i + 1}笔 金额", e.amount, fmtAmount(r.amountCents))
            assertEquals("第${i + 1}笔 单号", e.id, r.sourceId)
            assertEquals(BillParser.WECHAT, r.source)
        }
    }

    @Test
    fun voucherFixtureParsesEveryRecord() {
        val r = PdfTableParser.parse(fixturePages())
        assertNotNull(r)
        assertNull(r!!.error)
        assertEquals(BillParser.WECHAT, r.source)
        assertEquals(0, r.skippedLines)
        assertMatchesExpected(r.rows)

        // 单号 31~32 位、全数字（3 段拼接完整，没有混入商户单号）
        assertTrue(r.rows.all { it.sourceId.length in 31..32 && it.sourceId.all(Char::isDigit) })
        assertEquals(r.rows.size, r.rows.map { it.sourceId }.toSet().size)

        // 收入：交易方式为「/」
        val income = r.rows[0]
        assertEquals("李四", income.counterparty)
        assertEquals("转账", income.goods)
        assertEquals("已存入零钱", income.status)
        assertEquals("已存入零钱 付款方说明：微信转账", income.note)

        // 支出：交易方式「××银行储」+「蓄卡(xxxx)」折行拼接；商户单号列内容不进入任何字段
        val expense = r.rows[2]
        assertTrue(!expense.isIncome)
        assertTrue(expense.goods, Regex("""转账 示例银行储蓄卡\(\d{4}\)""").matches(expense.goods))
        assertEquals("对方已收钱 付款方说明：微信转账", expense.note)
        assertEquals("李四", expense.counterparty)
    }

    @Test
    fun importerUsesCoordinatePathFirst() {
        var textCalled = false
        val r = BillImporter.parse(
            "%PDF-1.3\n".toByteArray(), "x.pdf",
            pdfPages = { textCalled = true; emptyList() },
            pdfWords = { fixturePages() },
        )
        assertNull(r.error)
        assertTrue("有表头时不应走旧启发式", !textCalled)
        assertMatchesExpected(r.rows)
    }

    /** 交易跨页：第 3 笔的单号第 3 段及商户单号续行被挪到下一页顶部，仍能拼接完整 */
    @Test
    fun recordContinuesAcrossPageBreak() {
        val pages = fixturePages().toMutableList()
        val p1 = pages[0]
        val cut = 383f // 第 3 笔单号第 2 段所在行(y=376.7)与第 3 段所在行(y=389.3)之间
        pages[0] = p1.filter { it.y < cut }
        pages.add(1, p1.filter { it.y >= cut }.map { it.copy(y = it.y - cut + 90f) })
        val r = PdfTableParser.parse(pages)!!
        assertMatchesExpected(r.rows)
    }

    /** 表尾合计行（无收支类型、金额列有数字）忽略，不污染上一笔 */
    @Test
    fun totalRowIgnored() {
        val pages = fixturePages().toMutableList()
        val last = pages[1]
        val y = 720.5f
        pages[1] = last + listOf(
            PdfWord("合计", 40f, 58f, y, 6.6f),
            PdfWord("28笔", 113f, 130f, y, 6.6f),
            PdfWord("9999.99", 313f, 341f, y, 6.6f),
        )
        val r = PdfTableParser.parse(pages)!!
        assertMatchesExpected(r.rows)
        assertEquals(0, r.skippedLines)
    }

    /** 收支类型不是 收入/支出 的整笔丢弃，计入跳过 */
    @Test
    fun nonIncomeExpenseDropped() {
        val pages = fixturePages().map { ws ->
            ws.map { if (it.y == 288.5f && it.text == "收入") it.copy(text = "/") else it }
        }
        val r = PdfTableParser.parse(pages)!!
        assertEquals(27, r.rows.size)
        assertEquals(1, r.skippedLines)
    }

    /** 找不到表头 → 交给旧启发式；旧启发式也识别不出 → 报「无法识别的账单格式」 */
    @Test
    fun noHeaderFallsBackThenReportsUnrecognized() {
        val noHeader = fixturePages().map { ws -> ws.filter { it.y != 275.3f } }
        assertNull(PdfTableParser.parse(noHeader))

        var textCalled = false
        val r = BillImporter.parse(
            "%PDF-1.3\n".toByteArray(), "x.pdf",
            pdfPages = { textCalled = true; listOf("微信支付交易电子凭证\n随便一些文字") },
            pdfWords = { noHeader },
        )
        assertTrue(textCalled)
        assertNotNull(r.error)
        assertTrue(r.error!!, r.error!!.startsWith("无法识别的账单格式"))
        assertTrue(r.rows.isEmpty())
    }

    /**
     * 真实样本端到端（仅本机存在 /root/wechat-sample.pdf 时运行；样本不入仓库）：
     * 走 App 实际使用的 pdfbox-android 提取 + BillImporter，结果写入 pdf-parse-check.txt 供人工核对。
     */
    @Test
    fun realSampleIfPresent() {
        val f = File(System.getProperty("ledger.wechatSample") ?: "/root/wechat-sample.pdf")
        assumeTrue("本机无真实样本，跳过", f.exists())
        val r = BillImporter.parse(f.readBytes(), f.name)
        assertNull(r.error)
        val sb = StringBuilder()
        sb.append("# 真实样本 ${f.path} 解析结果（PdfTableParser 坐标分列，pdfbox-android 提取）\n")
        sb.append("# 共 ${r.rows.size} 笔，跳过 ${r.skippedLines}；收入 ${r.rows.count { it.isIncome }} 笔 ")
        sb.append("合计 ${fmtAmount(r.rows.filter { it.isIncome }.sumOf { it.amountCents })}，")
        sb.append("支出 ${r.rows.count { !it.isIncome }} 笔 合计 ${fmtAmount(r.rows.filter { !it.isIncome }.sumOf { it.amountCents })}\n")
        sb.append("#序号\t交易时间\t收支\t金额\t单号位数\t单号尾4位\t对方\t商品\t备注\n")
        r.rows.forEachIndexed { i, x ->
            sb.append(listOf(
                i + 1, fmtTime(x.timestamp), if (x.isIncome) "收入" else "支出", fmtAmount(x.amountCents),
                x.sourceId.length, x.sourceId.takeLast(4), x.counterparty, x.goods, x.note,
            ).joinToString("\t")).append('\n')
        }
        // 对照：旧启发式（强制兜底路径）在同一样本上的结果
        val old = BillImporter.parse(f.readBytes(), f.name, pdfWords = { emptyList() })
        sb.append("\n# 对照：旧启发式（v1.4 及之前的 PDF 主路径）解析同一样本：${old.rows.size} 笔，跳过 ${old.skippedLines}，error=${old.error}\n")
        old.rows.take(5).forEachIndexed { i, x ->
            sb.append("#  旧$i\t${fmtTime(x.timestamp)}\t${if (x.isIncome) "收入" else "支出"}\t${fmtAmount(x.amountCents)}\t单号${x.sourceId.length}位\t对方=${x.counterparty}\t商品=${x.goods}\n")
        }
        sb.append("\n")
        val out = File(System.getProperty("ledger.parseCheckOut") ?: "/root/ledger-app/pdf-parse-check.txt")
        out.writeText(sb.toString())
        // 完整单号仅写到构建目录供交叉核对脚本使用（不入仓库、不打包）
        File("build/pdf-parse-full.tsv").apply { parentFile?.mkdirs() }.writeText(
            r.rows.joinToString("\n") { "${fmtTime(it.timestamp)}\t${if (it.isIncome) "收入" else "支出"}\t${fmtAmount(it.amountCents)}\t${it.sourceId}" } + "\n",
        )
        assertTrue(r.rows.isNotEmpty())
        assertTrue(r.rows.all { it.sourceId.all(Char::isDigit) && it.sourceId.length >= 28 })
    }
}
