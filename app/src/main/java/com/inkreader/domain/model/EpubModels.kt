package com.inkreader.domain.model

data class Chapter(
    val id: String,
    val title: String,
    val href: String,
    val index: Int
)

data class EpubMetadata(
    val title: String,
    val author: String,
    val chapters: List<Chapter>
)
