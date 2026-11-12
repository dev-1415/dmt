package dev.jyotiraditya.dmt.presentation.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.jyotiraditya.dmt.BuildConfig
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.domain.model.DmtSettings
import dev.jyotiraditya.dmt.domain.model.Folder
import dev.jyotiraditya.dmt.domain.model.LibraryTab
import dev.jyotiraditya.dmt.domain.model.SourceMode
import dev.jyotiraditya.dmt.presentation.library.BackLink
import dev.jyotiraditya.dmt.presentation.library.folderTitle
import dev.jyotiraditya.dmt.presentation.main.LocalContentBottom
import dev.jyotiraditya.dmt.presentation.player.DmtAction
import dev.jyotiraditya.dmt.presentation.player.DmtState
import dev.jyotiraditya.dmt.presentation.player.DmtView
import dev.jyotiraditya.dmt.ui.components.ChoiceRow
import dev.jyotiraditya.dmt.ui.components.DmtIcon
import dev.jyotiraditya.dmt.ui.components.DmtSheet
import dev.jyotiraditya.dmt.ui.components.PageHead
import dev.jyotiraditya.dmt.ui.components.ScrollMemory
import dev.jyotiraditya.dmt.ui.components.SheetHeader
import dev.jyotiraditya.dmt.ui.components.Toggle
import dev.jyotiraditya.dmt.ui.components.pressable
import dev.jyotiraditya.dmt.ui.components.pressableToggle
import dev.jyotiraditya.dmt.ui.icons.DmtIcons
import dev.jyotiraditya.dmt.ui.motion.Depth
import dev.jyotiraditya.dmt.ui.theme.Ink
import dev.jyotiraditya.dmt.ui.theme.Margin
import dev.jyotiraditya.dmt.ui.theme.Muted

/** The way back out of a page inside Settings, or null at its top. */
@Composable
fun settingsBack(state: DmtState): BackLink? = when (state.view) {
    DmtView.SOURCE_KINDS ->
        BackLink(
            stringResource(R.string.sources_title),
            listOf(DmtAction.Show(DmtView.SOURCES)),
        )

    DmtView.SOURCE_FORM -> {
        val backTo = state.sourceForm.backTo
        BackLink(
            stringResource(
                if (backTo == DmtView.SOURCE_KINDS) {
                    R.string.sources_add
                } else {
                    R.string.sources_title
                },
            ),
            listOf(DmtAction.Show(backTo)),
        )
    }

    DmtView.SOURCES,
    DmtView.PERMISSIONS,
    DmtView.LIBRARY_FOLDERS,
    DmtView.LIBRARY_TABS,
    DmtView.LICENSES,
    ->
        BackLink(
            stringResource(R.string.nav_settings),
            listOf(DmtAction.Show(DmtView.SETTINGS)),
        )

    else -> null
}

private val SETTINGS_PAGES =
    setOf(
        DmtView.SOURCES,
        DmtView.SOURCE_KINDS,
        DmtView.SOURCE_FORM,
        DmtView.PERMISSIONS,
        DmtView.LIBRARY_FOLDERS,
        DmtView.LIBRARY_TABS,
        DmtView.LICENSES,
    )

/** Settings and the pages inside it; [cover] is the colour of the cover playing now. */
@Composable
fun SettingsPage(state: DmtState, cover: Color, dispatch: (DmtAction) -> Unit) {
    val page = state.view.takeIf { it in SETTINGS_PAGES } ?: DmtView.SETTINGS
    Depth(
        target = page,
        keyOf = { it },
        depthOf = {
            when (it) {
                DmtView.SOURCE_KINDS -> 2
                DmtView.SOURCE_FORM -> 3
                DmtView.SETTINGS -> 0
                else -> 1
            }
        },
    ) { shown ->
        when (shown) {
            DmtView.SOURCES -> ScrollMemory("sources") { SourcesPage(state, dispatch) }

            DmtView.SOURCE_KINDS -> ScrollMemory("kinds") { SourceKindsPage(dispatch) }

            DmtView.SOURCE_FORM -> SourceFormPage(state, dispatch)

            DmtView.PERMISSIONS -> ScrollMemory("perms") { PermissionsPage(state, dispatch) }

            DmtView.LIBRARY_FOLDERS ->
                ScrollMemory("folders") {
                    LibraryFoldersPage(state, dispatch)
                }

            DmtView.LIBRARY_TABS ->
                ScrollMemory("library-tabs") {
                    LibraryTabsPage(state, dispatch)
                }

            DmtView.LICENSES -> ScrollMemory("licenses") { LicensesPage() }

            else -> ScrollMemory("settings") { SettingsHome(state, cover, dispatch) }
        }
    }
}

