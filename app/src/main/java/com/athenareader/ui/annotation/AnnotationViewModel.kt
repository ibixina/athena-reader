package com.athenareader.ui.annotation

import android.graphics.Color as AndroidColor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athenareader.domain.model.*
import com.athenareader.domain.repository.AnnotationRepository
import com.athenareader.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.hypot

data class ToolSettings(
    val color: Int,
    val strokeWidth: Float
)

@HiltViewModel
class AnnotationViewModel @Inject constructor(
    private val repository: AnnotationRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _activeTool = MutableStateFlow<PenTool?>(null)
    val activeTool: StateFlow<PenTool?> = _activeTool

    // Per-tool settings
    private val _penSettings = MutableStateFlow(ToolSettings(AndroidColor.BLACK, 3f))
    val penSettings: StateFlow<ToolSettings> = _penSettings

    private val _highlighterSettings = MutableStateFlow(
        ToolSettings(AndroidColor.parseColor("#FF8F00"), 12f)
    )
    val highlighterSettings: StateFlow<ToolSettings> = _highlighterSettings

    private val _eraserWidth = MutableStateFlow(20f)
    val eraserWidth: StateFlow<Float> = _eraserWidth

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            _penSettings.value = ToolSettings(settings.penColor, settings.penWidth)
            _highlighterSettings.value = ToolSettings(settings.highlighterColor, settings.highlighterWidth)
        }
    }

    // Convenience: active color/width based on current tool
    val activeColor: StateFlow<Int> = combine(_activeTool, _penSettings, _highlighterSettings) { tool, pen, hl ->
        when (tool) {
            PenTool.FINE_PEN -> pen.color
            PenTool.HIGHLIGHTER -> hl.color
            else -> pen.color
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, _penSettings.value.color)

    val strokeWidth: StateFlow<Float> = combine(_activeTool, _penSettings, _highlighterSettings, _eraserWidth) { tool, pen, hl, ew ->
        when (tool) {
            PenTool.FINE_PEN -> pen.strokeWidth
            PenTool.HIGHLIGHTER -> hl.strokeWidth
            PenTool.ERASER -> ew
            else -> pen.strokeWidth
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, _penSettings.value.strokeWidth)

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

    fun setColor(tool: PenTool, color: Int) {
        when (tool) {
            PenTool.FINE_PEN -> {
                _penSettings.value = _penSettings.value.copy(color = color)
                viewModelScope.launch { settingsRepository.setPenColor(color) }
            }
            PenTool.HIGHLIGHTER -> {
                _highlighterSettings.value = _highlighterSettings.value.copy(color = color)
                viewModelScope.launch { settingsRepository.setHighlighterColor(color) }
            }
            PenTool.ERASER -> {} // eraser has no color
        }
    }

    fun setStrokeWidth(tool: PenTool, width: Float) {
        when (tool) {
            PenTool.FINE_PEN -> {
                _penSettings.value = _penSettings.value.copy(strokeWidth = width)
                viewModelScope.launch { settingsRepository.setPenWidth(width) }
            }
            PenTool.HIGHLIGHTER -> {
                _highlighterSettings.value = _highlighterSettings.value.copy(strokeWidth = width)
                viewModelScope.launch { settingsRepository.setHighlighterWidth(width) }
            }
            PenTool.ERASER -> _eraserWidth.value = width
        }
    }

    fun addLivePoint(point: StrokePoint) {
        val current = _livePoints.value
        if (current.isNotEmpty()) {
            val prev = current.last()
            if (hypot(point.x - prev.x, point.y - prev.y) < 2f) return
        }
        _livePoints.value = current + point
    }

    fun takeCompletedStroke(pageIndex: Int, forceTool: PenTool? = null): Stroke? {
        val points = _livePoints.value
        _livePoints.value = emptyList()
        if (points.size < 2) return null

        val tool = forceTool ?: _activeTool.value ?: return null
        val color = when (tool) {
            PenTool.FINE_PEN -> _penSettings.value.color
            PenTool.HIGHLIGHTER -> _highlighterSettings.value.color
            PenTool.ERASER -> _penSettings.value.color
        }
        val width = when (tool) {
            PenTool.FINE_PEN -> _penSettings.value.strokeWidth
            PenTool.HIGHLIGHTER -> _highlighterSettings.value.strokeWidth
            PenTool.ERASER -> _eraserWidth.value
        }
        return Stroke(
            documentId = _documentId,
            pageIndex = pageIndex,
            tool = tool,
            color = color,
            strokeWidth = width,
            points = points
        )
    }

    fun commitStroke(pageIndex: Int) {
        val stroke = takeCompletedStroke(pageIndex) ?: return
        viewModelScope.launch { repository.saveStroke(stroke) }
    }

    fun saveStroke(stroke: Stroke) {
        // Optimistic update: show stroke immediately without waiting for Room round-trip
        _strokes.value = _strokes.value + stroke
        viewModelScope.launch { repository.saveStroke(stroke) }
    }

    // Highlight merging: buffer highlights arriving within MERGE_WINDOW_MS
    private var pendingHighlightText = StringBuilder()
    private var pendingHighlightPage = -1
    private var pendingHighlightColor = 0
    private var pendingHighlightRanges = mutableListOf<String>()
    private var highlightFlushJob: Job? = null
    private val MERGE_WINDOW_MS = 4000L

    fun commitHighlight(pageIndex: Int, extractedText: String, rangeData: String = "") {
        // If same page and within time window, merge
        if (pageIndex == pendingHighlightPage && highlightFlushJob != null) {
            highlightFlushJob?.cancel()
            if (pendingHighlightText.isNotEmpty()) pendingHighlightText.append(" ")
            pendingHighlightText.append(extractedText)
            if (rangeData.isNotEmpty()) pendingHighlightRanges.add(rangeData)
        } else {
            // Flush any previous pending highlight
            flushPendingHighlight()
            pendingHighlightPage = pageIndex
            pendingHighlightColor = activeColor.value
            pendingHighlightText = StringBuilder(extractedText)
            pendingHighlightRanges = if (rangeData.isNotEmpty()) mutableListOf(rangeData) else mutableListOf()
        }

        // Schedule flush after delay
        highlightFlushJob = viewModelScope.launch {
            delay(MERGE_WINDOW_MS)
            flushPendingHighlight()
        }
    }

    private fun flushPendingHighlight() {
        highlightFlushJob?.cancel()
        highlightFlushJob = null
        val text = pendingHighlightText.toString()
        if (text.isBlank() || pendingHighlightPage < 0) return

        val mergedRange = if (pendingHighlightRanges.isNotEmpty()) {
            mergeRanges(pendingHighlightRanges)
        } else ""

        val highlight = Highlight(
            documentId = _documentId,
            pageIndex = pendingHighlightPage,
            color = pendingHighlightColor,
            extractedText = text,
            rangeData = mergedRange
        )
        viewModelScope.launch { repository.saveHighlight(highlight) }
        pendingHighlightText.clear()
        pendingHighlightPage = -1
        pendingHighlightRanges.clear()
    }

    private fun mergeRanges(ranges: List<String>): String {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE; var maxY = Float.MIN_VALUE
        for (r in ranges) {
            val parts = r.split(',')
            if (parts.size != 4) continue
            val x = parts[0].toFloatOrNull() ?: continue
            val y = parts[1].toFloatOrNull() ?: continue
            val w = parts[2].toFloatOrNull() ?: continue
            val h = parts[3].toFloatOrNull() ?: continue
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x + w > maxX) maxX = x + w
            if (y + h > maxY) maxY = y + h
        }
        return "${minX.toInt()},${minY.toInt()},${(maxX - minX).toInt()},${(maxY - minY).toInt()}"
    }

    fun updateHighlightNote(highlight: Highlight, note: String) {
        viewModelScope.launch { repository.updateHighlight(highlight.copy(userNote = note)) }
    }

    fun deleteStroke(stroke: Stroke) {
        viewModelScope.launch { repository.deleteStroke(stroke) }
    }

    fun eraseWithCurrentStroke(pageIndex: Int, pageHeight: Int, pageGap: Int) {
        val eraserPoints = PenInputProcessor.simplify(_livePoints.value)
        _livePoints.value = emptyList()
        if (eraserPoints.isEmpty()) return

        val hitRadius = (strokeWidth.value * 2f).coerceAtLeast(24f)
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
