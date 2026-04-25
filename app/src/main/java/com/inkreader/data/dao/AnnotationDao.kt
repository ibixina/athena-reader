package com.inkreader.data.dao

import androidx.room.*
import com.inkreader.data.entity.AnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {

    @Query("SELECT * FROM annotations WHERE document_id = :documentId AND page_index = :pageIndex ORDER BY timestamp")
    fun getByPage(documentId: Long, pageIndex: Int): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE document_id = :documentId ORDER BY timestamp")
    fun getByDocument(documentId: Long): Flow<List<AnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(annotation: AnnotationEntity): Long

    @Delete
    suspend fun delete(annotation: AnnotationEntity)

    @Query("DELETE FROM annotations WHERE document_id = :documentId AND page_index = :pageIndex")
    suspend fun deleteByPage(documentId: Long, pageIndex: Int)
}
