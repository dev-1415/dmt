package dev.jyotiraditya.dmt.domain.model

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private val COLLAB_SEPARATORS = Regex("""\s*[;/]\s*""")

val Track.primaryArtist: String
    get() = albumArtist.ifBlank { artist }

fun String.asCredit(): String = replace(COLLAB_SEPARATORS, " + ")

private val DISC_FOLDER = Regex("""/(cd|disc|disk)\s*\d+$""", RegexOption.IGNORE_CASE)

/** Letters and digits only, so apostrophes, spacing and punctuation don't split one album. */
private val NON_ALNUM = Regex("[^a-z0-9]")

/** A featured artist is not the album's owner. */
private val FEATURING = Regex("""\s+(?:feat\.?|ft\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE)

private fun String.normalizedKey(): String = lowercase().replace(NON_ALNUM, "")

/** The artist or folder that owns this track's album when its album artist is blank. */
private fun Track.albumOwnerKey(): String =
    albumArtist.ifBlank { artist }.ifBlank {
        path.substringBeforeLast('/', "").replace(DISC_FOLDER, "")
    }.replace(FEATURING, "").normalizedKey()

/**
 * The album this track belongs to: its name with its album artist, or with its artist or folder
 * when the tags name no album artist, so albums that share a name stay apart.
 */
val Track.albumKey: String
    get() = "${album.normalizedKey()}\u001F${albumOwnerKey()}"

/** These tracks grouped into albums, artists, folders and genres at once. */
suspend fun List<Track>.toLibrary(): LibrarySnapshot = coroutineScope {
    val albums = async { toAlbums() }
    val artists = async { toArtists() }
    val folders = async { toFolders() }
    val genres = async { toGenres() }
    val folderRoots = async { toFolderRoots() }
    LibrarySnapshot(
        this@toLibrary,
        albums.await(),
        artists.await(),
        folders.await(),
        genres.await(),
        folderRoots.await(),
    )
}

fun List<Track>.toFolders(): List<Folder> = asSequence()
    .filter { it.path.isNotEmpty() }
    .groupBy { it.path.substringBeforeLast('/') }
    .map { (dir, tracks) -> Folder(path = dir, tracks = tracks) }
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.relativePath })

/** The folders that hold music, as a forest with one root per storage volume. */
fun List<Track>.toFolderRoots(): List<Folder> {
    val byDirectory =
        asSequence()
            .filter { it.path.isNotEmpty() }
            .groupBy { it.path.substringBeforeLast('/') }
    if (byDirectory.isEmpty()) return emptyList()
    return byDirectory.entries
        .groupBy { (directory, _) -> storageRootOf(directory) }
        .map { (root, entries) -> buildFolderTree(root, entries) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.path })
}

/** The storage volume [directory] sits on, such as `/storage/emulated/0`. */
private fun storageRootOf(directory: String): String = when {
    directory == SHARED_STORAGE_ROOT || directory.startsWith("$SHARED_STORAGE_ROOT/") ->
        SHARED_STORAGE_ROOT

    directory.startsWith("/storage/") -> {
        val parts = directory.split('/')
        if (parts.size >= 3) "/storage/${parts[2]}" else "/storage"
    }

    else -> {
        val first = directory.split('/').firstOrNull { it.isNotEmpty() }
        if (first != null) "/$first" else "/"
    }
}

/** The tree of [entries] below [root], with each folder's direct tracks. */
private fun buildFolderTree(
    root: String,
    entries: List<Map.Entry<String, List<Track>>>,
): Folder {
    class Node(val path: String) {
        val tracks = mutableListOf<Track>()
        val children = linkedMapOf<String, Node>()
    }

    val rootNode = Node(root)
    entries.forEach { (directory, tracks) ->
        var node = rootNode
        if (directory != root) {
            directory.removePrefix(root).trim('/').split('/').filter { it.isNotEmpty() }.forEach {
                segment ->
                node =
                    node.children.getOrPut(segment) {
                        Node(node.path.trimEnd('/') + "/" + segment)
                    }
            }
        }
        node.tracks += tracks
    }

    fun Node.toFolder(): Folder =
        Folder(
            path = path,
            tracks = tracks,
            children =
                children.values
                    .map { it.toFolder() }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }),
        )

    return rootNode.toFolder()
}

fun List<Track>.toArtists(): List<Artist> = groupBy {
    it.primaryArtist.lowercase()
}
    .map { (_, tracks) ->
        Artist(
            name = tracks.groupingBy { it.primaryArtist }.eachCount().maxBy { it.value }.key,
            albums = tracks.mapTo(HashSet()) { it.album }.size,
            tracks =
                tracks.sortedWith(
                    compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.album }
                        .thenBy { it.discNumber }
                        .thenBy { it.trackNumber },
                ),
        )
    }
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

fun List<Track>.toGenres(): List<Genre> = groupBy {
    it.genre?.takeIf(String::isNotBlank)?.lowercase() ?: UNKNOWN_GENRE
}
    .map { (_, tracks) ->
        Genre(
            name = tracks.first().genre?.takeIf(String::isNotBlank) ?: UNKNOWN_GENRE,
            tracks = tracks,
        )
    }
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

fun List<Track>.toAlbums(): List<Album> =
    groupBy { it.album.normalizedKey() }
        .flatMap { (albumName, tracks) ->
            val albumArtists =
                tracks.mapNotNull { it.albumArtist.takeIf(String::isNotBlank) }.distinct()
            val groups =
                when {
                    // Some tracks name the album artist and some don't: they still share it.
                    albumArtists.size == 1 -> mapOf(albumArtists.single() to tracks)
                    albumArtists.size > 1 -> tracks.groupBy { it.albumOwnerKey() }
                    // No album artist at all: keep folders apart, so compilations stay together.
                    else ->
                        tracks.groupBy {
                            it.path
                                .substringBeforeLast('/', "")
                                .replace(DISC_FOLDER, "")
                                .normalizedKey()
                        }
                }
            groups.map { (owner, group) ->
                val artists = group.mapTo(HashSet()) { it.primaryArtist.lowercase() }
                Album(
                    id = "$albumName\u001F${owner.normalizedKey()}",
                    name = group.first().album,
                    artist =
                        if (artists.size == 1) group.first().primaryArtist else "various artists",
                    tracks = group.sortedWith(compareBy({ it.discNumber }, { it.trackNumber })),
                )
            }
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
