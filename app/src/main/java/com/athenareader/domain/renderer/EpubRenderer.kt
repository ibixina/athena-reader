package com.athenareader.domain.renderer

import com.athenareader.domain.model.EpubMetadata

interface EpubRenderer {
    suspend fun openDocument(filePath: String): EpubMetadata
    suspend fun getChapterContent(chapterHref: String): String
    suspend fun closeDocument()
}
