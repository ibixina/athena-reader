package com.inkreader.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.inkreader.data.dao.AnnotationDao
import com.inkreader.data.dao.DocumentDao
import com.inkreader.data.dao.HighlightDao
import com.inkreader.data.dao.ReadingProgressDao
import com.inkreader.data.entity.AnnotationEntity
import com.inkreader.data.entity.DocumentEntity
import com.inkreader.data.entity.HighlightEntity
import com.inkreader.data.entity.ReadingProgressEntity

@Database(
    entities = [
        DocumentEntity::class,
        ReadingProgressEntity::class,
        AnnotationEntity::class,
        HighlightEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class InkReaderDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun highlightDao(): HighlightDao
}
