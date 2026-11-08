package dev.jyotiraditya.dmt.presentation.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.domain.model.Album
import dev.jyotiraditya.dmt.domain.model.Artist
import dev.jyotiraditya.dmt.domain.model.Folder
import dev.jyotiraditya.dmt.domain.model.Genre
import dev.jyotiraditya.dmt.domain.model.Playlist
import dev.jyotiraditya.dmt.presentation.player.DmtAction
import dev.jyotiraditya.dmt.presentation.player.DmtState
import dev.jyotiraditya.dmt.presentation.player.DmtView

internal val SHELVES =
    listOf(
        DmtView.FOLDERS,
        DmtView.LIBRARY,
        DmtView.ALBUMS,
        DmtView.ARTISTS,
        DmtView.PLAYLISTS,
        DmtView.GENRES,
    )

/** The label of the back link inside the library, and the actions it sends. */
data class BackLink(val label: String, val actions: List<DmtAction>)

/** The back link for the open library view, or null at the top level. */
@Composable
fun libraryBack(state: DmtState, view: DmtView): BackLink? {
    val effective =
        when {
            view == DmtView.FOLDERS && state.folders.isEmpty() -> DmtView.LIBRARY
            view == DmtView.PLAYLISTS && state.folders.isEmpty() -> DmtView.LIBRARY
            view == DmtView.GENRES && state.genres.isEmpty() -> DmtView.LIBRARY
            else -> view
        }
    return when {
        effective == DmtView.ARTISTS && state.openArtist != null && state.openAlbum != null ->
            BackLink(state.openArtist, listOf(DmtAction.OpenAlbum(null)))

        effective == DmtView.ARTISTS && state.openArtist != null ->
            BackLink(
                stringResource(R.string.library_tab_artists),
                listOf(DmtAction.OpenArtist(null), DmtAction.OpenAlbum(null)),
            )

        effective == DmtView.ALBUMS && state.openAlbum != null ->
            BackLink(stringResource(R.string.library_tab_albums), listOf(DmtAction.OpenAlbum(null)))

        effective == DmtView.GENRES && state.openGenre != null ->
            BackLink(stringResource(R.string.library_tab_genres), listOf(DmtAction.OpenGenre(null)))

        effective == DmtView.FOLDERS && state.openFolder != null -> folderBack(state)

        effective == DmtView.PLAYLISTS && state.openPlaylist != null ->
            BackLink(
                stringResource(R.string.library_tab_playlists),
                listOf(DmtAction.OpenPlaylist(null)),
            )

        else -> null
    }
}

/** The back link for the open folder: its parent, or the Folders root at the top. */
@Composable
private fun folderBack(state: DmtState): BackLink? {
    val path = state.openFolder ?: return null
    val folder = state.library.folder(path) ?: return null
    val parentPath = folder.path.substringBeforeLast('/', missingDelimiterValue = "")
    val parent = parentPath.takeIf { it.isNotEmpty() }?.let(state.library::folder)
    val roots = state.library.folderRoots
    val parentIsRoot = parent != null && roots.any { it.path == parent.path }
    return when {
        parent == null ->
            BackLink(
                stringResource(R.string.library_tab_folders),
                listOf(DmtAction.OpenFolder(null)),
            )

        parentIsRoot && roots.size == 1 ->
            BackLink(
                stringResource(R.string.library_tab_folders),
                listOf(DmtAction.OpenFolder(null)),
            )

        else ->
            BackLink(
                folderTitle(parent),
                listOf(DmtAction.OpenFolder(parent.path)),
            )
    }
}

/** What the library shows: a shelf or one opened collection. */
internal sealed interface LibraryScreen {
    /** The screen's identity; the transition runs only when it changes. */
    val key: String

    /** How far in the screen sits, which sets the direction of the transition. */
    val depth: Int

    data class Shelf(val view: DmtView, val searching: Boolean) : LibraryScreen {
        override val key = "shelf"
        override val depth = 0
    }

    data class OfArtist(val artist: Artist) : LibraryScreen {
        override val key = "artist:${artist.name}"
        override val depth = 1
    }

    data class OfArtistAlbum(val album: Album) : LibraryScreen {
        override val key = "artist-album:${album.id}"
        override val depth = 2
    }

    data class OfAlbum(val album: Album) : LibraryScreen {
        override val key = "album:${album.id}"
        override val depth = 1
    }

    data class OfGenre(val genre: Genre) : LibraryScreen {
        override val key = "genre:${genre.name}"
        override val depth = 1
    }

    data class OfFolder(val folder: Folder) : LibraryScreen {
        override val key = "folder:${folder.path}"
        override val depth = 1 + folder.path.count { it == '/' }
    }

    data class OfPlaylist(val playlist: Playlist) : LibraryScreen {
        override val key = "playlist:${playlist.name}"
        override val depth = 1
    }
}

internal fun DmtState.libraryScreen(view: DmtView): LibraryScreen {
    val effective =
        when {
            view == DmtView.FOLDERS && folders.isEmpty() -> DmtView.LIBRARY
            view == DmtView.PLAYLISTS && folders.isEmpty() -> DmtView.LIBRARY
            view == DmtView.GENRES && genres.isEmpty() -> DmtView.LIBRARY
            else -> view
        }
    val artist = openArtist?.takeIf { effective == DmtView.ARTISTS }?.let(library::artist)
    val artistAlbum = artist?.discography?.find { it.id == openAlbum }
    return when {
        artistAlbum != null -> LibraryScreen.OfArtistAlbum(artistAlbum)

        artist != null -> LibraryScreen.OfArtist(artist)

        effective == DmtView.ALBUMS -> openAlbum?.let(library::album)?.let(LibraryScreen::OfAlbum)

        effective == DmtView.GENRES -> openGenre?.let(library::genre)?.let(LibraryScreen::OfGenre)

        effective == DmtView.FOLDERS -> openFolder?.let(library::folder)?.let(LibraryScreen::OfFolder)

        effective == DmtView.PLAYLISTS ->
            playlists.find { it.name == openPlaylist }?.let(LibraryScreen::OfPlaylist)

        else -> null
    } ?: LibraryScreen.Shelf(effective, searching = query.isNotBlank())
}

/** The shared-element key of an album's cover, used by its tiles and its page header. */
internal fun albumCoverKey(id: String): String = "cover:album:$id"
