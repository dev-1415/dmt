package dev.jyotiraditya.dmt.presentation.nowplaying

import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberShuffleButtonState
import dev.jyotiraditya.dmt.R
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
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
import dev.jyotiraditya.dmt.ui.components.ScrollBar
import dev.jyotiraditya.dmt.ui.components.SearchBox
import dev.jyotiraditya.dmt.ui.components.SheetAction
import dev.jyotiraditya.dmt.ui.components.SheetHeader
import dev.jyotiraditya.dmt.ui.components.TextLink
import dev.jyotiraditya.dmt.ui.components.bottomSeparator
import dev.jyotiraditya.dmt.ui.components.pressable
import dev.jyotiraditya.dmt.ui.components.topSeparator
import dev.jyotiraditya.dmt.ui.icons.DmtIcons
import dev.jyotiraditya.dmt.ui.theme.Ink
import dev.jyotiraditya.dmt.ui.theme.LocalCoverColors
import dev.jyotiraditya.dmt.ui.theme.Margin
import dev.jyotiraditya.dmt.ui.theme.Muted
import dev.jyotiraditya.dmt.ui.theme.Surface2
import dev.jyotiraditya.dmt.util.QueueEntry
import dev.jyotiraditya.dmt.util.queueEntries

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
    val currentEntry = state.queue.getOrNull(state.queuePosition)
    val currentKey = currentEntry?.key
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
    val reorderable = !shuffled && sort == QueueSort.QUEUE && query.isBlank()
    val order = remember { filtered.toMutableStateList() }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var justDropped by remember { mutableStateOf(false) }
    val reorderState =
        rememberReorderableLazyListState(listState) { from, to ->
            if (from.index in order.indices && to.index in order.indices) {
                order.add(to.index, order.removeAt(from.index))
            }
        }
    LaunchedEffect(filtered) {
        if (!reorderState.isAnyItemDragging && !justDropped) {
            syncOrder(order, filtered)
        }
    }
    LaunchedEffect(reorderState.isAnyItemDragging) {
        if (!reorderState.isAnyItemDragging) {
            // Let the player queue catch up with the last drop before syncing, so the local
            // order is never replaced by a stale queue snapshot.
            var waited = 0
            while (justDropped && waited < 1000) {
                val entries = player?.queueEntries()
                if (
                    entries != null &&
                    entries.size == order.size &&
                    entries.indices.all { entries[it].key == order[it].key }
                ) {
                    break
                }
                delay(50)
                waited += 50
            }
            justDropped = false
            syncOrder(order, filtered)
        }
    }
    val displayed = if (reorderable) order else filtered
    LaunchedEffect(scrollToCurrent, displayed) {
        if (!scrollToCurrent) return@LaunchedEffect
        val position = displayed.indexOfFirst { it.key == currentKey }
        if (position < 0) {
            if (query.isNotEmpty()) {
                query = ""
            } else {
                onScrolledToCurrent()
            }
            return@LaunchedEffect
        }
        listState.animateScrollToItem(position)
        onScrolledToCurrent()
    }
    Column(modifier = Modifier.fillMaxSize().background(tint)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier.fillMaxWidth()
                    .bottomSeparator()
                    .padding(horizontal = Margin, vertical = 4.dp),
        ) {
            Text(
                text = "${displayed.size}",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
            Spacer(modifier = Modifier.weight(1f))
            TextLink(
                label = stringResource(R.string.queue_save_playlist),
                onClick = { dispatch(DmtAction.SaveQueue) },
                compact = true,
            )
            TextLink(
                label = stringResource(R.string.sort_by, stringResource(sort.label)),
                onClick = { sorting = true },
                compact = true,
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = LocalContentBottom.current),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(displayed, key = { it.key }) { entry ->
                    ReorderableItem(reorderState, key = entry.key) { isDragging ->
                        FullQueueRow(
                            entry = entry,
                            track = state.library.track(entry.mediaId),
                            current = entry.key == currentKey,
                            raised = isDragging,
                            onClick = { dispatch(DmtAction.Jump(entry.index)) },
                            onLongClick = { menuFor = entry },
                            handle =
                                if (reorderable) {
                                    {
                                        DragHandle(
                                            modifier =
                                                Modifier.draggableHandle(
                                                    onDragStarted = { dragKey = entry.key },
                                                    onDragStopped = {
                                                        justDropped = true
                                                        val key = dragKey
                                                        if (key != null && player != null) {
                                                            // Resolve the move against the player's
                                                            // current queue, not indices captured
                                                            // while the list was scrolling.
                                                            val entries = player.queueEntries()
                                                            val from =
                                                                entries
                                                                    .firstOrNull { it.key == key }
                                                                    ?.index
                                                            val finalPos =
                                                                order.indexOfFirst { it.key == key }
                                                            val to = entries.getOrNull(finalPos)?.index
                                                            if (
                                                                from != null &&
                                                                to != null &&
                                                                from != to
                                                            ) {
                                                                dispatch(
                                                                    DmtAction.MoveInQueue(from, to),
                                                                )
                                                            }
                                                        }
                                                        dragKey = null
                                                    },
                                                ),
                                        )
                                    }
                                } else {
                                    null
                                },
                        )
                    }
                }
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
                height = 40.dp,
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
    val mark = LocalCoverColors.current.primary
    val endPadding = if (handle != null) 24.dp else Margin
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    when {
                        current -> mark.copy(alpha = 0.14f)
                        raised -> Surface2
                        else -> Color.Transparent
                    }
                )
                .padding(start = Margin, top = 8.dp, bottom = 8.dp, end = endPadding),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.weight(1f)
                    .pressable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                        sink = 0.985f,
                        label = entry.title,
                    ),
        ) {
            Artwork(track = track, modifier = Modifier.size(RowArt))
            TrackText(entry.title, entry.artist, modifier = Modifier.weight(1f))
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

/** Updates [order] to [source] without replacing items whose stable key is unchanged. */
private fun syncOrder(order: MutableList<QueueEntry>, source: List<QueueEntry>) {
    if (order.size == source.size && order.indices.all { order[it].key == source[it].key }) {
        for (i in order.indices) order[i] = source[i]
    } else {
        order.clear()
        order.addAll(source)
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
