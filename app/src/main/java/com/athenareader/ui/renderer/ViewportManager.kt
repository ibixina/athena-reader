package com.athenareader.ui.renderer

import android.graphics.Rect
import com.athenareader.domain.model.Tile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ViewportManager @Inject constructor() {
    private val tileSize = 256
    private val _viewport = MutableStateFlow(Rect())
    val viewport: StateFlow<Rect> = _viewport

    private var zoom = 1.0f
    private var currentPage = 0

    fun updateViewport(left: Int, top: Int, right: Int, bottom: Int) {
        _viewport.value = Rect(left, top, right, bottom)
    }

    fun setZoom(newZoom: Float) {
        zoom = newZoom
    }

    fun setCurrentPage(page: Int) {
        currentPage = page
    }

    fun getVisibleTiles(
        pageCount: Int,
        pageWidth: Int,
        pageHeight: Int,
        gap: Int = 20,
        overscanTiles: Int = 0
    ): List<Tile> {
        val visibleRect = expandedViewport(overscanTiles)
        val tiles = mutableListOf<Tile>()

        val startY = visibleRect.top
        val endY = visibleRect.bottom
        val startX = visibleRect.left
        val endX = visibleRect.right
        
        val fullPageHeight = pageHeight + gap

        val startPage = maxOf(0, startY / fullPageHeight)
        val endPage = minOf(pageCount - 1, endY / fullPageHeight)

        for (page in startPage..endPage) {
            val pageTop = page * fullPageHeight
            
            // The intersection of the page and the viewport relative to the page
            val pageVisibleTop = maxOf(0, startY - pageTop)
            val pageVisibleBottom = minOf(pageHeight, endY - pageTop)
            
            if (pageVisibleTop >= pageVisibleBottom) continue

            val tStartX = (maxOf(0, startX) / tileSize) * tileSize
            val tStartY = (pageVisibleTop / tileSize) * tileSize
            
            for (x in tStartX until endX step tileSize) {
                for (y in tStartY until pageVisibleBottom step tileSize) {
                    val width = if (x + tileSize > pageWidth) pageWidth - x else tileSize
                    val height = if (y + tileSize > pageHeight) pageHeight - y else tileSize
                    
                    if (width > 0 && height > 0 && x < pageWidth && y < pageHeight) {
                        tiles.add(
                            Tile(
                                pageIndex = page,
                                x = x,
                                y = y,
                                width = width,
                                height = height,
                                zoom = zoom
                            )
                        )
                    }
                }
            }
        }
        return tiles
    }

    fun getPrefetchTiles(
        pageCount: Int,
        pageWidth: Int,
        pageHeight: Int,
        gap: Int = 20,
        overscanTiles: Int = 1
    ): List<Tile> {
        if (overscanTiles <= 0) return emptyList()

        val visibleTiles = getVisibleTiles(pageCount, pageWidth, pageHeight, gap, overscanTiles = 0)
        val visibleKeys = visibleTiles.asSequence()
            .map { tileKey(it) }
            .toSet()

        return getVisibleTiles(pageCount, pageWidth, pageHeight, gap, overscanTiles = overscanTiles)
            .filterNot { tileKey(it) in visibleKeys }
            .sortedBy { calculateTilePriority(it, pageHeight, gap) }
    }

    fun calculateTilePriority(tile: Tile, pageHeight: Int, gap: Int = 20): Int {
        val visibleRect = _viewport.value
        val viewportCenterX = (visibleRect.left + visibleRect.right) / 2
        val viewportCenterY = (visibleRect.top + visibleRect.bottom) / 2
        val tileCenterX = tile.x + (tile.width / 2)
        val tileCenterY = tile.pageIndex * (pageHeight + gap) + tile.y + (tile.height / 2)
        val distance = kotlin.math.abs(tileCenterX - viewportCenterX) +
            kotlin.math.abs(tileCenterY - viewportCenterY)
        val pagePenalty = kotlin.math.abs(tile.pageIndex - currentPage) * 10_000
        return pagePenalty + distance
    }

    private fun expandedViewport(overscanTiles: Int): Rect {
        if (overscanTiles <= 0) return _viewport.value
        val overscan = overscanTiles * tileSize
        val viewport = _viewport.value
        return Rect(
            viewport.left - overscan,
            viewport.top - overscan,
            viewport.right + overscan,
            viewport.bottom + overscan
        )
    }

    private fun tileKey(tile: Tile): String {
        return "${tile.pageIndex}-${tile.x}-${tile.y}-${tile.zoom}"
    }
}
