package com.ledger.app

import com.ledger.app.csv.BillParser
import com.ledger.app.data.Record
import com.ledger.app.data.RecordType
import com.ledger.app.util.Money
import com.ledger.app.util.Series
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class BillParserTest {

    private val wechat = """
微信支付账单明细,,,,,,,,,,
微信昵称：[测试],,,,,,,,,,
起始时间：[2024-03-01 00:00:00] 终止时间：[2024-03-31 23:59:59],,,,,,,,,,
导出类型：[全部],,,,,,,,,,
共3笔记录,,,,,,,,,,
----------------------微信支付账单明细列表--------------------,,,,,,,,,,
交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注
2024-03-01 12:33:11,商户消费,噜噜王国游戏,"648元宝,礼包",支出,¥648.00,零钱,支付成功,4200001	,M001	,/
2024-03-02 08:00:00,转账,张三,/,收入,¥100.50,/,已收钱,4200002	,/,/
2024-03-03 09:00:00,零钱提现,招商银行,/,/,¥50.00,/,提现已到账,4200003	,/,/
""".trimIndent()

    private val alipay = """
------------------------------------------------------------------------------------
导出信息：
交易号,商家订单号,交易创建时间,付款时间,最近修改时间,交易来源地,类型,交易对方,商品名称,金额（元）,收/支,交易状态,服务费（元）,成功退款（元）,备注,资金状态,
2024031122001	,T1	,2024-03-11 10:00:00,2024-03-11 10:00:01,2024-03-11 10:00:01,其他（包括阿里巴巴和外部商家）,即时到账交易,海域科技,海域 月卡,30.00,支出,交易成功,0.00,0.00,,已支出,
2024031122002	,T2	,2024-03-12 10:00:00,,2024-03-12 10:00:01,其他,即时到账交易,海域科技,海域 月卡,30.00,支出,交易关闭,0.00,0.00,,,
2024031122003	,T3	,2024-03-13 10:00:00,,2024-03-13 10:00:01,其他,即时到账交易,余额宝,收益,1.23,不计收支,交易成功,0.00,0.00,,,
2024031122004	,T4	,2024-03-14 10:00:00,,2024-03-14 10:00:01,其他,即时到账交易,李四,还款,88.00,收入,还款成功,0.00,0.00,,,
------------------------------------------------------------------------------------
共4笔记录
已收入:1笔,88.00元
本数据由支付宝提供
""".trimIndent()

    @Test
    fun wechatUtf8Bom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + wechat.toByteArray(Charsets.UTF_8)
        val r = BillParser.parse(bytes, "微信支付账单(20240301).csv")
        assertNull(r.error)
        assertEquals(BillParser.WECHAT, r.source)
        assertEquals(2, r.rows.size)
        val a = r.rows[0]
        assertEquals(64800L, a.amountCents)
        assertEquals(false, a.isIncome)
        assertEquals("4200001", a.sourceId)
        assertEquals("648元宝,礼包", a.goods)
        assertEquals(10050L, r.rows[1].amountCents)
        assertTrue(r.rows[1].isIncome)
    }

    @Test
    fun alipayGbk() {
        val bytes = alipay.toByteArray(Charset.forName("GBK"))
        val r = BillParser.parse(bytes, "alipay_record_20240301.csv")
        assertNull(r.error)
        assertEquals("GBK", r.encoding)
        assertEquals(BillParser.ALIPAY, r.source)
        assertEquals(2, r.rows.size)
        assertEquals("2024031122001", r.rows[0].sourceId)
        assertEquals(3000L, r.rows[0].amountCents)
        assertEquals(8800L, r.rows[1].amountCents)
        assertTrue(r.rows[1].isIncome)
    }

    @Test
    fun utf16le() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + wechat.toByteArray(Charsets.UTF_16LE)
        val r = BillParser.parse(bytes)
        assertEquals("UTF-16LE", r.encoding)
        assertEquals(2, r.rows.size)
    }

    @Test
    fun alipayNewFormatNoPreamble() {
        val csv = "交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注\n" +
            "2024/3/5 9:01,娱乐,海域,abc,充值,支出,12.5,余额,交易成功,X1,Y1,\n"
        val r = BillParser.parse(csv.toByteArray())
        assertEquals(BillParser.ALIPAY, r.source)
        assertEquals(1, r.rows.size)
        assertEquals(1250L, r.rows[0].amountCents)
        assertEquals("X1", r.rows[0].sourceId)
    }

    @Test
    fun garbageNoCrash() {
        val r = BillParser.parse(byteArrayOf(1, 2, 3, 0xC3.toByte(), 0x28))
        assertTrue(r.error != null)
    }

    @Test
    fun money() {
        assertEquals("¥12.34", Money.format(1234))
        assertEquals("-¥0.05", Money.format(-5))
        assertEquals("+¥1.00", Money.formatSigned(100))
        assertEquals(1230L, Money.parseInput("12.3"))
        assertNull(Money.parseInput("1.234"))
    }

    @Test
    fun seriesFillsGapsAndDownsamples() {
        val day = 86_400_000L
        val base = 1_700_000_000_000L
        val recs = listOf(
            Record(projectId = 1, type = RecordType.INCOME, amountCents = 100, timestamp = base),
            Record(projectId = 1, type = RecordType.EXPENSE, amountCents = 300, timestamp = base + 3 * day),
        )
        val s = Series.cumulative(recs)
        assertEquals(4, s.size)
        assertEquals(100L, s[1].value)
        assertEquals(-200L, s.last().value)
        val many = (0 until 2000).map { Record(projectId = 1, type = RecordType.INCOME, amountCents = 1, timestamp = base + it * day) }
        assertTrue(Series.cumulative(many).size <= 200)
    }
}
