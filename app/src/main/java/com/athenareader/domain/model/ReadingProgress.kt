package com.athenareader.domain.model

data class ReadingProgress(
    val documentId: Long,
    val pageIndex: Int = 0,
    val scrollX: Int = 0,
    val scrollY: Int = 0,
    val zoom: Float = 1.0f,
    val chapterId: String? = null,
    val totalPages: Int = 0
)