@Composable
private fun SettingsHome(state: DmtState, cover: Color, dispatch: (DmtAction) -> Unit) {
    val settings = state.settings
    fun save(next: DmtSettings) = dispatch(DmtAction.Config(next))
    var pickingAccent by remember { mutableStateOf(false) }
    var pickingStartFolder by remember { mutableStateOf(false) }
    val openEqualizer = rememberEqualizerLauncher()

    if (pickingAccent) {
        AccentSheet(
            current = settings.accent,
            cover = cover,
            onPick = { save(settings.copy(accent = it)) },
            onDismiss = { pickingAccent = false },
        )
    }
    if (pickingStartFolder) {
        StartFolderSheet(
            roots = state.library.folderRoots,
            selected = settings.startFolder,
            onPick = { path -> save(settings.copy(startFolder = path)) },
            onDismiss = { pickingStartFolder = false },
        )
    }

    LazyColumn(contentPadding = PaddingValues(bottom = LocalContentBottom.current)) {
        item { GroupHeading(stringResource(R.string.settings_section_library)) }
        item {
            LinkRow(
                label = stringResource(R.string.sources_title),
                value = settings.server?.name ?: stringResource(R.string.source_kind_local),
                onClick = { dispatch(DmtAction.Show(DmtView.SOURCES)) },
            )
        }
        item {
            RescanRow(
                scanning = state.scanning,
                last = state.lastScan,
                onRescan = { dispatch(DmtAction.Rescan) },
            )
        }
        item {
            RepairIndexRow(
                scanning = state.scanning,
                onRepair = { dispatch(DmtAction.RepairIndex) },
            )
        }
        item {
            LinkRow(
                label = stringResource(R.string.permissions_title),
                value = null,
                onClick = { dispatch(DmtAction.Show(DmtView.PERMISSIONS)) },
            )
        }
        if (settings.sourceMode == SourceMode.LOCAL) {
            item {
                val hidden = settings.hiddenFolders.size
                LinkRow(
                    label = stringResource(R.string.folders_title),
                    value =
                        if (hidden == 0) {
                            stringResource(R.string.folders_all_shown)
                        } else {
                            pluralStringResource(R.plurals.folders_hidden_count, hidden, hidden)
                        },
                    onClick = { dispatch(DmtAction.Show(DmtView.LIBRARY_FOLDERS)) },
                )
            }
            item {
                val startFolder = settings.startFolder
                val value =
                    if (startFolder == null) {
                        stringResource(R.string.settings_start_folder_none)
                    } else {
                        state.library.folder(startFolder)?.let {
                            it.relativePath.ifEmpty { folderTitle(it) }
                        } ?: startFolder.substringAfterLast('/')
                    }
                LinkRow(
                    label = stringResource(R.string.settings_start_folder),
                    value = value,
                    onClick = { pickingStartFolder = true },
                )
            }
        }
        item {
            val visible = settings.visibleLibraryTabs.size
            LinkRow(
                label = stringResource(R.string.settings_library_tabs),
                value =
                    pluralStringResource(
                        R.plurals.settings_library_tabs_count,
                        visible,
                        visible,
                    ),
                onClick = { dispatch(DmtAction.Show(DmtView.LIBRARY_TABS)) },
            )
        }

        item { GroupHeading(stringResource(R.string.settings_section_playback)) }
        item {
            ToggleRow(
                label = stringResource(R.string.settings_volume_levelling),
                hint = stringResource(R.string.settings_volume_levelling_hint),
                checked = settings.normalizeVolume,
                onToggle = { save(settings.copy(normalizeVolume = !settings.normalizeVolume)) },
            )
        }
        item {
            ToggleRow(
                label = stringResource(R.string.settings_stop_on_dismiss),
                hint = stringResource(R.string.settings_stop_on_dismiss_hint),
                checked = settings.stopOnDismiss,
                onToggle = { save(settings.copy(stopOnDismiss = !settings.stopOnDismiss)) },
            )
        }
        if (openEqualizer != null) {
            item {
                LinkRow(
                    label = stringResource(R.string.settings_equalizer),
                    value = null,
                    onClick = openEqualizer,
                    icon = DmtIcons.ArrowOut,
                )
            }
        }

        item { GroupHeading(stringResource(R.string.settings_section_look)) }
        item {
            LinkRow(
                label = stringResource(R.string.settings_accent),
                value = stringResource(settings.accent.title),
                onClick = { pickingAccent = true },
            )
        }
        item {
            ToggleRow(
                label = stringResource(R.string.settings_animated_art),
                hint = stringResource(R.string.settings_animated_art_hint),
                checked = settings.animatedArt,
                onToggle = { save(settings.copy(animatedArt = !settings.animatedArt)) },
                info = stringResource(R.string.settings_animated_art_info),
            )
        }
        item {
            ToggleRow(
                label = stringResource(R.string.settings_romanized_lyrics),
                hint = stringResource(R.string.settings_romanized_lyrics_hint),
                checked = settings.romanizedLyrics,
                onToggle = { save(settings.copy(romanizedLyrics = !settings.romanizedLyrics)) },
            )
        }
        item {
            ToggleRow(
                label = stringResource(R.string.settings_singer_colors),
                hint = stringResource(R.string.settings_singer_colors_hint),
                checked = settings.singerColors,
                onToggle = { save(settings.copy(singerColors = !settings.singerColors)) },
            )
        }

        item { About(dispatch) }
    }
}

