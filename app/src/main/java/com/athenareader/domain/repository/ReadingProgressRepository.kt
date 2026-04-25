package com.athenareader.domain.repository

import com.athenareader.domain.model.ReadingProgress

interface ReadingProgressRepository {
    suspend fun getProgress(documentId: Long): ReadingProgress?
    fun getAllProgresses(): kotlinx.coroutines.flow.Flow<List<ReadingProgress>>
    suspend fun saveProgress(progress: ReadingProgress)
}

