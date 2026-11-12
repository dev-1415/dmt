package dev.jyotiraditya.dmt.util

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.scale
import androidx.palette.graphics.Palette
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.SchemeContent
import com.google.android.material.color.utilities.Score
import dev.jyotiraditya.dmt.ui.theme.CoverColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt
import android.graphics.Color as PlatformColor

private const val SAMPLE_PX = 40
private const val CLUSTERS = 6
private const val ITERATIONS = 8
private const val MIN_ALPHA = 128
private const val MIN_VALUE = 0.08f
private const val MAX_VALUE = 0.97f
private const val MIN_SATURATION = 0.08f

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

/**
 * The cover's dominant colour as a fill colour, or a grey when it has none.
 *
 * The pixels are grouped into a handful of colour clusters, then the cluster that is both
 * populous and colourful wins. This keeps a large dull background from deciding the page colour
 * while still avoiding a single stray pixel.
 */
suspend fun Bitmap.fieldColor(): Color = withContext(Dispatchers.Default) {
    // Palette is Android's standard album-art colour extractor; its swatches are tuned to be
    // pleasing, which is what Samsung's media player uses too.
    val palette = Palette.from(this@fieldColor).maximumColorCount(16).generate()
    val swatch =
        palette.vibrantSwatch
            ?: palette.darkVibrantSwatch
            ?: palette.mutedSwatch
            ?: palette.darkMutedSwatch
            ?: palette.dominantSwatch
    if (swatch != null) return@withContext Color(swatch.rgb)

    val sample = software().scale(SAMPLE_PX, SAMPLE_PX)
    val width = sample.width
    val height = sample.height
    val pixels = IntArray(width * height)
    sample.getPixels(pixels, 0, width, 0, 0, width, height)

    val usable = ArrayList<Int>(pixels.size)
    val hsv = FloatArray(3)
    var brightness = 0f
    var saturationSum = 0f
    for (pixel in pixels) {
        if (PlatformColor.alpha(pixel) < MIN_ALPHA) continue
        PlatformColor.colorToHSV(pixel, hsv)
        brightness += hsv[2]
        if (hsv[2] < MIN_VALUE || hsv[2] > MAX_VALUE) continue
        // Near-white pixels are usually paper or sky, not the cover's colour.
        if (hsv[1] < MIN_SATURATION && hsv[2] > 0.9f) continue
        usable += pixel
        saturationSum += hsv[1]
    }
    if (usable.size < CLUSTERS) return@withContext neutralField(brightness / pixels.size)
    // A black-and-white or paper cover can have a faint warm cast; keep it neutral.
    if (saturationSum / usable.size < 0.20f) {
        return@withContext neutralField(brightness / pixels.size)
    }

    val centroids = seedCentroids(usable, hsv)
    val sums = Array(CLUSTERS) { FloatArray(3) }
    val counts = IntArray(CLUSTERS)
    repeat(ITERATIONS) {
        sums.forEach { it.fill(0f) }
        counts.fill(0)
        for (pixel in usable) {
            val r = PlatformColor.red(pixel).toFloat()
            val g = PlatformColor.green(pixel).toFloat()
            val b = PlatformColor.blue(pixel).toFloat()
            var best = 0
            var bestDistance = Float.MAX_VALUE
            for (k in 0 until CLUSTERS) {
                val dr = r - centroids[k][0]
                val dg = g - centroids[k][1]
                val db = b - centroids[k][2]
                val distance = dr * dr + dg * dg + db * db
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = k
                }
            }
            sums[best][0] += r
            sums[best][1] += g
            sums[best][2] += b
            counts[best]++
        }
        for (k in 0 until CLUSTERS) {
            val count = counts[k]
            if (count == 0) continue
            centroids[k][0] = sums[k][0] / count
            centroids[k][1] = sums[k][1] / count
            centroids[k][2] = sums[k][2] / count
        }
    }

    var bestScore = -1f
    var bestColor = 0
    var bestPopulation = 0f
    for (k in 0 until CLUSTERS) {
        if (counts[k] == 0) continue
        val color =
            PlatformColor.rgb(
                centroids[k][0].toInt().coerceIn(0, 255),
                centroids[k][1].toInt().coerceIn(0, 255),
                centroids[k][2].toInt().coerceIn(0, 255),
            )
        PlatformColor.colorToHSV(color, hsv)
        val chroma = hsv[1]
        // A grey cluster can be large, but it should never decide the page colour.
        if (chroma < 0.12f) continue
        val population = counts[k].toFloat() / usable.size
        val value = hsv[2]
        // Favour colourful, mid-toned clusters; a large dull area should not win.
        val tone = (1f - abs(value - 0.55f) * 1.1f).coerceIn(0.2f, 1f)
        val score = sqrt(population) * (0.2f + chroma) * tone
        if (score > bestScore) {
            bestScore = score
            bestColor = color
            bestPopulation = population
        }
    }
    if (bestScore < 0f) return@withContext neutralField(brightness / pixels.size)

    PlatformColor.colorToHSV(bestColor, hsv)
    // The border is usually the cover's background, which is the colour the page should take.
    val hue = borderHue(pixels, width, height) ?: hsv[0]
    presetTint(hue, hsv[1], bestPopulation)
}