/** The app's version, who made it, and the open-source licenses. */
@Composable
private fun About(dispatch: (DmtAction) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val creditUrl = stringResource(R.string.about_credit_url)
    val originalCreditUrl = stringResource(R.string.about_original_credit_url)
    GroupHeading(stringResource(R.string.settings_section_about))
    InfoRow(label = stringResource(R.string.about_version), value = BuildConfig.VERSION_NAME)
    LinkRow(
        label = stringResource(R.string.about_developer),
        value = stringResource(R.string.about_credit_name),
        onClick = { runCatching { uriHandler.openUri(creditUrl) } },
        icon = DmtIcons.ArrowOut,
    )
    LinkRow(
        label = stringResource(R.string.about_original_code),
        value = stringResource(R.string.about_original_credit_name),
        onClick = { runCatching { uriHandler.openUri(originalCreditUrl) } },
        icon = DmtIcons.ArrowOut,
    )
    LinkRow(
        label = stringResource(R.string.licenses_title),
        value = null,
        onClick = { dispatch(DmtAction.Show(DmtView.LICENSES)) },
    )
}

/** Picks the folder the Folders tab opens at, with the tree indented by depth. */
@Composable
private fun StartFolderSheet(
    roots: List<Folder>,
    selected: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val folders = remember(roots) { roots.flattenForest() }
    DmtSheet(onDismiss = onDismiss) {
        SheetHeader(
            title = stringResource(R.string.settings_start_folder),
            meta = stringResource(R.string.settings_start_folder_hint),
        )
        LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
            item(key = "none") {
                ChoiceRow(
                    label = stringResource(R.string.settings_start_folder_none),
                    selected = selected == null,
                    onClick = {
                        onPick(null)
                        dismiss()
                    },
                )
            }
            if (folders.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.settings_start_folder_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Muted,
                        modifier = Modifier.padding(horizontal = Margin, vertical = 12.dp),
                    )
                }
            }
            items(folders, key = { it.first.path }) { (folder, depth) ->
                ChoiceRow(
                    label = folderTitle(folder),
                    detail = folder.relativePath.ifEmpty { null },
                    selected = folder.path == selected,
                    onClick = {
                        onPick(folder.path)
                        dismiss()
                    },
                    modifier = Modifier.padding(start = (depth * 16).dp),
                )
            }
        }
    }
}

