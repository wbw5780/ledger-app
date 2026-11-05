package com.ledger.app.util

import com.ledger.app.data.Record

data class SeriesPoint(val day: Long, val value: Long)

object Series {
    /**
     * 按日累计净额：每日净额 -> 补齐空隙日(净额0) -> 前缀和 -> 压缩到 maxPoints 内（保留每桶极值）
     */
    fun cumulative(records: List<Record>, maxPoints: Int = 200): List<SeriesPoint> {
        if (records.isEmpty()) return emptyList()
        val daily = HashMap<Long, Long>()
        for (r in records) {
            val d = Dates.epochDay(r.timestamp)
            daily[d] = (daily[d] ?: 0L) + r.signedCents
        }
        val minDay = daily.keys.min()
        val maxDay = daily.keys.max()
        val full = ArrayList<SeriesPoint>((maxDay - minDay + 1).toInt().coerceAtMost(1_000_000))
        var acc = 0L
        var d = minDay
        while (d <= maxDay) {
            acc += daily[d] ?: 0L
            full.add(SeriesPoint(d, acc))
            d++
        }
        return downsample(full, maxPoints)
    }

    fun downsample(points: List<SeriesPoint>, maxPoints: Int): List<SeriesPoint> {
        if (points.size <= maxPoints) return points
        val buckets = (maxPoints - 2) / 2
        val inner = points.subList(1, points.size - 1)
        val out = ArrayList<SeriesPoint>(maxPoints)
        out.add(points.first())
        val size = inner.size.toDouble() / buckets
        for (b in 0 until buckets) {
            val start = (b * size).toInt()
            val end = ((b + 1) * size).toInt().coerceAtMost(inner.size)
            if (start >= end) continue
            var minI = start
            var maxI = start
            for (i in start until end) {
                if (inner[i].value < inner[minI].value) minI = i
                if (inner[i].value > inner[maxI].value) maxI = i
            }
            if (minI == maxI) out.add(inner[minI])
            else if (minI < maxI) { out.add(inner[minI]); out.add(inner[maxI]) }
            else { out.add(inner[maxI]); out.add(inner[minI]) }
        }
        out.add(points.last())
        return out
    }
}
