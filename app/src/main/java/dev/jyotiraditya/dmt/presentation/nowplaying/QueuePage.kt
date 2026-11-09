package dev.jyotiraditya.dmt.presentation.nowplaying

import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberShuffleButtonState
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.domain.model.LibrarySnapshot
import dev.jyotiraditya.dmt.domain.model.Track
import dev.jyotiraditya.dmt.presentation.main.LocalContentBottom
import dev.jyotiraditya.dmt.presentation.player.DmtAction
import dev.jyotiraditya.dmt.presentation.player.DmtState
import dev.jyotiraditya.dmt.ui.components.Artwork
import dev.jyotiraditya.dmt.ui.components.ChoiceRow
import dev.jyotiraditya.dmt.ui.components.DmtIcon
import dev.jyotiraditya.dmt.ui.components.DmtSheet
import dev.jyotiraditya.dmt.ui.components.EmptyState
import dev.jyotiraditya.dmt.ui.components.PlayingBars
import dev.jyotiraditya.dmt.ui.components.ScrollBar
import dev.jyotiraditya.dmt.ui.components.SearchBox
import dev.jyotiraditya.dmt.ui.components.SheetAction
import dev.jyotiraditya.dmt.ui.components.SheetHeader
import dev.jyotiraditya.dmt.ui.components.TextLink
import dev.jyotiraditya.dmt.ui.components.pressable
import dev.jyotiraditya.dmt.ui.components.topSeparator
import dev.jyotiraditya.dmt.ui.icons.DmtIcons
import dev.jyotiraditya.dmt.ui.theme.Ink
import dev.jyotiraditya.dmt.ui.theme.LocalAccent
import dev.jyotiraditya.dmt.ui.theme.Margin
import dev.jyotiraditya.dmt.ui.theme.Muted
import dev.jyotiraditya.dmt.ui.theme.Surface2
import dev.jyotiraditya.dmt.util.QueueEntry

private val RowArt = 44.dp

/** The order the queue is shown in; the player's own order is [QUEUE]. */
private enum class QueueSort(@param:StringRes val label: Int) {
    QUEUE(R.string.queue_sort_queue),
    TITLE(R.string.sort_title),
    ARTIST(R.string.sort_artist),
    ALBUM(R.string.sort_album),
    DATE_ADDED(R.string.sort_date_added),
    DATE_MODIFIED(R.string.sort_date_modified),
}

