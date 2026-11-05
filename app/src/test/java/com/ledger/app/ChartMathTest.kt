package com.ledger.app

import com.ledger.app.data.Record
import com.ledger.app.data.RecordType
import com.ledger.app.util.ChartMath
import com.ledger.app.util.MonotoneSpline
import com.ledger.app.util.Series
import com.ledger.app.util.SeriesPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChartMathTest {

    @Test
    fun monotoneSplineNeverOvershoots() {
        // 非等距 x、陡升陡降、平段、单点尖峰：朴素 Catmull-Rom/中点 bezier 在这类数据上都会过冲
        val xs = doubleArrayOf(0.0, 1.0, 2.0, 2.5, 6.0, 7.0, 8.0, 20.0, 21.0, 22.0)
        val ys = doubleArrayOf(0.0, 0.0, 1000.0, 1000.0, 1010.0, -500.0, -500.0, -480.0, 3000.0, 2990.0)
        val s = MonotoneSpline(xs, ys)
        for (i in 0 until xs.size - 1) {
            val lo = minOf(ys[i], ys[i + 1])
            val hi = maxOf(ys[i], ys[i + 1])
            val up = ys[i + 1] >= ys[i]
            var prev = s.evaluate(xs[i])
            for (k in 1..200) {
                val x = xs[i] + (xs[i + 1] - xs[i]) * k / 200.0
                val y = s.evaluate(x)
                assertTrue("段 $i x=$x y=$y 越界 [$lo,$hi]", y >= lo - 1e-9 && y <= hi + 1e-9)
                assertTrue("段 $i 非单调", if (up) y >= prev - 1e-9 else y <= prev + 1e-9)
                if (ys[i] == ys[i + 1]) assertEquals("平段必须是直线", ys[i], y, 1e-9)
                prev = y
            }
            // 节点处精确经过数据点
            assertEquals(ys[i], s.evaluate(xs[i]), 1e-9)
        }
        // 绘制用的贝塞尔控制点与 Hermite 求值一致（控制点 x 位于 1/3、2/3 处，所以 x(t) 线性）
        for (i in 0 until xs.size - 1) {
            val c = s.controlPoints(i)
            for (t in listOf(0.25, 0.5, 0.8)) {
                val u = 1 - t
                val bx = u * u * u * xs[i] + 3 * u * u * t * c[0] + 3 * u * t * t * c[2] + t * t * t * xs[i + 1]
                val by = u * u * u * ys[i] + 3 * u * u * t * c[1] + 3 * u * t * t * c[3] + t * t * t * ys[i + 1]
                assertEquals(s.evaluate(bx), by, 1e-6)
                // 控制点 y 也不越过本段上下界（保证贝塞尔凸包内）
                assertTrue(c[1] >= minOf(ys[i], ys[i + 1]) - 1e-9 && c[1] <= maxOf(ys[i], ys[i + 1]) + 1e-9)
                assertTrue(c[3] >= minOf(ys[i], ys[i + 1]) - 1e-9 && c[3] <= maxOf(ys[i], ys[i + 1]) + 1e-9)
            }
        }
        // 随机数据：全局不越过数据上下界
        val rnd = Random(7)
        val rx = DoubleArray(300) { it * 1.0 + rnd.nextDouble() * 0.5 }.also { it.sort() }
        val ry = DoubleArray(300) { (rnd.nextDouble() - 0.5) * 1e5 }
        val rs = MonotoneSpline(rx, ry)
        val min = ry.min()
        val max = ry.max()
        var x = rx.first()
        while (x <= rx.last()) {
            val y = rs.evaluate(x)
            assertTrue(y >= min - 1e-6 && y <= max + 1e-6)
            x += 0.037
        }
    }

    @Test
    fun chartPointsCompressFlatRunsAndSampleWithin240() {
        val day = 86_400_000L
        val base = 1_700_000_000_000L
        // 首日 +1 元，100 天后 -3 元：中间 99 天平值被压成两段直线
        val recs = listOf(
            Record(projectId = 1, type = RecordType.INCOME, amountCents = 100, timestamp = base),
            Record(projectId = 1, type = RecordType.EXPENSE, amountCents = 300, timestamp = base + 100 * day),
        )
        val pts = ChartMath.chartPoints(recs)
        val full = Series.cumulative(recs, Int.MAX_VALUE)
        assertEquals(101, full.size)
        assertEquals(listOf(full[0], full[99], full[100]), pts)
        assertEquals(listOf(100L, 100L, -200L), pts.map { it.value })

        // 2000 天随机记账：≤240 点、首末保留、全局极值保留、日期严格递增，与原累计序列一致
        val rnd = Random(42)
        val many = (0 until 2000).filter { rnd.nextInt(3) != 0 }.map {
            Record(
                projectId = 1,
                type = if (rnd.nextBoolean()) RecordType.INCOME else RecordType.EXPENSE,
                amountCents = rnd.nextLong(1, 50_000),
                timestamp = base + it * day,
            )
        }
        val fullMany = Series.cumulative(many, Int.MAX_VALUE)
        val sampled = ChartMath.chartPoints(many)
        assertTrue(sampled.size <= ChartMath.MAX_CHART_POINTS)
        assertEquals(fullMany.first(), sampled.first())
        assertEquals(fullMany.last(), sampled.last())
        assertTrue(sampled.contains(fullMany.minBy { it.value }) || sampled.any { it.value == fullMany.minOf { p -> p.value } })
        assertTrue(sampled.any { it.value == fullMany.maxOf { p -> p.value } })
        for (i in 1 until sampled.size) assertTrue(sampled[i].day > sampled[i - 1].day)
        val byDay = fullMany.associateBy { it.day }
        for (p in sampled) assertEquals(byDay[p.day], p)

        // 原 Series.cumulative 默认行为不变
        assertTrue(Series.cumulative(many).size <= 200)
        // compressFlat 边界
        assertEquals(emptyList<SeriesPoint>(), ChartMath.compressFlat(emptyList()))
        val flat = (0L until 10L).map { SeriesPoint(it, 5L) }
        assertEquals(listOf(flat.first(), flat.last()), ChartMath.compressFlat(flat))
    }

    @Test
    fun chipShiftsInsideEdgesAndNearestIndex() {
        val w = 300f
        val chip = 100f
        // 中间：居中于手指
        assertEquals(100f, ChartMath.chipLeft(150f, chip, w), 0f)
        // 靠左：内移到 0（或 margin）
        assertEquals(0f, ChartMath.chipLeft(10f, chip, w), 0f)
        assertEquals(4f, ChartMath.chipLeft(10f, chip, w, margin = 4f), 0f)
        // 靠右：右边缘对齐画布
        assertEquals(200f, ChartMath.chipLeft(295f, chip, w), 0f)
        assertEquals(196f, ChartMath.chipLeft(295f, chip, w, margin = 4f), 0f)
        // 任意位置都不裁切
        for (x in -50..350 step 5) {
            val l = ChartMath.chipLeft(x.toFloat(), chip, w)
            assertTrue(l >= 0f && l + chip <= w)
        }
        // chip 比画布宽：贴左
        assertEquals(0f, ChartMath.chipLeft(150f, 400f, w), 0f)

        val xs = floatArrayOf(0f, 10f, 30f, 31f, 100f)
        assertEquals(0, ChartMath.nearestIndex(xs, -5f))
        assertEquals(1, ChartMath.nearestIndex(xs, 14f))
        assertEquals(2, ChartMath.nearestIndex(xs, 21f))
        assertEquals(3, ChartMath.nearestIndex(xs, 40f))
        assertEquals(4, ChartMath.nearestIndex(xs, 1000f))
        assertEquals(-1, ChartMath.nearestIndex(FloatArray(0), 3f))
    }
}
