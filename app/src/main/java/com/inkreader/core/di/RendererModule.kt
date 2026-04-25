package com.inkreader.core.di

import com.inkreader.data.renderer.AndroidPdfRendererImpl
import com.inkreader.data.renderer.WebViewEpubRendererImpl
import com.inkreader.domain.renderer.EpubRenderer
import com.inkreader.domain.renderer.PdfRenderer
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
