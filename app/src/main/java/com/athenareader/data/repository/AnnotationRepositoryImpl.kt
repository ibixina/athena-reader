package com.athenareader.data.repository

import com.athenareader.data.dao.AnnotationDao
import com.athenareader.data.dao.HighlightDao
import com.athenareader.data.mapper.AnnotationMapper
import com.athenareader.domain.model.Highlight
import com.athenareader.domain.model.Stroke
import com.athenareader.domain.repository.AnnotationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnnotationRepositoryImpl @Inject constructor(
    private val annotationDao: AnnotationDao,
    private val highlightDao: HighlightDao
) : AnnotationRepository {

    override fun getStrokesByPage(documentId: Long, pageIndex: Int): Flow<List<Stroke>> =
        annotationDao.getByPage(documentId, pageIndex).map { list -> list.map(AnnotationMapper::toDomain) }

    override fun getStrokesByDocument(documentId: Long): Flow<List<Stroke>> =
        annotationDao.getByDocument(documentId).map { list -> list.map(AnnotationMapper::toDomain) }

    override suspend fun saveStroke(stroke: Stroke): Long =
        annotationDao.insert(AnnotationMapper.toEntity(stroke))

    override suspend fun deleteStroke(stroke: Stroke) =
        annotationDao.delete(AnnotationMapper.toEntity(stroke))

    override fun getHighlightsByDocument(documentId: Long): Flow<List<Highlight>> =
        highlightDao.getByDocument(documentId).map { list -> list.map(AnnotationMapper::toDomain) }

    override fun getHighlightsByPage(documentId: Long, pageIndex: Int): Flow<List<Highlight>> =
        highlightDao.getByPage(documentId, pageIndex).map { list -> list.map(AnnotationMapper::toDomain) }

    override suspend fun saveHighlight(highlight: Highlight): Long =
        highlightDao.insert(AnnotationMapper.toEntity(highlight))

    override suspend fun updateHighlight(highlight: Highlight) =
        highlightDao.update(AnnotationMapper.toEntity(highlight))

    override suspend fun deleteHighlight(highlight: Highlight) =
        highlightDao.delete(AnnotationMapper.toEntity(highlight))
}
