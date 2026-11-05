package com.ledger.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledger.app.util.ChartMath
import com.ledger.app.util.Dates
import com.ledger.app.util.Money
import com.ledger.app.util.MonotoneSpline
import com.ledger.app.util.SeriesPoint
import kotlin.math.abs

/** 总览卡内图表高度 */
val ChartHeightOverview = 160.dp
/** 项目详情页图表高度 */
val ChartHeightDetail = 200.dp

private val LabelGray = Color(0xFF8A8A8A)
private val AxisGray = Color(0xFF999999)

/**
 * 累计盈亏曲线（总览卡与项目详情共用）。
 *
 * - 线：Fritsch–Carlson 单调三次样条（无过冲），2.5dp 圆头圆角，整条主文字色
 * - 面积：零轴以上 主文字色 12%→0%，零轴以下 红 10%→0%（按 y0 clip 分开画，都从线淡出到零轴）
 * - 零轴：1dp 虚线 6/6dp，#999 60%，仅在数据 X 范围内
 * - 刻度：底部首/末日期，右侧 max / 0 / min（不与曲线重叠才画）
 * - 端点：5dp 半径实心点 + 2dp 卡片底色外圈，净值 < 0 为红
 * - 手指按住/拖动查值：竖直指示线 + 放大圆点 + 浮动 chip「MM/dd · ±¥xx.xx」，松手 300ms 淡出
 */
