package com.athenareader.ui.annotation

import android.graphics.Color as AndroidColor
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athenareader.domain.model.PenTool

private val PRESET_COLORS = listOf(
    AndroidColor.BLACK,
    AndroidColor.RED,
    AndroidColor.parseColor("#1565C0"),
    AndroidColor.parseColor("#2E7D32"),
    AndroidColor.parseColor("#FF8F00"),
    AndroidColor.parseColor("#6A1B9A")
)

@Composable
fun AnnotationToolbar(
    activeTool: PenTool?,
    penSettings: ToolSettings,
    highlighterSettings: ToolSettings,
    onToolSelected: (PenTool) -> Unit,
    onColorSelected: (PenTool, Int) -> Unit,
    onWidthChanged: (PenTool, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(activeTool) {
        if (activeTool == null) showSettings = false
    }

    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Tool buttons column — pen and highlight only (eraser is pen button)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniToolFab(
                    emoji = "✒️",
                    selected = activeTool == PenTool.FINE_PEN,
                    onClick = {
                        if (activeTool == PenTool.FINE_PEN) showSettings = !showSettings
                        else { showSettings = false; onToolSelected(PenTool.FINE_PEN) }
                    }
                )
                MiniToolFab(
                    emoji = "🖍️",
                    selected = activeTool == PenTool.HIGHLIGHTER,
                    onClick = {
                        if (activeTool == PenTool.HIGHLIGHTER) showSettings = !showSettings
                        else { showSettings = false; onToolSelected(PenTool.HIGHLIGHTER) }
                    }
                )
            }

            // Settings popover (right of buttons)
            AnimatedVisibility(
                visible = showSettings && activeTool != null && activeTool != PenTool.ERASER,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f)
            ) {
                val currentColor = when (activeTool) {
                    PenTool.FINE_PEN -> penSettings.color
                    PenTool.HIGHLIGHTER -> highlighterSettings.color
                    else -> 0
                }
                val currentWidth = when (activeTool) {
                    PenTool.FINE_PEN -> penSettings.strokeWidth
                    PenTool.HIGHLIGHTER -> highlighterSettings.strokeWidth
                    else -> 4f
                }
                ToolSettingsPopover(
                    activeTool = activeTool,
                    activeColor = currentColor,
                    strokeWidth = currentWidth,
                    onColorSelected = { color -> activeTool?.let { onColorSelected(it, color) } },
                    onWidthChanged = { width -> activeTool?.let { onWidthChanged(it, width) } }
                )
            }
        }
    }
}

@Composable
private fun MiniToolFab(emoji: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(42.dp)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                else Modifier
            )
            .clickable(onClick = onClick),
        shape = CircleShape,
        tonalElevation = if (selected) 12.dp else 4.dp,
        shadowElevation = if (selected) 6.dp else 2.dp,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 18.sp)
        }
    }
}

@Composable
private fun ToolSettingsPopover(
    activeTool: PenTool?,
    activeColor: Int,
    strokeWidth: Float,
    onColorSelected: (Int) -> Unit,
    onWidthChanged: (Float) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 8.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.widthIn(max = 240.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Color row
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PRESET_COLORS.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(
                                width = if (color == activeColor) 2.dp else 0.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                            .clickable { onColorSelected(color) }
                    )
                }
            }

            // Width slider
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("W", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = strokeWidth,
                    onValueChange = onWidthChanged,
                    valueRange = if (activeTool == PenTool.FINE_PEN) 1f..8f else 4f..30f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                )
                Text("${strokeWidth.toInt()}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
