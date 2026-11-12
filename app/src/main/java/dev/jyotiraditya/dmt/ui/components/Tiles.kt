package dev.jyotiraditya.dmt.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jyotiraditya.dmt.domain.model.Folder
import dev.jyotiraditya.dmt.domain.model.Track
import dev.jyotiraditya.dmt.ui.theme.Ink
import dev.jyotiraditya.dmt.ui.theme.Muted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CaptionGap = 10.dp

/** A square cover with a title and subtitle beneath; [coverModifier] applies to the cover only. */
@Composable
fun CoverTile(
    cover: Track?,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleLines: Int = 1,
    onLongClick: (() -> Unit)? = null,
    coverModifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier.pressable(
                onClick = onClick,
                onLongClick = onLongClick,
                label = title,
            ),
    ) {
        Artwork(
            track = cover,
            modifier = coverModifier.fillMaxWidth().aspectRatio(1f),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = Ink,
            maxLines = titleLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = CaptionGap),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A collage of the first distinct album covers in [tracks]. */
@Composable
fun TrackMosaic(
    tracks: List<Track>,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    rows: Int = columns,
    background: Color = Color.Transparent,
    key: Any = tracks,
) {
    // A folder can hold thousands of tracks; finding its distinct covers must not run on the
    // main thread while a tab is sliding in.
    val initial = remember(key, columns, rows) { tracks.take(columns * rows) }
    val covers by
        produceState(initialValue = initial, key, columns, rows) {
            value =
                withContext(Dispatchers.Default) {
                    tracks
                        .distinctBy { it.album.ifBlank { it.coverUri?.toString().orEmpty() } }
                        .take(columns * rows)
                }
        }
    Column(modifier = modifier.background(background)) {
        repeat(rows) { row ->
            Row(modifier = Modifier.weight(1f)) {
                repeat(columns) { column ->
                    val track = covers.getOrNull(row * columns + column)
                    if (track != null) {
                        Artwork(
                            track = track,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    } else {
                        Box(
                            modifier =
                                Modifier.weight(1f)
                                    .fillMaxHeight()
                                    .background(background),
                        )
                    }
                }
            }
        }
    }
}

/** A square collage of the first album covers in [folder]. */
@Composable
fun FolderCover(
    folder: Folder,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    rows: Int = columns,
    background: Color = Color.Transparent,
) {
    TrackMosaic(
        tracks = folder.allTracks,
        modifier = modifier,
        columns = columns,
        rows = rows,
        background = background,
        key = folder,
    )
}