@Composable
fun CumulativeChart(
    points: List<SeriesPoint>,
    modifier: Modifier = Modifier,
    height: Dp = ChartHeightDetail,
) {
    if (points.isEmpty()) {
        Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text(
                "暂无数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val ink = MaterialTheme.colorScheme.onSurface // 浅色 #111111 / 深色 #F5F5F5
    val red = MoneyColors.expense()
    val card = LocalLedger.current.card
    val measurer = rememberTextMeasurer()
    val labelStyle = remember { TextStyle(fontSize = 10.sp, color = LabelGray, fontFeatureSettings = "tnum") }
    val chipStyle = remember(card) {
        TextStyle(fontSize = 11.sp, color = card, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
    }

    val spline = remember(points) {
        if (points.size < 2) null
        else MonotoneSpline(
            DoubleArray(points.size) { points[it].day.toDouble() },
            DoubleArray(points.size) { points[it].value.toDouble() },
        )
    }
    val minV = remember(points) { points.minOf { it.value } }
    val maxV = remember(points) { points.maxOf { it.value } }

    // 触摸状态：touchX 记录最后的手指位置（松手后保留，用于淡出期间继续绘制）
    var touching by remember { mutableStateOf(false) }
    var touchX by remember { mutableFloatStateOf(Float.NaN) }
    val overlayAlpha by animateFloatAsState(
        targetValue = if (touching) 1f else 0f,
        animationSpec = tween(durationMillis = if (touching) 120 else 300),
        label = "chartTouchAlpha",
    )

    val desc = "累计盈亏曲线，${Dates.dayLabel(points.first().day)} 至 ${Dates.dayLabel(points.last().day)}，" +
        "当前 ${Money.formatSigned(points.last().value)}"

    var m = modifier.fillMaxWidth().height(height).semantics { contentDescription = desc }
    if (spline != null) {
        m = m.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                touching = true
                touchX = down.position.x
                val slop = viewConfiguration.touchSlop
                var dx = 0f
                var dy = 0f
                var horizontal = false
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    val d = ch.positionChange()
                    dx += d.x
                    dy += d.y
                    // 明显的竖直滑动：交给外层列表滚动
                    if (!horizontal && abs(dy) > slop && abs(dy) > abs(dx)) break
                    if (!horizontal && abs(dx) > slop) horizontal = true
                    if (horizontal) ch.consume()
                    touchX = ch.position.x
                }
                touching = false
            }
        }
    }

    Canvas(m) {
        if (spline == null) {
            drawSinglePoint(points[0], ink, red, card, measurer, labelStyle)
            return@Canvas
        }
        val dateFirst = measurer.measure(Dates.dayLabel(points.first().day), labelStyle)
        val dateLast = measurer.measure(Dates.dayLabel(points.last().day), labelStyle)

        val dotR = 5.dp.toPx()
        val ring = 2.dp.toPx()
        val plotLeft = 2.dp.toPx()
        val plotRight = size.width - dotR - ring - 1.dp.toPx()
        val plotTop = 2.dp.toPx()
        val plotBottom = size.height - dateLast.size.height - 6.dp.toPx()
        val plotW = plotRight - plotLeft
        val plotH = plotBottom - plotTop
        if (plotW <= 0f || plotH <= 0f) return@Canvas

        val (lo, hi) = ChartMath.yRange(minV, maxV)
        val d0 = spline.xs.first()
        val dSpan = spline.xs.last() - d0
        fun px(day: Double): Float = (plotLeft + (day - d0) / dSpan * plotW).toFloat()
        fun py(v: Double): Float = (plotTop + (hi - v) / (hi - lo) * plotH).toFloat()
        fun dayAt(x: Float): Double = d0 + (x - plotLeft) / plotW * dSpan

        val n = spline.size
        val xsPx = FloatArray(n) { px(spline.xs[it]) }
        val ysPx = FloatArray(n) { py(spline.ys[it]) }
        val zeroY = py(0.0)

        val line = Path().apply {
            moveTo(xsPx[0], ysPx[0])
            for (i in 0 until n - 1) {
                val c = spline.controlPoints(i)
                cubicTo(px(c[0]), py(c[1]), px(c[2]), py(c[3]), xsPx[i + 1], ysPx[i + 1])
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(xsPx[n - 1], zeroY)
            lineTo(xsPx[0], zeroY)
            close()
        }

        // 1. 面积：零轴以上主文字色、以下红色，均从曲线一侧淡出到零轴
        if (maxV > 0) {
            clipRect(top = 0f, bottom = zeroY) {
                drawPath(
                    fill,
                    Brush.verticalGradient(listOf(ink.copy(alpha = 0.12f), ink.copy(alpha = 0f)), startY = py(maxV.toDouble()), endY = zeroY),
                )
            }
        }
        if (minV < 0) {
            clipRect(top = zeroY, bottom = size.height) {
                drawPath(
                    fill,
                    Brush.verticalGradient(listOf(red.copy(alpha = 0f), red.copy(alpha = 0.10f)), startY = zeroY, endY = py(minV.toDouble())),
                )
            }
        }

        // 2. 零轴虚线（仅数据 X 范围）
        drawLine(
            AxisGray.copy(alpha = 0.6f), Offset(xsPx[0], zeroY), Offset(xsPx[n - 1], zeroY), 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
        )

        // 3. 曲线（单色）
        drawPath(line, ink, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // 4. 刻度：底部首/末日期
        val dateY = size.height - dateFirst.size.height
        drawText(dateFirst, topLeft = Offset(0f, dateY))
        drawText(dateLast, topLeft = Offset(size.width - dateLast.size.width, dateY))

        // 5. 右侧数值标签：max（线上方）、min（线下方）、0（线上方）；与曲线或已画标签重叠则不画
        val gap = 2.dp.toPx()
        val pad = 3.dp.toPx()
        val placed = ArrayList<Pair<Float, Float>>() // 已占用的 [top, bottom]
        fun tryLabel(value: Long, above: Boolean) {
            val t = measurer.measure(Money.formatShort(value), labelStyle)
            val levelY = py(value.toDouble())
            val top = (if (above) levelY - gap - t.size.height else levelY + gap)
                .coerceIn(0f, (plotBottom + 4.dp.toPx() - t.size.height).coerceAtLeast(0f))
            val bottom = top + t.size.height
            if (placed.any { top < it.second + gap && bottom > it.first - gap }) return
            val left = size.width - t.size.width
            // 采样曲线在标签横向范围内的高度，判断是否压线
            val x0 = (left - pad).coerceAtLeast(plotLeft)
            val steps = 16
            for (s in 0..steps) {
                val x = x0 + (plotRight - x0) * s / steps
                if (x > plotRight) break
                val y = py(spline.evaluate(dayAt(x)))
                if (y >= top - pad && y <= bottom + pad) return
            }
            // 端点圆点
            val ey = ysPx[n - 1]
            if (xsPx[n - 1] + dotR + ring >= left - pad && ey + dotR + ring >= top - pad && ey - dotR - ring <= bottom + pad) return
            placed.add(top to bottom)
            drawText(t, topLeft = Offset(left, top))
        }
        if (maxV > 0) tryLabel(maxV, above = true)
        if (minV < 0) tryLabel(minV, above = false)
        tryLabel(0L, above = true)

        // 6. 端点
        val last = Offset(xsPx[n - 1], ysPx[n - 1])
        val endColor = if (points.last().value < 0) red else ink
        drawCircle(card, dotR + ring, last)
        drawCircle(endColor, dotR, last)

        // 7. 触摸查值
        val a = overlayAlpha
        if (a > 0.01f && !touchX.isNaN()) {
            val i = ChartMath.nearestIndex(xsPx, touchX)
            val p = Offset(xsPx[i], ysPx[i])
            val v = points[i].value
            drawLine(AxisGray.copy(alpha = 0.4f * a), Offset(p.x, plotTop), Offset(p.x, plotBottom), 1.dp.toPx())
            drawCircle(card.copy(alpha = a), 6.5.dp.toPx() + ring, p)
            drawCircle((if (v < 0) red else ink).copy(alpha = a), 6.5.dp.toPx(), p)
            val t = measurer.measure("${Dates.monthDayLabel(points[i].day)} · ${Money.formatSigned(v)}", chipStyle)
            val cw = t.size.width + 16.dp.toPx()
            val chH = t.size.height + 8.dp.toPx()
            val cl = ChartMath.chipLeft(p.x, cw, size.width)
            val off = 12.dp.toPx()
            var ct = p.y - off - chH
            if (ct < 0f) ct = p.y + off
            ct = ct.coerceIn(0f, (size.height - chH).coerceAtLeast(0f))
            drawRoundRect(ink.copy(alpha = a), Offset(cl, ct), Size(cw, chH), CornerRadius(8.dp.toPx()))
            drawText(t, topLeft = Offset(cl + 8.dp.toPx(), ct + 4.dp.toPx()), alpha = a)
        }
    }
}

/** 只有一个数据点：居中画点 + 数值文字 + 日期，不画线 */
private fun DrawScope.drawSinglePoint(
    p: SeriesPoint, ink: Color, red: Color, card: Color,
    measurer: TextMeasurer, labelStyle: TextStyle,
) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val color = if (p.value < 0) red else ink
    drawCircle(card, 7.dp.toPx(), c)
    drawCircle(color, 5.dp.toPx(), c)
    val v: TextLayoutResult = measurer.measure(
        Money.formatSigned(p.value),
        labelStyle.copy(fontSize = 12.sp, color = color, fontWeight = FontWeight.SemiBold),
    )
    drawText(v, topLeft = Offset(c.x - v.size.width / 2f, c.y - 7.dp.toPx() - 6.dp.toPx() - v.size.height))
    val d = measurer.measure(Dates.dayLabel(p.day), labelStyle)
    drawText(d, topLeft = Offset(c.x - d.size.width / 2f, size.height - d.size.height))
}
