package com.inkreader.domain.model

data class ReaderOutlineItem(
    val id: String,
    val title: String,
    val pageIndex: Int? = null,
    val chapterId: String? = null
)

