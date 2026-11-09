package dev.jyotiraditya.dmt.presentation.nowplaying

import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.ui.components.DmtSheet
import dev.jyotiraditya.dmt.ui.components.FlatSlider
import dev.jyotiraditya.dmt.ui.components.SheetHeader
import dev.jyotiraditya.dmt.ui.theme.Margin
import kotlin.math.roundToInt

/** The music stream's system setting, watched so the slider follows the hardware volume keys. */
private const val MUSIC_VOLUME_SETTING = "volume_music"

/** Sets the device's media volume with a slider. */
@Composable
internal fun VolumeSheet(onDismiss: () -> Unit) {
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
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(MUSIC_VOLUME_SETTING),
            false,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val volume = index.toFloat() / max
    DmtSheet(onDismiss = onDismiss) {
        SheetHeader(
            title = stringResource(R.string.player_volume),
            meta = "${(volume * 100).roundToInt()}%",
        )
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
            modifier = Modifier.padding(horizontal = Margin, vertical = 12.dp),
        )
    }
}
