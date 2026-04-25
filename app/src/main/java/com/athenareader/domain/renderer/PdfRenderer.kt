package com.athenareader.domain.renderer

import android.graphics.Bitmap
import com.athenareader.domain.model.PageInfo
import com.athenareader.domain.model.Tile

interface PdfRenderer {
    suspend fun openDocument(filePath: String)
    suspend fun closeDocument()
    suspend fun getPageCount(): Int
    suspend fun getPageInfo(pageIndex: Int): PageInfo
    suspend fun renderTile(tile: Tile): Bitmap
    suspend fun extractText(pageIndex: Int, x: Int, y: Int, width: Int, height: Int): String
    suspend fun getOutline(): List<com.athenareader.domain.model.ReaderOutlineItem>
}
