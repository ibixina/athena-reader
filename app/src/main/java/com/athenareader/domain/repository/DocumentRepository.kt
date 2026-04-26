package com.athenareader.domain.repository

import com.athenareader.domain.model.Document
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun getAllDocuments(): Flow<List<Document>>
    suspend fun getDocumentById(id: Long): Document?
    suspend fun upsertDocument(document: Document): Long
    suspend fun deleteDocument(document: Document)
    suspend fun scanForDocuments(folderUri: String): List<Document>
    suspend fun getDocumentFromUri(uriString: String): Document?
    suspend fun updateLastOpened(documentId: Long, timestamp: Long)
}
