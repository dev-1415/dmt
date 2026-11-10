package dev.jyotiraditya.dmt.presentation.library

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.domain.model.Folder
import dev.jyotiraditya.dmt.domain.model.LibrarySort
import dev.jyotiraditya.dmt.domain.model.Track
import dev.jyotiraditya.dmt.presentation.main.LocalContentBottom
import dev.jyotiraditya.dmt.presentation.nowplaying.rememberWantsPlayback
import dev.jyotiraditya.dmt.presentation.player.DmtAction
import dev.jyotiraditya.dmt.presentation.player.DmtState
import dev.jyotiraditya.dmt.presentation.player.DmtView
import dev.jyotiraditya.dmt.presentation.settings.noTracksMessage
import dev.jyotiraditya.dmt.ui.components.ChoiceRow
import dev.jyotiraditya.dmt.ui.components.CollectionRow
import dev.jyotiraditya.dmt.ui.components.CoverTile
import dev.jyotiraditya.dmt.ui.components.DmtSheet
import dev.jyotiraditya.dmt.ui.components.EmptyState
import dev.jyotiraditya.dmt.ui.components.FolderRow
import dev.jyotiraditya.dmt.ui.components.RowPlaceholders
import dev.jyotiraditya.dmt.ui.components.ScrollBar
import dev.jyotiraditya.dmt.ui.components.SearchBox
import dev.jyotiraditya.dmt.ui.components.SheetHeader
import dev.jyotiraditya.dmt.ui.components.TextLink
import dev.jyotiraditya.dmt.ui.components.TrackRow
import dev.jyotiraditya.dmt.ui.components.topSeparator
import dev.jyotiraditya.dmt.ui.motion.sharedElement
import dev.jyotiraditya.dmt.ui.theme.Ground
import dev.jyotiraditya.dmt.ui.theme.Margin
import dev.jyotiraditya.dmt.ui.theme.Muted

private val SearchTileWidth = 140.dp

/** The search field pinned above the library. */
@Composable
internal fun LibraryHead(
    state: DmtState,
    dispatch: (DmtAction) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(tint ?: Ground)
                .topSeparator()
                .padding(vertical = 8.dp),
    ) {
        SearchBox(
            query = state.query,
            hint = stringResource(R.string.search_hint),
            onQuery = { dispatch(DmtAction.Query(it)) },
            modifier = Modifier.padding(horizontal = Margin),
        )
    }
}

