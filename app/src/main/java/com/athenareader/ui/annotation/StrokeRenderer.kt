package com.athenareader.ui.annotation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as ComposeStroke
import com.athenareader.domain.model.PenTool
import com.athenareader.domain.model.StrokePoint
import com.athenareader.domain.model.Stroke

object StrokeRenderer {

    fun DrawScope.drawStroke(
        stroke: Stroke,
        scale: Float,
        offsetX: Float,
        offsetY: Float
    ) {
        if (stroke.points.size < 2) return

        val path = buildPath(stroke.points, scale, offsetX, offsetY)
        val alpha = if (stroke.tool == PenTool.HIGHLIGHTER) 0.35f else stroke.opacity
        val width = stroke.strokeWidth * scale

        drawPath(
            path = path,
            color = Color(stroke.color).copy(alpha = alpha),
            style = ComposeStroke(
                width = width,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }

    fun DrawScope.drawLiveStroke(
        points: List<StrokePoint>,
        color: Int,
        strokeWidth: Float,
        tool: PenTool,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        isErasing: Boolean = false
    ) {
        if (points.size < 2) return

        val path = buildPath(points, scale, offsetX, offsetY)

        if (isErasing) {
            // Semi-transparent dark overlay so user sees erase path
            drawPath(
                path = path,
                color = Color(0xFF444444).copy(alpha = 0.25f),
                style = ComposeStroke(
                    width = 24f * scale,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        } else {
            val alpha = if (tool == PenTool.HIGHLIGHTER) 0.35f else 1f
            drawPath(
                path = path,
                color = Color(color).copy(alpha = alpha),
                style = ComposeStroke(
                    width = strokeWidth * scale,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }

    private fun buildPath(
        points: List<StrokePoint>,
        scale: Float,
        ox: Float,
        oy: Float
    ): Path = Path().apply {
        val first = points.first()
        moveTo(first.x * scale + ox, first.y * scale + oy)

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            // Quadratic Bézier through midpoints for smooth curves
            val midX = (prev.x + curr.x) / 2f * scale + ox
            val midY = (prev.y + curr.y) / 2f * scale + oy
            quadraticTo(
                prev.x * scale + ox,
                prev.y * scale + oy,
                midX, midY
            )
        }
        val last = points.last()
        lineTo(last.x * scale + ox, last.y * scale + oy)
    }
}
