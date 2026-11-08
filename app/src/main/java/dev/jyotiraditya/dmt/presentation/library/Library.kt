package dev.jyotiraditya.dmt.presentation.library

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.domain.model.Album
import dev.jyotiraditya.dmt.presentation.player.DmtAction
import dev.jyotiraditya.dmt.presentation.player.DmtState
import dev.jyotiraditya.dmt.presentation.player.DmtView
import dev.jyotiraditya.dmt.ui.components.ScrollMemory
import dev.jyotiraditya.dmt.ui.motion.Depth

/** The library: a shelf, or the collection opened from it, sliding by depth between them. */
@Composable
fun LibraryPage(
    state: DmtState,
    dispatch: (DmtAction) -> Unit,
    view: DmtView,
    tint: Color? = null,
) {
    LaunchedEffect(
        state.settingsLoaded,
        view,
        state.folders.isNotEmpty(),
        state.settings.startFolder,
    ) {
        val folder = state.openFolder?.let(state.library::folder)
        if (
            state.settingsLoaded &&
                view == DmtView.FOLDERS &&
                state.folders.isNotEmpty() &&
                folder == null
        ) {
            state.settings.startFolder?.let { dispatch(DmtAction.OpenFolder(it)) }
        }
    }
    Depth(
        target = state.libraryScreen(view),
        keyOf = { it.key },
        depthOf = { it.depth },
    ) { screen ->
        LibraryContent(screen, state, dispatch, tint)
    }
}

@Composable
private fun LibraryContent(
    screen: LibraryScreen,
    state: DmtState,
    dispatch: (DmtAction) -> Unit,
    tint: Color?,
) {
    when (screen) {
        is LibraryScreen.OfArtistAlbum -> AlbumDetail(screen.album, state, dispatch)

        is LibraryScreen.OfArtist -> ArtistDetail(screen.artist, state, dispatch)

        is LibraryScreen.OfAlbum -> AlbumDetail(screen.album, state, dispatch)

        is LibraryScreen.OfGenre ->
            with(screen.genre) {
                TracksDetail(
                    name,
                    stringResource(R.string.collection_genre),
                    tracks,
                    numbered = false,
                    state,
                    dispatch,
                )
            }

        is LibraryScreen.OfFolder -> FolderDetail(screen.folder, state, dispatch, tint)

        is LibraryScreen.OfPlaylist -> PlaylistDetail(screen.playlist, state, dispatch)

        is LibraryScreen.Shelf ->
            Column {
                LibraryHead(
                    state = state,
                    dispatch = dispatch,
                    tint = if (screen.view == DmtView.FOLDERS) tint else null,
                )
                ScrollMemory("shelf:${screen.view.name}:${screen.searching}") {
                    Shelf(
                        screen.view,
                        state,
                        dispatch,
                    )
                }
            }
    }
}

@Composable
private fun AlbumDetail(album: Album, state: DmtState, dispatch: (DmtAction) -> Unit) {
    TracksDetail(
        album.name,
        album.artist,
        album.tracks,
        numbered = true,
        state,
        dispatch,
        coverKey = albumCoverKey(album.id),
    )
}
