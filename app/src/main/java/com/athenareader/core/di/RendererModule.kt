package com.athenareader.core.di

import com.athenareader.data.renderer.AndroidPdfRendererImpl
import com.athenareader.data.renderer.WebViewEpubRendererImpl
import com.athenareader.domain.renderer.EpubRenderer
import com.athenareader.domain.renderer.PdfRenderer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RendererModule {

    @Binds
    @Singleton
    abstract fun bindPdfRenderer(
        androidPdfRendererImpl: AndroidPdfRendererImpl
    ): PdfRenderer

    @Binds
    @Singleton
    abstract fun bindEpubRenderer(
        webViewEpubRendererImpl: WebViewEpubRendererImpl
    ): EpubRenderer
}
