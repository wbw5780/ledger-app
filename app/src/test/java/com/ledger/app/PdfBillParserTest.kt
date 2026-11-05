package com.ledger.app

import com.ledger.app.csv.BillImporter
import com.ledger.app.csv.BillParser
import com.ledger.app.csv.PdfBillParser
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.fontbox.ttf.TrueTypeCollection
import org.apache.fontbox.ttf.TrueTypeFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class PdfBillParserTest {

    private val cover = """
微信支付账单明细
微信昵称：[测试]
起始时间：[2024-03-01 00:00:00] 终止时间：[2024-03-31 23:59:59]
导出时间：[2024-04-01 10:00:00]
共3笔记录
收入：1笔 100.50元
支出：1笔 648.00元
""".trimIndent()

    /** SPEC 描述的版式：每格一行，列序 交易时间…备注；含页眉页脚、表头、折行的商品名 */
    private val page1 = """
微信支付账单明细列表
交易时间
交易类型
交易对方
商品
收/支
金额(元)
支付方式
当前状态
交易单号
商户单号
备注
2024-03-01 12:33:11
商户消费
噜噜王国游戏
648元宝,礼包
支出
¥648.00
零钱
支付成功
4200001234567890123456789
M001
/
2024-03-02 08:00:00
转账
张三
/
收入
¥100.50
/
已收钱
4200002234567890123456789
/
/
第 1 页 共 2 页
""".trimIndent()

    private val page2 = """
微信支付 账单明细
2024-03-03 09:00:00
零钱提现
招商银行
/
/
¥50.00
/
提现已到账
4200003234567890123456789
/
/
2024-03-04 20:15:00
商户消费
海域科技
海域 月卡
(首充双倍)
支出
30.00
零钱
支付成功
4200004234567890123456789
/
/
第 2 页 共 2 页
""".trimIndent()

    @Test
    fun cellPerLineLayout() {
        val r = PdfBillParser.parsePages(listOf(cover, page1, page2))
        assertNull(r.error)
        assertEquals(BillParser.WECHAT, r.source)
        assertEquals(PdfBillParser.ENCODING_LABEL, r.encoding)
        assertEquals(3, r.rows.size)
        assertEquals(1, r.skippedLines) // 收/支 为「/」的零钱提现整块丢弃

        val a = r.rows[0]
        assertEquals("4200001234567890123456789", a.sourceId)
        assertEquals(64800L, a.amountCents)
        assertFalse(a.isIncome)
        assertEquals("噜噜王国游戏", a.counterparty)
        assertEquals("648元宝,礼包", a.goods)
        assertEquals("支付成功", a.status)
        assertEquals(BillParser.parseTime("2024-03-01 12:33:11"), a.timestamp)

        val b = r.rows[1]
        assertTrue(b.isIncome)
        assertEquals(10050L, b.amountCents)
        assertEquals("张三", b.counterparty)
        assertEquals("", b.goods) // "/" → 空

        val c = r.rows[2]
        assertEquals("海域科技", c.counterparty)
        assertEquals("海域 月卡(首充双倍)", c.goods) // 折行并入商品
        assertEquals(3000L, c.amountCents)
        assertEquals("4200004234567890123456789", c.sourceId)
    }

    /** 整行一条（PDFTextStripper 按坐标排序时常见），且日期/时间可能分开 */
    @Test
    fun rowPerLineLayout() {
        val page = """
微信支付账单明细列表
交易时间 交易类型 交易对方 商品 收/支 金额(元) 支付方式 当前状态 交易单号 商户单号 备注
2024-03-01 12:33:11 商户消费 噜噜王国游戏 648元宝 支出 ¥648.00 零钱 支付成功 4200001234567890123456789 M001 /
2024-03-02 08:00:00 转账 张三 / 收入 ¥100.50 / 已收钱 4200002234567890123456789 / /
2024-03-03 09:00:00 零钱提现 招商银行 / / ¥50.00 / 提现已到账 4200003234567890123456789 / /
第1页/共1页
""".trimIndent()
        val r = PdfBillParser.parsePages(listOf(page))
        assertEquals(2, r.rows.size)
        assertEquals("噜噜王国游戏", r.rows[0].counterparty)
        assertEquals("648元宝", r.rows[0].goods)
        assertEquals("4200001234567890123456789", r.rows[0].sourceId)
        assertEquals("4200002234567890123456789", r.rows[1].sourceId)
        assertTrue(r.rows[1].isIncome)
    }

    /** 微信「交易明细证明」式列序：交易单号在交易时间之前，日期和时间折成两行，单号折行 */
    @Test
    fun idBeforeTimeLayout() {
        val page = """
交易单号 交易时间 交易类型 收/支/其他 交易方式 金额(元) 交易对方 商户单号
42000012345678901
23456789
2024-03-01
12:33:11
商户消费
支出
零钱
648.00
噜噜王国游戏
10001234567890123456789
4200002234567890123456789
2024-03-02
08:00:00
转账
收入
/
100.50
张三
/
""".trimIndent()
        val r = PdfBillParser.parsePages(listOf(page))
        assertEquals(2, r.rows.size)
        assertEquals("4200001234567890123456789", r.rows[0].sourceId)
        assertEquals("噜噜王国游戏", r.rows[0].counterparty)
        assertEquals(64800L, r.rows[0].amountCents)
        assertEquals("4200002234567890123456789", r.rows[1].sourceId)
        assertEquals("张三", r.rows[1].counterparty)
    }

    @Test
    fun noDataNoCrash() {
        val r = PdfBillParser.parsePages(listOf(cover, "", "随便一些文字\n第 1 页"))
        assertNotNull(r.error)
        assertEquals(0, r.rows.size)
        // 有时间但缺方向/金额的块整体丢弃
        val r2 = PdfBillParser.parsePages(listOf("2024-03-01 12:00:00\n商户消费\n支出\n2024-03-02 12:00:00\n收入\nabc"))
        assertNull(r2.error)
        assertEquals(0, r2.rows.size)
        assertEquals(2, r2.skippedLines)
    }

    // ---------------- 路由 ----------------

    @Test
    fun routingByMagic() {
        var called = false
        val fake: (ByteArray) -> List<String> = { called = true; listOf(page1) }
        // 扩展名是 .csv，但内容是 PDF → 走 PDF
        val r = BillImporter.parse("%PDF-1.7\n...".toByteArray(), "账单.csv", fake)
        assertTrue(called)
        assertEquals(2, r.rows.size)

        // 扩展名是 .pdf，但内容是 CSV → 走 CSV（GBK 识别仍生效）
        called = false
        val csv = "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注\n" +
            "2024-03-01 12:33:11,商户消费,噜噜王国游戏,礼包,支出,6.00,零钱,支付成功,4200009\t,/,/\n"
        val r2 = BillImporter.parse(csv.toByteArray(charset("GBK")), "x.pdf", fake)
        assertFalse(called)
        assertEquals("GBK", r2.encoding)
        assertEquals(1, r2.rows.size)

        // zip / xlsx → 友好提示
        val r3 = BillImporter.parse(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 0, 0), "a.zip", fake)
        assertNotNull(r3.error)

        // PDF 提取抛异常（损坏/加密）→ 返回错误，不崩
        val r4 = BillImporter.parse("%PDF-1.4 broken".toByteArray(), null) { throw IllegalStateException("boom") }
        assertNotNull(r4.error)
        assertTrue(PdfBillParser.isPdf("﻿%PDF-1.4".toByteArray()))
        assertFalse(PdfBillParser.isPdf("%PD".toByteArray()))
    }

    /** 同一笔交易：CSV 与 PDF 解析出的 (source, sourceId) 完全相同 → 跨格式去重天然生效 */
    @Test
    fun crossFormatDedupKey() {
        val csv = "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注\n" +
            "2024-03-01 12:33:11,商户消费,噜噜王国游戏,\"648元宝,礼包\",支出,¥648.00,零钱,支付成功,4200001234567890123456789\t,M001\t,/\n"
        val fromCsv = BillParser.parse(csv.toByteArray()).rows.single()
        val fromPdf = PdfBillParser.parsePages(listOf(page1)).rows.first()
        assertEquals(fromCsv.source, fromPdf.source)
        assertEquals(fromCsv.sourceId, fromPdf.sourceId)
        assertEquals(fromCsv.amountCents, fromPdf.amountCents)
        assertEquals(fromCsv.timestamp, fromPdf.timestamp)
        assertEquals(fromCsv.isIncome, fromPdf.isIncome)
    }

    // ---------------- 端到端：真实 PDF（桌面版 PDFBox 生成 + PDFTextStripper 提取） ----------------

    private fun loadCjkFont(doc: PDDocument): PDType0Font? {
        val candidates = listOf(
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
            "/usr/share/fonts/truetype/arphic/uming.ttc",
        )
        for (path in candidates) {
            val f = File(path)
            if (!f.exists()) continue
            var ttf: TrueTypeFont? = null
            runCatching { TrueTypeCollection(f).processAllFonts { if (ttf == null) ttf = it } }
            ttf?.let { return runCatching { PDType0Font.load(doc, it, true) }.getOrNull() }
        }
        return null
    }

    private fun buildPdf(): ByteArray? {
        PDDocument().use { doc ->
            val font = loadCjkFont(doc) ?: return null
            val header = listOf("交易时间", "交易类型", "交易对方", "商品", "收/支", "金额(元)", "支付方式", "当前状态", "交易单号", "商户单号", "备注")
            val xs = floatArrayOf(20f, 120f, 175f, 255f, 335f, 365f, 420f, 465f, 520f, 680f, 740f)
            val rows = listOf(
                listOf("2024-03-01 12:33:11", "商户消费", "噜噜王国游戏", "648元宝礼包", "支出", "¥648.00", "零钱", "支付成功", "4200001234567890123456789", "M001", "/"),
                listOf("2024-03-02 08:00:00", "转账", "张三", "/", "收入", "¥100.50", "/", "已收钱", "4200002234567890123456789", "/", "/"),
                listOf("2024-03-03 09:00:00", "零钱提现", "招商银行", "/", "/", "¥50.00", "/", "提现已到账", "4200003234567890123456789", "/", "/"),
            )
            val size = PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width) // 横版
            // 封面页
            PDPage(size).also { p ->
                doc.addPage(p)
                PDPageContentStream(doc, p).use { cs ->
                    fun text(x: Float, y: Float, s: String, fs: Float = 12f) {
                        cs.beginText(); cs.setFont(font, fs); cs.newLineAtOffset(x, y); cs.showText(s); cs.endText()
                    }
                    text(300f, 500f, "微信支付账单", 24f)
                    text(100f, 450f, "起始时间：2024-03-01 00:00:00  终止时间：2024-03-31 23:59:59")
                    text(100f, 420f, "本账单仅供个人对账使用")
                }
            }
            // 明细页
            PDPage(size).also { p ->
                doc.addPage(p)
                PDPageContentStream(doc, p).use { cs ->
                    fun text(x: Float, y: Float, s: String, fs: Float = 7f) {
                        cs.beginText(); cs.setFont(font, fs); cs.newLineAtOffset(x, y); cs.showText(s); cs.endText()
                    }
                    text(320f, 560f, "微信支付账单明细列表", 14f)
                    header.forEachIndexed { i, h -> text(xs[i], 530f, h) }
                    rows.forEachIndexed { ri, row ->
                        val y = 505f - ri * 25f
                        row.forEachIndexed { i, c -> text(xs[i], y, c) }
                    }
                    text(380f, 30f, "第 1 页 共 1 页")
                }
            }
            val out = ByteArrayOutputStream()
            doc.save(out)
            return out.toByteArray()
        }
    }

    private fun desktopExtract(bytes: ByteArray): List<String> =
        PDDocument.load(bytes).use { d ->
            val st = PDFTextStripper().apply { sortByPosition = true; lineSeparator = "\n" }
            (1..d.numberOfPages).map { st.startPage = it; st.endPage = it; st.getText(d) }
        }

    @Test
    fun realPdfEndToEnd() {
        val pdf = buildPdf()
        assumeTrue("系统无 CJK TrueType 字体，跳过", pdf != null)
        val r = BillImporter.parse(pdf!!, "微信支付账单(20240301-20240331).pdf", ::desktopExtract)
        assertNull(r.error)
        assertEquals(BillParser.WECHAT, r.source)
        assertEquals(2, r.rows.size)
        assertEquals("4200001234567890123456789", r.rows[0].sourceId)
        assertEquals(64800L, r.rows[0].amountCents)
        assertEquals("噜噜王国游戏", r.rows[0].counterparty)
        assertTrue(r.rows[1].isIncome)
        assertEquals(10050L, r.rows[1].amountCents)
    }

    /** 同一个 PDF 走 App 实际使用的 pdfbox-android 提取器（BillImporter 默认参数），并与 CSV 去重键一致 */
    @Test
    fun realPdfWithPdfboxAndroid() {
        val pdf = buildPdf()
        assumeTrue("系统无 CJK TrueType 字体，跳过", pdf != null)
        val r = BillImporter.parse(pdf!!, "微信支付账单.pdf")
        assertNull(r.error)
        assertEquals(2, r.rows.size)
        assertEquals("噜噜王国游戏", r.rows[0].counterparty)
        assertEquals("648元宝礼包", r.rows[0].goods)
        assertEquals("支付成功", r.rows[0].status)

        val csv = "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注\n" +
            "2024-03-01 12:33:11,商户消费,噜噜王国游戏,648元宝礼包,支出,¥648.00,零钱,支付成功,4200001234567890123456789\t,M001\t,/\n"
        val c = BillImporter.parse(csv.toByteArray(), "微信支付账单.csv").rows.single()
        assertEquals(c.source to c.sourceId, r.rows[0].source to r.rows[0].sourceId)
    }

    /** 以 %PDF- 开头但内容损坏：真实提取器报错，返回错误信息而不是崩溃 */
    @Test
    fun corruptPdfWithPdfboxAndroid() {
        val r = BillImporter.parse("%PDF-1.7\n%garbage\n1 0 obj << /Type /Catalog".toByteArray(), "x.pdf")
        assertNotNull(r.error)
        assertTrue(r.rows.isEmpty())
    }
}