private fun List<Folder>.flattenForest(): List<Pair<Folder, Int>> = flatMap { it.flatten(0) }

private fun Folder.flatten(depth: Int): List<Pair<Folder, Int>> =
    listOf(this to depth) + children.flatMap { it.flatten(depth + 1) }

/** Chooses which library sections appear as top-level tabs. */
@Composable
private fun LibraryTabsPage(state: DmtState, dispatch: (DmtAction) -> Unit) {
    val settings = state.settings
    LazyColumn(contentPadding = PaddingValues(bottom = LocalContentBottom.current)) {
        item {
            PageHead(
                title = stringResource(R.string.settings_library_tabs),
                modifier = Modifier.padding(horizontal = Margin),
            )
            Text(
                text = stringResource(R.string.settings_library_tabs_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = Ink,
                modifier = Modifier.padding(start = Margin, end = Margin, bottom = 12.dp),
            )
        }
        val order = settings.libraryTabOrder
        itemsIndexed(order, key = { _, tab -> tab.name }) { index, tab ->
            LibraryTabRow(
                tab = tab,
                checked = tab in settings.visibleLibraryTabs,
                canUp = index > 0,
                canDown = index < order.lastIndex,
                onToggle = {
                    val visible = settings.visibleLibraryTabs
                    val next = if (tab in visible) visible - tab else visible + tab
                    dispatch(DmtAction.Config(settings.copy(visibleLibraryTabs = next)))
                },
                onUp = {
                    val next = order.toMutableList().apply { add(index - 1, removeAt(index)) }
                    dispatch(DmtAction.Config(settings.copy(libraryTabOrder = next)))
                },
                onDown = {
                    val next = order.toMutableList().apply { add(index + 1, removeAt(index)) }
                    dispatch(DmtAction.Config(settings.copy(libraryTabOrder = next)))
                },
            )
        }
    }
}

/** One library tab: its visibility switch and the buttons that move it in the bar. */
@Composable
private fun LibraryTabRow(
    tab: LibraryTab,
    checked: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    onToggle: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Margin, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(tab.labelRes()),
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
            )
            Text(
                text = stringResource(tab.hintRes()),
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        LibraryTabMoveButton(up = true, enabled = canUp, onClick = onUp)
        LibraryTabMoveButton(up = false, enabled = canDown, onClick = onDown)
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier =
                Modifier.pressableToggle(value = checked, onValueChange = { onToggle() }),
        ) {
            Toggle(checked = checked)
        }
    }
}

@Composable
private fun LibraryTabMoveButton(up: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val description =
        stringResource(if (up) R.string.playlist_move_up else R.string.playlist_move_down)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.size(36.dp)
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

@StringRes
private fun LibraryTab.labelRes(): Int =
    when (this) {
        LibraryTab.FOLDERS -> R.string.library_tab_folders
        LibraryTab.TRACKS -> R.string.library_tab_tracks
        LibraryTab.ALBUMS -> R.string.library_tab_albums
        LibraryTab.ARTISTS -> R.string.library_tab_artists
        LibraryTab.PLAYLISTS -> R.string.library_tab_playlists
        LibraryTab.GENRES -> R.string.library_tab_genres
    }

@StringRes
private fun LibraryTab.hintRes(): Int =
    when (this) {
        LibraryTab.FOLDERS -> R.string.settings_library_tab_folders_hint
        LibraryTab.TRACKS -> R.string.settings_library_tab_tracks_hint
        LibraryTab.ALBUMS -> R.string.settings_library_tab_albums_hint
        LibraryTab.ARTISTS -> R.string.settings_library_tab_artists_hint
        LibraryTab.PLAYLISTS -> R.string.settings_library_tab_playlists_hint
        LibraryTab.GENRES -> R.string.settings_library_tab_genres_hint
    }
