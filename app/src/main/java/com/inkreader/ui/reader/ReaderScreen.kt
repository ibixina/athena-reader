package com.inkreader.ui.reader

import android.app.Activity
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

import com.inkreader.domain.model.PenTool
import com.inkreader.domain.model.ReaderSettings
import com.inkreader.ui.annotation.AnnotationToolbar
import com.inkreader.ui.annotation.AnnotationViewModel
import com.inkreader.ui.annotation.DrawingOverlay
import com.inkreader.ui.annotation.NotesPanel
import com.inkreader.ui.renderer.ViewportManager
import com.inkreader.ui.renderer.pdf.TileRendererPool

private const val PAGE_GAP = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    annotationViewModel: AnnotationViewModel,
    tilePool: TileRendererPool,
    viewportManager: ViewportManager,
    settings: ReaderSettings,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    var showHeader by remember { mutableStateOf(false) }
    var showToolbar by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var showOutline by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(0) }
    var requestedPageIndex by remember { mutableStateOf<Int?>(null) }
    var overlayScale by remember { mutableFloatStateOf(1f) }
    var overlayOffsetX by remember { mutableFloatStateOf(0f) }
    var overlayOffsetY by remember { mutableFloatStateOf(0f) }
    var editingHighlight by remember { mutableStateOf<com.inkreader.domain.model.Highlight?>(null) }
    var editedNote by remember { mutableStateOf("") }

    val activeTool by annotationViewModel.activeTool.collectAsState()
    val activeColor by annotationViewModel.activeColor.collectAsState()
    val strokeWidth by annotationViewModel.strokeWidth.collectAsState()
    val livePoints by annotationViewModel.livePoints.collectAsState()
    val strokes by annotationViewModel.strokes.collectAsState()
    val highlights by annotationViewModel.highlights.collectAsState()

    // Load annotations when document is ready
    val pdfDocumentId = (uiState as? ReaderUiState.PdfActive)?.document?.id
    LaunchedEffect(pdfDocumentId) {
        pdfDocumentId?.let(annotationViewModel::loadDocument)
    }

    // Fullscreen immersive mode
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(showHeader) {
        val window = (context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (showHeader) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose {
            view.keepScreenOn = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is ReaderUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            is ReaderUiState.PdfActive -> {
                // PDF reader
                PdfReaderView(
                    document = state.document,
                    pageCount = state.pageCount,
                    pageWidth = state.pageWidth,
                    pageHeight = state.pageHeight,
                    tilePool = tilePool,
                    viewportManager = viewportManager,
                    initialProgress = state.progress,
                    requestedPageIndex = requestedPageIndex,
                    onTransformChanged = { scale, offsetX, offsetY ->
                        overlayScale = scale
                        overlayOffsetX = offsetX
                        overlayOffsetY = offsetY
                    },
                    onViewportChanged = { page, scrollX, scrollY, zoom ->
                        currentPage = page
                        viewModel.persistPdfViewport(page, scrollX, scrollY, zoom)
                    },
                    onSingleTap = { showHeader = !showHeader }
                )

                // Drawing overlay on top of PDF
                // TODO: pass actual scale/offset from PdfReaderView once exposed via state hoisting
                DrawingOverlay(
                    strokes = strokes,
                    livePoints = livePoints,
                    activeTool = activeTool,
                    activeColor = activeColor,
                    strokeWidth = strokeWidth,
                    scale = overlayScale,
                    offsetX = overlayOffsetX,
                    offsetY = overlayOffsetY,
                    pageWidth = state.pageWidth,
                    pageHeight = state.pageHeight,
                    pageGap = PAGE_GAP,
                    pageCount = state.pageCount,
                    onPointAdded = { annotationViewModel.addLivePoint(it) },
                    onStrokeCommitted = { pageIndex ->
                        if (activeTool == PenTool.ERASER) {
                            annotationViewModel.eraseWithCurrentStroke(
                                pageIndex = pageIndex,
                                pageHeight = state.pageHeight,
                                pageGap = PAGE_GAP
                            )
                            return@DrawingOverlay
                        }

                        val stroke = annotationViewModel.takeCompletedStroke(pageIndex)
                        if (stroke != null) {
                            annotationViewModel.saveStroke(stroke)

                            if (stroke.tool == PenTool.HIGHLIGHTER) {
                                scope.launch {
                                    val result = viewModel.extractTextForHighlight(
                                        stroke = stroke,
                                        pageHeight = state.pageHeight,
                                        pageGap = PAGE_GAP
                                    ) ?: return@launch

                                    annotationViewModel.commitHighlight(
                                        pageIndex = stroke.pageIndex,
                                        extractedText = result.extractedText,
                                        rangeData = result.rangeData
                                    )
                                }
                            }
                        }
                    }
                )
            }
            is ReaderUiState.EpubActive -> EpubReaderView(
                document = state.document,
                currentChapterId = state.currentChapter.id,
                contentHtml = state.chapterContent,
                restoredScrollY = if (state.progress.chapterId == state.currentChapter.id) {
                    state.progress.scrollY
                } else {
                    0
                },
                onScrollChanged = { scrollY ->
                    viewModel.persistEpubViewport(state.currentChapter.id, scrollY)
                },
                onSingleTap = { showHeader = !showHeader }
            )
            is ReaderUiState.Error -> Text("Error: ${state.message}", modifier = Modifier.align(Alignment.Center))
        }

        // Header
        AnimatedVisibility(
            visible = showHeader,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it }
        ) {
            TopAppBar(
                title = {
                    val title = when (val state = uiState) {
                        is ReaderUiState.PdfActive -> state.document.name
                        is ReaderUiState.EpubActive -> state.title
                        else -> "Reader"
                    }
                    Text(title)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
                actions = {
                    IconButton(onClick = { showOutline = !showOutline }) { Text("☰") }
                    IconButton(onClick = { showToolbar = !showToolbar }) { Text("✏️") }
                    IconButton(onClick = { showNotes = !showNotes }) { Text("📝") }
                }
            )
        }

        // Notes panel (right edge)
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            NotesPanel(
                visible = showNotes,
                highlights = highlights,
                onNavigateToPage = {
                    requestedPageIndex = it
                    showNotes = false
                },
                onEditNote = { highlight ->
                    editingHighlight = highlight
                    editedNote = highlight.userNote
                },
                onDismiss = { showNotes = false }
            )
        }

        val outlineItems = when (val state = uiState) {
            is ReaderUiState.PdfActive -> state.outline
            is ReaderUiState.EpubActive -> state.outline
            else -> emptyList()
        }
        val currentOutlineId = when (val state = uiState) {
            is ReaderUiState.PdfActive -> "page-$currentPage"
            is ReaderUiState.EpubActive -> state.currentChapter.id
            else -> null
        }

        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            OutlineDrawer(
                visible = showOutline,
                items = outlineItems,
                currentItemId = currentOutlineId,
                onSelect = { item ->
                    when (val state = uiState) {
                        is ReaderUiState.PdfActive -> {
                            requestedPageIndex = item.pageIndex
                        }
                        is ReaderUiState.EpubActive -> {
                            item.chapterId?.let(viewModel::openChapter)
                        }
                        else -> Unit
                    }
                    showOutline = false
                },
                onDismiss = { showOutline = false }
            )
        }

        if (settings.showPageNumbers && uiState is ReaderUiState.PdfActive) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 72.dp, end = 16.dp),
                shape = RoundedCornerShape(999.dp),
                tonalElevation = 6.dp
            ) {
                Text(
                    text = "Page ${currentPage + 1}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        if (showHeader && settings.showPageScrubber) {
            val state = uiState as? ReaderUiState.PdfActive
            if (state != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (showToolbar) 88.dp else 0.dp)
                ) {
                    PageScrubber(
                        pageCount = state.pageCount,
                        currentPage = currentPage,
                        onPageSelected = { requestedPageIndex = it }
                    )
                }
            }
        }

        if (editingHighlight != null) {
            AlertDialog(
                onDismissRequest = { editingHighlight = null },
                confirmButton = {
                    TextButton(
                        onClick = {
                            editingHighlight?.let { annotationViewModel.updateHighlightNote(it, editedNote) }
                            editingHighlight = null
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingHighlight = null }) {
                        Text("Cancel")
                    }
                },
                title = { Text("Edit Note") },
                text = {
                    OutlinedTextField(
                        value = editedNote,
                        onValueChange = { editedNote = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            )
        }

        // Annotation toolbar (bottom)
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            AnnotationToolbar(
                visible = showToolbar,
                activeTool = activeTool,
                activeColor = activeColor,
                strokeWidth = strokeWidth,
                onToolSelected = { annotationViewModel.selectTool(it) },
                onColorSelected = { annotationViewModel.setColor(it) },
                onWidthChanged = { annotationViewModel.setStrokeWidth(it) }
            )
        }
    }
}

