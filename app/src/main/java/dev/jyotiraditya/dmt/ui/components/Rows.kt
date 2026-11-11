package dev.jyotiraditya.dmt.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.domain.model.Folder
import dev.jyotiraditya.dmt.domain.model.Track
import dev.jyotiraditya.dmt.ui.icons.DmtIcons
import dev.jyotiraditya.dmt.ui.theme.Ink
import dev.jyotiraditya.dmt.ui.theme.LocalAccent
import dev.jyotiraditya.dmt.ui.theme.Margin
import dev.jyotiraditya.dmt.ui.theme.Muted
import dev.jyotiraditya.dmt.ui.theme.Numerals
import dev.jyotiraditya.dmt.ui.theme.Surface
import dev.jyotiraditya.dmt.ui.theme.Surface2

private val ROW_ART = 56.dp

/** A track with its cover or number, marking the current one with an accent box. */
@Composable
fun TrackRow(
    track: Track,
    meta: String,
    current: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    number: Int? = null,
    onLongClick: (() -> Unit)? = null,
    metaTrailing: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val mark = LocalAccent.current.highlight
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .background(if (current) mark.copy(alpha = 0.14f) else Color.Transparent)
                .pressable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    sink = 0.985f,
                    label = track.title,
                )
                .padding(horizontal = Margin, vertical = 8.dp),
    ) {
        if (number != null) {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.size(width = 36.dp, height = 44.dp),
            ) {
                Text(
                    text = "$number",
                    style = Numerals,
                    color = if (current) mark else Muted,
                )
            }
        } else {
            Artwork(track = track, modifier = Modifier.size(ROW_ART))
            Spacer(modifier = Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (current) mark else Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotEmpty() || metaTrailing != null || track.cue) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (track.cue) {
                        Tag(
                            text = stringResource(R.string.library_cue_tag),
                            color = Muted,
                            field = Surface2,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                if (metaTrailing != null) {
                                    Modifier.weight(1f)
                                } else {
                                    Modifier
                                },
                        )
                    }
                    if (metaTrailing != null) {
                        if (meta.isNotEmpty()) {
                            Text(
                                text = " · ",
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted,
                                maxLines = 1,
                            )
                        }
                        Text(
                            text = metaTrailing,
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        trailing?.invoke()
    }
}

/** An artist, genre, folder or playlist: its name and one detail line. */
@Composable
fun CollectionRow(
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .pressable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    sink = 0.985f,
                    label = title,
                )
                .padding(horizontal = Margin, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (detail.isNotEmpty()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** A folder with a collage of its album covers, its name and one detail line. */
@Composable
fun FolderRow(
    folder: Folder,
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .pressable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    sink = 0.985f,
                    label = title,
                )
                .padding(horizontal = Margin, vertical = 12.dp),
    ) {
        FolderCover(folder = folder, modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
fun SearchBox(
    query: String,
    hint: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    height: Dp = 48.dp,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .background(Surface)
                .border(1.dp, Muted.copy(alpha = 0.5f))
                .padding(start = 16.dp, end = 4.dp),
    ) {
        DmtIcon(icon = DmtIcons.Search, tint = Muted, size = 22.dp)
        Spacer(modifier = Modifier.width(12.dp))
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
            cursorBrush = SolidColor(Ink),
            modifier =
                Modifier.weight(1f)
                    .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(text = hint, style = MaterialTheme.typography.bodyLarge, color = Muted)
                }
                inner()
            },
        )
        if (query.isNotEmpty()) {
            IconTap(
                icon = DmtIcons.Close,
                description = stringResource(R.string.search_clear),
                onClick = { onQuery("") },
                iconSize = 20.dp,
            )
        }
    }
}
