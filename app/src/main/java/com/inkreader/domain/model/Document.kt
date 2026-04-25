package com.inkreader.domain.model

data class Document(
    val id: Long = 0,
    val name: String,
    val filePath: String,
    val hash: String,
    val format: DocumentFormat,
    val lastOpened: Long
)

enum class DocumentFormat {
    PDF, EPUB
}
