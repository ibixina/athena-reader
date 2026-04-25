package com.inkreader.domain.repository

import com.inkreader.domain.model.Highlight
import com.inkreader.domain.model.Stroke
import kotlinx.coroutines.flow.Flow

interface AnnotationRepository {
    fun getStrokesByPage(documentId: Long, pageIndex: Int): Flow<List<Stroke>>
    fun getStrokesByDocument(documentId: Long): Flow<List<Stroke>>
    suspend fun saveStroke(stroke: Stroke): Long
    suspend fun deleteStroke(stroke: Stroke)

    fun getHighlightsByDocument(documentId: Long): Flow<List<Highlight>>
    fun getHighlightsByPage(documentId: Long, pageIndex: Int): Flow<List<Highlight>>
    suspend fun saveHighlight(highlight: Highlight): Long
    suspend fun updateHighlight(highlight: Highlight)
    suspend fun deleteHighlight(highlight: Highlight)
}