@Composable
internal fun Shelf(view: DmtView, state: DmtState, dispatch: (DmtAction) -> Unit) {
    val bottom = PaddingValues(bottom = LocalContentBottom.current)
    val navigation = libraryNavigation(dispatch)
    val openTrackMenu = rememberTrackMenu(state, dispatch, navigation)
    val playing = rememberWantsPlayback()
    var actions by remember { mutableStateOf<Pair<String, List<Track>>?>(null) }
    actions?.let { (title, tracks) ->
        ActionSheet(
            title = title,
            tracks = tracks,
            dispatch = dispatch,
            onDismiss = { actions = null },
        )
    }
    val listState = rememberLazyListState()

    if (state.query.isNotBlank()) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = listState, contentPadding = bottom) {
                results(state, view, playing, dispatch, navigation, openTrackMenu)
            }
            if (view == DmtView.FOLDERS) {
                ScrollBar(
                    state = listState,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
        return
    }

    if (view == DmtView.ALBUMS && state.albums.isNotEmpty()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding =
                PaddingValues(
                    start = Margin,
                    end = Margin,
                    top = 8.dp,
                    bottom = LocalContentBottom.current,
                ),
        ) {
            items(state.filteredAlbums, key = { it.id }) { album ->
                CoverTile(
                    cover = album.tracks.firstOrNull(),
                    title = album.name,
                    subtitle = album.artist,
                    titleLines = 2,
                    onClick = { dispatch(DmtAction.OpenAlbum(album.id)) },
                    onLongClick = { actions = album.name to album.tracks },
                    coverModifier = Modifier.sharedElement(albumCoverKey(album.id)),
                )
            }
        }
        return
    }

    val root = state.library.folderRoots.firstOrNull()
    val rootTracks =
        remember(root, state.settings.folderSort, state.settings.folderSortAscending) {
            root?.let {
                state.settings.folderSort.sorted(it.tracks, state.settings.folderSortAscending)
            }.orEmpty()
        }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, contentPadding = bottom) {
            when (view) {
                DmtView.ARTISTS ->
                    items(state.filteredArtists, key = { it.name }) { artist ->
                        CollectionRow(
                            title = artist.name,
                            detail = artistSummary(artist),
                            onClick = { dispatch(DmtAction.OpenArtist(artist.name)) },
                            onLongClick = { actions = artist.name to artist.tracks },
                            modifier = Modifier.animateItem(),
                        )
                    }

                DmtView.GENRES ->
                    items(state.filteredGenres, key = { it.name }) { genre ->
                        CollectionRow(
                            title = genre.name,
                            detail = tracksSummary(genre.tracks),
                            onClick = { dispatch(DmtAction.OpenGenre(genre.name)) },
                            onLongClick = { actions = genre.name to genre.tracks },
                            modifier = Modifier.animateItem(),
                        )
                    }

                DmtView.FOLDERS ->
                    folderShelf(
                        state = state,
                        root = root,
                        rootTracks = rootTracks,
                        dispatch = dispatch,
                        openTrackMenu = openTrackMenu,
                        playing = playing,
                        onLongPress = { title, tracks ->
                            actions =
                                title to
                                    state.settings.folderSort.sorted(
                                        tracks,
                                        state.settings.folderSortAscending,
                                    )
                        },
                    )

                DmtView.PLAYLISTS -> playlists(state, dispatch)

                else -> tracks(state, playing, dispatch, openTrackMenu)
            }
        }
        if (view == DmtView.FOLDERS) {
            ScrollBar(
                state = listState,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

/** The Folders shelf: one storage root's contents, or the roots when there are several. */
private fun LazyListScope.folderShelf(
    state: DmtState,
    root: Folder?,
    rootTracks: List<Track>,
    dispatch: (DmtAction) -> Unit,
    openTrackMenu: (Track) -> Unit,
    playing: Boolean,
    onLongPress: (String, List<Track>) -> Unit,
) {
    val roots = state.library.folderRoots
    when {
        roots.isEmpty() ->
            item(key = "folders-empty") {
                EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    body = stringResource(state.settings.sourceMode.noTracksMessage),
                    modifier = Modifier.padding(horizontal = Margin),
                )
            }

        roots.size == 1 && root != null ->
            folderContents(
                folder = root,
                tracks = rootTracks,
                state = state,
                dispatch = dispatch,
                openTrackMenu = openTrackMenu,
                playing = playing,
                onLongPress = onLongPress,
            )

        else ->
            items(roots, key = { "root:${it.path}" }) { folder ->
                val title = folderTitle(folder)
                FolderRow(
                    folder = folder,
                    title = title,
                    detail = folderSummary(folder),
                    onClick = { dispatch(DmtAction.OpenFolder(folder.path)) },
                    onLongClick = { onLongPress(title, folder.allTracks) },
                    modifier = Modifier.animateItem(),
                )
            }
    }
}

private fun LazyListScope.tracks(
    state: DmtState,
    playing: Boolean,
    dispatch: (DmtAction) -> Unit,
    openTrackMenu: (Track) -> Unit,
) {
    if (state.scanning && state.tracks.isEmpty()) {
        item {
            RowPlaceholders(text = stringResource(R.string.library_scanning))
        }
        return
    }
    if (state.tracks.isEmpty()) {
        item {
            EmptyState(
                title = stringResource(R.string.home_empty_title),
                body = stringResource(state.settings.sourceMode.noTracksMessage),
                modifier = Modifier.padding(horizontal = Margin),
            )
        }
        return
    }
    item(key = "summary") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Margin),
        ) {
            Text(
                text = tracksSummary(state.tracks),
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.weight(1f),
            )
            var sorting by remember { mutableStateOf(false) }
            TextLink(
                label =
                    stringResource(
                        R.string.sort_by,
                        stringResource(state.settings.librarySort.labelRes()),
                    ),
                onClick = { sorting = true },
            )
            if (sorting) {
                SortSheet(
                    selected = state.settings.librarySort,
                    options = LibrarySort.options(state.settings.sourceMode),
                    onPick = { dispatch(DmtAction.Config(state.settings.copy(librarySort = it))) },
                    onDismiss = { sorting = false },
                )
            }
        }
    }
    itemsIndexed(state.filtered, key = { _, track -> track.id }) { index, track ->
        TrackRow(
            track = track,
            meta = trackSummary(track),
            current = track.id.toString() == state.nowPlayingId,
            onClick = { dispatch(DmtAction.PlayAt(state.filtered, index)) },
            onLongClick = { openTrackMenu(track) },
            modifier = Modifier.animateItem(),
        )
    }
}

