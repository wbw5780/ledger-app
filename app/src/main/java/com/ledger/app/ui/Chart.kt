package com.ledger.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledger.app.util.Dates
import com.ledger.app.util.Money
import com.ledger.app.util.SeriesPoint

/**
 * 累计盈亏折线图（纯 Canvas 绘制）。线条 3dp：零轴以上为主文字色（黑/白），
 * 以下为红色；零轴上方灰色渐变填充（主文字色 18%→透明），下方红色渐变（25%→透明），
 * 无实线坐标轴，零轴淡灰虚线，刻度文字 10.5sp 淡灰，末端点灰色光晕。
 */
@Composable
fun CumulativeChart(
    points: List<SeriesPoint>,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
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
    val pos = MoneyColors.income()
    val neg = MoneyColors.expense()
    val glow = MaterialTheme.colorScheme.onSurfaceVariant
    val cardColor = LocalLedger.current.card
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.5.sp, color = labelColor, fontFeatureSettings = "tnum")

    // 单点时补一个点，画成一条水平线
    val pts = remember(points) {
        if (points.size == 1) listOf(SeriesPoint(points[0].day - 1, 0L), points[0]) else points
    }
    val maxV = remember(pts) { maxOf(pts.maxOf { it.value }, 0L) }
    val minV = remember(pts) { minOf(pts.minOf { it.value }, 0L) }

    Canvas(modifier.fillMaxWidth().height(height)) {
        val maxLabel = measurer.measure(Money.formatShort(maxV), labelStyle)
        val minLabel = measurer.measure(Money.formatShort(minV), labelStyle)
        val leftPad = maxOf(maxLabel.size.width, minLabel.size.width).toFloat() + 6.dp.toPx()
        val bottomPad = maxLabel.size.height.toFloat() + 6.dp.toPx()
        val topPad = 8.dp.toPx()
        val rightPad = 8.dp.toPx()

        val w = size.width - leftPad - rightPad
        val h = size.height - topPad - bottomPad
        if (w <= 0f || h <= 0f) return@Canvas

        val range = (maxV - minV).toFloat().let { if (it == 0f) 1f else it }
        val firstDay = pts.first().day
        val daySpan = (pts.last().day - firstDay).toFloat().let { if (it == 0f) 1f else it }
        fun x(day: Long) = leftPad + (day - firstDay) / daySpan * w
        fun y(v: Long) = topPad + (maxV - v) / range * h
        val zeroY = y(0)

        // 不画实线坐标轴，仅保留淡色零轴虚线
        drawLine(
            axisColor, Offset(leftPad, zeroY), Offset(leftPad + w, zeroY), 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            cap = StrokeCap.Round,
        )

        // 平滑曲线（单调 cubic：控制点取相邻中点横坐标）
        val offsets = pts.map { Offset(x(it.day), y(it.value)) }
        val line = Path().apply {
            moveTo(offsets[0].x, offsets[0].y)
            for (i in 1 until offsets.size) {
                val p0 = offsets[i - 1]
                val p1 = offsets[i]
                val mx = (p0.x + p1.x) / 2f
                cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(offsets.last().x, zeroY)
            lineTo(offsets.first().x, zeroY)
            close()
        }
        val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

        // 零轴以上：主文字色（灰色渐变）
        clipRect(left = 0f, top = 0f, right = size.width, bottom = zeroY) {
            drawPath(
                fill,
                Brush.verticalGradient(listOf(pos.copy(alpha = 0.18f), pos.copy(alpha = 0.0f)), startY = topPad, endY = zeroY)
            )
            drawPath(line, pos, style = stroke)
        }
        // 零轴以下：红色
        clipRect(left = 0f, top = zeroY, right = size.width, bottom = size.height) {
            drawPath(
                fill,
                Brush.verticalGradient(listOf(neg.copy(alpha = 0.0f), neg.copy(alpha = 0.25f)), startY = zeroY, endY = topPad + h)
            )
            drawPath(line, neg, style = stroke)
        }
        // 末端点
        val last = offsets.last()
        val lastColor = if (pts.last().value >= 0) pos else neg
        drawCircle(glow.copy(alpha = 0.3f), 8.dp.toPx(), last)
        drawCircle(lastColor, 5.dp.toPx(), last)
        drawCircle(cardColor, 2.2.dp.toPx(), last)

        // Y 轴标签：最大、最小、0
        drawText(maxLabel, topLeft = Offset(0f, topPad - maxLabel.size.height / 2f).let { Offset(it.x, it.y.coerceAtLeast(0f)) })
        drawText(minLabel, topLeft = Offset(0f, (topPad + h - minLabel.size.height / 2f).coerceAtMost(size.height - bottomPad - minLabel.size.height / 2f)))
        if (maxV != 0L && minV != 0L) {
            val zl = measurer.measure("0", labelStyle)
            drawText(zl, topLeft = Offset(leftPad - zl.size.width - 4.dp.toPx(), zeroY - zl.size.height / 2f))
        }
        // X 轴标签：首末日期
        val startL = measurer.measure(Dates.dayLabel(points.first().day), labelStyle)
        val endL = measurer.measure(Dates.dayLabel(points.last().day), labelStyle)
        val ly = size.height - startL.size.height
        drawText(startL, topLeft = Offset(leftPad, ly))
        drawText(endL, topLeft = Offset(size.width - rightPad - endL.size.width, ly))
    }
}
