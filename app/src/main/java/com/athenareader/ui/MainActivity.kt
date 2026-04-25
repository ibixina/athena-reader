package com.athenareader.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.athenareader.domain.model.Document
import com.athenareader.ui.library.LibraryScreen
import com.athenareader.ui.library.LibraryViewModel
import com.athenareader.ui.reader.ReaderScreen
import com.athenareader.ui.annotation.AnnotationViewModel
import com.athenareader.ui.reader.ReaderViewModel
import com.athenareader.ui.renderer.ViewportManager
import com.athenareader.ui.renderer.pdf.TileRendererPool
import com.athenareader.ui.settings.SettingsScreen
import com.athenareader.ui.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var tilePool: TileRendererPool
    @Inject lateinit var viewportManager: ViewportManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            InkReaderTheme {
                AppNavigation(tilePool, viewportManager)
            }
        }
    }
}

@Composable
fun AppNavigation(
    tilePool: TileRendererPool,
    viewportManager: ViewportManager
) {
    var destination by remember { mutableStateOf<AppDestination>(AppDestination.Library) }
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settings by settingsViewModel.settings.collectAsState()
    
    val context = LocalContext.current
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            libraryViewModel.scanDirectory(it.toString())
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            libraryViewModel.addFiles(uris.map { it.toString() })
        }
    }

    when (val currentDestination = destination) {
        is AppDestination.Library -> {
            LibraryScreen(
                viewModel = libraryViewModel,
                onPickFolder = { folderPickerLauncher.launch(null) },
                onPickFiles = { filePickerLauncher.launch(arrayOf("application/pdf", "application/epub+zip")) },
                onOpenSettings = { destination = AppDestination.Settings },
                onDocumentClick = { destination = AppDestination.Reader(it) }
            )
        }

        is AppDestination.Settings -> {
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { destination = AppDestination.Library }
            )
        }

        is AppDestination.Reader -> {
            val readerViewModel: ReaderViewModel = hiltViewModel()
            val annotationViewModel: AnnotationViewModel = hiltViewModel()
            LaunchedEffect(currentDestination.document.id) {
                readerViewModel.openDocument(currentDestination.document)
            }
            ReaderScreen(
                viewModel = readerViewModel,
                annotationViewModel = annotationViewModel,
                tilePool = tilePool,
                viewportManager = viewportManager,
                settings = settings,
                onBack = { destination = AppDestination.Library }
            )
        }
    }
}

@Composable
fun InkReaderTheme(content: @Composable () -> Unit) {
    androidx.compose.material3.MaterialTheme(
        content = content
    )
}

private sealed interface AppDestination {
    data object Library : AppDestination
    data object Settings : AppDestination
    data class Reader(val document: Document) : AppDestination
}
