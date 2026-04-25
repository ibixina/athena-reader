package com.inkreader.domain.model

data class ReaderSettings(
    val showPageNumbers: Boolean = true,
    val showPageScrubber: Boolean = true,
    val keepScreenOn: Boolean = true
)

