package com.inkreader.data.dao

import androidx.room.*
import com.inkreader.data.entity.HighlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {

    @Query("SELECT * FROM highlights WHERE document_id = :documentId ORDER BY created_at DESC")
    fun getByDocument(documentId: Long): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE document_id = :documentId AND page_index = :pageIndex ORDER BY created_at")
    fun getByPage(documentId: Long, pageIndex: Int): Flow<List<HighlightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(highlight: HighlightEntity): Long

    @Update
    suspend fun update(highlight: HighlightEntity)

    @Delete
    suspend fun delete(highlight: HighlightEntity)
}
