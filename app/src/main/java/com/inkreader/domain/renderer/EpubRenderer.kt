package com.inkreader.domain.renderer

import com.inkreader.domain.model.EpubMetadata

interface EpubRenderer {
    suspend fun openDocument(filePath: String): EpubMetadata
    suspend fun getChapterContent(chapterHref: String): String
    suspend fun closeDocument()
}
