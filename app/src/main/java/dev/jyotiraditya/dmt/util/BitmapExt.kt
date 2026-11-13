package dev.jyotiraditya.dmt.util

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.scale
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.SchemeContent
import com.google.android.material.color.utilities.Score
import dev.jyotiraditya.dmt.ui.theme.CoverColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** This bitmap in a config whose pixels can be read and scaled. */
fun Bitmap.software(): Bitmap =
    if (config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) else this

/** This bitmap scaled down so its longer side is at most [max] pixels. */
fun Bitmap.fitWithin(max: Int): Bitmap {
    val soft = software()
    val longer = maxOf(soft.width, soft.height)
    if (longer <= max) return soft
    val scale = max.toFloat() / longer
    return soft.scale(
        width = (soft.width * scale).toInt().coerceAtLeast(1),
        height = (soft.height * scale).toInt().coerceAtLeast(1),
    )
}

/**
 * The cover's Material You content-based surface colour, the same strategy Gramophone uses:
 * a full Material colour scheme is generated from the bitmap, then its surface is taken and
 * given Gramophone's elevated-background HSL adjustment.
 *
 * The scheme is always generated dark. DynamicColors chooses light or dark from the context
 * theme, which is unreliable for an application context and produced a pale olive page.
 */
@SuppressLint("RestrictedApi") // Material's quantizer has no public entry point.
suspend fun Bitmap.contentFieldColor(): Color =
    withContext(Dispatchers.Default) {
        val sample = fitWithin(128)
        val pixels = IntArray(sample.width * sample.height)
        sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
        val seed = Score.score(QuantizerCelebi.quantize(pixels, 128)).firstOrNull()
        if (seed == null) return@withContext neutralField(0.5f)
        val scheme = SchemeContent(Hct.fromInt(seed), true, 0.0)
        val surface = scheme.surface
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(surface, hsl)
        // Gramophone's COLOR_BACKGROUND_ELEVATED in dark mode.
        hsl[1] = (hsl[1] * 1.2f).coerceAtMost(1f)
        hsl[2] = (hsl[2] * 0.99f).coerceAtMost(1f)
        val result = ColorUtils.HSLToColor(hsl)
        Log.i(
            "DmtTint",
            "seed=${Integer.toHexString(seed)} surface=${Integer.toHexString(surface)} " +
                "result=${Integer.toHexString(result)}",
        )
        Color(result)
    }

/**
 * The full Material You scheme generated from this cover, always dark. [CoverColors.surface] is
 * the page colour; the primary roles drive the play button, seek bar and live lyric.
 */
@SuppressLint("RestrictedApi") // Material's quantizer has no public entry point.
suspend fun Bitmap.contentColors(): CoverColors =
    withContext(Dispatchers.Default) {
        val sample = fitWithin(128)
        val pixels = IntArray(sample.width * sample.height)
        sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
        val seed = Score.score(QuantizerCelebi.quantize(pixels, 128)).firstOrNull()
        if (seed == null) return@withContext CoverColors.Fallback
        val scheme = SchemeContent(Hct.fromInt(seed), true, 0.0)
        CoverColors(
            surface = Color(scheme.surface),
            surfaceContainer = Color(scheme.surfaceContainer),
            onSurfaceVariant = Color(scheme.onSurfaceVariant),
            primary = Color(scheme.primary),
            onPrimary = Color(scheme.onPrimary),
            primaryContainer = Color(scheme.primaryContainer),
            onPrimaryContainer = Color(scheme.onPrimaryContainer),
            secondary = Color(scheme.secondary),
            onSecondary = Color(scheme.onSecondary),
        )
    }

/** A grey as bright as a cover with no clear colour. */
internal fun neutralField(brightness: Float): Color {
    val grey = brightness.coerceIn(0.14f, 0.82f)
    return Color(grey, grey, grey)
}
