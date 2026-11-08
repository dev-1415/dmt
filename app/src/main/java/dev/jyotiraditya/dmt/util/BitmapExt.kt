package dev.jyotiraditya.dmt.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.scale
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
    val sample = software().scale(SAMPLE_PX, SAMPLE_PX)
    val width = sample.width
    val height = sample.height
    val pixels = IntArray(width * height)
    sample.getPixels(pixels, 0, width, 0, 0, width, height)

    val usable = ArrayList<Int>(pixels.size)
    val hsv = FloatArray(3)
    var brightness = 0f
    for (pixel in pixels) {
        if (PlatformColor.alpha(pixel) < MIN_ALPHA) continue
        PlatformColor.colorToHSV(pixel, hsv)
        brightness += hsv[2]
        if (hsv[2] < MIN_VALUE || hsv[2] > MAX_VALUE) continue
        // Near-white pixels are usually paper or sky, not the cover's colour.
        if (hsv[1] < MIN_SATURATION && hsv[2] > 0.9f) continue
        usable += pixel
    }
    if (usable.size < CLUSTERS) return@withContext neutralField(brightness / pixels.size)

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
        }
    }
    if (bestScore < 0f) return@withContext neutralField(brightness / pixels.size)

    PlatformColor.colorToHSV(bestColor, hsv)
    hsv[1] = hsv[1].coerceIn(0.38f, 0.62f)
    hsv[2] = hsv[2].coerceIn(0.38f, 0.58f)
    Color(PlatformColor.HSVToColor(hsv))
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
