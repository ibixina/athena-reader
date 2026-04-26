package com.athenareader.data.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.athenareader.domain.model.PageInfo
import com.athenareader.domain.model.Tile
import com.athenareader.domain.renderer.PdfRenderer as IPdfRenderer
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripperByArea
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
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
    private var currentFilePath: String? = null
    private var firstPageWidth: Int = -1
    private var firstPageHeight: Int = -1

    override suspend fun openDocument(filePath: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(filePath)
                    closeOpenDocuments()
                    firstPageWidth = -1
                    firstPageHeight = -1
                    currentFilePath = filePath
                    fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                    fileDescriptor?.let {
                        pdfRenderer = PdfRenderer(it)
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
        }
    }

    private suspend fun getTextDocument(): PDDocument? {
        textDocument?.let { return it }
        val filePath = currentFilePath ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(filePath)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    textDocument = PDDocument.load(input, com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly())
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
            textDocument
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
                if (pageIndex == 0) {
                    firstPageWidth = page.width
                    firstPageHeight = page.height
                }
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
                        
                        val targetW = if (firstPageWidth > 0) firstPageWidth else page.width
                        val targetH = if (firstPageHeight > 0) firstPageHeight else page.height
                        
                        // Fit page into targetW x targetH maintaining aspect ratio
                        val scaleX = targetW.toFloat() / page.width
                        val scaleY = targetH.toFloat() / page.height
                        val pageScale = minOf(scaleX, scaleY)
                        
                        val dx = (targetW - page.width * pageScale) / 2f
                        val dy = (targetH - page.height * pageScale) / 2f
                        
                        // Transform from PDF page space to uniform page space
                        matrix.postScale(pageScale, pageScale)
                        matrix.postTranslate(dx, dy)
                        
                        // Transform from uniform page space to tile bitmap space
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
        val document = getTextDocument() ?: return@withContext ""
        mutex.withLock {
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
                // PDFTextStripperByArea uses top-left origin, same as Android PdfRenderer
                val pdfRegion = RectF(
                    clampedRect.left,
                    clampedRect.top,
                    clampedRect.right,
                    clampedRect.bottom
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

    override suspend fun getOutline(): List<com.athenareader.domain.model.ReaderOutlineItem> = withContext(Dispatchers.IO) {
        val doc = getTextDocument() ?: return@withContext emptyList()
        mutex.withLock {
            val outline = doc.documentCatalog.documentOutline ?: return@withLock emptyList()
            val results = mutableListOf<com.athenareader.domain.model.ReaderOutlineItem>()
            var idCounter = 0

            fun traverse(item: PDOutlineItem?, depth: Int) {
                var current = item
                while (current != null) {
                    val title = current.title ?: "Untitled"
                    val indent = "  ".repeat(depth)
                    var pageIndex: Int? = null

                    try {
                        var dest = current.destination
                        if (dest == null && current.action is PDActionGoTo) {
                            dest = (current.action as PDActionGoTo).destination
                        }
                        if (dest is PDPageDestination) {
                            pageIndex = dest.retrievePageNumber()
                            if (pageIndex == -1) {
                                val page = dest.page
                                if (page != null) {
                                    pageIndex = doc.pages.indexOf(page)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    results.add(
                        com.athenareader.domain.model.ReaderOutlineItem(
                            id = "outline-${idCounter++}",
                            title = "$indent$title",
                            pageIndex = if (pageIndex != null && pageIndex >= 0) pageIndex else null
                        )
                    )

                    traverse(current.firstChild, depth + 1)
                    current = current.nextSibling
                }
            }

            try {
                traverse(outline.firstChild, 0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            results
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
        currentFilePath = null
    }

    private companion object {
        const val HIGHLIGHT_REGION_NAME = "highlight"
    }
}
