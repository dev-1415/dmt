package dev.jyotiraditya.dmt.presentation.nowplaying

import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.ui.components.FlatSlider
import dev.jyotiraditya.dmt.ui.theme.Ink
import dev.jyotiraditya.dmt.ui.theme.Muted
import dev.jyotiraditya.dmt.ui.theme.Surface
import kotlin.math.roundToInt

/** A compact device-volume slider just above the volume button. */
@Composable
internal fun VolumePopup(anchor: LayoutCoordinates, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val audioManager =
        remember(context) {
            checkNotNull(context.getSystemService(AudioManager::class.java))
        }
    val max = remember(audioManager) {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    var index by remember(audioManager) {
        mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }
    DisposableEffect(context, audioManager) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    index = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                }
            }
        // The music setting is per output device on newer versions, so watch all settings.
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val gapPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    val volume = index.toFloat() / max
    Popup(
        popupPositionProvider = remember(anchor, gapPx) { AboveAnchorPositionProvider(anchor, gapPx) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier =
                Modifier.width(240.dp)
                    .background(Surface)
                    .border(1.dp, Muted.copy(alpha = 0.4f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.player_volume),
                    style = MaterialTheme.typography.labelLarge,
                    color = Ink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${(volume * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = Muted,
                )
            }
            FlatSlider(
                value = volume,
                onValueChange = { value ->
                    val next = (value * max).roundToInt().coerceIn(0, max)
                    if (next != index) {
                        index = next
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                    }
                },
                range = 0f..1f,
                label = stringResource(R.string.player_volume),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

/** Places the popup above [anchor], centred on it and kept inside the window. */
private class AboveAnchorPositionProvider(
    private val anchor: LayoutCoordinates,
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val bounds = anchor.boundsInWindow()
        val x =
            (bounds.center.x - popupContentSize.width / 2f)
                .roundToInt()
                .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y =
            (bounds.top - popupContentSize.height - gapPx)
                .roundToInt()
                .coerceAtLeast(0)
        return IntOffset(x, y)
    }
}
