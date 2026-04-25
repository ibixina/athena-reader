package com.inkreader.data.mapper

import com.inkreader.data.entity.AnnotationEntity
import com.inkreader.data.entity.HighlightEntity
import com.inkreader.domain.model.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AnnotationMapper {

    private const val FLOATS_PER_POINT = 6 // x, y, pressure, tiltX, tiltY, timestamp

    fun toEntity(stroke: Stroke): AnnotationEntity = AnnotationEntity(
        id = stroke.id,
        documentId = stroke.documentId,
        pageIndex = stroke.pageIndex,
        tool = stroke.tool.name,
        color = stroke.color,
        strokeWidth = stroke.strokeWidth,
        opacity = stroke.opacity,
        vectorData = encodePoints(stroke.points)
    )

    fun toDomain(entity: AnnotationEntity): Stroke = Stroke(
        id = entity.id,
        documentId = entity.documentId,
        pageIndex = entity.pageIndex,
        tool = PenTool.valueOf(entity.tool),
        color = entity.color,
        strokeWidth = entity.strokeWidth,
        opacity = entity.opacity,
        points = decodePoints(entity.vectorData)
    )

    fun toEntity(highlight: Highlight): HighlightEntity = HighlightEntity(
        id = highlight.id,
        documentId = highlight.documentId,
        pageIndex = highlight.pageIndex,
        color = highlight.color,
        extractedText = highlight.extractedText,
        rangeData = highlight.rangeData,
        userNote = highlight.userNote,
        tags = highlight.tags,
        createdAt = highlight.createdAt
    )

    fun toDomain(entity: HighlightEntity): Highlight = Highlight(
        id = entity.id,
        documentId = entity.documentId,
        pageIndex = entity.pageIndex,
        color = entity.color,
        extractedText = entity.extractedText,
        rangeData = entity.rangeData,
        userNote = entity.userNote,
        tags = entity.tags,
        createdAt = entity.createdAt
    )

    private fun encodePoints(points: List<StrokePoint>): ByteArray {
        val buffer = ByteBuffer.allocate(points.size * FLOATS_PER_POINT * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (p in points) {
            buffer.putFloat(p.x)
            buffer.putFloat(p.y)
            buffer.putFloat(p.pressure)
            buffer.putFloat(p.tiltX)
            buffer.putFloat(p.tiltY)
            buffer.putFloat(p.timestamp.toFloat())
        }
        return buffer.array()
    }

    private fun decodePoints(data: ByteArray): List<StrokePoint> {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val count = data.size / (FLOATS_PER_POINT * 4)
        return List(count) {
            StrokePoint(
                x = buffer.getFloat(),
                y = buffer.getFloat(),
                pressure = buffer.getFloat(),
                tiltX = buffer.getFloat(),
                tiltY = buffer.getFloat(),
                timestamp = buffer.getFloat().toLong()
            )
        }
    }
}
