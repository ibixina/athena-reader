package com.inkreader.data.dao

import androidx.room.*
import com.inkreader.data.entity.ReadingProgressEntity

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE document_id = :documentId")
    suspend fun getProgress(documentId: Long): ReadingProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: ReadingProgressEntity)
}
