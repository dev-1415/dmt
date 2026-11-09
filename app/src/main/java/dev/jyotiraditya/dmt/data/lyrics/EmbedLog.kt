package dev.jyotiraditya.dmt.data.lyrics

import android.content.Context
import android.os.Environment
import android.util.Log
import dev.jyotiraditya.dmt.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes lyric-embedding diagnostics to files in debug builds, so a failure can be read without
 * logcat. The log is kept next to the track, in the public Downloads folder, and in the app's
 * external files folder.
 */
internal object EmbedLog {
    private const val TAG = "LyricsEmbedder"
    private const val FILE_NAME = "dmt-lyrics-embed.log"
    private val lock = Any()
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun write(
        context: Context,
        message: String,
        failure: Throwable? = null,
        folder: File? = null,
    ) {
        if (!BuildConfig.DEBUG) return
        synchronized(lock) {
            val entry =
                buildString {
                    append(timeFormat.format(Date()))
                    append(' ')
                    append(message)
                    if (failure != null) {
                        append('\n')
                        append(Log.getStackTraceString(failure))
                    }
                    append('\n')
                }
            folder?.let { dir -> runCatching { File(dir, FILE_NAME).appendText(entry) } }
            publicFile()?.let { file -> runCatching { file.appendText(entry) } }
            runCatching {
                val dir = context.getExternalFilesDir(null) ?: context.filesDir
                File(dir, FILE_NAME).appendText(entry)
            }
        }
        Log.d(TAG, message, failure)
    }

    @Suppress("DEPRECATION")
    private fun publicFile(): File? =
        runCatching {
            val downloads =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(downloads, FILE_NAME)
        }.getOrNull()
}
