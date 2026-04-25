package com.athenareader.ui.annotation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.athenareader.domain.model.PenTool
import com.athenareader.domain.model.Stroke
import com.athenareader.domain.model.StrokePoint
import com.athenareader.ui.annotation.StrokeRenderer.drawLiveStroke
import com.athenareader.ui.annotation.StrokeRenderer.drawStroke

/**
 * Pure rendering overlay — draws persisted strokes and the live stroke.
 * Pointer/stylus input is now handled by PdfReaderView directly.
 */
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
    pageCount: Int
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        for (stroke in strokes) {
            drawStroke(stroke, scale, offsetX, offsetY)
        }

        if (livePoints.isNotEmpty() && activeTool != null) {
            drawLiveStroke(livePoints, activeColor, strokeWidth, activeTool, scale, offsetX, offsetY)
        }
    }
}
