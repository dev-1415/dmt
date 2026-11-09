package dev.jyotiraditya.dmt.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jyotiraditya.dmt.domain.model.Playlist
import dev.jyotiraditya.dmt.domain.model.Track
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val M3U_HEADER = "#EXTM3U"
private const val M3U_EXTENSION = "m3u8"
private val PATH_SEPARATORS = Regex("""[/\\]""")

/** Playlists saved as M3U8 files of track paths in the app's storage. */
@Singleton
class PlaylistRepository
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val dir: File
        get() =
            File(context.getExternalFilesDir(null) ?: context.filesDir, "playlists").apply {
                mkdirs()
            }

    fun load(tracks: List<Track>): List<Playlist> {
        val byPath = tracks.associateBy { it.path }

        return dir.listFiles { file -> file.extension == M3U_EXTENSION }
            .orEmpty()
            .sortedBy { it.nameWithoutExtension.lowercase() }
            .map { file ->
                Playlist(
                    name = file.nameWithoutExtension,
                    tracks = entriesOf(file).mapNotNull { byPath[it] },
                )
            }
    }

    fun create(name: String): Boolean {
        val file = fileFor(name) ?: return false
        if (file.exists()) return false

        return runCatching { file.writeText(M3U_HEADER + "\n") }.isSuccess
    }

    fun delete(name: String) {
        fileFor(name)?.delete()
    }

    fun addTrack(name: String, track: Track): Boolean {
        val file = fileFor(name) ?: return false
        if (!file.exists() || track.path.isEmpty()) return false
        if (track.path in entriesOf(file)) return false

        return runCatching { file.appendText(track.path + "\n") }.isSuccess
    }

    fun removeTrack(name: String, path: String) {
        val file = fileFor(name) ?: return
        if (!file.exists()) return

        val kept = file.readLines().filterNot { it.trim() == path }
        runCatching { file.writeText(kept.joinToString("\n", postfix = "\n")) }
    }

    /** Reads an M3U/M3U8 file at [uri] and saves its matching [tracks] as a new playlist. */
    fun import(uri: Uri, tracks: List<Track>): Playlist? {
        val entries =
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readLines() }
            }
                .getOrNull()
                ?: return null
        val paths = entries.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        if (paths.isEmpty()) return null
        val byPath = tracks.associateBy { it.path }
        val byName = tracks.groupBy { it.path.substringAfterLast('/') }
        val resolved =
            paths.mapNotNull { entry ->
                byPath[entry]
                    ?: byName[entry.substringAfterLast('/')]?.firstOrNull()
                    ?: tracks.firstOrNull { it.title.equals(entry, ignoreCase = true) }
            }
        if (resolved.isEmpty()) return null
        val base =
            uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() }
                ?: "Imported"
        val name = uniqueName(base)
        val file = fileFor(name) ?: return null
        val written =
            runCatching {
                file.writeText(
                    buildString {
                        appendLine(M3U_HEADER)
                        resolved.forEach { appendLine(it.path) }
                    },
                )
            }
                .isSuccess
        return if (written) Playlist(name, resolved) else null
    }

    /** Writes the playlist [name] to the M3U/M3U8 file at [uri]. */
    fun export(name: String, uri: Uri, tracks: List<Track>): Boolean = runCatching {
        val playlist = load(tracks).firstOrNull { it.name == name } ?: return@runCatching false
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.appendLine(M3U_HEADER)
            playlist.tracks.forEach { track ->
                writer.appendLine(
                    "#EXTINF:${track.durationMs / 1000},${track.artist} - ${track.title}",
                )
                writer.appendLine(track.path.ifBlank { track.uri.toString() })
            }
        }
        true
    }
        .getOrDefault(false)

    /** [base] with a number appended when a playlist of that name already exists. */
    private fun uniqueName(base: String): String {
        val safe = base.trim().replace(PATH_SEPARATORS, "").ifEmpty { "Imported" }
        if (fileFor(safe)?.exists() != true) return safe
        var index = 2
        while (fileFor("$safe ($index)")?.exists() == true) index++
        return "$safe ($index)"
    }

    private fun entriesOf(file: File): List<String> = runCatching {
        file.readLines()
    }
        .getOrDefault(emptyList())
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }

    private fun fileFor(name: String): File? {
        val safe = name.trim().replace(PATH_SEPARATORS, "")
        if (safe.isEmpty()) return null

        return File(dir, "$safe.$M3U_EXTENSION")
    }
}
