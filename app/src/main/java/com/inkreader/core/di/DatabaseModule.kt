package com.inkreader.core.di

import android.content.Context
import androidx.room.Room
import com.inkreader.data.database.InkReaderDatabase
import com.inkreader.data.dao.AnnotationDao
import com.inkreader.data.dao.DocumentDao
import com.inkreader.data.dao.HighlightDao
import com.inkreader.data.dao.ReadingProgressDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): InkReaderDatabase {
        return Room.databaseBuilder(
            context,
            InkReaderDatabase::class.java,
            "ink_reader_db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideDocumentDao(database: InkReaderDatabase): DocumentDao =
        database.documentDao()

    @Provides
    fun provideReadingProgressDao(database: InkReaderDatabase): ReadingProgressDao =
        database.readingProgressDao()

    @Provides
    fun provideAnnotationDao(database: InkReaderDatabase): AnnotationDao =
        database.annotationDao()

    @Provides
    fun provideHighlightDao(database: InkReaderDatabase): HighlightDao =
        database.highlightDao()
}

