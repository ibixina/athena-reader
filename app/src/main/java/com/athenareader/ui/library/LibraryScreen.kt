package com.athenareader.ui.library

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.athenareader.domain.model.Document
import com.athenareader.domain.model.DocumentFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onPickFolder: () -> Unit,
    onPickFiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onDocumentClick: (Document) -> Unit,
    coverCache: com.athenareader.core.cache.CoverCache? = null
) {
    val documents by viewModel.documents.collectAsState()
    val selectedUri by viewModel.selectedUri.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ATHENA", fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.List, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Recently Viewed") },
                                onClick = { viewModel.setSortOption(SortOption.RECENTLY_VIEWED); showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Alphabetical (A-Z)") },
                                onClick = { viewModel.setSortOption(SortOption.ALPHA_ASC); showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Alphabetical (Z-A)") },
                                onClick = { viewModel.setSortOption(SortOption.ALPHA_DESC); showSortMenu = false }
                            )
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onPickFolder,
                        shape = RoundedCornerShape(0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text(if (selectedUri == null) "SCAN FOLDER" else "CHANGE FOLDER", fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    if (selectedUri != null) {
                        IconButton(onClick = { viewModel.scanDirectory(selectedUri!!) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
                
                Button(
                    onClick = onPickFiles,
                    shape = RoundedCornerShape(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ADD FILES", fontWeight = FontWeight.Bold)
                }
            }
            
            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.onSurface)

            if (documents.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (selectedUri == null) "SELECT A DIRECTORY" else "EMPTY SHELF",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(documents) { doc ->
                        BookShelfItem(doc, onClick = { onDocumentClick(doc) }, coverCache = coverCache)
                    }
                }
            }
        }
    }
}

@Composable
fun BookShelfItem(document: Document, onClick: () -> Unit, coverCache: com.athenareader.core.cache.CoverCache? = null) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() }
    ) {
        // Book Cover
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(2.dp, MaterialTheme.colorScheme.onSurface),
            contentAlignment = Alignment.TopStart
        ) {
            if (document.format == DocumentFormat.PDF) {
                PdfCoverImage(
                    uriString = document.filePath,
                    modifier = Modifier.fillMaxSize(),
                    coverCache = coverCache
                )
            } else {
                Text(
                    text = document.format.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
                Text(
                    text = document.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Progress Bar
        LinearProgressIndicator(
            progress = { document.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.onSurface,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            drawStopIndicator = {}
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Title
        Text(
            text = document.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun PdfCoverImage(uriString: String, modifier: Modifier = Modifier, coverCache: com.athenareader.core.cache.CoverCache? = null) {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val context = LocalContext.current

    val cacheKey = uriString

    LaunchedEffect(uriString) {
        withContext(Dispatchers.IO) {
            val cached = coverCache?.get(cacheKey)
            if (cached != null) {
                withContext(Dispatchers.Main) {
                    bitmap = cached.asImageBitmap()
                }
                return@withContext
            }

            try {
                val uri = Uri.parse(uriString)
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val renderer = android.graphics.pdf.PdfRenderer(pfd)
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        val scale = 1.5f
                        val w = (page.width * scale).toInt().coerceIn(100, 600)
                        val h = (page.height * scale).toInt().coerceIn(100, 800)
                        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()

                        coverCache?.put(cacheKey, bmp)

                        withContext(Dispatchers.Main) {
                            bitmap = bmp.asImageBitmap()
                        }
                    }
                    renderer.close()
                }
            } catch (t: Throwable) {
                // Fallback on error
            }
        }
    }
    
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap!!,
            contentDescription = "Cover",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("PDF", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
