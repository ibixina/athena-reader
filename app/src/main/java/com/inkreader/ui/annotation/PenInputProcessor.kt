package com.inkreader.ui.annotation

import com.inkreader.domain.model.StrokePoint
import kotlin.math.hypot

object PenInputProcessor {

    // Reduce point density: skip points closer than this threshold (in pixels)
    private const val MIN_DISTANCE = 2f

    fun simplify(raw: List<StrokePoint>): List<StrokePoint> {
        if (raw.size <= 2) return raw
        val result = mutableListOf(raw.first())
        for (i in 1 until raw.size - 1) {
            val prev = result.last()
            val curr = raw[i]
            if (hypot(curr.x - prev.x, curr.y - prev.y) >= MIN_DISTANCE) {
                result.add(curr)
            }
        }
        result.add(raw.last())
        return result
    }

    fun pressureToWidth(baseWidth: Float, pressure: Float): Float =
        baseWidth * (0.5f + pressure.coerceIn(0f, 1f) * 0.5f)
}
