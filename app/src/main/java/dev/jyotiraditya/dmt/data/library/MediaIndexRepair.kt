package dev.jyotiraditya.dmt.data.library

import android.content.ContentResolver
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

private val AUDIO_EXTENSIONS =
    setOf("mp3", "m4a", "flac", "ogg", "opus", "wav", "aiff", "aif", "wma")

/** Re-indexes audio files so MediaStore rows lost when a file was replaced come back. */
object MediaIndexRepair {

    /** Scans every audio file below [roots] and returns how many were re-indexed. */
    suspend fun repair(context: Context, roots: Collection<String>): Int =
        withContext(Dispatchers.IO) {
            var repaired = 0
            for (root in roots) {
                val dir = File(root)
                if (!dir.isDirectory) continue
                dir.walkTopDown()
                    .filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
                    .forEach { file ->
                        if (scan(context, file) != null) repaired++
                    }
            }
            repaired
        }

    private suspend fun scan(context: Context, file: File): Uri? {
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
                context.contentResolver.call(volume, "scan_file", file.absolutePath, null)
            }.getOrNull()
        if (result != null) {
            val uri =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    result.getParcelable("android.intent.extra.STREAM", Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    result.getParcelable("android.intent.extra.STREAM")
                }
            if (uri != null) return uri
        }

        return withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine { continuation ->
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    null,
                ) { _, uri ->
                    if (continuation.isActive) continuation.resume(uri)
                }
            }
        }
    }
}
