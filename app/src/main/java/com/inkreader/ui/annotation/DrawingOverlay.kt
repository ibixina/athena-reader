package com.inkreader.ui.annotation

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import com.inkreader.domain.model.PenTool
import com.inkreader.domain.model.Stroke
import com.inkreader.domain.model.StrokePoint
import com.inkreader.ui.annotation.StrokeRenderer.drawLiveStroke
import com.inkreader.ui.annotation.StrokeRenderer.drawStroke

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingOverlay(
    strokes: List<Stroke>,
    livePoints: List<StrokePoint>,
    activeTool: PenTool?,
    activeColor: Int,
    strokeWidth: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    pageWidth: Int,
    pageHeight: Int,
    pageGap: Int,
    pageCount: Int,
    onPointAdded: (StrokePoint) -> Unit,
    onStrokeCommitted: (pageIndex: Int) -> Unit
) {
    // Track whether we're currently drawing
    var isDrawing by remember { mutableStateOf(false) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInteropFilter { event ->
                // Only intercept stylus events when annotation mode is active
                val isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
                if (activeTool == null || (!isStylus && activeTool != PenTool.ERASER)) {
                    return@pointerInteropFilter false
                }

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isDrawing = true
                        val point = toPagePoint(event, scale, offsetX, offsetY)
                        onPointAdded(point.copy(pressure = event.pressure))
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isDrawing) {
                            // Process batched historical events for higher fidelity
                            for (i in 0 until event.historySize) {
                                val point = StrokePoint(
                                    x = (event.getHistoricalX(i) - offsetX) / scale,
                                    y = (event.getHistoricalY(i) - offsetY) / scale,
                                    pressure = event.getHistoricalPressure(i),
                                    timestamp = event.getHistoricalEventTime(i)
                                )
                                onPointAdded(point)
                            }
                            val point = toPagePoint(event, scale, offsetX, offsetY)
                            onPointAdded(point.copy(pressure = event.pressure))
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDrawing) {
                            isDrawing = false
                            // Determine which page this stroke belongs to
                            val pageIndex = resolvePageIndex(event, scale, offsetY, pageHeight, pageGap, pageCount)
                            onStrokeCommitted(pageIndex)
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        // Draw persisted strokes
        for (stroke in strokes) {
            drawStroke(stroke, scale, offsetX, offsetY)
        }

        // Draw live stroke
        if (livePoints.isNotEmpty() && activeTool != null) {
            drawLiveStroke(livePoints, activeColor, strokeWidth, activeTool, scale, offsetX, offsetY)
        }
    }
}

private fun toPagePoint(event: MotionEvent, scale: Float, offsetX: Float, offsetY: Float): StrokePoint {
    return StrokePoint(
        x = (event.x - offsetX) / scale,
        y = (event.y - offsetY) / scale,
        pressure = event.pressure,
        timestamp = event.eventTime
    )
}

private fun resolvePageIndex(
    event: MotionEvent,
    scale: Float,
    offsetY: Float,
    pageHeight: Int,
    pageGap: Int,
    pageCount: Int
): Int {
    val contentY = (event.y - offsetY) / scale
    val fullPageH = pageHeight + pageGap
    return (contentY / fullPageH).toInt().coerceIn(0, pageCount - 1)
}
