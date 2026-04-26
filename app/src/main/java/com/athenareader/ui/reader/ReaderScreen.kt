package com.athenareader.ui.reader

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

import com.athenareader.domain.model.PenTool
import com.athenareader.domain.model.ReaderSettings
import com.athenareader.ui.annotation.AnnotationToolbar
import com.athenareader.ui.annotation.AnnotationViewModel
import com.athenareader.ui.annotation.NotesPanel
import com.athenareader.ui.renderer.ViewportManager
import com.athenareader.ui.renderer.pdf.TileRendererPool

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
    var showNotes by remember { mutableStateOf(false) }
    var showOutline by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(0) }
    var requestedPageJump by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var jumpCounter by remember { mutableIntStateOf(0) }
    var editingHighlight by remember { mutableStateOf<com.athenareader.domain.model.Highlight?>(null) }
    var editedNote by remember { mutableStateOf("") }

    val activeTool by annotationViewModel.activeTool.collectAsState()
    val penSettings by annotationViewModel.penSettings.collectAsState()
    val highlighterSettings by annotationViewModel.highlighterSettings.collectAsState()
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
                // PDF reader — handles stylus input and renders strokes in the same Canvas
                PdfReaderView(
                    document = state.document,
                    pageCount = state.pageCount,
                    pageWidth = state.pageWidth,
                    pageHeight = state.pageHeight,
                    tilePool = tilePool,
                    viewportManager = viewportManager,
                    initialProgress = state.progress,
                    requestedPageJump = requestedPageJump,
                    activeToolFlow = annotationViewModel.activeTool,
                    strokesFlow = annotationViewModel.strokes,
                    livePointsFlow = annotationViewModel.livePoints,
                    activeColorFlow = annotationViewModel.activeColor,
                    strokeWidthFlow = annotationViewModel.strokeWidth,
                    onPointAdded = { annotationViewModel.addLivePoint(it) },
                    onStrokeCommitted = { pageIndex, isStylusButtonErasing, isTouchHighlight ->
                        if (isStylusButtonErasing || activeTool == PenTool.ERASER) {
                            annotationViewModel.eraseWithCurrentStroke(
                                pageIndex = pageIndex,
                                pageHeight = state.pageHeight,
                                pageGap = PAGE_GAP
                            )
                            return@PdfReaderView
                        }

                        val forceTool = if (isTouchHighlight) PenTool.HIGHLIGHTER else null
                        val stroke = annotationViewModel.takeCompletedStroke(pageIndex, forceTool)
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
                    },
                    onViewportChanged = { page, scrollX, scrollY, zoom ->
                        currentPage = page
                        viewModel.persistPdfViewport(page, scrollX, scrollY, zoom)
                    },
                    onSingleTap = { showHeader = !showHeader }
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
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { showOutline = !showOutline }) { Icon(Icons.Default.Menu, contentDescription = "Outline") }
                    IconButton(onClick = { showNotes = !showNotes }) { Icon(Icons.Default.Edit, contentDescription = "Notes") }
                }
            )
        }

        // Notes panel (right edge)
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            NotesPanel(
                visible = showNotes,
                highlights = highlights,
                onNavigateToPage = {
                    jumpCounter++
                    requestedPageJump = Pair(it, jumpCounter)
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
                            item.pageIndex?.let { pageIndex ->
                                jumpCounter++
                                requestedPageJump = Pair(pageIndex, jumpCounter)
                            }
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
            val pdfState = uiState as ReaderUiState.PdfActive
            Text(
                text = "${currentPage + 1} / ${pdfState.pageCount}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 8.dp)
            )
        }

        if (showHeader && settings.showPageScrubber) {
            val state = uiState as? ReaderUiState.PdfActive
            if (state != null) {
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    PageScrubber(
                        pageCount = state.pageCount,
                        currentPage = currentPage,
                        onPageSelected = { jumpCounter++; requestedPageJump = Pair(it, jumpCounter) }
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

        // Floating annotation toolbar (left edge)
        if (uiState is ReaderUiState.PdfActive) {
            androidx.compose.animation.AnimatedVisibility(
                visible = showHeader,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
            ) {
                AnnotationToolbar(
                    activeTool = activeTool,
                    penSettings = penSettings,
                    highlighterSettings = highlighterSettings,
                    onToolSelected = { annotationViewModel.selectTool(it) },
                    onColorSelected = { tool, color -> annotationViewModel.setColor(tool, color) },
                    onWidthChanged = { tool, width -> annotationViewModel.setStrokeWidth(tool, width) }
                )
            }
        }
    }
}

@Composable
fun EpubReaderView(
    document: com.athenareader.domain.model.Document,
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