@Composable
fun EpubReaderView(
    document: com.inkreader.domain.model.Document,
    currentChapterId: String,
    contentHtml: String,
    restoredScrollY: Int,
    onScrollChanged: (Int) -> Unit,
    onSingleTap: () -> Unit
) {
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = false
                setBackgroundColor(android.graphics.Color.WHITE)
                setOnLongClickListener { false }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        post { scrollTo(0, restoredScrollY) }
                    }
                }
                setOnScrollChangeListener { _, _, scrollY, _, _ ->
                    onScrollChanged(scrollY)
                }
                setOnClickListener { onSingleTap() }
            }
        },
        update = { webView ->
            val loadKey = "$currentChapterId:${contentHtml.hashCode()}"
            val wrappedHtml = """
                <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                        <style>
                            body { font-family: serif; line-height: 1.6; margin: 16px; color: #1d1d1d; }
                            img, svg { max-width: 100%; height: auto; }
                            pre, code { white-space: pre-wrap; }
                        </style>
                    </head>
                    <body data-chapter-id="$currentChapterId">
                        $contentHtml
                    </body>
                </html>
            """.trimIndent()
            if (webView.tag != loadKey) {
                webView.tag = loadKey
                webView.loadDataWithBaseURL(document.filePath, wrappedHtml, "text/html", "utf-8", null)
            }
        }
    )
}
