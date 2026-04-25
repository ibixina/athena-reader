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

    private const val MIN_LINE_HEIGHT = 16f
    // Horizontal padding in PDF points to capture edge characters
    private const val H_PAD = 6f

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

        // Expand horizontally to capture edge characters
        minX -= H_PAD
        maxX += H_PAD

        // Expand vertically so the region covers actual text glyphs
        val height = maxY - minY
        if (height < MIN_LINE_HEIGHT) {
            val expand = (MIN_LINE_HEIGHT - height) / 2f
            minY -= expand
            maxY += expand
        }

        val x = minX.toInt().coerceAtLeast(0)
        val y = minY.toInt().coerceAtLeast(0)
        return BoundingBox(pageIndex, x, y, (maxX - minX).toInt().coerceAtLeast(1), (maxY - minY).toInt().coerceAtLeast(1))
    }

    suspend fun extractText(
        pdfRenderer: PdfRenderer,
        bounds: BoundingBox
    ): String {
        val raw = pdfRenderer.extractText(bounds.pageIndex, bounds.x, bounds.y, bounds.width, bounds.height)
        return sanitize(raw)
    }

    private fun sanitize(text: String): String {
        return text
            .replace("\r\n", " ")
            .replace("\r", " ")
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
