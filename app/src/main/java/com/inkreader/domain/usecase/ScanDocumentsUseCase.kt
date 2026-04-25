package com.inkreader.domain.usecase

import com.inkreader.domain.repository.DocumentRepository
import javax.inject.Inject

class ScanDocumentsUseCase @Inject constructor(
    private val repository: DocumentRepository
) {
    suspend operator fun invoke(directoryPath: String) {
        val discoveredDocs = repository.scanForDocuments(directoryPath)
        discoveredDocs.forEach { doc ->
            repository.upsertDocument(doc)
        }
    }
}
