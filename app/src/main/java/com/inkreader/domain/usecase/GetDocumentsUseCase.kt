package com.inkreader.domain.usecase

import com.inkreader.domain.model.Document
import com.inkreader.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetDocumentsUseCase @Inject constructor(
    private val repository: DocumentRepository
) {
    operator fun invoke(): Flow<List<Document>> {
        return repository.getAllDocuments()
    }
}
