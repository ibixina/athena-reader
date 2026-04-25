package com.inkreader.domain.model

import android.graphics.Bitmap

data class Tile(
    val pageIndex: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val zoom: Float,
    val bitmap: Bitmap? = null
)

data class PageInfo(
    val index: Int,
    val width: Int,
    val height: Int
)
