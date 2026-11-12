package dev.jyotiraditya.dmt.presentation.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.domain.model.Playlist
import dev.jyotiraditya.dmt.domain.model.Track
import dev.jyotiraditya.dmt.presentation.main.LocalContentBottom
import dev.jyotiraditya.dmt.presentation.nowplaying.rememberWantsPlayback
import dev.jyotiraditya.dmt.presentation.player.DmtAction
import dev.jyotiraditya.dmt.presentation.player.DmtState
import dev.jyotiraditya.dmt.ui.components.Artwork
import dev.jyotiraditya.dmt.ui.components.Block
import dev.jyotiraditya.dmt.ui.components.CollectionRow
import dev.jyotiraditya.dmt.ui.components.DmtIcon
import dev.jyotiraditya.dmt.ui.components.DmtSheet
import dev.jyotiraditya.dmt.ui.components.IconTap
import dev.jyotiraditya.dmt.ui.components.SheetAction
import dev.jyotiraditya.dmt.ui.components.SheetHeader
import dev.jyotiraditya.dmt.ui.components.TextLink
import dev.jyotiraditya.dmt.ui.components.TrackMosaic
import dev.jyotiraditya.dmt.ui.components.TrackRow
import dev.jyotiraditya.dmt.ui.components.pressable
import dev.jyotiraditya.dmt.ui.icons.DmtIcons
import dev.jyotiraditya.dmt.ui.theme.Ink
import dev.jyotiraditya.dmt.ui.theme.Margin
import dev.jyotiraditya.dmt.ui.theme.Muted
import dev.jyotiraditya.dmt.ui.theme.Surface

/** The playlist shelf: a button to make one, then every saved playlist. */
internal fun LazyListScope.playlists(state: DmtState, dispatch: (DmtAction) -> Unit) {
    item(key = "actions") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Margin, vertical = 8.dp),
        ) {
            NewPlaylist(dispatch, Modifier.weight(1f))
            ImportPlaylist(dispatch, Modifier.weight(1f))
        }
    }
    if (state.playlists.isEmpty()) {
        item {
            Text(
                text = stringResource(R.string.playlist_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = Ink,
                modifier = Modifier.padding(horizontal = Margin, vertical = 12.dp),
            )
        }
    }
    items(state.playlists, key = { it.name }) { playlist ->
        PlaylistLine(playlist, dispatch)
    }
}

@Composable
private fun NewPlaylist(dispatch: (DmtAction) -> Unit, modifier: Modifier = Modifier) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        CreateSheet(
            onCreate = { dispatch(DmtAction.CreatePlaylist(it)) },
            onDismiss = { creating = false },
        )
    }
    Block(
        label = stringResource(R.string.playlist_new_title),
        icon = DmtIcons.Plus,
        fill = Ink,
        ink = Surface,
        onClick = { creating = true },
        modifier = modifier,
    )
}

