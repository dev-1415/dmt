package dev.jyotiraditya.dmt.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.jyotiraditya.dmt.ui.theme.Muted

/** A subtle 1dp line along the bottom edge, marking a pinned section. */
fun Modifier.bottomSeparator(color: Color = Muted.copy(alpha = 0.4f)): Modifier =
    drawBehind {
        val stroke = 1.dp.toPx()
        drawRect(
            color = color,
            topLeft = Offset(0f, size.height - stroke),
            size = Size(size.width, stroke),
        )
    }

/** A subtle 1dp line along the top edge, marking a pinned section. */
fun Modifier.topSeparator(color: Color = Muted.copy(alpha = 0.4f)): Modifier =
    drawBehind {
        val stroke = 1.dp.toPx()
        drawRect(
            color = color,
            topLeft = Offset(0f, 0f),
            size = Size(size.width, stroke),
        )
    }
