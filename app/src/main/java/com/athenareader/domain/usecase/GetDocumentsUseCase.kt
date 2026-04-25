package com.athenareader.domain.usecase

import com.athenareader.domain.model.Document
import com.athenareader.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetDocumentsUseCase @Inject constructor(
    private val repository: DocumentRepository
) {
    operator fun invoke(): Flow<List<Document>> {
        return repository.getAllDocuments()
    }
}
