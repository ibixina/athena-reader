package com.inkreader.ui.annotation

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import com.inkreader.domain.model.PenTool

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
    visible: Boolean,
    activeTool: PenTool?,
    activeColor: Int,
    strokeWidth: Float,
    onToolSelected: (PenTool) -> Unit,
    onColorSelected: (Int) -> Unit,
    onWidthChanged: (Float) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 8.dp,
            shadowElevation = 4.dp,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tool row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolButton("✒️", "Pen", activeTool == PenTool.FINE_PEN) { onToolSelected(PenTool.FINE_PEN) }
                    ToolButton("🖍️", "Highlight", activeTool == PenTool.HIGHLIGHTER) { onToolSelected(PenTool.HIGHLIGHTER) }
                    ToolButton("⌫", "Eraser", activeTool == PenTool.ERASER) { onToolSelected(PenTool.ERASER) }

                    Spacer(Modifier.width(16.dp))

                    // Color dots
                    PRESET_COLORS.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
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
                    Text("Width", fontSize = 12.sp)
                    Slider(
                        value = strokeWidth,
                        onValueChange = onWidthChanged,
                        valueRange = 1f..20f,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolButton(emoji: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = if (selected) 12.dp else 0.dp,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 20.sp)
            Text(label, fontSize = 10.sp)
        }
    }
}
