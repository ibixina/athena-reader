package com.inkreader.ui.annotation

import android.graphics.Color as AndroidColor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inkreader.domain.model.*
import com.inkreader.domain.repository.AnnotationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.hypot

@HiltViewModel
class AnnotationViewModel @Inject constructor(
    private val repository: AnnotationRepository
) : ViewModel() {

    private val _activeTool = MutableStateFlow<PenTool?>(null)
    val activeTool: StateFlow<PenTool?> = _activeTool

    private val _activeColor = MutableStateFlow(AndroidColor.BLACK)
    val activeColor: StateFlow<Int> = _activeColor

    private val _strokeWidth = MutableStateFlow(4f)
    val strokeWidth: StateFlow<Float> = _strokeWidth

    private val _livePoints = MutableStateFlow<List<StrokePoint>>(emptyList())
    val livePoints: StateFlow<List<StrokePoint>> = _livePoints

    private var _documentId: Long = -1L

    // Persisted strokes for current document, keyed by page
    private val _strokes = MutableStateFlow<List<Stroke>>(emptyList())
    val strokes: StateFlow<List<Stroke>> = _strokes

    private val _highlights = MutableStateFlow<List<Highlight>>(emptyList())
    val highlights: StateFlow<List<Highlight>> = _highlights

    private var loadedDocumentId: Long = -1L
    private var strokesJob: Job? = null
    private var highlightsJob: Job? = null

    val isAnnotationMode: Boolean get() = _activeTool.value != null

    fun loadDocument(documentId: Long) {
        if (loadedDocumentId == documentId) return
        loadedDocumentId = documentId
        _documentId = documentId
        _livePoints.value = emptyList()
        strokesJob?.cancel()
        highlightsJob?.cancel()
        strokesJob = viewModelScope.launch {
            repository.getStrokesByDocument(documentId).collect { _strokes.value = it }
        }
        highlightsJob = viewModelScope.launch {
            repository.getHighlightsByDocument(documentId).collect { _highlights.value = it }
        }
    }

    fun selectTool(tool: PenTool?) {
        _activeTool.value = if (_activeTool.value == tool) null else tool
    }

    fun setColor(color: Int) { _activeColor.value = color }
    fun setStrokeWidth(width: Float) { _strokeWidth.value = width }

    fun addLivePoint(point: StrokePoint) {
        _livePoints.value = _livePoints.value + point
    }

    fun takeCompletedStroke(pageIndex: Int): Stroke? {
        val points = PenInputProcessor.simplify(_livePoints.value)
        _livePoints.value = emptyList()
        if (points.size < 2) return null

        val tool = _activeTool.value ?: return null
        return Stroke(
            documentId = _documentId,
            pageIndex = pageIndex,
            tool = tool,
            color = _activeColor.value,
            strokeWidth = _strokeWidth.value,
            points = points
        )
    }

    fun commitStroke(pageIndex: Int) {
        val stroke = takeCompletedStroke(pageIndex) ?: return

        viewModelScope.launch { repository.saveStroke(stroke) }
    }

    fun saveStroke(stroke: Stroke) {
        viewModelScope.launch { repository.saveStroke(stroke) }
    }

    fun commitHighlight(pageIndex: Int, extractedText: String, rangeData: String = "") {
        val highlight = Highlight(
            documentId = _documentId,
            pageIndex = pageIndex,
            color = _activeColor.value,
            extractedText = extractedText,
            rangeData = rangeData
        )
        viewModelScope.launch { repository.saveHighlight(highlight) }
    }

    fun updateHighlightNote(highlight: Highlight, note: String) {
        viewModelScope.launch { repository.updateHighlight(highlight.copy(userNote = note)) }
    }

    fun deleteStroke(stroke: Stroke) {
        viewModelScope.launch { repository.deleteStroke(stroke) }
    }

    fun eraseWithCurrentStroke(pageIndex: Int, pageHeight: Int, pageGap: Int) {
        if (_activeTool.value != PenTool.ERASER) {
            _livePoints.value = emptyList()
            return
        }

        val eraserPoints = PenInputProcessor.simplify(_livePoints.value)
        _livePoints.value = emptyList()
        if (eraserPoints.isEmpty()) return

        val hitRadius = (_strokeWidth.value * 2f).coerceAtLeast(24f)
        val hitStrokes = _strokes.value.filter { stroke ->
            stroke.pageIndex == pageIndex && stroke.points.any { strokePoint ->
                eraserPoints.any { eraserPoint ->
                    hypot(strokePoint.x - eraserPoint.x, strokePoint.y - eraserPoint.y) <=
                        (hitRadius + stroke.strokeWidth)
                }
            }
        }

        val pageTopY = pageIndex * (pageHeight + pageGap).toFloat()
        val eraserBounds = boundsOf(
            eraserPoints.map { point ->
                point.copy(y = point.y - pageTopY)
            }
        )
        val hitHighlights = _highlights.value.filter { highlight ->
            highlight.pageIndex == pageIndex &&
                parseRangeData(highlight.rangeData)?.let { highlightBounds ->
                    boundsIntersect(eraserBounds, highlightBounds)
                } == true
        }

        viewModelScope.launch {
            for (stroke in hitStrokes) {
                repository.deleteStroke(stroke)
            }
            for (highlight in hitHighlights) {
                repository.deleteHighlight(highlight)
            }
        }
    }

    private fun boundsOf(points: List<StrokePoint>): RectBounds {
        val minX = points.minOf { it.x }
        val minY = points.minOf { it.y }
        val maxX = points.maxOf { it.x }
        val maxY = points.maxOf { it.y }
        return RectBounds(minX, minY, maxX, maxY)
    }

    private fun parseRangeData(rangeData: String): RectBounds? {
        val parts = rangeData.split(',')
        if (parts.size != 4) return null
        val x = parts[0].toFloatOrNull() ?: return null
        val y = parts[1].toFloatOrNull() ?: return null
        val width = parts[2].toFloatOrNull() ?: return null
        val height = parts[3].toFloatOrNull() ?: return null
        return RectBounds(x, y, x + width, y + height)
    }

    private fun boundsIntersect(a: RectBounds, b: RectBounds): Boolean {
        return a.left <= b.right && a.right >= b.left &&
            a.top <= b.bottom && a.bottom >= b.top
    }

    private data class RectBounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )
}
