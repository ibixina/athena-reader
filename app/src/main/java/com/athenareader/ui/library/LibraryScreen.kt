package com.athenareader.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.athenareader.domain.model.Document

import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onPickFolder: () -> Unit,
    onPickFiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onDocumentClick: (Document) -> Unit
) {
    val documents by viewModel.documents.collectAsState()
    val selectedUri by viewModel.selectedUri.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("InkReader Library") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Text("⚙")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onPickFolder) {
                    Text(if (selectedUri == null) "Scan Folder" else "Change Folder")
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                OutlinedButton(onClick = onPickFiles) {
                    Text("Add Files")
                }
                
                if (selectedUri != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { viewModel.scanDirectory(selectedUri!!) }) {
                        Text("🔄") // Simple refresh icon
                    }
                }
            }
            
            selectedUri?.let {
                val folderName = Uri.parse(it).lastPathSegment ?: "Selected Folder"
                Text(
                    text = "Source: $folderName",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (documents.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (selectedUri == null) "Pick a folder to start" else "No documents found in folder")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(documents) { doc ->
                        DocumentItem(doc, onClick = { onDocumentClick(doc) })
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentItem(document: Document, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(document.name) },
        supportingContent = { Text("${document.format} - ${document.hash.take(8)}") },
        modifier = Modifier.clickable { onClick() }
    )
}
