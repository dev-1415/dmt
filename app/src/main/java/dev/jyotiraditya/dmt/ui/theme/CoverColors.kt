package dev.jyotiraditya.dmt.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The Material You colours generated from the current cover, as Gramophone uses them: [surface]
 * for the page, [primary] for accents and the live lyric, [primaryContainer] for the play button.
 */
@Immutable
data class CoverColors(
    val surface: Color,
    val surfaceContainer: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
) {
    companion object {
        val Fallback =
            CoverColors(
                surface = Ground,
                surfaceContainer = Surface,
                onSurfaceVariant = Muted,
                primary = GreenAccent.fill,
                onPrimary = GreenAccent.onFill,
                primaryContainer = Green,
                onPrimaryContainer = GreenInk,
                secondary = Ink,
                onSecondary = Ground,
            )
    }
}

/** The cover colours of the track playing now, or [CoverColors.Fallback]. */
val LocalCoverColors = staticCompositionLocalOf { CoverColors.Fallback }
