package dev.jyotiraditya.dmt.presentation.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.domain.model.LibraryTab
import dev.jyotiraditya.dmt.presentation.library.BackLink
import dev.jyotiraditya.dmt.presentation.player.DmtView
import dev.jyotiraditya.dmt.ui.components.DmtIcon
import dev.jyotiraditya.dmt.ui.components.IconTap
import dev.jyotiraditya.dmt.ui.components.background
import dev.jyotiraditya.dmt.ui.components.bottomSeparator
import dev.jyotiraditya.dmt.ui.components.pressable
import dev.jyotiraditya.dmt.ui.icons.DmtIcons
import dev.jyotiraditya.dmt.ui.theme.Ink
import dev.jyotiraditya.dmt.ui.theme.Margin
import dev.jyotiraditya.dmt.ui.theme.Muted
import dev.jyotiraditya.dmt.ui.theme.Surface
import dev.jyotiraditya.dmt.ui.theme.darkenedForPlayer
import dev.jyotiraditya.dmt.ui.theme.inkOn
import dev.jyotiraditya.dmt.ui.theme.secondary

/** The top-level destinations, with the label and icon they are shown with. */
enum class Place(
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    val libraryTab: LibraryTab? = null,
) {
    QUEUE(R.string.nav_queue, DmtIcons.Queue),
    NOW_PLAYING(R.string.nav_now_playing, DmtIcons.Play),
    FOLDERS(R.string.library_tab_folders, DmtIcons.Library, LibraryTab.FOLDERS),
    TRACKS(R.string.library_tab_tracks, DmtIcons.Playlist, LibraryTab.TRACKS),
    ALBUMS(R.string.library_tab_albums, DmtIcons.Disc, LibraryTab.ALBUMS),
    ARTISTS(R.string.library_tab_artists, DmtIcons.Person, LibraryTab.ARTISTS),
    PLAYLISTS(R.string.library_tab_playlists, DmtIcons.Playlist, LibraryTab.PLAYLISTS),
    GENRES(R.string.library_tab_genres, DmtIcons.Library, LibraryTab.GENRES),
    LISTEN(R.string.nav_listen, DmtIcons.Home),
    ;

    /** The library view this place shows, or null outside the library. */
    val view: DmtView?
        get() =
            when (libraryTab) {
                LibraryTab.FOLDERS -> DmtView.FOLDERS
                LibraryTab.TRACKS -> DmtView.LIBRARY
                LibraryTab.ALBUMS -> DmtView.ALBUMS
                LibraryTab.ARTISTS -> DmtView.ARTISTS
                LibraryTab.PLAYLISTS -> DmtView.PLAYLISTS
                LibraryTab.GENRES -> DmtView.GENRES
                null -> null
            }
}

internal val BackRowHeight = 52.dp

/** The back link inside a page, with the destinations taking the rest of the bar. */
@Composable
internal fun TopBar(
    selected: Float?,
    places: List<Place>,
    back: BackLink?,
    field: Color,
    settingsOpen: Boolean,
    onBack: () -> Unit,
    onPlace: (Place) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = field.darkenedForPlayer()
    val ink = background.inkOn()
    val rest = ink.secondary(background)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .background(background)
                .bottomSeparator()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(60.dp)
                .padding(horizontal = Margin),
    ) {
        if (back != null) {
            IconTap(
                icon = DmtIcons.ChevronLeft,
                description = stringResource(R.string.nav_back),
                onClick = onBack,
                ink = ink,
                size = 40.dp,
                iconSize = 24.dp,
                modifier = Modifier.offset(x = (-8).dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
        ) {
            places.forEachIndexed { index, p ->
                val factor =
                    if (selected != null) {
                        (1f - kotlin.math.abs(selected - index)).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                Text(
                    text = stringResource(p.label),
                    style = MaterialTheme.typography.labelLarge,
                    color = lerp(rest, ink, factor),
                    modifier =
                        Modifier.pressable(
                            onClick = { onPlace(p) },
                            role = Role.Tab,
                            label = stringResource(p.label),
                        )
                            .padding(vertical = 14.dp),
                )
            }
        }
        IconTap(
            icon = DmtIcons.Settings,
            description = stringResource(R.string.nav_settings),
            onClick = onSettings,
            ink = if (settingsOpen) ink else rest,
            active = settingsOpen,
            field = background,
            size = 40.dp,
            iconSize = 22.dp,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** The back link above a page in a wide window, where there is no top bar. */
@Composable
internal fun BackRow(label: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier.fillMaxWidth().height(BackRowHeight).padding(horizontal = Margin),
    ) {
        BackLinkLabel(label = label, onBack = onBack)
    }
}

@Composable
private fun BackLinkLabel(label: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .offset(x = (-6).dp)
                .pressable(onClick = onBack, label = stringResource(R.string.nav_back))
                .padding(vertical = 12.dp),
    ) {
        DmtIcon(icon = DmtIcons.ChevronLeft, tint = Ink, size = 24.dp)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The navigation rail for wide windows: the destinations, then Settings. */
@Composable
internal fun SideRail(
    selected: Float?,
    places: List<Place>,
    settingsOpen: Boolean,
    onPlace: (Place) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            modifier
                .width(184.dp)
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 12.dp, top = 8.dp),
    ) {
        places.forEachIndexed { index, p ->
            val factor =
                if (selected != null) {
                    (1f - kotlin.math.abs(selected - index)).coerceIn(0f, 1f)
                } else {
                    0f
                }
            RailRow(
                icon = p.icon,
                label = stringResource(p.label),
                factor = factor,
                onClick = { onPlace(p) },
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        RailRow(
            icon = DmtIcons.Settings,
            label = stringResource(R.string.nav_settings),
            factor = if (settingsOpen) 1f else 0f,
            onClick = onSettings,
        )
    }
}

/** One destination in the rail, filled while [factor] is high. */
@Composable
private fun RailRow(
    @DrawableRes icon: Int,
    label: String,
    factor: Float,
    onClick: () -> Unit,
) {
    val fill = lerp(Color.Transparent, Surface, factor)
    val ink = lerp(Muted, Ink, factor)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.fillMaxWidth()
                .pressable(
                    onClick = onClick,
                    role = Role.Tab,
                    label = label,
                )
                .background { fill }
                .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        DmtIcon(icon = icon, tint = ink, size = 22.dp)
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = ink,
        )
    }
}

/** The lowercase wordmark, scaled by [size]. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, color: Color = Ink, size: Float = 1f) {
    Text(
        text = stringResource(R.string.app_name),
        style =
            MaterialTheme.typography.headlineMedium.copy(
                fontSize = MaterialTheme.typography.headlineMedium.fontSize * size,
                letterSpacing = (-0.06).em,
            ),
        color = color,
        modifier = modifier,
    )
}
