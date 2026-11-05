package com.ledger.app.util

import com.ledger.app.data.Record
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Fritsch–Carlson 单调三次 Hermite 样条（支持非等距 x）。
 *
 * 每段 [x_i, x_{i+1}] 内的曲线值都落在 [min(y_i, y_{i+1}), max(y_i, y_{i+1})] 之间，
 * 因此整条曲线不会越过数据点的上下界（无过冲）；相邻相等的点之间是严格的水平直线。
 * 曲线对 x/y 的仿射变换不变（切线条件只依赖斜率比值），所以可以在数据空间算好切线，
 * 再把贝塞尔控制点映射到像素空间绘制。
 */
class MonotoneSpline(val xs: DoubleArray, val ys: DoubleArray) {
    /** 每个节点处的切线斜率 */
    val tangents: DoubleArray

    init {
        require(xs.size == ys.size) { "xs/ys 长度不一致" }
        for (i in 1 until xs.size) require(xs[i] > xs[i - 1]) { "xs 必须严格递增" }
        tangents = computeTangents(xs, ys)
    }

    val size: Int get() = xs.size

    /** 第 i 段（i..i+1）的三次贝塞尔控制点（数据空间）：c1x, c1y, c2x, c2y */
    fun controlPoints(i: Int): DoubleArray {
        val h = xs[i + 1] - xs[i]
        return doubleArrayOf(
            xs[i] + h / 3.0, ys[i] + tangents[i] * h / 3.0,
            xs[i + 1] - h / 3.0, ys[i + 1] - tangents[i + 1] * h / 3.0,
        )
    }

    /** 在 x 处求值；x 超出范围时夹到端点 */
    fun evaluate(x: Double): Double {
        val n = xs.size
        if (n == 0) return 0.0
        if (n == 1 || x <= xs[0]) return ys[0]
        if (x >= xs[n - 1]) return ys[n - 1]
        var lo = 0
        var hi = n - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (xs[mid] <= x) lo = mid else hi = mid
        }
        val h = xs[hi] - xs[lo]
        val t = (x - xs[lo]) / h
        val t2 = t * t
        val t3 = t2 * t
        val h00 = 2 * t3 - 3 * t2 + 1
        val h10 = t3 - 2 * t2 + t
        val h01 = -2 * t3 + 3 * t2
        val h11 = t3 - t2
        return h00 * ys[lo] + h10 * h * tangents[lo] + h01 * ys[hi] + h11 * h * tangents[hi]
    }

    companion object {
        fun computeTangents(xs: DoubleArray, ys: DoubleArray): DoubleArray {
            val n = xs.size
            val m = DoubleArray(n)
            if (n < 2) return m
            val delta = DoubleArray(n - 1) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]) }
            // 1. 初始切线：端点取单侧割线斜率，内部点同号取平均、异号或为 0 取 0（局部极值点水平）
            m[0] = delta[0]
            m[n - 1] = delta[n - 2]
            for (k in 1 until n - 1) {
                val a = delta[k - 1]
                val b = delta[k]
                m[k] = if (a == 0.0 || b == 0.0 || (a > 0) != (b > 0)) 0.0 else (a + b) / 2.0
            }
            // 2. Fritsch–Carlson 约束：平段两端切线为 0；alpha²+beta² > 9 时按 tau 缩放
            for (k in 0 until n - 1) {
                val d = delta[k]
                if (d == 0.0) {
                    m[k] = 0.0
                    m[k + 1] = 0.0
                    continue
                }
                val alpha = m[k] / d
                val beta = m[k + 1] / d
                if (alpha < 0) m[k] = 0.0
                if (beta < 0) m[k + 1] = 0.0
                val s = alpha * alpha + beta * beta
                if (s > 9.0) {
                    val tau = 3.0 / sqrt(s)
                    m[k] = tau * alpha * d
                    m[k + 1] = tau * beta * d
                }
            }
            return m
        }
    }
}

object ChartMath {
    /** 图表最多采样点数 */
    const val MAX_CHART_POINTS = 240

    /**
     * 图表用点：完整逐日累计序列 → 压缩连续平值日 → 极值抽样到 ≤ maxPoints。
     * 累计/补零逻辑与 [Series.cumulative] 完全相同（直接复用其前缀和，不抽样）。
     */
    fun chartPoints(records: List<Record>, maxPoints: Int = MAX_CHART_POINTS): List<SeriesPoint> {
        val full = Series.cumulative(records, Int.MAX_VALUE)
        return Series.downsample(compressFlat(full), maxPoints)
    }

    /**
     * 连续相同值的一段只保留首尾两点（中间是一条水平直线，单调样条在两点间也画成直线），
     * 首末点始终保留。
     */
    fun compressFlat(points: List<SeriesPoint>): List<SeriesPoint> {
        if (points.size <= 2) return points
        val out = ArrayList<SeriesPoint>()
        out.add(points[0])
        for (i in 1 until points.size - 1) {
            val v = points[i].value
            // 与前后都相等 → 位于平段内部，可删
            if (v == points[i - 1].value && v == points[i + 1].value) continue
            out.add(points[i])
        }
        out.add(points.last())
        return out
    }

    /** 在升序 xs 中找离 x 最近的下标；xs 为空返回 -1 */
    fun nearestIndex(xs: FloatArray, x: Float): Int {
        if (xs.isEmpty()) return -1
        var lo = 0
        var hi = xs.size - 1
        if (x <= xs[lo]) return lo
        if (x >= xs[hi]) return hi
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (xs[mid] <= x) lo = mid else hi = mid
        }
        return if (abs(x - xs[lo]) <= abs(xs[hi] - x)) lo else hi
    }

    /**
     * 浮动 chip 的左边 x：以 anchorX 居中，靠近左右边缘时内移到 [margin, width - margin] 内不裁切；
     * chip 比可用宽度还宽时贴左（尽量保证起始文字可见）。
     */
    fun chipLeft(anchorX: Float, chipWidth: Float, canvasWidth: Float, margin: Float = 0f): Float {
        val minLeft = margin
        val maxLeft = canvasWidth - margin - chipWidth
        if (maxLeft <= minLeft) return minLeft.coerceAtMost(maxOf(0f, canvasWidth - chipWidth)).coerceAtLeast(0f)
        return (anchorX - chipWidth / 2f).coerceIn(minLeft, maxLeft)
    }

    /** 数值 → 像素 y 的范围：包含 0，上下各留 headroom 比例 */
    fun yRange(minV: Long, maxV: Long, headroom: Double = 0.11): Pair<Double, Double> {
        val lo = minOf(minV, 0L).toDouble()
        val hi = maxOf(maxV, 0L).toDouble()
        val span = (hi - lo).takeIf { it > 0 } ?: 100.0 // 全为 0 时给 ¥1 的跨度
        return (lo - span * headroom) to (hi + span * headroom)
    }
}
