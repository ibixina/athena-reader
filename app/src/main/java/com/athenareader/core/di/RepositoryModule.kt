package com.athenareader.core.di

import com.athenareader.data.repository.AnnotationRepositoryImpl
import com.athenareader.data.repository.DocumentRepositoryImpl
import com.athenareader.data.repository.ReadingProgressRepositoryImpl
import com.athenareader.data.repository.SettingsRepositoryImpl
import com.athenareader.domain.repository.AnnotationRepository
import com.athenareader.domain.repository.DocumentRepository
import com.athenareader.domain.repository.ReadingProgressRepository
import com.athenareader.domain.repository.SettingsRepository
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
