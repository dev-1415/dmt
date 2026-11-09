package dev.jyotiraditya.dmt.presentation.library

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.domain.model.DmtSettings
import dev.jyotiraditya.dmt.domain.model.Folder
import dev.jyotiraditya.dmt.domain.model.FolderSort
import dev.jyotiraditya.dmt.domain.model.Track
import dev.jyotiraditya.dmt.presentation.main.LocalContentBottom
import dev.jyotiraditya.dmt.presentation.nowplaying.rememberWantsPlayback
import dev.jyotiraditya.dmt.presentation.player.DmtAction
import dev.jyotiraditya.dmt.presentation.player.DmtState
import dev.jyotiraditya.dmt.presentation.settings.noTracksMessage
import dev.jyotiraditya.dmt.ui.components.ChoiceRow
import dev.jyotiraditya.dmt.ui.components.CollectionRow
import dev.jyotiraditya.dmt.ui.components.DmtSheet
import dev.jyotiraditya.dmt.ui.components.EmptyState
import dev.jyotiraditya.dmt.ui.components.FolderCover
import dev.jyotiraditya.dmt.ui.components.FolderRow
import dev.jyotiraditya.dmt.ui.components.IconTap
import dev.jyotiraditya.dmt.ui.components.ScrollBar
import dev.jyotiraditya.dmt.ui.components.SheetAction
import dev.jyotiraditya.dmt.ui.components.SheetHeader
import dev.jyotiraditya.dmt.ui.components.TextLink
import dev.jyotiraditya.dmt.ui.components.TrackRow
import dev.jyotiraditya.dmt.ui.components.animateFieldAsState
import dev.jyotiraditya.dmt.ui.components.background
import dev.jyotiraditya.dmt.ui.components.rememberFieldColor
import dev.jyotiraditya.dmt.ui.icons.DmtIcons
import dev.jyotiraditya.dmt.ui.theme.Ground
import dev.jyotiraditya.dmt.ui.theme.Margin
import dev.jyotiraditya.dmt.ui.theme.Muted
import dev.jyotiraditya.dmt.ui.theme.darkenedForPlayer
import dev.jyotiraditya.dmt.ui.theme.inkOn

/** A folder's subfolders and direct tracks, with the folder's sort controls. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FolderDetail(
    folder: Folder,
    state: DmtState,
    dispatch: (DmtAction) -> Unit,
    tint: Color? = null,
) {
    val settings = state.settings
    val sortedTracks =
        remember(folder, settings.folderSort, settings.folderSortAscending) {
            settings.folderSort.sorted(folder.tracks, settings.folderSortAscending)
        }
    val sortedAllTracks =
        remember(folder, settings.folderSort, settings.folderSortAscending) {
            settings.folderSort.sorted(folder.allTracks, settings.folderSortAscending)
        }
    val openTrackMenu = rememberTrackMenu(state, dispatch, libraryNavigation(dispatch))
    val playing = rememberWantsPlayback()
    val listState = rememberLazyListState()
    val stickyOffset = with(LocalDensity.current) { 120.dp.roundToPx() }
    var actions by remember { mutableStateOf<Pair<String, List<Track>>?>(null) }
    actions?.let { (title, tracks) ->
        ActionSheet(
            title = title,
            tracks = tracks,
            dispatch = dispatch,
            onDismiss = { actions = null },
        )
    }

    LaunchedEffect(state.scrollToTrackId, folder.path, sortedTracks) {
        val target = state.scrollToTrackId ?: return@LaunchedEffect
        val trackIndex = sortedTracks.indexOfFirst { it.id.toString() == target }
        if (trackIndex < 0) {
            dispatch(DmtAction.ScrollToTrack(null))
            return@LaunchedEffect
        }
        val parentPath = folder.path.substringBeforeLast('/', missingDelimiterValue = "")
        val parent = parentPath.takeIf { it.isNotEmpty() }?.let(state.library::folder)
        val base = 3 // sticky header, cover, spacer
        val up = if (parent != null) 1 else 0
        val children = folder.children.size
        val sort = if (sortedTracks.isNotEmpty()) 1 else 0
        listState.scrollToItem(
            index = base + up + children + sort + trackIndex,
            scrollOffset = -stickyOffset,
        )
        dispatch(DmtAction.ScrollToTrack(null))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = LocalContentBottom.current),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "cover:${folder.path}") {
                FolderCover(
                    folder = folder,
                    columns = 4,
                    rows = 3,
                    background = tint ?: Ground,
                    modifier =
                        Modifier.padding(horizontal = Margin, vertical = 12.dp)
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f),
                )
            }
            stickyHeader {
                FolderHeader(
                    folder = folder,
                    tracks = sortedAllTracks,
                    tint = tint,
                    dispatch = dispatch,
                )
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            folderContents(
                folder = folder,
                tracks = sortedTracks,
                state = state,
                dispatch = dispatch,
                openTrackMenu = openTrackMenu,
                playing = playing,
                onLongPress = { title, tracks ->
                    actions =
                        title to settings.folderSort.sorted(tracks, settings.folderSortAscending)
                },
            )
        }
        ScrollBar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

/** The folder's name and details, pinned above its play, shuffle, queue and search row. */
@Composable
private fun FolderHeader(
    folder: Folder,
    tracks: List<Track>,
    tint: Color?,
    dispatch: (DmtAction) -> Unit,
) {
    val seed = tracks.firstOrNull()
    val ownField = rememberFieldColor(seed)
    val field = tint ?: ownField.darkenedForPlayer()
    val paint by animateFieldAsState(field)
    val ink = field.inkOn()
    Column(modifier = Modifier.fillMaxWidth().background { paint }) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Margin, vertical = 8.dp)) {
            Text(
                text = folderTitle(folder),
                style = MaterialTheme.typography.headlineSmall,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = folderSummary(folder),
                style = MaterialTheme.typography.bodySmall,
                color = ink.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DetailActions(
            title = folderTitle(folder),
            tracks = tracks,
            dispatch = dispatch,
            tint = field,
            action = { actionInk ->
                IconTap(
                    icon = DmtIcons.Search,
                    description = stringResource(R.string.search_hint),
                    onClick = {
                        dispatch(DmtAction.OpenFolder(null))
                        dispatch(DmtAction.Query(""))
                    },
                    ink = actionInk,
                )
            },
        )
    }
}

