package dev.jyotiraditya.dmt.domain.model

import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.mock

class LibraryTest {

    private fun track(
        id: Long,
        title: String = "Song $id",
        artist: String = "Artist",
        albumArtist: String = "",
        album: String = "Album",
        path: String = "/storage/emulated/0/Music/Album/$id.flac",
        disc: Int = 0,
        number: Int = id.toInt(),
    ) = Track(
        id = id,
        uri = mock(Uri::class.java),
        title = title,
        artist = artist,
        albumArtist = albumArtist,
        album = album,
        path = path,
        durationMs = 1_000L,
        mime = "audio/flac",
        bitrate = 0,
        size = 0L,
        trackNumber = number,
        discNumber = disc,
    )

    @Test
    fun albumsWithOneNameButDifferentAlbumArtistsStayApart() {
        val albums =
            listOf(
                track(1, album = "Greatest Hits", albumArtist = "Queen"),
                track(2, album = "Greatest Hits", albumArtist = "ABBA"),
            )
                .toAlbums()
        assertEquals(2, albums.size)
        assertEquals(setOf("Queen", "ABBA"), albums.map { it.artist }.toSet())
    }

    @Test
    fun albumsWithoutAlbumArtistAreToldApartByFolder() {
        val albums =
            listOf(
                track(1, album = "Live", path = "/music/a/1.flac"),
                track(2, album = "Live", path = "/music/b/2.flac"),
            )
                .toAlbums()
        assertEquals(2, albums.size)
    }

    @Test
    fun albumNamesDifferingOnlyByApostrophesStayTogether() {
        val albums =
            listOf(
                track(1, album = "1989 (Taylor's Version)", albumArtist = "Taylor Swift"),
                track(2, album = "1989 (Taylor’s Version)", albumArtist = "Taylor Swift"),
                track(3, album = "1989 (Taylor s Version)", albumArtist = "Taylor Swift"),
            )
                .toAlbums()
        assertEquals(1, albums.size)
        assertEquals(3, albums.single().tracks.size)
    }

    @Test
    fun tracksWithAndWithoutAlbumArtistStayTogether() {
        val albums =
            listOf(
                track(1, album = "Reputation", artist = "Taylor Swift", albumArtist = "Taylor Swift"),
                track(2, album = "Reputation", artist = "Taylor Swift"),
            )
                .toAlbums()
        assertEquals(1, albums.size)
        assertEquals(2, albums.single().tracks.size)
    }

    @Test
    fun albumArtistFeaturingSuffixDoesNotSplitAnAlbum() {
        val albums =
            listOf(
                track(1, album = "Reputation", albumArtist = "Taylor Swift"),
                track(2, album = "Reputation", albumArtist = "Taylor Swift feat. Ed Sheeran"),
            )
                .toAlbums()
        assertEquals(1, albums.size)
    }

    @Test
    fun albumLookupFindsTracksWhoseAlbumArtistIsBlank() = runBlocking {
        val named = track(1, album = "Reputation", albumArtist = "Taylor Swift")
        val blank = track(2, album = "Reputation", artist = "Taylor Swift")
        val library = listOf(named, blank).toLibrary()

        assertEquals(library.albumOf(named), library.albumOf(blank))
        assertEquals(1, library.albums.size)
    }

    @Test
    fun discFoldersOfOneAlbumStayTogether() {
        val albums =
            listOf(
                track(1, album = "Box", path = "/music/Box/CD1/1.flac", disc = 1, number = 1),
                track(
                    2,
                    album = "Box",
                    path = "/music/Box/Disc 2/1.flac",
                    disc = 2,
                    number = 1,
                ),
            )
                .toAlbums()
        assertEquals(1, albums.size)
        assertEquals(listOf(1L, 2L), albums.single().tracks.map { it.id })
    }

    @Test
    fun aCompilationWithManyArtistsIsOneVariousArtistsAlbum() {
        val album = listOf(track(1, artist = "A"), track(2, artist = "B")).toAlbums().single()
        assertEquals("various artists", album.artist)
    }

    @Test
    fun lookupsFindTracksAlbumsAndArtists() = runBlocking {
        val first = track(1, album = "One", albumArtist = "Band")
        val second = track(2, album = "Two", albumArtist = "Band")
        val library = listOf(first, second).toLibrary()

        assertSame(first, library.track(1L))
        assertSame(second, library.track("2"))
        assertNull(library.track("album/2"))
        assertEquals("One", library.albumOf(first)?.name)
        assertEquals(library.albumOf(second), library.album(second.albumKey))
        assertEquals(2, library.artist("band")?.tracks?.size)
    }

    @Test
    fun tracksAreSortedOncePerOrder() = runBlocking {
        val library = listOf(track(1, title = "b"), track(2, title = "a")).toLibrary()
        val byTitle = library.tracksSortedBy(LibrarySort.TITLE)

        assertEquals(listOf(2L, 1L), byTitle.map { it.id })
        assertSame(byTitle, library.tracksSortedBy(LibrarySort.TITLE))
    }

    @Test
    fun folderForestKeepsIntermediateFolders() = runBlocking {
        val library =
            listOf(
                track(1, path = "/storage/emulated/0/Music/Artist/Album/1.flac"),
                track(2, path = "/storage/emulated/0/Music/Artist/Album/2.flac"),
                track(3, path = "/storage/emulated/0/Music/Other/3.flac"),
            )
                .toLibrary()
        val root = library.folderRoots.single()
        assertEquals("/storage/emulated/0", root.path)
        val music = root.children.single { it.name == "Music" }
        val artist = music.children.single { it.name == "Artist" }
        val album = artist.children.single { it.name == "Album" }
        assertEquals(2, album.tracks.size)
        assertEquals(2, artist.allTracks.size)
        assertSame(artist, library.folderNode(artist.path))
        assertSame(album, library.folder(album.path))
    }

    @Test
    fun folderForestSeparatesStorageVolumes() = runBlocking {
        val library =
            listOf(
                track(1, path = "/storage/emulated/0/Music/1.flac"),
                track(2, path = "/storage/1234-5678/Music/2.flac"),
            )
                .toLibrary()
        assertEquals(
            setOf("/storage/emulated/0", "/storage/1234-5678"),
            library.folderRoots.map { it.path }.toSet(),
        )
    }

    @Test
    fun folderSortOrdersTracksBothWays() {
        val tracks = listOf(track(1, title = "b"), track(2, title = "a"))
        assertEquals(
            listOf(2L, 1L),
            FolderSort.TITLE.sorted(tracks, ascending = true).map { it.id },
        )
        assertEquals(
            listOf(1L, 2L),
            FolderSort.TITLE.sorted(tracks, ascending = false).map { it.id },
        )
    }
}
