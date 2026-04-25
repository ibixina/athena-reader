package com.athenareader.domain.usecase

import com.athenareader.domain.repository.DocumentRepository
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
