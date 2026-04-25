package com.athenareader.domain.repository

import com.athenareader.domain.model.ReadingProgress

interface ReadingProgressRepository {
    suspend fun getProgress(documentId: Long): ReadingProgress?
    suspend fun saveProgress(progress: ReadingProgress)
}

