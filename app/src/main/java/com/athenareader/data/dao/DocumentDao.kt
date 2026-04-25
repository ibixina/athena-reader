package com.athenareader.data.dao

import androidx.room.*
import com.athenareader.data.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY last_opened DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Query("SELECT * FROM documents WHERE hash = :hash LIMIT 1")
    suspend fun getDocumentByHash(hash: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE file_path = :filePath LIMIT 1")
    suspend fun getDocumentByFilePath(filePath: String): DocumentEntity?

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): DocumentEntity?
}
