package dev.jyotiraditya.dmt.data.lyrics

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import dev.jyotiraditya.dmt.domain.model.Track
import dev.jyotiraditya.dmt.domain.model.TrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import java.io.File
import kotlin.coroutines.resume

/** Writes lyrics into a local track's tags, replacing any lyrics already embedded. */
object LyricsEmbedder {

    suspend fun embed(context: Context, track: Track, lyrics: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(track.source == TrackSource.LOCAL) { "Only local tracks can be edited" }
                val file = File(track.path)
                require(file.isFile) { "The track file is missing" }
                val modified = file.lastModified()
                val audio = AudioFileIO.read(file)
                val tag = audio.tagOrCreateAndSetDefault
                tag.setField(FieldKey.LYRICS, lyrics)
                // Keep the comment header small enough for MediaStore and Media3 to read it.
                if (tag.hasField("UNSYNCEDLYRICS")) tag.deleteField("UNSYNCEDLYRICS")
                AudioFileIO.write(audio)
                // Keep the file where it was in date-modified order.
                if (modified > 0L) file.setLastModified(modified)
                scanAndRefresh(context, file, track, tag)
            }
        }

    /**
     * Lets MediaStore scan the rewritten file, then puts the file's own tags back. The scan sets
     * the modified date from the restored file time; the update fixes a scan that lost the tags.
     */
    private suspend fun scanAndRefresh(context: Context, file: File, track: Track, tag: Tag) {
        val scanned =
            withTimeoutOrNull(5_000) {
                suspendCancellableCoroutine { continuation ->
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath),
                        null,
                    ) { _, uri ->
                        refreshMediaStore(context, uri ?: track.uri, track, tag)
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            }
        if (scanned == null) refreshMediaStore(context, track.uri, track, tag)
    }

    /** Puts the file's own tags back into MediaStore, which may have re-read the file badly. */
    private fun refreshMediaStore(context: Context, uri: Uri, track: Track, tag: Tag) {
        if (uri.scheme != "content") return
        runCatching {
            val title = tag.text(FieldKey.TITLE, track.title)
            val artist = tag.text(FieldKey.ARTIST, track.artist)
            val album = tag.text(FieldKey.ALBUM, track.album)
            val albumArtist = tag.text(FieldKey.ALBUM_ARTIST, track.albumArtist)
            val trackNumber = tag.number(FieldKey.TRACK, track.trackNumber)
            val discNumber = tag.number(FieldKey.DISC_NO, track.discNumber)
            val genre = tag.text(FieldKey.GENRE, track.genre.orEmpty()).takeIf(String::isNotBlank)
            val values =
                ContentValues().apply {
                    put(MediaStore.Audio.Media.TITLE, title)
                    put(MediaStore.Audio.Media.ARTIST, artist)
                    put(MediaStore.Audio.Media.ALBUM, album)
                    put(MediaStore.Audio.Media.ALBUM_ARTIST, albumArtist)
                    put(MediaStore.Audio.Media.TRACK, discNumber * 1000 + trackNumber)
                    genre?.let { put(MediaStore.Audio.Media.GENRE, it) }
                }
            context.contentResolver.update(uri, values, null, null)
        }
    }

    private fun Tag.text(key: FieldKey, fallback: String): String =
        getFirst(key).ifBlank { fallback }

    private fun Tag.number(key: FieldKey, fallback: Int): Int =
        getFirst(key).substringBefore('/').trim().toIntOrNull() ?: fallback
}
