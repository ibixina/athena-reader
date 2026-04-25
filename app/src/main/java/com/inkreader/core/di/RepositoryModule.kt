package com.inkreader.core.di

import com.inkreader.data.repository.AnnotationRepositoryImpl
import com.inkreader.data.repository.DocumentRepositoryImpl
import com.inkreader.data.repository.ReadingProgressRepositoryImpl
import com.inkreader.data.repository.SettingsRepositoryImpl
import com.inkreader.domain.repository.AnnotationRepository
import com.inkreader.domain.repository.DocumentRepository
import com.inkreader.domain.repository.ReadingProgressRepository
import com.inkreader.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(
        documentRepositoryImpl: DocumentRepositoryImpl
    ): DocumentRepository

    @Binds
    @Singleton
    abstract fun bindAnnotationRepository(
        annotationRepositoryImpl: AnnotationRepositoryImpl
    ): AnnotationRepository

    @Binds
    @Singleton
    abstract fun bindReadingProgressRepository(
        readingProgressRepositoryImpl: ReadingProgressRepositoryImpl
    ): ReadingProgressRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository
}
