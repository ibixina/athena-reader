package com.athenareader.ui.annotation

import com.athenareader.domain.model.StrokePoint
import com.athenareader.domain.renderer.PdfRenderer

object HighlightProcessor {

    data class BoundingBox(
        val pageIndex: Int,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    )

    fun computeBounds(
        points: List<StrokePoint>,
        pageIndex: Int,
        pageHeight: Int,
        pageGap: Int
    ): BoundingBox? {
        if (points.isEmpty()) return null
        val pageTopY = pageIndex * (pageHeight + pageGap)

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (p in points) {
            val localY = p.y - pageTopY
            if (p.x < minX) minX = p.x
            if (localY < minY) minY = localY
            if (p.x > maxX) maxX = p.x
            if (localY > maxY) maxY = localY
        }

        val x = minX.toInt()
        val y = minY.toInt()
        return BoundingBox(pageIndex, x, y, (maxX - minX).toInt().coerceAtLeast(1), (maxY - minY).toInt().coerceAtLeast(1))
    }

    suspend fun extractText(
        pdfRenderer: PdfRenderer,
        bounds: BoundingBox
    ): String {
        return pdfRenderer.extractText(bounds.pageIndex, bounds.x, bounds.y, bounds.width, bounds.height)
    }
}
