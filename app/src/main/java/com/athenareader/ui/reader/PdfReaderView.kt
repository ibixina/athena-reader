package com.athenareader.ui.reader

import android.graphics.Bitmap
import android.view.MotionEvent
import android.widget.OverScroller
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.athenareader.core.cache.TileCache
import com.athenareader.domain.model.Document
import com.athenareader.domain.model.PenTool
import com.athenareader.domain.model.ReadingProgress
import com.athenareader.domain.model.Stroke
import com.athenareader.domain.model.StrokePoint
import com.athenareader.ui.annotation.StrokeRenderer.drawLiveStroke
import com.athenareader.ui.annotation.StrokeRenderer.drawStroke
import com.athenareader.ui.renderer.ViewportManager
import com.athenareader.ui.renderer.pdf.TileRendererPool
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow

private const val MIN_SCALE = 0.5f
private const val MAX_SCALE = 5.0f
private const val PAGE_GAP = 4
private const val DOUBLE_TAP_TIMEOUT_MS = 300L
private const val TAP_SLOP = 20f
private const val LONG_PRESS_MS = 400L
private const val PREFETCH_OVERSCAN_TILES = 1
private const val PREFETCH_PRIORITY_OFFSET = 100_000

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PdfReaderView(
    document: Document,
    pageCount: Int,
    pageWidth: Int,
    pageHeight: Int,
    tilePool: TileRendererPool,
    viewportManager: ViewportManager,
    initialProgress: ReadingProgress?,
    requestedPageJump: Pair<Int, Int>?,  // (pageIndex, counter) to allow repeated jumps
    activeToolFlow: StateFlow<PenTool?>,
    strokesFlow: StateFlow<List<Stroke>>,
    livePointsFlow: StateFlow<List<StrokePoint>>,
    activeColorFlow: StateFlow<Int>,
    strokeWidthFlow: StateFlow<Float>,
    onPointAdded: (StrokePoint) -> Unit,
    onStrokeCommitted: (pageIndex: Int, isStylusButtonErasing: Boolean, isTouchHighlight: Boolean) -> Unit,
    onViewportChanged: (pageIndex: Int, scrollX: Int, scrollY: Int, zoom: Float) -> Unit,
    onSingleTap: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val loadedTiles = remember(document.id) { mutableStateMapOf<String, Bitmap>() }
    val context = LocalContext.current
    val scroller = remember { OverScroller(context) }

    // Collect flows as State — read only inside Canvas to avoid recomposition
    val strokes by strokesFlow.collectAsState()
    val livePoints by livePointsFlow.collectAsState()
    val activeColor by activeColorFlow.collectAsState()
    val strokeWidth by strokeWidthFlow.collectAsState()
    val activeTool by activeToolFlow.collectAsState()

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

        LaunchedEffect(requestedPageJump) {
            val (targetPage, _) = requestedPageJump ?: return@LaunchedEffect
            val targetTop = targetPage * (pageHeight + PAGE_GAP)
            offsetY = (-1f * targetTop * scale).coerceIn(boundsY(scale))
        }

        var isStylusDrawing by remember { mutableStateOf(false) }
        var stylusButtonHeld by remember { mutableStateOf(false) }
        var flingJob by remember { mutableStateOf<Job?>(null) }
        var lastTapTime by remember { mutableLongStateOf(0L) }
        var fingerDownX by remember { mutableFloatStateOf(0f) }
        var fingerDownY by remember { mutableFloatStateOf(0f) }
        var lastFingerX by remember { mutableFloatStateOf(0f) }
        var lastFingerY by remember { mutableFloatStateOf(0f) }
        var fingerDragged by remember { mutableStateOf(false) }
        var fingerDown by remember { mutableStateOf(false) }
        var secondPointerDown by remember { mutableStateOf(false) }
        var lastPointer0X by remember { mutableFloatStateOf(0f) }
        var lastPointer0Y by remember { mutableFloatStateOf(0f) }
        var lastPointer1X by remember { mutableFloatStateOf(0f) }
        var lastPointer1Y by remember { mutableFloatStateOf(0f) }
        var lastPinchDist by remember { mutableFloatStateOf(0f) }
        val velocitySamples = remember { mutableListOf<Triple<Long, Float, Float>>() }

        // Long-press touch highlight
        var isTouchHighlighting by remember { mutableStateOf(false) }
        var fingerDownTime by remember { mutableLongStateOf(0L) }
        var longPressJob by remember { mutableStateOf<Job?>(null) }

        // Palm rejection: timestamp of last stylus event (hover or touch)
        var lastStylusEventMs by remember { mutableLongStateOf(0L) }
        val palmCooldownMs = 300L

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { event ->
                    val isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS || 
                                   event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER

                    // Track stylus proximity (hover events)
                    if (isStylus) {
                        lastStylusEventMs = System.currentTimeMillis()
                    }

                    // Aggressively reject finger events when stylus is near or was recently used
                    val palmCooldownMs = 1000L // Increased cooldown
                    val stylusNear = isStylusDrawing ||
                        (System.currentTimeMillis() - lastStylusEventMs < palmCooldownMs)
                    val isFinger = !isStylus
                    
                    if (isFinger && stylusNear) {
                        // Cancel any ongoing touch operations
                        fingerDragged = false
                        fingerDown = false
                        secondPointerDown = false
                        isTouchHighlighting = false
                        longPressJob?.cancel()
                        return@pointerInteropFilter true // consume and ignore
                    }

                    val shouldDraw = isStylus && (activeTool != null || hasStylusButton(event))

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            if (shouldDraw) {
                                isStylusDrawing = true
                                stylusButtonHeld = hasStylusButton(event)
                                onPointAdded(StrokePoint(
                                    x = (event.x - offsetX) / scale,
                                    y = (event.y - offsetY) / scale,
                                    pressure = event.pressure,
                                    timestamp = event.eventTime
                                ))
                            } else {
                                flingJob?.cancel(); flingJob = null
                                scroller.forceFinished(true)
                                fingerDownX = event.x; fingerDownY = event.y
                                lastFingerX = event.x; lastFingerY = event.y
                                fingerDragged = false; fingerDown = true; secondPointerDown = false
                                isTouchHighlighting = false
                                fingerDownTime = event.eventTime
                                velocitySamples.clear()
                                velocitySamples.add(Triple(event.eventTime, event.x, event.y))
                                // Schedule long press detection
                                longPressJob?.cancel()
                                longPressJob = scope.launch {
                                    delay(LONG_PRESS_MS)
                                    if (fingerDown && !fingerDragged && !secondPointerDown) {
                                        isTouchHighlighting = true
                                        onPointAdded(StrokePoint(
                                            x = (fingerDownX - offsetX) / scale,
                                            y = (fingerDownY - offsetY) / scale,
                                            pressure = 0.5f,
                                            timestamp = fingerDownTime
                                        ))
                                    }
                                }
                            }
                            true
                        }

                        MotionEvent.ACTION_POINTER_DOWN -> {
                            if (!isStylusDrawing && event.pointerCount >= 2) {
                                secondPointerDown = true; fingerDragged = true
                                lastPointer0X = event.getX(0); lastPointer0Y = event.getY(0)
                                lastPointer1X = event.getX(1); lastPointer1Y = event.getY(1)
                                lastPinchDist = pinchDist(event)
                            }
                            true
                        }

                        MotionEvent.ACTION_POINTER_UP -> {
                            if (!isStylusDrawing && event.pointerCount <= 2) {
                                secondPointerDown = false
                                val remainIdx = if (event.actionIndex == 0) 1 else 0
                                if (remainIdx < event.pointerCount) {
                                    lastFingerX = event.getX(remainIdx)
                                    lastFingerY = event.getY(remainIdx)
                                }
                            }
                            true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            if (isStylusDrawing) {
                                // Track button across entire stroke
                                if (hasStylusButton(event)) stylusButtonHeld = true
                                for (i in 0 until event.historySize) {
                                    onPointAdded(StrokePoint(
                                        x = (event.getHistoricalX(i) - offsetX) / scale,
                                        y = (event.getHistoricalY(i) - offsetY) / scale,
                                        pressure = event.getHistoricalPressure(i),
                                        timestamp = event.getHistoricalEventTime(i)
                                    ))
                                }
                                onPointAdded(StrokePoint(
                                    x = (event.x - offsetX) / scale,
                                    y = (event.y - offsetY) / scale,
                                    pressure = event.pressure,
                                    timestamp = event.eventTime
                                ))
                            } else if (secondPointerDown && event.pointerCount >= 2) {
                                val p0x = event.getX(0); val p0y = event.getY(0)
                                val p1x = event.getX(1); val p1y = event.getY(1)
                                val newDist = pinchDist(event)

                                if (lastPinchDist > 0f) {
                                    val zoom = newDist / lastPinchDist
                                    val cx = (p0x + p1x) / 2f; val cy = (p0y + p1y) / 2f
                                    val panX = (p0x - lastPointer0X + p1x - lastPointer1X) / 2f
                                    val panY = (p0y - lastPointer0Y + p1y - lastPointer1Y) / 2f
                                    val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                                    val ratio = newScale / scale
                                    val focusX = (cx - offsetX) * (1 - ratio)
                                    val focusY = (cy - offsetY) * (1 - ratio)
                                    scale = newScale
                                    offsetX = (offsetX + focusX + panX).coerceIn(boundsX(newScale))
                                    offsetY = (offsetY + focusY + panY).coerceIn(boundsY(newScale))
                                }
                                lastPointer0X = p0x; lastPointer0Y = p0y
                                lastPointer1X = p1x; lastPointer1Y = p1y
                                lastPinchDist = newDist
                            } else if (fingerDown) {
                                val dx = event.x - lastFingerX; val dy = event.y - lastFingerY
                                val totalDx = event.x - fingerDownX; val totalDy = event.y - fingerDownY
                                if (totalDx * totalDx + totalDy * totalDy > TAP_SLOP * TAP_SLOP) {
                                    fingerDragged = true
                                }

                                offsetX = (offsetX + dx).coerceIn(boundsX(scale))
                                offsetY = (offsetY + dy).coerceIn(boundsY(scale))
                                lastFingerX = event.x; lastFingerY = event.y
                                velocitySamples.add(Triple(event.eventTime, event.x, event.y))
                                if (velocitySamples.size > 20) velocitySamples.removeAt(0)
                            }
                            true
                        }

                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            longPressJob?.cancel()
                            if (isStylusDrawing) {
                                val wasErasing = stylusButtonHeld
                                isStylusDrawing = false
                                stylusButtonHeld = false
                                val contentY = (event.y - offsetY) / scale
                                val fullPageH = pageHeight + PAGE_GAP
                                val pageIndex = (contentY / fullPageH).toInt().coerceIn(0, pageCount - 1)
                                onStrokeCommitted(pageIndex, wasErasing, false)
                            } else if (isTouchHighlighting) {
                                isTouchHighlighting = false
                                fingerDown = false
                                val contentY = (event.y - offsetY) / scale
                                val fullPageH = pageHeight + PAGE_GAP
                                val pageIndex = (contentY / fullPageH).toInt().coerceIn(0, pageCount - 1)
                                onStrokeCommitted(pageIndex, false, true)
                            } else if (fingerDown) {
                                fingerDown = false
                                if (!fingerDragged) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastTapTime < DOUBLE_TAP_TIMEOUT_MS) {
                                        lastTapTime = 0L
                                        scale = fitScale; offsetX = 0f
                                        offsetY = offsetY.coerceIn(boundsY(fitScale))
                                    } else {
                                        lastTapTime = now
                                        scope.launch {
                                            delay(DOUBLE_TAP_TIMEOUT_MS)
                                            if (lastTapTime != 0L) onSingleTap()
                                        }
                                    }
                                } else {
                                    lastTapTime = 0L
                                    val vx: Float; val vy: Float
                                    if (velocitySamples.size >= 2) {
                                        val last = velocitySamples.last()
                                        val prev = velocitySamples[velocitySamples.size - 2]
                                        val dt = (last.first - prev.first).coerceAtLeast(1L)
                                        vx = (last.second - prev.second) / dt * 1000f
                                        vy = (last.third - prev.third) / dt * 1000f
                                    } else { vx = 0f; vy = 0f }
                                    val bx = boundsX(scale); val by = boundsY(scale)
                                    scroller.fling(
                                        offsetX.toInt(), offsetY.toInt(),
                                        vx.toInt(), vy.toInt(),
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
                            true
                        }

                        else -> false
                    }
                }
        ) {
            // Update viewport manager BEFORE computing tiles so first render has correct data
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

            LaunchedEffect(scale, offsetX, offsetY, viewportW, viewportH, pageCount, pageHeight) {
                delay(100)
                val page = ((-offsetY / scale) / (pageHeight + PAGE_GAP))
                    .toInt()
                    .coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                onViewportChanged(
                    page,
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
                    .map { tile -> TileCache.generateKey(tile.pageIndex, tile.x, tile.y, tile.zoom) }
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

            // Single Canvas: tiles + strokes drawn together to eliminate lag
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Draw tiles
                visibleTiles.forEach { tile ->
                    val key = TileCache.generateKey(tile.pageIndex, tile.x, tile.y, tile.zoom)
                    val bitmap = loadedTiles[key] ?: return@forEach
                    val globalY = tile.pageIndex * (pageHeight + PAGE_GAP) + tile.y
                    
                    val startX = (tile.x * scale + offsetX).toInt()
                    val startY = (globalY * scale + offsetY).toInt()
                    val endX = ((tile.x + tile.width) * scale + offsetX).toInt()
                    val endY = ((globalY + tile.height) * scale + offsetY).toInt()

                    drawImage(
                        image = bitmap.asImageBitmap(),
                        dstOffset = IntOffset(startX, startY),
                        dstSize = IntSize(endX - startX, endY - startY)
                    )
                }

                // Draw persisted strokes
                for (stroke in strokes) {
                    drawStroke(stroke, scale, offsetX, offsetY)
                }

                // Draw live stroke
                if (livePoints.isNotEmpty() && (activeTool != null || stylusButtonHeld)) {
                    val renderTool = activeTool ?: PenTool.ERASER
                    drawLiveStroke(livePoints, activeColor, strokeWidth, renderTool, scale, offsetX, offsetY, isErasing = stylusButtonHeld)
                }
            }
        }
    }
}

private fun pinchDist(event: MotionEvent): Float {
    if (event.pointerCount < 2) return 0f
    val dx = event.getX(0) - event.getX(1)
    val dy = event.getY(0) - event.getY(1)
    return kotlin.math.hypot(dx, dy)
}

private fun hasStylusButton(event: MotionEvent): Boolean {
    val bs = event.buttonState
    return (bs and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0 ||
        (bs and MotionEvent.BUTTON_SECONDARY) != 0
}
