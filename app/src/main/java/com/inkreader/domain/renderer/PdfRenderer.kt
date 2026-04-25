package com.inkreader.domain.renderer

import android.graphics.Bitmap
import com.inkreader.domain.model.PageInfo
import com.inkreader.domain.model.Tile

interface PdfRenderer {
    suspend fun openDocument(filePath: String)
    suspend fun closeDocument()
    suspend fun getPageCount(): Int
    suspend fun getPageInfo(pageIndex: Int): PageInfo
    suspend fun renderTile(tile: Tile): Bitmap
    suspend fun extractText(pageIndex: Int, x: Int, y: Int, width: Int, height: Int): String
}
