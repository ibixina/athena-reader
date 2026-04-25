package com.inkreader.data.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.inkreader.domain.model.PageInfo
import com.inkreader.domain.model.Tile
import com.inkreader.domain.renderer.PdfRenderer as IPdfRenderer
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripperByArea
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

import android.graphics.RectF
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class AndroidPdfRendererImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : IPdfRenderer {
    
    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private val mutex = Mutex()
    private var currentPage: PdfRenderer.Page? = null
    private var currentPageIndex: Int = -1
    private var textDocument: PDDocument? = null

    override suspend fun openDocument(filePath: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(filePath)
                    closeOpenDocuments()
                    fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                    fileDescriptor?.let {
                        pdfRenderer = PdfRenderer(it)
                    }
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        textDocument = PDDocument.load(input)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override suspend fun closeDocument() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                closeOpenDocuments()
            }
        }
    }

    override suspend fun getPageCount(): Int = mutex.withLock {
        pdfRenderer?.pageCount ?: 0
    }

    override suspend fun getPageInfo(pageIndex: Int): PageInfo = mutex.withLock {
        pdfRenderer?.let { renderer ->
            if (pageIndex >= 0 && pageIndex < renderer.pageCount) {
                // We shouldn't close the page if it's the cached one, so let's just use the cached one if it matches
                if (currentPageIndex != pageIndex) {
                    currentPage?.close()
                    currentPage = renderer.openPage(pageIndex)
                    currentPageIndex = pageIndex
                }
                val page = currentPage!!
                return@withLock PageInfo(pageIndex, page.width, page.height)
            }
        }
        PageInfo(pageIndex, 0, 0)
    }

    override suspend fun renderTile(tile: Tile): Bitmap = withContext(Dispatchers.IO) {
        val z = tile.zoom.coerceAtLeast(0.1f)
        val bw = ((if (tile.width > 0) tile.width else 1) * z).toInt().coerceAtLeast(1)
        val bh = ((if (tile.height > 0) tile.height else 1) * z).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)

        mutex.withLock {
            pdfRenderer?.let { renderer ->
                if (tile.pageIndex >= 0 && tile.pageIndex < renderer.pageCount) {
                    try {
                        if (currentPageIndex != tile.pageIndex) {
                            currentPage?.close()
                            currentPage = renderer.openPage(tile.pageIndex)
                            currentPageIndex = tile.pageIndex
                        }

                        val page = currentPage!!
                        bitmap.eraseColor(Color.WHITE)
                        val matrix = android.graphics.Matrix()
                        matrix.postScale(z, z)
                        matrix.postTranslate(-tile.x * z, -tile.y * z)
                        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        bitmap
    }

    override suspend fun extractText(pageIndex: Int, x: Int, y: Int, width: Int, height: Int): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val document = textDocument ?: return@withLock ""
            if (pageIndex !in 0 until document.numberOfPages) return@withLock ""

            try {
                val page = document.getPage(pageIndex)
                val mediaBox = page.mediaBox ?: return@withLock ""
                val clampedRect = RectF(
                    x.toFloat().coerceIn(0f, mediaBox.width),
                    y.toFloat().coerceIn(0f, mediaBox.height),
                    (x + width).toFloat().coerceIn(0f, mediaBox.width),
                    (y + height).toFloat().coerceIn(0f, mediaBox.height)
                )
                if (clampedRect.width() <= 1f || clampedRect.height() <= 1f) {
                    return@withLock ""
                }

                val pdfRegion = RectF(
                    clampedRect.left,
                    mediaBox.height - clampedRect.bottom,
                    clampedRect.right,
                    mediaBox.height - clampedRect.top
                )

                val stripper = PDFTextStripperByArea().apply {
                    sortByPosition = true
                    addRegion(HIGHLIGHT_REGION_NAME, pdfRegion)
                    extractRegions(page)
                }

                stripper.getTextForRegion(HIGHLIGHT_REGION_NAME).trim()
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }
    }

    private fun closeOpenDocuments() {
        currentPage?.close()
        currentPage = null
        currentPageIndex = -1

        pdfRenderer?.close()
        fileDescriptor?.close()
        textDocument?.close()
        pdfRenderer = null
        fileDescriptor = null
        textDocument = null
    }

    private companion object {
        const val HIGHLIGHT_REGION_NAME = "highlight"
    }
}
