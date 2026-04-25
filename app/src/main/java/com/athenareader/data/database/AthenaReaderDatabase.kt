package com.athenareader.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.athenareader.data.dao.AnnotationDao
import com.athenareader.data.dao.DocumentDao
import com.athenareader.data.dao.HighlightDao
import com.athenareader.data.dao.ReadingProgressDao
import com.athenareader.data.entity.AnnotationEntity
import com.athenareader.data.entity.DocumentEntity
import com.athenareader.data.entity.HighlightEntity
import com.athenareader.data.entity.ReadingProgressEntity

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
abstract class AthenaReaderDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun highlightDao(): HighlightDao
}
