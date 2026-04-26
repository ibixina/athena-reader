package com.athenareader.domain.model

data class ReaderSettings(
    val showPageNumbers: Boolean = true,
    val showPageScrubber: Boolean = true,
    val keepScreenOn: Boolean = true,
    val penColor: Int = android.graphics.Color.BLACK,
    val penWidth: Float = 3f,
    val highlighterColor: Int = android.graphics.Color.parseColor("#FF8F00"),
    val highlighterWidth: Float = 12f
)