@Composable
private fun ImportPlaylist(dispatch: (DmtAction) -> Unit, modifier: Modifier = Modifier) {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) dispatch(DmtAction.ImportPlaylist(uri))
        }
    Block(
        label = stringResource(R.string.playlist_import),
        icon = DmtIcons.Folder,
        fill = Surface,
        ink = Ink,
        onClick = {
            launcher.launch(
                arrayOf(
                    "audio/x-mpegurl",
                    "audio/mpegurl",
                    "application/vnd.apple.mpegurl",
                    "text/plain",
                    "*/*",
                ),
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun PlaylistLine(playlist: Playlist, dispatch: (DmtAction) -> Unit) {
    var acting by remember { mutableStateOf(false) }
    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("audio/x-mpegurl"),
        ) { uri ->
            if (uri != null) dispatch(DmtAction.ExportPlaylist(playlist.name, uri))
        }
    if (acting) {
        ActionSheet(
            title = playlist.name,
            tracks = playlist.tracks,
            dispatch = dispatch,
            onDismiss = { acting = false },
            onDelete = { dispatch(DmtAction.DeletePlaylist(playlist.name)) },
            onExport = { exportLauncher.launch("${playlist.name}.m3u8") },
        )
    }
    PlaylistRow(
        playlist = playlist,
        onClick = { dispatch(DmtAction.OpenPlaylist(playlist.name)) },
        onLongClick = { acting = true },
    )
}

/** A playlist with a mosaic of its album covers when it spans more than one album. */
@Composable
private fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val albums =
        remember(playlist.tracks) {
            playlist.tracks.distinctBy { it.album.ifBlank { it.coverUri?.toString().orEmpty() } }
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.fillMaxWidth()
                .pressable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    sink = 0.985f,
                    label = playlist.name,
                )
                .padding(horizontal = Margin, vertical = 12.dp),
    ) {
        if (albums.size > 1) {
            TrackMosaic(
                tracks = playlist.tracks,
                columns = 2,
                rows = 2,
                modifier = Modifier.size(56.dp),
                key = playlist.name,
            )
        } else {
            Artwork(
                track = playlist.tracks.firstOrNull(),
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = tracksSummary(playlist.tracks),
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlaylistDetail(playlist: Playlist, state: DmtState, dispatch: (DmtAction) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        PickerSheet(playlist, state, dispatch) { picking = false }
    }
    val playing = rememberWantsPlayback()
    val albums =
        remember(playlist.tracks) {
            playlist.tracks.distinctBy { it.album.ifBlank { it.coverUri?.toString().orEmpty() } }
        }
    LazyColumn(contentPadding = PaddingValues(bottom = LocalContentBottom.current)) {
        item {
            DetailHead(
                title = playlist.name,
                meta = stringResource(R.string.collection_playlist),
                tracks = playlist.tracks,
                mosaic = albums.size > 1,
            )
        }
        stickyHeader {
            DetailActions(
                title = playlist.name,
                tracks = playlist.tracks,
                dispatch = dispatch,
                action = { ink ->
                    TextLink(
                        label = stringResource(R.string.playlist_add),
                        color = ink,
                        onClick = { picking = true },
                    )
                },
            )
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        itemsIndexed(playlist.tracks, key = { _, track -> track.id }) { index, track ->
            TrackRow(
                track = track,
                meta = trackSummary(track, album = false),
                current = track.id.toString() == state.nowPlayingId,
                onClick = { dispatch(DmtAction.PlayAt(playlist.tracks, index)) },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlaylistMoveButton(
                            up = true,
                            enabled = index > 0,
                            onClick = {
                                dispatch(
                                    DmtAction.MoveInPlaylist(playlist.name, index, index - 1),
                                )
                            },
                        )
                        PlaylistMoveButton(
                            up = false,
                            enabled = index < playlist.tracks.lastIndex,
                            onClick = {
                                dispatch(
                                    DmtAction.MoveInPlaylist(playlist.name, index, index + 1),
                                )
                            },
                        )
                        IconTap(
                            icon = DmtIcons.Close,
                            description = stringResource(R.string.playlist_remove),
                            iconSize = 18.dp,
                            onClick = {
                                dispatch(
                                    DmtAction.RemoveFromPlaylist(
                                        playlist.name,
                                        track.path,
                                    ),
                                )
                            },
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun PlaylistMoveButton(up: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val description =
        stringResource(if (up) R.string.playlist_move_up else R.string.playlist_move_down)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.size(32.dp)
                .then(
                    if (enabled) {
                        Modifier.pressable(onClick = onClick, label = description)
                    } else {
                        Modifier
                    },
                ),
    ) {
        DmtIcon(
            icon = DmtIcons.ChevronDown,
            tint = if (enabled) Muted else Muted.copy(alpha = 0.3f),
            modifier = if (up) Modifier.rotate(180f) else Modifier,
            description = description,
        )
    }
}

/** Long-press actions for a collection of tracks. */
@Composable
internal fun ActionSheet(
    title: String,
    tracks: List<Track>,
    dispatch: (DmtAction) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
) {
    DmtSheet(onDismiss = onDismiss) {
        val act = { action: DmtAction ->
            dispatch(action)
            dismiss()
        }
        SheetHeader(title = title, meta = tracksSummary(tracks))
        if (tracks.isNotEmpty()) {
            SheetAction(
                DmtIcons.Play,
                stringResource(R.string.action_play),
                onClick = {
                    act(DmtAction.PlayAt(tracks, 0))
                },
            )
            SheetAction(
                DmtIcons.Shuffle,
                stringResource(R.string.player_shuffle),
                onClick = {
                    act(DmtAction.ShufflePlay(tracks))
                },
            )
            SheetAction(
                DmtIcons.PlayNext,
                stringResource(R.string.action_play_next),
                onClick = {
                    act(DmtAction.PlayNext(tracks, title))
                },
            )
            SheetAction(
                DmtIcons.AddToQueue,
                stringResource(R.string.action_add_to_queue),
                onClick = {
                    act(DmtAction.Enqueue(tracks, title))
                },
            )
        }
        if (onExport != null) {
            SheetAction(
                DmtIcons.ArrowOut,
                stringResource(R.string.playlist_export),
                onClick = {
                    onExport()
                    dismiss()
                },
            )
        }
        if (onDelete != null) {
            SheetAction(
                DmtIcons.Trash,
                stringResource(R.string.action_delete),
                onClick = {
                    onDelete()
                    dismiss()
                },
            )
        }
    }
}

@Composable
private fun CreateSheet(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    DmtSheet(onDismiss = onDismiss) {
        val submit = {
            if (name.isNotBlank()) {
                onCreate(name.trim())
                dismiss()
            }
        }
        SheetHeader(title = stringResource(R.string.playlist_new_title))
        BasicTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium.copy(color = Ink),
            cursorBrush = SolidColor(Ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = Margin)
                    .background(Surface)
                    .padding(horizontal = 16.dp, vertical = 18.dp),
            decorationBox = { inner ->
                if (name.isEmpty()) {
                    Text(
                        text = stringResource(R.string.playlist_name_hint),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Muted,
                    )
                }
                inner()
            },
        )
        Block(
            label = stringResource(R.string.playlist_create),
            onClick = submit,
            enabled = name.isNotBlank(),
            modifier = Modifier.padding(horizontal = Margin, vertical = 16.dp),
        )
    }
}

@Composable
private fun PickerSheet(
    playlist: Playlist,
    state: DmtState,
    dispatch: (DmtAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val candidates =
        remember(playlist, state.tracks) {
            val inPlaylist = playlist.tracks.mapTo(HashSet()) { it.path }
            state.tracks.filter { it.path.isNotEmpty() && it.path !in inPlaylist }
        }
    DmtSheet(onDismiss = onDismiss) {
        SheetHeader(
            title = stringResource(R.string.playlist_add_tracks_title),
            meta = playlist.name,
        )
        LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
            items(candidates, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    meta = trackSummary(track, album = false),
                    current = false,
                    onClick = { dispatch(DmtAction.AddToPlaylist(playlist.name, track)) },
                    trailing = {
                        IconTap(
                            icon = DmtIcons.Plus,
                            description = stringResource(R.string.playlist_add),
                            onClick = { dispatch(DmtAction.AddToPlaylist(playlist.name, track)) },
                        )
                    },
                )
            }
        }
    }
}