/** The whole queue, with the playing track marked, so earlier tracks stay visible. */
@OptIn(UnstableApi::class)
@Composable
internal fun QueuePage(
    state: DmtState,
    player: Player?,
    tint: Color,
    dispatch: (DmtAction) -> Unit,
    scrollToCurrent: Boolean,
    onScrolledToCurrent: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(QueueSort.QUEUE) }
    var sorting by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<QueueEntry?>(null) }
    menuFor?.let { entry ->
        QueueItemSheet(
            entry = entry,
            track = state.library.track(entry.mediaId),
            dispatch = dispatch,
            onDismiss = { menuFor = null },
        )
    }
    if (sorting) {
        QueueSortSheet(
            selected = sort,
            onPick = {
                sort = it
                sorting = false
            },
            onDismiss = { sorting = false },
        )
    }
    val shuffled = player?.let { rememberShuffleButtonState(it).shuffleOn } ?: false
    val currentIndex = state.queue.getOrNull(state.queuePosition)?.index
    val filtered =
        remember(state.queue, state.library, query, sort, shuffled) {
            val base =
                if (query.isBlank()) {
                    state.queue
                } else {
                    state.queue.filter { entry ->
                        entry.title.contains(query, ignoreCase = true) ||
                            entry.artist.contains(query, ignoreCase = true)
                    }
                }
            if (shuffled || sort == QueueSort.QUEUE) {
                base
            } else {
                base.sortedWith(queueComparator(sort, state.library))
            }
        }
    if (state.queue.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.queue_empty_title),
            body = stringResource(R.string.queue_empty_body),
            modifier = Modifier.fillMaxSize().background(tint).padding(horizontal = Margin),
        )
        return
    }
    val listState = rememberLazyListState()
    val reorderable = !shuffled && sort == QueueSort.QUEUE
    val order = remember(filtered) { filtered.toMutableStateList() }
    val displayed = if (reorderable) order else filtered
    val drag = remember { DragState(listState) }
    LaunchedEffect(scrollToCurrent, displayed) {
        if (!scrollToCurrent) return@LaunchedEffect
        val position = displayed.indexOfFirst { it.index == currentIndex }
        if (position < 0) {
            if (query.isNotEmpty()) {
                query = ""
            } else {
                onScrolledToCurrent()
            }
            return@LaunchedEffect
        }
        // The heading is the list's first item.
        listState.animateScrollToItem(position + 1)
        onScrolledToCurrent()
    }
    Column(modifier = Modifier.fillMaxSize().background(tint)) {
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = LocalContentBottom.current),
                modifier = Modifier.fillMaxSize(),
            ) {
                fullQueue(
                    state = state,
                    dispatch = dispatch,
                    displayed = displayed,
                    currentIndex = currentIndex,
                    sort = sort,
                    onSort = { sorting = true },
                    onMenu = { menuFor = it },
                    reorderable = reorderable,
                    drag = drag,
                    order = order,
                )
            }
            ScrollBar(
                state = listState,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.fillMaxWidth()
                    .topSeparator()
                    .padding(horizontal = Margin, vertical = 4.dp),
        ) {
            SearchBox(
                query = query,
                hint = stringResource(R.string.queue_search_hint),
                onQuery = { query = it },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The queue heading and rows, shared by the queue page. */
private fun LazyListScope.fullQueue(
    state: DmtState,
    dispatch: (DmtAction) -> Unit,
    displayed: List<QueueEntry>,
    currentIndex: Int?,
    sort: QueueSort,
    onSort: () -> Unit,
    onMenu: (QueueEntry) -> Unit,
    reorderable: Boolean,
    drag: DragState,
    order: MutableList<QueueEntry>,
) {
    item(key = "queue-head") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = Margin, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.nav_queue),
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${displayed.size}",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
            TextLink(
                label = stringResource(R.string.sort_by, stringResource(sort.label)),
                onClick = onSort,
            )
        }
    }
    items(displayed, key = { it.index }) { entry ->
        val dragging = drag.key == entry.index
        Box(
            modifier =
                Modifier.then(if (dragging) Modifier.zIndex(1f) else Modifier.animateItem())
                    .graphicsLayer { translationY = if (dragging) drag.offset else 0f },
        ) {
            FullQueueRow(
                entry = entry,
                track = state.library.track(entry.mediaId),
                current = entry.index == currentIndex,
                raised = dragging,
                onClick = { dispatch(DmtAction.Jump(entry.index)) },
                onLongClick = { onMenu(entry) },
                handle =
                    if (reorderable) {
                        {
                            DragHandle(
                                Modifier.reorderDrag(entry.index, drag, order) { from, to ->
                                    dispatch(DmtAction.MoveInQueue(from, to))
                                },
                            )
                        }
                    } else {
                        null
                    },
            )
        }
    }
}

@Composable
private fun QueueSortSheet(
    selected: QueueSort,
    onPick: (QueueSort) -> Unit,
    onDismiss: () -> Unit,
) {
    DmtSheet(onDismiss = onDismiss) {
        SheetHeader(title = stringResource(R.string.queue_sort_sheet_title))
        Column(modifier = Modifier.selectableGroup()) {
            QueueSort.entries.forEach { option ->
                ChoiceRow(
                    label = stringResource(option.label),
                    selected = option == selected,
                    onClick = {
                        onPick(option)
                        dismiss()
                    },
                )
            }
        }
    }
}

private fun queueComparator(
    sort: QueueSort,
    library: LibrarySnapshot,
): Comparator<QueueEntry> =
    when (sort) {
        QueueSort.QUEUE -> compareBy { it.index }
        QueueSort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        QueueSort.ARTIST -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist }
        QueueSort.ALBUM ->
            compareBy(String.CASE_INSENSITIVE_ORDER) { library.track(it.mediaId)?.album.orEmpty() }
        QueueSort.DATE_ADDED -> compareBy { library.track(it.mediaId)?.dateAdded ?: 0L }
        QueueSort.DATE_MODIFIED -> compareBy { library.track(it.mediaId)?.dateModified ?: 0L }
    }

