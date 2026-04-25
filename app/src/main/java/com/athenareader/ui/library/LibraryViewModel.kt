package com.athenareader.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.athenareader.domain.model.Document
import com.athenareader.domain.model.DocumentFormat
import com.athenareader.domain.usecase.AddDocumentsUseCase
import com.athenareader.domain.usecase.GetDocumentsUseCase
import com.athenareader.domain.usecase.ScanDocumentsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import com.athenareader.domain.repository.ReadingProgressRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOption {
    RECENTLY_VIEWED, ALPHA_ASC, ALPHA_DESC
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    getDocumentsUseCase: GetDocumentsUseCase,
    private val readingProgressRepository: ReadingProgressRepository,
    private val scanDocumentsUseCase: ScanDocumentsUseCase,
    private val addDocumentsUseCase: AddDocumentsUseCase
) : ViewModel() {

    private val _selectedUri = MutableStateFlow<String?>(null)
    val selectedUri = _selectedUri.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.RECENTLY_VIEWED)
    val sortOption = _sortOption.asStateFlow()

    val documents: StateFlow<List<Document>> = combine(
        getDocumentsUseCase(),
        readingProgressRepository.getAllProgresses(),
        _sortOption
    ) { docs, progresses, sort ->
        val progressMap = progresses.associateBy { it.documentId }
        val docsWithProgress = docs.map { doc ->
            val p = progressMap[doc.id]
            val progressVal = if (p != null && p.totalPages > 0) {
                (p.pageIndex + 1).toFloat() / p.totalPages
            } else 0f
            doc.copy(progress = progressVal.coerceIn(0f, 1f))
        }
        
        when (sort) {
            SortOption.RECENTLY_VIEWED -> docsWithProgress.sortedByDescending { it.lastOpened }
            SortOption.ALPHA_ASC -> docsWithProgress.sortedBy { it.name.lowercase() }
            SortOption.ALPHA_DESC -> docsWithProgress.sortedByDescending { it.name.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun scanDirectory(uriString: String) {
        _selectedUri.value = uriString
        viewModelScope.launch {
            scanDocumentsUseCase(uriString)
        }
    }

    fun addFiles(uriStrings: List<String>) {
        viewModelScope.launch {
            addDocumentsUseCase(uriStrings)
        }
    }
}
