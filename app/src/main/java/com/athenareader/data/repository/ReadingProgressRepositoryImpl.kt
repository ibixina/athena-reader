package com.athenareader.data.repository

import com.athenareader.data.dao.ReadingProgressDao
import com.athenareader.data.mapper.toDomain
import com.athenareader.data.mapper.toEntity
import com.athenareader.domain.model.ReadingProgress
import com.athenareader.domain.repository.ReadingProgressRepository
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingProgressRepositoryImpl @Inject constructor(
    private val readingProgressDao: ReadingProgressDao
) : ReadingProgressRepository {

    override suspend fun getProgress(documentId: Long): ReadingProgress? {
        return readingProgressDao.getProgress(documentId)?.toDomain()
    }

    override fun getAllProgresses(): kotlinx.coroutines.flow.Flow<List<ReadingProgress>> {
        return readingProgressDao.getAllProgresses().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun saveProgress(progress: ReadingProgress) {
        readingProgressDao.saveProgress(progress.toEntity())
    }
}

