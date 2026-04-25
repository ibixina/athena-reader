package com.athenareader.domain.model

enum class PenTool { FINE_PEN, HIGHLIGHTER, ERASER }

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    val timestamp: Long = 0L
)

data class Stroke(
    val id: Long = 0,
    val documentId: Long,
    val pageIndex: Int,
    val tool: PenTool,
    val color: Int,
    val strokeWidth: Float,
    val opacity: Float = 1f,
    val points: List<StrokePoint>
)

data class Highlight(
    val id: Long = 0,
    val documentId: Long,
    val pageIndex: Int,
    val color: Int,
    val extractedText: String = "",
    val rangeData: String = "",
    val userNote: String = "",
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
