package dev.jyotiraditya.dmt.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

// A dark-only palette; surfaces are told apart by tone, never by borders or shadows.

val Ground = Color(0xFF111111)

/** A raised surface: fields and selected rows. */
val Surface = Color(0xFF1C1C1C)

/** A surface placed on [Surface]. */
val Surface2 = Color(0xFF2A2A2A)

val Ink = Color(0xFFF2F2F2)

val Muted = Color(0xFF9C9C9C)

val Dark = Color(0xFF0A0A0A)

val Mint = Color(0xFFC3EDCE)

val Pink = Color(0xFFF8C7C9)

val Green = Color(0xFF1F6B3A)

val GreenInk = Color(0xFFFFFFFF)

val Night = Color(0xFF000000)

val NightInk = Color(0xFFF2F2F2)

/** A dark neutral the cover tint is blended into, so no cover turns the UI olive or neon. */
private val TintBase = Color(0xFF0E1114)

/** This colour at a dark, muted tint that keeps light text readable. */
fun Color.tintedDark(lightness: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = (hsl[1] * 0.72f).coerceIn(0f, 1f)
    hsl[2] = lightness
    return lerp(TintBase, Color(ColorUtils.HSLToColor(hsl)), 0.78f)
}

/** The player background: a deep, rich tint of the cover colour. */
fun Color.darkenedForPlayer(): Color = tintedDark(0.12f)

/** The lighter top of the player gradient. */
fun Color.darkenedForPlayerTop(): Color = tintedDark(0.20f)

val Danger = Color(0xFFFF8A80)

/** The fill of the row at this position in a list, alternating [Surface] and [Surface2]. */
val Int.rowFill: Color
    get() = if (this % 2 == 0) Surface else Surface2

/** The text colour with the most contrast against this background. */
fun Color.inkOn(): Color =
    if ((red * 0.2126f + green * 0.7152f + blue * 0.0722f) > 0.42f) Dark else Ink
