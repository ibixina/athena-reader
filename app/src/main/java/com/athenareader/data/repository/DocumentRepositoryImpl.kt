package com.athenareader.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.athenareader.core.util.FileUtils
import com.athenareader.data.dao.DocumentDao
import com.athenareader.data.mapper.toDomain
import com.athenareader.data.mapper.toEntity
import com.athenareader.domain.model.Document
import com.athenareader.domain.model.DocumentFormat
import com.athenareader.domain.repository.DocumentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DocumentRepositoryImpl @Inject constructor(
    private val documentDao: DocumentDao,
    @ApplicationContext private val context: Context
) : DocumentRepository {

    override fun getAllDocuments(): Flow<List<Document>> {
        return documentDao.getAllDocuments().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getDocumentById(id: Long): Document? {
        return documentDao.getDocumentById(id)?.toDomain()
    }

    override suspend fun upsertDocument(document: Document): Long {
        val existing = documentDao.getDocumentByHash(document.hash)
            ?: documentDao.getDocumentByFilePath(document.filePath)

        val merged = if (existing != null) {
            document.copy(
                id = existing.id,
                lastOpened = maxOf(existing.lastOpened, document.lastOpened)
            )
        } else {
            document
        }

        return documentDao.insertDocument(merged.toEntity())
    }

    override suspend fun deleteDocument(document: Document) {
        documentDao.deleteDocument(document.toEntity())
    }

    override suspend fun getDocumentFromUri(uriString: String): Document? = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        val file = DocumentFile.fromSingleUri(context, uri) ?: return@withContext null
        
        val name = file.name?.lowercase() ?: ""
        val format = when {
            name.endsWith(".pdf") -> DocumentFormat.PDF
            name.endsWith(".epub") -> DocumentFormat.EPUB
            else -> null
        } ?: return@withContext null

        val hash = FileUtils.calculateHash(context, uri)
        Document(
            id = 0,
            name = file.name ?: "Unknown",
            filePath = uriString,
            hash = hash,
            format = format,
            lastOpened = 0L
        )
    }

    override suspend fun scanForDocuments(folderUri: String): List<Document> = withContext(Dispatchers.IO) {
        val rootUri = Uri.parse(folderUri)
        val rootFile = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext emptyList()
        
        val foundDocuments = mutableListOf<Document>()
        scanRecursively(rootFile, foundDocuments)
        foundDocuments
    }

    private fun scanRecursively(parent: DocumentFile, results: MutableList<Document>) {
        parent.listFiles().forEach { file ->
            if (file.isDirectory) {
                scanRecursively(file, results)
            } else if (file.isFile) {
                val name = file.name?.lowercase() ?: ""
                val format = when {
                    name.endsWith(".pdf") -> DocumentFormat.PDF
                    name.endsWith(".epub") -> DocumentFormat.EPUB
                    else -> null
                }
                
                if (format != null) {
                    val hash = FileUtils.calculateHash(context, file.uri)
                    results.add(
                        Document(
                            id = 0,
                            name = file.name ?: "Unknown",
                            filePath = file.uri.toString(),
                            hash = hash,
                            format = format,
                            lastOpened = 0L
                        )
                    )
                }
            }
        }
    }
}
