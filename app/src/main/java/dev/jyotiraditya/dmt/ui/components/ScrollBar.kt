package dev.jyotiraditya.dmt.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.jyotiraditya.dmt.ui.theme.Ink
import dev.jyotiraditya.dmt.ui.theme.Muted
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ScrollBarWidth = 24.dp
private val ThumbWidth = 4.dp
private val ThumbMinHeight = 32.dp

/** A draggable scrollbar for [state]. */
@Composable
fun ScrollBar(
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    val info = state.layoutInfo
    val total = info.totalItemsCount
    val visible = info.visibleItemsInfo.size
    if (total <= 0 || visible >= total) return

    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxHeight().width(ScrollBarWidth)) {
        val density = LocalDensity.current
        val trackHeight = constraints.maxHeight.toFloat()
        val minThumb = with(density) { ThumbMinHeight.toPx() }
        val thumbHeight = (trackHeight * visible / total).coerceIn(minThumb, trackHeight)
        val first = state.firstVisibleItemIndex
        val firstOffset = state.firstVisibleItemScrollOffset
        val firstSize = info.visibleItemsInfo.firstOrNull { it.index == first }?.size ?: 1
        val scrollFraction =
            when {
                !state.canScrollBackward -> 0f
                !state.canScrollForward -> 1f
                else -> ((first + firstOffset.toFloat() / firstSize) / total).coerceIn(0f, 1f)
            }
        val thumbOffset = (trackHeight - thumbHeight) * scrollFraction
        val thumbColor = if (dragging) Ink else Muted.copy(alpha = 0.5f)

        Box(
            modifier =
                Modifier.align(Alignment.CenterEnd)
                    .width(ThumbWidth)
                    .fillMaxHeight()
                    .background(Muted.copy(alpha = 0.12f), RoundedCornerShape(2.dp)),
        )
        Box(
            modifier =
                Modifier.align(Alignment.TopEnd)
                    .offset { IntOffset(0, thumbOffset.roundToInt()) }
                    .width(ThumbWidth)
                    .height(with(density) { thumbHeight.toDp() })
                    .background(thumbColor, RoundedCornerShape(2.dp)),
        )
        Box(
            modifier =
                Modifier.fillMaxHeight()
                    .width(ScrollBarWidth)
                    .pointerInput(total, trackHeight) {
                        fun scrollTo(y: Float) {
                            val fraction = (y / trackHeight).coerceIn(0f, 1f)
                            val index = (fraction * total).toInt().coerceIn(0, total - 1)
                            scope.launch { state.scrollToItem(index) }
                        }
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragging = true
                                scrollTo(offset.y)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                scrollTo(change.position.y)
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        )
                    },
        )
    }
}