/** The subfolders and tracks of [folder], with a sort row above the tracks. */
internal fun LazyListScope.folderContents(
    folder: Folder,
    tracks: List<Track>,
    state: DmtState,
    dispatch: (DmtAction) -> Unit,
    openTrackMenu: (Track) -> Unit,
    playing: Boolean,
    onLongPress: (String, List<Track>) -> Unit,
) {
    val parentPath = folder.path.substringBeforeLast('/', missingDelimiterValue = "")
    val parent = parentPath.takeIf { it.isNotEmpty() }?.let(state.library::folder)
    val roots = state.library.folderRoots
    val parentIsRoot = parent != null && roots.any { it.path == parent.path }
    if (parent != null) {
        item(key = "up:${folder.path}") {
            CollectionRow(
                title = "...",
                detail = folderTitle(parent),
                onClick = {
                    dispatch(
                        DmtAction.OpenFolder(
                            if (parentIsRoot && roots.size == 1) null else parent.path,
                        ),
                    )
                },
                modifier = Modifier.animateItem(),
            )
        }
    }
    if (folder.children.isNotEmpty()) {
        items(folder.children, key = { "folder:${it.path}" }) { child ->
            val title = folderTitle(child)
            FolderRow(
                folder = child,
                title = title,
                detail = folderSummary(child),
                onClick = { dispatch(DmtAction.OpenFolder(child.path)) },
                onLongClick = { onLongPress(title, child.allTracks) },
                modifier = Modifier.animateItem(),
            )
        }
    }
    if (tracks.isNotEmpty()) {
        item(key = "sort:${folder.path}") {
            FolderSortRow(tracks = tracks, settings = state.settings, dispatch = dispatch)
        }
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            TrackRow(
                track = track,
                meta = trackSummary(track),
                current = track.id.toString() == state.nowPlayingId,
                playing = playing,
                onClick = { dispatch(DmtAction.PlayAt(tracks, index)) },
                onLongClick = { openTrackMenu(track) },
                modifier = Modifier.animateItem(),
            )
        }
    }
    if (folder.children.isEmpty() && tracks.isEmpty()) {
        item(key = "empty:${folder.path}") {
            EmptyState(
                title = stringResource(R.string.home_empty_title),
                body = stringResource(state.settings.sourceMode.noTracksMessage),
                modifier = Modifier.padding(horizontal = Margin),
            )
        }
    }
}

@Composable
private fun FolderSortRow(
    tracks: List<Track>,
    settings: DmtSettings,
    dispatch: (DmtAction) -> Unit,
) {
    var sorting by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = Margin),
    ) {
        Text(
            text = tracksSummary(tracks),
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            modifier = Modifier.weight(1f),
        )
        TextLink(
            label = stringResource(R.string.sort_by, stringResource(settings.folderSort.labelRes())),
            onClick = { sorting = true },
        )
    }
    if (sorting) {
        FolderSortSheet(
            selected = settings.folderSort,
            ascending = settings.folderSortAscending,
            onPick = { sort, ascending ->
                dispatch(
                    DmtAction.Config(
                        settings.copy(folderSort = sort, folderSortAscending = ascending),
                    ),
                )
            },
            onDismiss = { sorting = false },
        )
    }
}

@Composable
private fun FolderSortSheet(
    selected: FolderSort,
    ascending: Boolean,
    onPick: (FolderSort, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    DmtSheet(onDismiss = onDismiss) {
        SheetHeader(title = stringResource(R.string.sort_sheet_title))
        Column(
            modifier =
                Modifier.heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.selectableGroup()) {
                FolderSort.entries.forEach { option ->
                    ChoiceRow(
                        label = stringResource(option.labelRes()),
                        selected = option == selected,
                        onClick = { onPick(option, ascending) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.sort_order),
                style = MaterialTheme.typography.titleMedium,
                color = Muted,
                modifier = Modifier.padding(horizontal = Margin, vertical = 12.dp),
            )
            Column(modifier = Modifier.selectableGroup()) {
                ChoiceRow(
                    label = stringResource(R.string.sort_ascending),
                    selected = ascending,
                    onClick = { onPick(selected, true) },
                )
                ChoiceRow(
                    label = stringResource(R.string.sort_descending),
                    selected = !ascending,
                    onClick = { onPick(selected, false) },
                )
            }
        }
        SheetAction(
            icon = DmtIcons.Check,
            label = stringResource(R.string.sort_done),
            onClick = { dismiss() },
        )
    }
}

@StringRes
private fun FolderSort.labelRes(): Int =
    when (this) {
        FolderSort.TITLE -> R.string.sort_title
        FolderSort.ARTIST -> R.string.sort_artist
        FolderSort.ALBUM -> R.string.sort_album
        FolderSort.TRACK_NUMBER -> R.string.sort_track_number
        FolderSort.DATE_ADDED -> R.string.sort_date_added
        FolderSort.DATE_MODIFIED -> R.string.sort_date_modified
        FolderSort.DURATION -> R.string.sort_duration
        FolderSort.FILE_NAME -> R.string.sort_file_name
    }
