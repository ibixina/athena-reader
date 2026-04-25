package com.athenareader.ui.reader

import android.graphics.Bitmap
import android.widget.OverScroller
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.athenareader.core.cache.TileCache
import com.athenareader.domain.model.Document
import com.athenareader.domain.model.ReadingProgress
import com.athenareader.ui.renderer.ViewportManager
import com.athenareader.ui.renderer.pdf.TileRendererPool
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MIN_SCALE = 0.5f
private const val MAX_SCALE = 5.0f
private const val PAGE_GAP = 4
private const val DOUBLE_TAP_TIMEOUT_MS = 300L
private const val TAP_SLOP = 20f
private const val PREFETCH_OVERSCAN_TILES = 1
private const val PREFETCH_PRIORITY_OFFSET = 100_000

@Composable
fun PdfReaderView(
    document: Document,
    pageCount: Int,
    pageWidth: Int,
    pageHeight: Int,
    tilePool: TileRendererPool,
    viewportManager: ViewportManager,
    initialProgress: ReadingProgress?,
    requestedPageIndex: Int?,
    onTransformChanged: (scale: Float, offsetX: Float, offsetY: Float) -> Unit,
    onViewportChanged: (pageIndex: Int, scrollX: Int, scrollY: Int, zoom: Float) -> Unit,
    onSingleTap: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val loadedTiles = remember(document.id) { mutableStateMapOf<String, Bitmap>() }
    val context = LocalContext.current
    val scroller = remember { OverScroller(context) }

    LaunchedEffect(document.id) {
        loadedTiles.clear()
        tilePool.clearQueue()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportW = constraints.maxWidth.toFloat()
        val viewportH = constraints.maxHeight.toFloat()

        val fitScale = (viewportW / pageWidth.toFloat()).coerceIn(MIN_SCALE, MAX_SCALE)
        var scale by remember(document.id, fitScale) { mutableFloatStateOf(fitScale) }
        var offsetX by remember(document.id) { mutableFloatStateOf(0f) }
        var offsetY by remember(document.id) { mutableFloatStateOf(0f) }
        var restoredInitialProgress by remember(document.id) { mutableStateOf(false) }

        fun boundsX(s: Float): ClosedFloatingPointRange<Float> {
            val contentW = pageWidth * s
            val minX = (viewportW - contentW).coerceAtMost(0f)
            return minX..0f
        }

        fun boundsY(s: Float): ClosedFloatingPointRange<Float> {
            val contentH = (pageHeight + PAGE_GAP) * pageCount * s
            val minY = (viewportH - contentH).coerceAtMost(0f)
            return minY..0f
        }

        LaunchedEffect(document.id, initialProgress, fitScale) {
            if (!restoredInitialProgress) {
                val restoredScale = initialProgress?.zoom?.coerceIn(MIN_SCALE, MAX_SCALE) ?: fitScale
                scale = restoredScale
                offsetX = (-1f * (initialProgress?.scrollX ?: 0) * restoredScale).coerceIn(boundsX(restoredScale))
                offsetY = (-1f * (initialProgress?.scrollY ?: 0) * restoredScale).coerceIn(boundsY(restoredScale))
                restoredInitialProgress = true
            }
        }

        LaunchedEffect(requestedPageIndex) {
            val targetPage = requestedPageIndex ?: return@LaunchedEffect
            val targetTop = targetPage * (pageHeight + PAGE_GAP)
            offsetY = (-1f * targetTop * scale).coerceIn(boundsY(scale))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pageCount, pageWidth, pageHeight) {
                    var flingJob: Job? = null
                    var lastTapTime = 0L

                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        flingJob?.cancel()
                        flingJob = null
                        scroller.forceFinished(true)

                        val velocityTracker = VelocityTracker()
                        val downPosition = currentEvent.changes.first().position
                        var dragged = false

                        do {
                            val event = awaitPointerEvent()
                            val activePointers = event.changes.filter { it.pressed }

                            if (activePointers.size >= 2) {
                                dragged = true
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid()

                                val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                                val ratio = newScale / scale
                                val focusX = (centroid.x - offsetX) * (1 - ratio)
                                val focusY = (centroid.y - offsetY) * (1 - ratio)

                                scale = newScale
                                offsetX = (offsetX + focusX + pan.x).coerceIn(boundsX(newScale))
                                offsetY = (offsetY + focusY + pan.y).coerceIn(boundsY(newScale))
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (activePointers.size == 1) {
                                val change = activePointers.first()
                                val pan = event.calculatePan()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)

                                val dx = change.position.x - downPosition.x
                                val dy = change.position.y - downPosition.y
                                if (dx * dx + dy * dy > TAP_SLOP * TAP_SLOP) dragged = true

                                offsetX = (offsetX + pan.x).coerceIn(boundsX(scale))
                                offsetY = (offsetY + pan.y).coerceIn(boundsY(scale))
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (activePointers.isNotEmpty())

                        if (!dragged) {
                            val now = System.currentTimeMillis()
                            if (now - lastTapTime < DOUBLE_TAP_TIMEOUT_MS) {
                                lastTapTime = 0L
                                scale = fitScale
                                offsetX = 0f
                                offsetY = offsetY.coerceIn(boundsY(fitScale))
                            } else {
                                lastTapTime = now
                                scope.launch {
                                    kotlinx.coroutines.delay(DOUBLE_TAP_TIMEOUT_MS)
                                    if (lastTapTime != 0L) onSingleTap()
                                }
                            }
                        } else {
                            lastTapTime = 0L
                            val velocity = velocityTracker.calculateVelocity()
                            val bx = boundsX(scale)
                            val by = boundsY(scale)

                            scroller.fling(
                                offsetX.toInt(), offsetY.toInt(),
                                velocity.x.toInt(), velocity.y.toInt(),
                                bx.start.toInt(), bx.endInclusive.toInt(),
                                by.start.toInt(), by.endInclusive.toInt()
                            )

                            flingJob = scope.launch {
                                while (scroller.computeScrollOffset()) {
                                    offsetX = scroller.currX.toFloat()
                                    offsetY = scroller.currY.toFloat()
                                    withFrameNanos { }
                                }
                            }
                        }
                    }
                }
        ) {
            SideEffect {
                viewportManager.setZoom(scale)
                viewportManager.updateViewport(
                    (-offsetX / scale).toInt(),
                    (-offsetY / scale).toInt(),
                    ((-offsetX + viewportW) / scale).toInt(),
                    ((-offsetY + viewportH) / scale).toInt()
                )
                val currentPage = ((-offsetY / scale) / (pageHeight + PAGE_GAP))
                    .toInt()
                    .coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                viewportManager.setCurrentPage(currentPage)
            }

            LaunchedEffect(scale, offsetX, offsetY, viewportW, viewportH, pageCount, pageHeight) {
                onTransformChanged(scale, offsetX, offsetY)
                // Debounce persistence to actual viewport changes instead of composition churn.
                delay(100)
                val currentPage = ((-offsetY / scale) / (pageHeight + PAGE_GAP))
                    .toInt()
                    .coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                onViewportChanged(
                    currentPage,
                    (-offsetX / scale).toInt(),
                    (-offsetY / scale).toInt(),
                    scale
                )
            }

            val visibleTiles = remember(offsetX, offsetY, scale, pageCount, pageWidth, pageHeight) {
                viewportManager.getVisibleTiles(pageCount, pageWidth, pageHeight, PAGE_GAP)
            }
            val prefetchTiles = remember(offsetX, offsetY, scale, pageCount, pageWidth, pageHeight) {
                viewportManager.getPrefetchTiles(
                    pageCount = pageCount,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                    gap = PAGE_GAP,
                    overscanTiles = PREFETCH_OVERSCAN_TILES
                )
            }

            LaunchedEffect(visibleTiles, prefetchTiles) {
                val activeKeys = (visibleTiles.asSequence() + prefetchTiles.asSequence())
                    .map { tile ->
                        TileCache.generateKey(tile.pageIndex, tile.x, tile.y, tile.zoom)
                    }
                    .toSet()

                val staleKeys = loadedTiles.keys.filterNot(activeKeys::contains)
                staleKeys.forEach(loadedTiles::remove)

                visibleTiles.forEach { tile ->
                    val key = TileCache.generateKey(tile.pageIndex, tile.x, tile.y, tile.zoom)
                    if (!loadedTiles.containsKey(key)) {
                        val priority = viewportManager.calculateTilePriority(tile, pageHeight, PAGE_GAP)
                        tilePool.requestTile(tile, priority = priority) { bitmap ->
                            loadedTiles[key] = bitmap
                        }
                    }
                }

                prefetchTiles.forEach { tile ->
                    val key = TileCache.generateKey(tile.pageIndex, tile.x, tile.y, tile.zoom)
                    if (!loadedTiles.containsKey(key)) {
                        val priority = viewportManager.calculateTilePriority(tile, pageHeight, PAGE_GAP) +
                            PREFETCH_PRIORITY_OFFSET
                        tilePool.requestTile(tile, priority = priority) { bitmap ->
                            loadedTiles[key] = bitmap
                        }
                    }
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                visibleTiles.forEach { tile ->
                    val key = TileCache.generateKey(tile.pageIndex, tile.x, tile.y, tile.zoom)
                    val bitmap = loadedTiles[key] ?: return@forEach

                    val globalY = tile.pageIndex * (pageHeight + PAGE_GAP) + tile.y
                    drawImage(
                        image = bitmap.asImageBitmap(),
                        dstOffset = IntOffset(
                            (tile.x * scale + offsetX).toInt(),
                            (globalY * scale + offsetY).toInt()
                        ),
                        dstSize = IntSize(
                            (tile.width * scale).toInt(),
                            (tile.height * scale).toInt()
                        )
                    )
                }
            }
        }
    }
}
