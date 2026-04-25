package com.inkreader.domain.usecase

import com.inkreader.domain.model.Document
import com.inkreader.domain.model.DocumentFormat
import com.inkreader.domain.repository.DocumentRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ScanDocumentsUseCaseTest {

    private val repository: DocumentRepository = mock()
    private val useCase = ScanDocumentsUseCase(repository)

    @Test
    fun `invoke should scan and upsert discovered documents`() = runTest {
        val path = "/test/path"
        val discoveredDocs = listOf(
            Document(name = "First PDF", filePath = "1.pdf", hash = "h1", format = DocumentFormat.PDF, lastOpened = 0L),
            Document(name = "Second EPUB", filePath = "2.epub", hash = "h2", format = DocumentFormat.EPUB, lastOpened = 0L)
        )
        whenever(repository.scanForDocuments(path)).thenReturn(discoveredDocs)

        useCase(path)

        verify(repository).scanForDocuments(path)
        verify(repository).upsertDocument(discoveredDocs[0])
        verify(repository).upsertDocument(discoveredDocs[1])
    }
}
