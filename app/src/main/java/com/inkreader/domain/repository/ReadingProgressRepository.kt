package com.inkreader.domain.repository

import com.inkreader.domain.model.ReadingProgress

interface ReadingProgressRepository {
    suspend fun getProgress(documentId: Long): ReadingProgress?
    suspend fun saveProgress(progress: ReadingProgress)
}

