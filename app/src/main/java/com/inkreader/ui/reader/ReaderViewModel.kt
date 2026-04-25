package com.inkreader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inkreader.domain.model.Chapter
import com.inkreader.domain.model.Document
import com.inkreader.domain.model.DocumentFormat
import com.inkreader.domain.model.ReaderOutlineItem
import com.inkreader.domain.model.ReadingProgress
import com.inkreader.domain.model.Stroke
import com.inkreader.domain.renderer.EpubRenderer
import com.inkreader.domain.renderer.PdfRenderer
import com.inkreader.domain.repository.ReadingProgressRepository
import com.inkreader.ui.annotation.HighlightProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val pdfRenderer: PdfRenderer,
    private val epubRenderer: EpubRenderer,
    private val readingProgressRepository: ReadingProgressRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState
    private var currentDocument: Document? = null
    private var pendingProgress: ReadingProgress? = null
    private var progressSaveJob: Job? = null

    fun openDocument(document: Document) {
        viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading
            currentDocument = document
            try {
                val savedProgress = readingProgressRepository.getProgress(document.id)
                if (document.format == DocumentFormat.PDF) {
                    pdfRenderer.openDocument(document.filePath)
                    val count = pdfRenderer.getPageCount()
                    val pageInfo = if (count > 0) pdfRenderer.getPageInfo(0) else null
                    val width = pageInfo?.width ?: 1000
                    val height = pageInfo?.height ?: 1400
                    val progress = savedProgress ?: ReadingProgress(documentId = document.id)
                    _uiState.value = ReaderUiState.PdfActive(
                        document = document,
                        pageCount = count,
                        pageWidth = width,
                        pageHeight = height,
                        outline = buildPdfOutline(count),
                        progress = progress
                    )
                } else {
                    val metadata = epubRenderer.openDocument(document.filePath)
                    val currentChapter = metadata.chapters.firstOrNull { it.id == savedProgress?.chapterId }
                        ?: metadata.chapters.firstOrNull()
                    if (currentChapter == null) {
                        _uiState.value = ReaderUiState.Error("EPUB has no readable chapters")
                        return@launch
                    }
                    val chapterContent = epubRenderer.getChapterContent(currentChapter.href)
                    _uiState.value = ReaderUiState.EpubActive(
                        document = document,
                        title = metadata.title,
                        chapters = metadata.chapters,
                        currentChapter = currentChapter,
                        chapterContent = chapterContent,
                        outline = metadata.chapters.map {
                            ReaderOutlineItem(
                                id = it.id,
                                title = it.title,
                                chapterId = it.id
                            )
                        },
                        progress = savedProgress ?: ReadingProgress(
                            documentId = document.id,
                            chapterId = currentChapter.id
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.value = ReaderUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun openChapter(chapterId: String) {
        val state = _uiState.value as? ReaderUiState.EpubActive ?: return
        val chapter = state.chapters.firstOrNull { it.id == chapterId } ?: return
        viewModelScope.launch {
            val content = epubRenderer.getChapterContent(chapter.href)
            val progress = state.progress.copy(chapterId = chapter.id, scrollY = 0)
            _uiState.value = state.copy(
                currentChapter = chapter,
                chapterContent = content,
                progress = progress
            )
            scheduleProgressSave(progress)
        }
    }

    fun persistPdfViewport(pageIndex: Int, scrollX: Int, scrollY: Int, zoom: Float) {
        val document = currentDocument ?: return
        val progress = ReadingProgress(
            documentId = document.id,
            pageIndex = pageIndex,
            scrollX = scrollX,
            scrollY = scrollY,
            zoom = zoom
        )
        scheduleProgressSave(progress)
    }

    fun persistEpubViewport(chapterId: String, scrollY: Int) {
        val document = currentDocument ?: return
        val progress = ReadingProgress(
            documentId = document.id,
            chapterId = chapterId,
            scrollY = scrollY
        )
        scheduleProgressSave(progress)
    }

    suspend fun extractTextForHighlight(
        stroke: Stroke,
        pageHeight: Int,
        pageGap: Int
    ): ExtractedHighlightResult? {
        val bounds = HighlightProcessor.computeBounds(
            points = stroke.points,
            pageIndex = stroke.pageIndex,
            pageHeight = pageHeight,
            pageGap = pageGap
        ) ?: return null

        val extractedText = HighlightProcessor.extractText(pdfRenderer, bounds)
            .takeUnless { it.isBlank() || it == PDF_EXTRACTION_PLACEHOLDER }
            ?: fallbackHighlightText(stroke.pageIndex)

        return ExtractedHighlightResult(
            extractedText = extractedText,
            rangeData = "${bounds.x},${bounds.y},${bounds.width},${bounds.height}"
        )
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            pendingProgress?.let { readingProgressRepository.saveProgress(it) }
            pdfRenderer.closeDocument()
            epubRenderer.closeDocument()
        }
    }

    private fun scheduleProgressSave(progress: ReadingProgress) {
        pendingProgress = progress
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            delay(500)
            readingProgressRepository.saveProgress(progress)
        }
    }

    private fun buildPdfOutline(pageCount: Int): List<ReaderOutlineItem> {
        if (pageCount <= 0) return emptyList()
        val stride = if (pageCount <= 20) 1 else 10
        return buildList {
            for (pageIndex in 0 until pageCount step stride) {
                add(
                    ReaderOutlineItem(
                        id = "page-$pageIndex",
                        title = "Page ${pageIndex + 1}",
                        pageIndex = pageIndex
                    )
                )
            }
            if (lastOrNull()?.pageIndex != pageCount - 1) {
                add(
                    ReaderOutlineItem(
                        id = "page-${pageCount - 1}",
                        title = "Page $pageCount",
                        pageIndex = pageCount - 1
                    )
                )
            }
        }
    }

    private fun fallbackHighlightText(pageIndex: Int): String {
        return "Highlighted passage on page ${pageIndex + 1}"
    }

    data class ExtractedHighlightResult(
        val extractedText: String,
        val rangeData: String
    )

    private companion object {
        const val PDF_EXTRACTION_PLACEHOLDER = "Text extraction placeholder"
    }
}

sealed class ReaderUiState {
    object Loading : ReaderUiState()
    data class PdfActive(
        val document: Document,
        val pageCount: Int,
        val pageWidth: Int,
        val pageHeight: Int,
        val outline: List<ReaderOutlineItem>,
        val progress: ReadingProgress
    ) : ReaderUiState()

    data class EpubActive(
        val document: Document,
        val title: String,
        val chapters: List<Chapter>,
        val currentChapter: Chapter,
        val chapterContent: String,
        val outline: List<ReaderOutlineItem>,
        val progress: ReadingProgress
    ) : ReaderUiState()

    data class Error(val message: String) : ReaderUiState()
}
