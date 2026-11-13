package dev.jyotiraditya.dmt.data.lyrics

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import dev.jyotiraditya.dmt.BuildConfig
import dev.jyotiraditya.dmt.domain.model.Track
import dev.jyotiraditya.dmt.domain.model.TrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3FileWriter
import org.jaudiotagger.audio.mp4.Mp4FileWriter
import org.jaudiotagger.audio.ogg.OggFileWriter
import org.jaudiotagger.audio.opus.OpusFileWriter
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.io.StringReader
import java.io.StringWriter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.coroutines.resume

private const val TAG = "LyricsEmbedder"

/** Marks a TTML sidecar this app wrote, so an edited LRC can remove only our own file. */
private const val TTML_MARKER = "<!-- DMT-TTML -->"

/** The TTML timing attributes whose values need normalizing for other players. */
private val TTML_TIME_ATTRIBUTE = Regex("""(begin|end|dur)="([^"]+)"""")

private val DIV_BEGIN = Regex("""<div[^>]*\bbegin="([^"]+)"""")

private val P_BEGIN = Regex("""<p[^>]*\bbegin="([^"]+)"""")

/** Writes lyrics into a local track's tags, replacing any lyrics already embedded. */
object LyricsEmbedder {

    suspend fun embed(
        context: Context,
        track: Track,
        lyrics: String,
        ttml: String? = null,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(track.source == TrackSource.LOCAL) { "Only local tracks can be edited" }
                val file = File(track.path)
                require(file.isFile) { "The track file is missing" }
                // Remove backups left behind by an older version of the embedder.
                file.parentFile
                    ?.listFiles { candidate -> candidate.name.endsWith(".dmt-backup") }
                    ?.forEach { it.delete() }
                // The extension can lie, so the container is read from the file's own header.
                val format = audioFormat(file, track)
                if (format == null) {
                    EmbedLog.write(
                        context,
                        "embed failed: unsupported format for ${file.absolutePath} " +
                            "mime=${track.mime} header=${headerHex(file)}",
                        folder = file.parentFile,
                    )
                }
                require(format != null) { "This file format cannot hold lyrics" }
                val modified = file.lastModified()
                EmbedLog.write(
                    context,
                    "embed ${file.absolutePath} format=$format size=${file.length()} " +
                        "header=${headerHex(file)}",
                    folder = file.parentFile,
                )
                // The source copy is the recovery copy and is never written to.
                val source = File(file.parentFile, ".dmt-source-${System.nanoTime()}.$format")
                // The destination is the rewritten file that replaces the original.
                val destination = File(file.parentFile, ".dmt-dest-${System.nanoTime()}.$format")
                var sourceReady = false
                try {
                    file.copyTo(source, overwrite = true)
                    if (modified > 0L) source.setLastModified(modified)
                    sourceReady = true
                    val tag = writeLyrics(context, source, destination, format, lyrics)
                    require(destination.isFile && headerMatches(destination, format)) {
                        "The rewritten file is not readable"
                    }
                    // Overwrite the original in place. Renaming a new file over it makes
                    // MediaStore drop the row, which is what made tracks disappear.
                    FileOutputStream(file).use { output ->
                        destination.inputStream().use { input -> input.copyTo(output) }
                    }
                    destination.delete()
                    runCatching { if (modified > 0L) file.setLastModified(modified) }
                    source.delete()
                    // TTML-aware players read a sidecar; the standard LRC tag covers the rest.
                    if (ttml != null) {
                        runCatching { writeTtmlSidecar(file, ttml) }
                    } else {
                        runCatching { deleteTtmlSidecar(file) }
                    }
                    // The scan may lose tags for some formats, so the refresh puts them back.
                    runCatching { scanAndRefresh(context, file, track, tag, modified) }
                    if (BuildConfig.DEBUG) {
                        val reread = runCatching { AudioFileIO.readAs(file, format).tag }.getOrNull()
                        EmbedLog.write(
                            context,
                            "embedded ${file.name} format=$format " +
                                "title=${reread?.getFirst(FieldKey.TITLE)} " +
                                "artist=${reread?.getFirst(FieldKey.ARTIST)} " +
                                "album=${reread?.getFirst(FieldKey.ALBUM)} " +
                                "lyrics=${reread?.getFirst(FieldKey.LYRICS)?.length} " +
                                "artwork=${reread?.getFirstArtwork() != null}",
                            folder = file.parentFile,
                        )
                    }
                    Unit
                } catch (failure: Throwable) {
                    Log.e(TAG, "embed failed for ${file.name} format=$format", failure)
                    EmbedLog.write(
                        context,
                        "embed failed for ${file.name} format=$format",
                        failure,
                        folder = file.parentFile,
                    )
                    destination.delete()
                    // Put the original back. Keep the source copy if the restore fails, so the
                    // file can still be recovered by hand.
                    var restored = !sourceReady
                    if (sourceReady) {
                        restored =
                            runCatching {
                                source.copyTo(file, overwrite = true)
                                if (modified > 0L) file.setLastModified(modified)
                            }.isSuccess
                    }
                    if (restored) source.delete()
                    // Make MediaStore show the original tags again after a failed write.
                    runCatching {
                        val restoredTag =
                            runCatching { AudioFileIO.readAs(file, format).tag }.getOrNull()
                        scanAndRefresh(context, file, track, restoredTag, modified)
                    }
                    throw failure
                }
            }
        }

    /** Writes the raw TTML beside the track, marked so an edited LRC can remove only our file. */
    private fun writeTtmlSidecar(file: File, ttml: String) {
        // A sidecar we wrote is already normalized and relative; only raw lrc.red TTML needs it.
        val prepared =
            if (ttml.contains(TTML_MARKER)) {
                ttml.substringAfter(TTML_MARKER).trimStart()
            } else {
                relativizeTtmlTimes(normalizeTtmlTimes(ttml))
            }
        val marked =
            if (prepared.startsWith("<?xml")) {
                val end = prepared.indexOf("?>")
                if (end >= 0) {
                    prepared.substring(0, end + 2) + "\n" + TTML_MARKER + "\n" +
                        prepared.substring(end + 2)
                } else {
                    "$TTML_MARKER\n$prepared"
                }
            } else {
                "$TTML_MARKER\n$prepared"
            }
        File(file.parentFile, "${file.nameWithoutExtension}.ttml").writeText(marked)
    }

    /** These TTML times moved by [offsetMs], for applying a lyric offset to the sidecar. */
    fun shiftTtmlTimes(ttml: String, offsetMs: Long): String =
        runCatching {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val document = factory.newDocumentBuilder().parse(InputSource(StringReader(ttml)))
            shiftElement(document.documentElement, offsetMs)
            val transformer = TransformerFactory.newInstance().newTransformer()
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            val writer = StringWriter()
            transformer.transform(DOMSource(document), StreamResult(writer))
            writer.toString()
        }.getOrDefault(ttml)

    private fun shiftElement(element: Element, offsetMs: Long) {
        for (attribute in listOf("begin", "end")) {
            val ms = parseTtmlTime(element.getAttribute(attribute)) ?: continue
            element.setAttribute(attribute, formatTtmlTimeMs((ms + offsetMs).coerceAtLeast(0)))
        }
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) shiftElement(child, offsetMs)
        }
    }

    /** Whether [ttml] uses times relative to its parents (our sidecar) rather than absolute times. */
    fun isRelativeTtml(ttml: String): Boolean {
        val div = DIV_BEGIN.find(ttml)?.groupValues?.get(1)?.let(::parseTtmlTime) ?: return false
        val p = P_BEGIN.find(ttml)?.groupValues?.get(1)?.let(::parseTtmlTime) ?: return false
        return p < div
    }

    /** lrc.red writes `mm:ss.SSS` and bare seconds; TTML players expect `hh:mm:ss.SSS`. */
    private fun normalizeTtmlTimes(ttml: String): String =
        TTML_TIME_ATTRIBUTE.replace(ttml) { match ->
            val attribute = match.groupValues[1]
            val value = match.groupValues[2]
            "$attribute=\"${normalizeTtmlTime(value)}\""
        }

    private fun normalizeTtmlTime(value: String): String {
        val ms = parseTtmlTime(value) ?: return value
        return formatTtmlTimeMs(ms)
    }

    /**
     * lrc.red writes absolute times on every level, while TTML players add the parent's time.
     * Make each child's time relative to its parent so the player's addition lands correctly.
     */
    private fun relativizeTtmlTimes(ttml: String): String =
        runCatching {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val document = factory.newDocumentBuilder().parse(InputSource(StringReader(ttml)))
            relativizeElement(document.documentElement, 0L)
            val transformer = TransformerFactory.newInstance().newTransformer()
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            val writer = StringWriter()
            transformer.transform(DOMSource(document), StreamResult(writer))
            writer.toString()
        }.getOrDefault(ttml)

    private fun relativizeElement(element: Element, parentBeginMs: Long) {
        val beginMs = parseTtmlTime(element.getAttribute("begin"))
        val endMs = parseTtmlTime(element.getAttribute("end"))
        if (beginMs != null) {
            element.setAttribute("begin", formatTtmlTimeMs((beginMs - parentBeginMs).coerceAtLeast(0)))
        }
        if (endMs != null) {
            element.setAttribute("end", formatTtmlTimeMs((endMs - parentBeginMs).coerceAtLeast(0)))
        }
        val childParentMs = beginMs ?: parentBeginMs
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) relativizeElement(child, childParentMs)
        }
    }

    private fun parseTtmlTime(value: String): Long? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        // Offset times with a unit are already relative.
        if (trimmed.last() in "hmsft") return null
        val parts = trimmed.split(':')
        return when (parts.size) {
            1 -> parts[0].toDoubleOrNull()?.let { (it * 1000).toLong() }

            2 -> {
                val minutes = parts[0].toLongOrNull() ?: return null
                val seconds = parts[1].toDoubleOrNull() ?: return null
                (minutes * 60_000 + seconds * 1000).toLong()
            }

            3 -> {
                val hours = parts[0].toLongOrNull() ?: return null
                val minutes = parts[1].toLongOrNull() ?: return null
                val seconds = parts[2].toDoubleOrNull() ?: return null
                (hours * 3_600_000 + minutes * 60_000 + seconds * 1000).toLong()
            }

            else -> null
        }
    }

    private fun formatTtmlTimeMs(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val millis = ms % 1000
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }

    /** Removes the TTML sidecar only when this app wrote it. */
    private fun deleteTtmlSidecar(file: File) {
        val sidecar = File(file.parentFile, "${file.nameWithoutExtension}.ttml")
        if (!sidecar.isFile) return
        val ours = runCatching { sidecar.readText().contains(TTML_MARKER) }.getOrDefault(false)
        if (ours) sidecar.delete()
    }

    /** Reads [source], writes the lyrics, and returns the tag that was written. */
    private fun writeLyrics(
        context: Context,
        source: File,
        destination: File,
        format: String,
        lyrics: String,
    ): Tag {
        // These writers edit the file in place, so they work on the destination copy.
        val inPlace =
            format == "mp3" ||
                format == "flac" ||
                format == "wav" ||
                format == "aiff" ||
                format == "wma"
        val readFile =
            if (inPlace) {
                source.copyTo(destination, overwrite = true)
                destination
            } else {
                source
            }
        val audio = AudioFileIO.readAs(readFile, format)
        val tag = audio.tagOrCreateAndSetDefault
        EmbedLog.write(
            context,
            "before title=${tag.getFirst(FieldKey.TITLE)} " +
                "artist=${tag.getFirst(FieldKey.ARTIST)} " +
                "album=${tag.getFirst(FieldKey.ALBUM)} " +
                "lyrics=${tag.getFirst(FieldKey.LYRICS).length} " +
                "artwork=${tag.getFirstArtwork() != null}",
            folder = source.parentFile,
        )
        // Replace every existing lyrics frame, then add the new one.
        tag.deleteField(FieldKey.LYRICS)
        tag.setField(tag.createField(FieldKey.LYRICS, lyrics))
        // Keep the comment header small enough for MediaStore and Media3 to read it.
        if (tag.hasField("UNSYNCEDLYRICS")) tag.deleteField("UNSYNCEDLYRICS")
        when {
            format == "mp3" -> MP3FileWriter().writeFile(audio)
            inPlace -> AudioFileIO.write(audio)
            else ->
                RandomAccessFile(source, "r").use { input ->
                    RandomAccessFile(destination, "rw").use { output ->
                        when (format) {
                            "m4a" -> Mp4Writer().writeTagTo(audio, tag, input, output)
                            "opus" -> OpusWriter().writeTagTo(audio, tag, input, output)
                            "ogg" -> OggWriter().writeTagTo(audio, tag, input, output)
                            else -> error("Unsupported format $format")
                        }
                    }
                }
        }
        return tag
    }

    private class Mp4Writer : Mp4FileWriter() {
        fun writeTagTo(
            audio: AudioFile,
            tag: Tag,
            source: RandomAccessFile,
            destination: RandomAccessFile,
        ) {
            writeTag(audio, tag, source, destination)
        }
    }

    private class OpusWriter : OpusFileWriter() {
        fun writeTagTo(
            audio: AudioFile,
            tag: Tag,
            source: RandomAccessFile,
            destination: RandomAccessFile,
        ) {
            writeTag(audio, tag, source, destination)
        }
    }

    private class OggWriter : OggFileWriter() {
        fun writeTagTo(
            audio: AudioFile,
            tag: Tag,
            source: RandomAccessFile,
            destination: RandomAccessFile,
        ) {
            writeTag(audio, tag, source, destination)
        }
    }

    private fun headerHex(file: File): String {
        val bytes = ByteArray(16)
        val read = file.inputStream().use { it.read(bytes) }
        return bytes.take(read).joinToString(" ") { "%02X".format(it) }
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
    private suspend fun scanAndRefresh(
        context: Context,
        file: File,
        track: Track,
        tag: Tag?,
        modified: Long,
    ) {
        // Prefer updating the existing row. A scan can give the file a new id, which would
        // break the queue's media id and the lyrics lookup.
        val existing = mediaStoreUri(context, file)
        if (existing != null) {
            refreshMediaStore(context, existing, track, tag, modified)
            return
        }
        // Replacing the file can remove its row. Ask the media store to index it again.
        val scannedUri = scanFile(context, file)
        if (scannedUri == null) Log.e(TAG, "scan_file failed for ${file.name}")
        EmbedLog.write(
            context,
            "scan_file ${file.name} -> $scannedUri",
            folder = file.parentFile,
        )
        if (scannedUri != null) {
            refreshMediaStore(context, scannedUri, track, tag, modified)
            return
        }
        val scanned =
            withTimeoutOrNull(5_000) {
                suspendCancellableCoroutine { continuation ->
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath),
                        null,
                    ) { _, uri ->
                        refreshMediaStore(
                            context,
                            uri ?: mediaStoreUri(context, file) ?: track.uri,
                            track,
                            tag,
                            modified,
                        )
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            }
        if (scanned == null) {
            val inserted = insertMediaStore(context, file, track, tag, modified)
            if (inserted == null) Log.e(TAG, "insert failed for ${file.name}")
            EmbedLog.write(
                context,
                "insert ${file.name} -> $inserted",
                folder = file.parentFile,
            )
            refreshMediaStore(
                context,
                inserted ?: mediaStoreUri(context, file) ?: track.uri,
                track,
                tag,
                modified,
            )
        }
    }

    /** Inserts a new media store row for [file] when a scan could not create one. */
    private fun insertMediaStore(
        context: Context,
        file: File,
        track: Track,
        tag: Tag?,
        modified: Long,
    ): Uri? {
        val title = tag?.text(FieldKey.TITLE, track.title) ?: track.title
        val artist = tag?.text(FieldKey.ARTIST, track.artist) ?: track.artist
        val album = tag?.text(FieldKey.ALBUM, track.album) ?: track.album
        val albumArtist = tag?.text(FieldKey.ALBUM_ARTIST, track.albumArtist) ?: track.albumArtist
        val trackNumber = tag?.number(FieldKey.TRACK, track.trackNumber) ?: track.trackNumber
        val discNumber = tag?.number(FieldKey.DISC_NO, track.discNumber) ?: track.discNumber
        val genre =
            tag?.text(FieldKey.GENRE, track.genre.orEmpty())?.takeIf { it.isNotBlank() }
                ?: track.genre?.takeIf { it.isNotBlank() }
        val values =
            ContentValues().apply {
                put(MediaStore.Audio.Media.DATA, file.absolutePath)
                put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.ALBUM, album)
                put(MediaStore.Audio.Media.ALBUM_ARTIST, albumArtist)
                put(MediaStore.Audio.Media.TRACK, discNumber * 1000 + trackNumber)
                genre?.let { put(MediaStore.Audio.Media.GENRE, it) }
                put(MediaStore.Audio.Media.MIME_TYPE, track.mime)
                put(MediaStore.Audio.Media.SIZE, file.length())
                put(MediaStore.Audio.Media.DURATION, track.durationMs)
                put(MediaStore.Audio.Media.BITRATE, track.bitrate)
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.DATE_MODIFIED, (modified / 1000).toInt())
                put(
                    MediaStore.Audio.Media.DATE_ADDED,
                    (track.dateAdded.takeIf { it > 0 } ?: (modified / 1000)).toInt(),
                )
            }
        return runCatching {
            context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull()
    }

    /** Asks the media provider to index [file] again, returning its new row if it did. */
    @Suppress("DEPRECATION")
    private fun scanFile(context: Context, file: File): Uri? {
        // The public scanFile API exists on Android 10+ but is hidden from the compile SDK.
        val reflected =
            runCatching {
                val method =
                    MediaStore::class.java.getMethod(
                        "scanFile",
                        ContentResolver::class.java,
                        File::class.java,
                    )
                method.invoke(null, context.contentResolver, file) as? Uri
            }.getOrNull()
        if (reflected != null) return reflected

        val volume = MediaStore.AUTHORITY_URI.buildUpon().appendPath("external").build()
        val result =
            runCatching {
                context.contentResolver.call(
                    volume,
                    "scan_file",
                    file.absolutePath,
                    null,
                )
            }.getOrNull() ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result.getParcelable("android.intent.extra.STREAM", Uri::class.java)
        } else {
            result.getParcelable("android.intent.extra.STREAM")
        }
    }

    /** The media store row for [file], which may have a new id after the file was replaced. */
    private fun mediaStoreUri(context: Context, file: File): Uri? =
        context.contentResolver
            .query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.Audio.Media.DATA} = ?",
                arrayOf(file.absolutePath),
                null,
            )
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(0),
                    )
                } else {
                    null
                }
            }

    /** Puts the file's own tags back into MediaStore, which may have re-read the file badly. */
    private fun refreshMediaStore(
        context: Context,
        uri: Uri,
        track: Track,
        tag: Tag?,
        modified: Long,
    ) {
        if (uri.scheme != "content") return
        val title = tag?.text(FieldKey.TITLE, track.title) ?: track.title
        val artist = tag?.text(FieldKey.ARTIST, track.artist) ?: track.artist
        val album = tag?.text(FieldKey.ALBUM, track.album) ?: track.album
        val albumArtist = tag?.text(FieldKey.ALBUM_ARTIST, track.albumArtist) ?: track.albumArtist
        val trackNumber = tag?.number(FieldKey.TRACK, track.trackNumber) ?: track.trackNumber
        val discNumber = tag?.number(FieldKey.DISC_NO, track.discNumber) ?: track.discNumber
        val genre =
            tag?.text(FieldKey.GENRE, track.genre.orEmpty())?.takeIf { it.isNotBlank() }
                ?: track.genre?.takeIf { it.isNotBlank() }

        // Keep the core tags in one update, so an optional column cannot block them.
        runCatching {
            val values =
                ContentValues().apply {
                    put(MediaStore.Audio.Media.TITLE, title)
                    put(MediaStore.Audio.Media.ARTIST, artist)
                    put(MediaStore.Audio.Media.ALBUM, album)
                    put(MediaStore.Audio.Media.ALBUM_ARTIST, albumArtist)
                    put(MediaStore.Audio.Media.TRACK, discNumber * 1000 + trackNumber)
                }
            context.contentResolver.update(uri, values, null, null)
        }
        genre?.let { value ->
            runCatching {
                context.contentResolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Audio.Media.GENRE, value) },
                    null,
                    null,
                )
            }
        }
        runCatching {
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Audio.Media.IS_MUSIC, 1) },
                null,
                null,
            )
        }
        if (modified > 0L) {
            runCatching {
                context.contentResolver.update(
                    uri,
                    ContentValues().apply {
                        put(MediaStore.Audio.Media.DATE_MODIFIED, modified / 1000)
                    },
                    null,
                    null,
                )
            }
        }
    }

    private fun Tag.text(key: FieldKey, fallback: String): String =
        getFirst(key).ifBlank { fallback }

    private fun Tag.number(key: FieldKey, fallback: Int): Int =
        getFirst(key).substringBefore('/').trim().toIntOrNull() ?: fallback
}