@Composable
private fun QueueItemSheet(
    entry: QueueEntry,
    track: Track?,
    dispatch: (DmtAction) -> Unit,
    onDismiss: () -> Unit,
) {
    DmtSheet(onDismiss = onDismiss) {
        SheetHeader(title = entry.title, meta = entry.artist.takeIf { it.isNotBlank() })
        SheetAction(
            DmtIcons.Play,
            stringResource(R.string.action_play),
            onClick = {
                dispatch(DmtAction.Jump(entry.index))
                dismiss()
            },
        )
        if (track != null) {
            SheetAction(
                DmtIcons.PlayNext,
                stringResource(R.string.action_play_next),
                onClick = {
                    dispatch(DmtAction.PlayNext(listOf(track), track.title))
                    dismiss()
                },
            )
        }
        SheetAction(
            DmtIcons.Trash,
            stringResource(R.string.queue_remove),
            onClick = {
                dispatch(DmtAction.RemoveAt(entry.index))
                dismiss()
            },
        )
    }
}

@Composable
private fun FullQueueRow(
    entry: QueueEntry,
    track: Track?,
    current: Boolean,
    raised: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    handle: (@Composable () -> Unit)?,
) {
    val mark = LocalAccent.current.highlight
    val playing = rememberWantsPlayback()
    val endPadding = if (handle != null) 24.dp else Margin
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.fillMaxWidth()
                .background(if (raised) Surface2 else Color.Transparent)
                .drawBehind {
                    if (current) drawRect(mark, size = Size(4.dp.toPx(), size.height))
                }
                .pressable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    sink = 0.985f,
                    label = entry.title,
                )
                .padding(start = Margin, top = 8.dp, bottom = 8.dp, end = endPadding),
    ) {
        Artwork(track = track, modifier = Modifier.size(RowArt))
        TrackText(entry.title, entry.artist, modifier = Modifier.weight(1f))
        if (current) {
            PlayingBars(
                playing = playing,
                color = mark,
                modifier = Modifier.size(18.dp),
            )
        }
        handle?.invoke()
    }
}

@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(48.dp)) {
        DmtIcon(
            icon = DmtIcons.DragHandle,
            tint = Muted,
            description = stringResource(R.string.queue_reorder),
        )
    }
}

/** Drags row [key] through [order], then reports its player index and new place to [onMove]. */
private fun Modifier.reorderDrag(
    key: Int,
    drag: DragState,
    order: MutableList<QueueEntry>,
    onMove: (from: Int, to: Int) -> Unit,
): Modifier = pointerInput(key) {
    detectDragGestures(
        onDragStart = { drag.start(key) },
        onDrag = { change, amount ->
            change.consume()
            drag.by(amount.y, order)
        },
        onDragEnd = { drag.finish(order)?.let { (from, to) -> onMove(from, to) } },
        onDragCancel = { drag.cancel() },
    )
}

/** The row being dragged, how far it has moved, and where it started. */
private class DragState(private val listState: LazyListState) {
    var key by mutableStateOf<Int?>(null)
        private set

    var offset by mutableFloatStateOf(0f)
        private set

    private var startPosition = -1

    fun start(key: Int) {
        this.key = key
        offset = 0f
        startPosition =
            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.index ?: -1
    }

    /** Moves the dragged row by [dy], swapping it with the row its centre passes over. */
    fun by(dy: Float, order: MutableList<QueueEntry>) {
        val dragged = key ?: return
        offset += dy
        val items = listState.layoutInfo.visibleItemsInfo
        val info = items.firstOrNull { it.key == dragged } ?: return
        val centre = info.offset + info.size / 2f + offset
        val target =
            items.firstOrNull {
                it.key != dragged && centre.toInt() in it.offset until it.offset + it.size
            } ?: return
        val from = order.indexOfFirst { it.index == dragged }
        val to = order.indexOfFirst { it.index == target.key }
        if (from < 0 || to < 0) return
        order.add(to, order.removeAt(from))
        // The row now sits where the target was; keep it under the finger.
        offset -= target.offset - info.offset
    }

    /** Ends the drag, returning the row's player index and new place if it moved. */
    fun finish(order: List<QueueEntry>): Pair<Int, Int>? {
        val dragged = key ?: return null
        val to = order.indexOfFirst { it.index == dragged }
        val moved = to >= 0 && to != startPosition
        cancel()
        return if (moved) dragged to to else null
    }

    fun cancel() {
        key = null
        offset = 0f
        startPosition = -1
    }
}

@Composable
private fun TrackText(title: String, artist: String, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.padding(start = 14.dp, end = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (artist.isNotBlank()) {
            Text(
                text = artist,
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
