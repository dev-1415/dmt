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
                // The extension can lie, so the container is read from the file's own header.
                val format = audioFormat(file, track)
                require(format != null) { "This file format cannot hold lyrics" }
                val modified = file.lastModified()
                val backup = File(file.parentFile, "${file.name}.dmt-backup")
                file.copyTo(backup, overwrite = true)
                if (modified > 0L) backup.setLastModified(modified)
                try {
                    val tag =
                        if (format == "opus") {
                            OpusTags.write(file, lyrics)
                            null
                        } else {
                            val audio = AudioFileIO.readAs(file, format)
                            val tag = audio.tagOrCreateAndSetDefault
                            tag.setField(FieldKey.LYRICS, lyrics)
                            // Keep the comment header small enough for MediaStore and Media3 to read it.
                            if (tag.hasField("UNSYNCEDLYRICS")) tag.deleteField("UNSYNCEDLYRICS")
                            AudioFileIO.write(audio)
                            require(headerMatches(file, format)) {
                                "The rewritten file is not readable"
                            }
                            tag
                        }
                    // Keep the file where it was in date-modified order.
                    if (modified > 0L) file.setLastModified(modified)
                    backup.delete()
                    // The Opus tags are preserved, so a MediaStore rescan can only lose them.
                    if (format != "opus") {
                        runCatching { scanAndRefresh(context, file, track, tag) }
                    }
                    Unit
                } catch (failure: Throwable) {
                    // Put the original back when the writer damaged the file.
                    runCatching {
                        backup.copyTo(file, overwrite = true)
                        if (modified > 0L) file.setLastModified(modified)
                        backup.delete()
                    }
                    throw failure
                }
            }
        }

    /** The container extension jaudiotagger should use, read from the file header first. */
    private fun audioFormat(file: File, track: Track): String? {
        val header = ByteArray(12)
        val read = file.inputStream().use { it.read(header) }
        return when {
            read >= 8 && header.copyOfRange(4, 8).contentEquals("ftyp".toByteArray()) -> "m4a"
            read >= 4 && header.copyOfRange(0, 4).contentEquals("fLaC".toByteArray()) -> "flac"
            read >= 4 && header.copyOfRange(0, 4).contentEquals("OggS".toByteArray()) ->
                if (isOpus(file)) "opus" else "ogg"
            read >= 12 &&
                header.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
                header.copyOfRange(8, 12).contentEquals("WAVE".toByteArray()) -> "wav"
            read >= 12 &&
                header.copyOfRange(0, 4).contentEquals("FORM".toByteArray()) &&
                header.copyOfRange(8, 12).contentEquals("AIFF".toByteArray()) -> "aiff"
            read >= 3 && header.copyOfRange(0, 3).contentEquals("ID3".toByteArray()) -> "mp3"
            read >= 2 &&
                header[0] == 0xFF.toByte() &&
                (header[1].toInt() and 0xE0) == 0xE0 -> "mp3"
            else -> track.mime.extensionForMime()
        }
    }

    /** Whether the Ogg file's first packet is an OpusHead. */
    private fun isOpus(file: File): Boolean {
        val header = ByteArray(300)
        val read = file.inputStream().use { it.read(header) }
        if (read < 28) return false
        val segments = header[26].toInt() and 0xFF
        val dataStart = 27 + segments
        if (dataStart + 8 > read) return false
        return header.copyOfRange(dataStart, dataStart + 8).contentEquals("OpusHead".toByteArray())
    }

    /** Whether the file still starts with the container it was written as. */
    private fun headerMatches(file: File, format: String): Boolean {
        val header = ByteArray(12)
        val read = file.inputStream().use { it.read(header) }
        return when (format) {
            "m4a" -> read >= 8 && header.copyOfRange(4, 8).contentEquals("ftyp".toByteArray())
            "flac" -> read >= 4 && header.copyOfRange(0, 4).contentEquals("fLaC".toByteArray())
            "ogg",
            "opus" -> read >= 4 && header.copyOfRange(0, 4).contentEquals("OggS".toByteArray())
            "wav" ->
                read >= 12 &&
                    header.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
                    header.copyOfRange(8, 12).contentEquals("WAVE".toByteArray())
            "aiff" ->
                read >= 12 &&
                    header.copyOfRange(0, 4).contentEquals("FORM".toByteArray()) &&
                    header.copyOfRange(8, 12).contentEquals("AIFF".toByteArray())
            "mp3" ->
                read >= 3 &&
                    (header.copyOfRange(0, 3).contentEquals("ID3".toByteArray()) ||
                        (header[0] == 0xFF.toByte() &&
                            (header[1].toInt() and 0xE0) == 0xE0))
            else -> true
        }
    }

    private fun String.extensionForMime(): String? = when {
        contains("mpeg", true) || contains("mp3", true) -> "mp3"
        contains("mp4", true) || contains("m4a", true) || contains("aac", true) -> "m4a"
        contains("flac", true) -> "flac"
        contains("ogg", true) || contains("opus", true) || contains("vorbis", true) -> "ogg"
        contains("wav", true) -> "wav"
        contains("aiff", true) -> "aiff"
        contains("wma", true) -> "wma"
        else -> null
    }

    /**
     * Lets MediaStore scan the rewritten file, then puts the file's own tags back. The scan sets
     * the modified date from the restored file time; the update fixes a scan that lost the tags.
     */
    private suspend fun scanAndRefresh(context: Context, file: File, track: Track, tag: Tag?) {
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
    private fun refreshMediaStore(context: Context, uri: Uri, track: Track, tag: Tag?) {
        if (tag == null || uri.scheme != "content") return
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