/** The dominant hue along the cover's border, or null when the border is mostly neutral. */
private fun borderHue(pixels: IntArray, width: Int, height: Int): Float? {
    val bins = FloatArray(12)
    val hueSums = FloatArray(12)
    val hsv = FloatArray(3)
    var total = 0f
    var counted = 0

    fun add(pixel: Int) {
        if (PlatformColor.alpha(pixel) < MIN_ALPHA) return
        PlatformColor.colorToHSV(pixel, hsv)
        if (hsv[2] < MIN_VALUE || hsv[2] > MAX_VALUE) return
        // A faint colour cast on a white or grey border is not a real cover colour.
        if (hsv[1] < 0.25f) return
        val bin = (hsv[0] / 30f).toInt() % 12
        val weight = hsv[1] * hsv[2]
        bins[bin] += weight
        hueSums[bin] += hsv[0] * weight
        total += weight
        counted++
    }

    for (x in 0 until width) {
        add(pixels[x])
        add(pixels[(height - 1) * width + x])
    }
    for (y in 1 until height - 1) {
        add(pixels[y * width])
        add(pixels[y * width + width - 1])
    }
    if (counted == 0 || total < 0.5f) return null
    var best = 0
    for (i in 1 until bins.size) if (bins[i] > bins[best]) best = i
    if (bins[best] / total < 0.35f) return null
    return hueSums[best] / bins[best]
}

/**
 * Hues that keep the page tint varied but always readable. The warm range is deliberately dense:
 * a beige or amber cover must not snap to yellow, which would turn the page olive.
 */
private val PresetHues =
    floatArrayOf(
        0f,
        340f,
        320f,
        300f,
        280f,
        260f,
        240f,
        220f,
        200f,
        185f,
        170f,
        155f,
        140f,
        120f,
        65f,
        60f,
        55f,
        45f,
        35f,
        30f,
        20f,
    )

/** The preset tint nearest [hue], or a neutral when the cover has little colour. */
private fun presetTint(hue: Float, chroma: Float, population: Float): Color {
    if (chroma < 0.12f || population < 0.08f) return neutralField(0.5f)
    val nearest = PresetHues.minBy { angularDistance(hue, it) }
    // Greens and yellows need more saturation so they read as their hue, not olive.
    val saturation =
        when {
            nearest in 40f..70f -> 0.65f
            nearest in 100f..160f -> 0.68f
            else -> 0.55f
        }
    return Color.hsv(nearest, saturation, 0.55f)
}

/** The shortest distance between two hues, in degrees. */
private fun angularDistance(a: Float, b: Float): Float {
    val d = abs(a - b) % 360f
    return minOf(d, 360f - d)
}

/** K-means++ seeds: the most colourful pixel, then the pixels furthest from the seeds so far. */
private fun seedCentroids(pixels: List<Int>, hsv: FloatArray): Array<FloatArray> {
    val centroids = Array(CLUSTERS) { FloatArray(3) }
    var first = 0
    var best = -1f
    for (i in pixels.indices) {
        PlatformColor.colorToHSV(pixels[i], hsv)
        val score = hsv[1] * hsv[2]
        if (score > best) {
            best = score
            first = i
        }
    }
    centroids[0] = rgb(pixels[first])
    for (k in 1 until CLUSTERS) {
        var farthest = 0
        var farthestDistance = -1f
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = PlatformColor.red(pixel).toFloat()
            val g = PlatformColor.green(pixel).toFloat()
            val b = PlatformColor.blue(pixel).toFloat()
            var nearest = Float.MAX_VALUE
            for (j in 0 until k) {
                val dr = r - centroids[j][0]
                val dg = g - centroids[j][1]
                val db = b - centroids[j][2]
                val distance = dr * dr + dg * dg + db * db
                if (distance < nearest) nearest = distance
            }
            if (nearest > farthestDistance) {
                farthestDistance = nearest
                farthest = i
            }
        }
        centroids[k] = rgb(pixels[farthest])
    }
    return centroids
}

private fun rgb(pixel: Int): FloatArray =
    floatArrayOf(
        PlatformColor.red(pixel).toFloat(),
        PlatformColor.green(pixel).toFloat(),
        PlatformColor.blue(pixel).toFloat(),
    )
