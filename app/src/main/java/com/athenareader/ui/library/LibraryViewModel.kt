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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    getDocumentsUseCase: GetDocumentsUseCase,
    private val scanDocumentsUseCase: ScanDocumentsUseCase,
    private val addDocumentsUseCase: AddDocumentsUseCase
) : ViewModel() {

    private val _selectedUri = MutableStateFlow<String?>(null)
    val selectedUri = _selectedUri.asStateFlow()

    val documents: StateFlow<List<Document>> = getDocumentsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
