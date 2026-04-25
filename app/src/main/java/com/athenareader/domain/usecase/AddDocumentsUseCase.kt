package com.athenareader.domain.usecase

import com.athenareader.domain.repository.DocumentRepository
import javax.inject.Inject

class AddDocumentsUseCase @Inject constructor(
    private val repository: DocumentRepository
) {
    suspend operator fun invoke(uriStrings: List<String>) {
        uriStrings.forEach { uriString ->
            val doc = repository.getDocumentFromUri(uriString)
            if (doc != null) {
                repository.upsertDocument(doc)
            }
        }
    }
}