/** Search results: matching artists, albums, then tracks. */
private fun LazyListScope.results(
    state: DmtState,
    view: DmtView,
    playing: Boolean,
    dispatch: (DmtAction) -> Unit,
    navigation: TrackMenuNavigation,
    openTrackMenu: (Track) -> Unit,
) {
    val artists = state.filteredArtists.take(4)
    val albums = state.filteredAlbums.take(10)
    if (artists.isEmpty() && albums.isEmpty() && state.filtered.isEmpty()) {
        item {
            EmptyState(
                title = stringResource(R.string.search_no_match_title),
                body = stringResource(R.string.search_no_match_body, state.query),
                modifier = Modifier.padding(horizontal = Margin),
            )
        }
        return
    }
    items(artists, key = { "r:${it.name}" }) { artist ->
        CollectionRow(
            title = artist.name,
            detail = artistSummary(artist),
            onClick = { navigation.openArtist(artist.name) },
        )
    }
    if (albums.isNotEmpty()) {
        item(key = "albums") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = Margin, vertical = 16.dp),
            ) {
                items(albums, key = { "a:${it.id}" }) { album ->
                    CoverTile(
                        cover = album.tracks.firstOrNull(),
                        title = album.name,
                        onClick = { navigation.openAlbum(album.id) },
                        modifier = Modifier.width(SearchTileWidth),
                        coverModifier = Modifier.sharedElement(albumCoverKey(album.id)),
                    )
                }
            }
        }
    }
    itemsIndexed(state.filtered, key = { _, track -> track.id }) { index, track ->
        TrackRow(
            track = track,
            meta = trackSummary(track),
            current = track.id.toString() == state.nowPlayingId,
            onClick = {
                if (view == DmtView.FOLDERS) {
                    dispatch(DmtAction.OpenFolder(track.path.substringBeforeLast('/')))
                    dispatch(DmtAction.ScrollToTrack(track.id.toString()))
                    dispatch(DmtAction.Query(""))
                } else {
                    dispatch(DmtAction.PlayAt(state.filtered, index))
                }
            },
            onLongClick = { openTrackMenu(track) },
        )
    }
}

@StringRes
private fun LibrarySort.labelRes(): Int = when (this) {
    LibrarySort.TITLE -> R.string.sort_title
    LibrarySort.ARTIST -> R.string.sort_artist
    LibrarySort.RECENT_ADDED -> R.string.sort_date_added
    LibrarySort.RECENT_MODIFIED -> R.string.sort_date_modified
    LibrarySort.RECENT -> R.string.sort_recent
}

/** Picks the order tracks are listed in, closing once picked. */
@Composable
private fun SortSheet(
    selected: LibrarySort,
    options: List<LibrarySort>,
    onPick: (LibrarySort) -> Unit,
    onDismiss: () -> Unit,
) {
    DmtSheet(onDismiss = onDismiss) {
        SheetHeader(title = stringResource(R.string.sort_sheet_title))
        Column(modifier = Modifier.selectableGroup()) {
            options.forEach { option ->
                ChoiceRow(
                    label = stringResource(option.labelRes()),
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
