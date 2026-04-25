package com.athenareader.core.di

import android.content.Context
import androidx.room.Room
import com.athenareader.data.database.AthenaReaderDatabase
import com.athenareader.data.dao.AnnotationDao
import com.athenareader.data.dao.DocumentDao
import com.athenareader.data.dao.HighlightDao
import com.athenareader.data.dao.ReadingProgressDao
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
    fun provideDatabase(@ApplicationContext context: Context): AthenaReaderDatabase {
        return Room.databaseBuilder(
            context,
            AthenaReaderDatabase::class.java,
            "athena_reader_db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideDocumentDao(database: AthenaReaderDatabase): DocumentDao =
        database.documentDao()

    @Provides
    fun provideReadingProgressDao(database: AthenaReaderDatabase): ReadingProgressDao =
        database.readingProgressDao()

    @Provides
    fun provideAnnotationDao(database: AthenaReaderDatabase): AnnotationDao =
        database.annotationDao()

    @Provides
    fun provideHighlightDao(database: AthenaReaderDatabase): HighlightDao =
        database.highlightDao()
}

