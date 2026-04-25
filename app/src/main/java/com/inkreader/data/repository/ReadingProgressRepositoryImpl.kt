package com.inkreader.data.repository

import com.inkreader.data.dao.ReadingProgressDao
import com.inkreader.data.mapper.toDomain
import com.inkreader.data.mapper.toEntity
import com.inkreader.domain.model.ReadingProgress
import com.inkreader.domain.repository.ReadingProgressRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingProgressRepositoryImpl @Inject constructor(
    private val readingProgressDao: ReadingProgressDao
) : ReadingProgressRepository {

    override suspend fun getProgress(documentId: Long): ReadingProgress? {
        return readingProgressDao.getProgress(documentId)?.toDomain()
    }

    override suspend fun saveProgress(progress: ReadingProgress) {
        readingProgressDao.saveProgress(progress.toEntity())
    }
}

