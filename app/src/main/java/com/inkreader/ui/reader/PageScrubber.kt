package com.inkreader.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PageScrubber(
    pageCount: Int,
    currentPage: Int,
    onPageSelected: (Int) -> Unit
) {
    if (pageCount <= 1) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Page ${currentPage + 1} of $pageCount",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = currentPage.toFloat(),
                onValueChange = { onPageSelected(it.toInt()) },
                valueRange = 0f..(pageCount - 1).toFloat()
            )
        }
    }
}

