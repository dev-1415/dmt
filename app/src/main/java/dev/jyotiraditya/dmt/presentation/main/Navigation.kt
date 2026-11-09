package dev.jyotiraditya.dmt.presentation.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.domain.model.LibraryTab
import dev.jyotiraditya.dmt.presentation.player.DmtView
import dev.jyotiraditya.dmt.ui.components.DmtIcon
import dev.jyotiraditya.dmt.ui.components.IconTap
import dev.jyotiraditya.dmt.ui.components.background
import dev.jyotiraditya.dmt.ui.components.bottomSeparator
import dev.jyotiraditya.dmt.ui.components.pressable
import dev.jyotiraditya.dmt.ui.components.topSeparator
import dev.jyotiraditya.dmt.ui.icons.DmtIcons
import dev.jyotiraditya.dmt.ui.theme.Ink
import dev.jyotiraditya.dmt.ui.theme.Margin
import dev.jyotiraditya.dmt.ui.theme.Muted
import dev.jyotiraditya.dmt.ui.theme.Surface
import dev.jyotiraditya.dmt.ui.theme.darkenedForPlayer
import dev.jyotiraditya.dmt.ui.theme.inkOn
import dev.jyotiraditya.dmt.ui.theme.secondary
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor

/** The top-level destinations, with the label and icon they are shown with. */
enum class Place(
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    val libraryTab: LibraryTab? = null,
) {
    QUEUE(R.string.nav_queue, DmtIcons.Queue),
    NOW_PLAYING(R.string.nav_now_playing, DmtIcons.NowPlaying),
    FOLDERS(R.string.library_tab_folders, DmtIcons.Folder, LibraryTab.FOLDERS),
    TRACKS(R.string.library_tab_tracks, DmtIcons.Tracks, LibraryTab.TRACKS),
    ALBUMS(R.string.library_tab_albums, DmtIcons.Disc, LibraryTab.ALBUMS),
    ARTISTS(R.string.library_tab_artists, DmtIcons.Person, LibraryTab.ARTISTS),
    PLAYLISTS(R.string.library_tab_playlists, DmtIcons.Playlist, LibraryTab.PLAYLISTS),
    GENRES(R.string.library_tab_genres, DmtIcons.Genre, LibraryTab.GENRES),
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

/** The back link above a page, with the player tint behind it. */
@Composable
internal fun BackBar(
    label: String,
    field: Color,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = field.darkenedForPlayer()
    val ink = background.inkOn()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .background(background)
                .bottomSeparator()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(52.dp)
                .padding(horizontal = Margin),
    ) {
        IconTap(
            icon = DmtIcons.ChevronLeft,
            description = stringResource(R.string.nav_back),
            onClick = onBack,
            ink = ink,
            size = 40.dp,
            iconSize = 24.dp,
            modifier = Modifier.offset(x = (-8).dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The top-level destinations and Settings, pinned below the content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BottomBar(
    pagerState: PagerState,
    places: List<Place>,
    settingsOpen: Boolean,
    field: Color,
    onPlace: (Place) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = field.darkenedForPlayer()
    val ink = background.inkOn()
    val rest = ink.secondary(background)
    val listState = rememberLazyListState()
    val selectedIndex = pagerState.currentPage.coerceIn(0, places.lastIndex)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .background(background)
                .topSeparator()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(60.dp)
                .padding(horizontal = Margin),
    ) {
        BoxWithConstraints(modifier = Modifier.weight(1f).height(40.dp)) {
            val tabWidth = 44.dp
            val fits = tabWidth * places.size <= maxWidth
            if (fits) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                ) {
                    places.forEachIndexed { index, p ->
                        BottomTab(
                            place = p,
                            factor = if (!settingsOpen && index == selectedIndex) 1f else 0f,
                            ink = ink,
                            rest = rest,
                            onClick = { onPlace(p) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                LaunchedEffect(selectedIndex) {
                    val info = listState.layoutInfo
                    val item = info.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
                    val hidden =
                        item == null ||
                            item.offset < info.viewportStartOffset ||
                            item.offset + item.size > info.viewportEndOffset
                    if (hidden) listState.animateScrollToItem(selectedIndex)
                }
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                ) {
                    itemsIndexed(places) { index, p ->
                        BottomTab(
                            place = p,
                            factor = if (!settingsOpen && index == selectedIndex) 1f else 0f,
                            ink = ink,
                            rest = rest,
                            onClick = { onPlace(p) },
                        )
                    }
                }
            }
            if (!settingsOpen) {
                Canvas(
                    modifier =
                        Modifier.align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(2.dp),
                ) {
                    val selected = pagerState.currentPage + pagerState.currentPageOffsetFraction
                    val basePage = floor(selected).toInt().coerceIn(0, places.lastIndex)
                    val fraction = (selected - basePage).coerceIn(0f, 1f)
                    fun centerOf(index: Int): Float? =
                        if (fits) {
                            if (index in places.indices) {
                                (index + 0.5f) * size.width / places.size
                            } else {
                                null
                            }
                        } else {
                            listState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.index == index }
                                ?.let { it.offset + it.size / 2f }
                        }
                    val current = centerOf(basePage) ?: return@Canvas
                    val next = centerOf((basePage + 1).coerceAtMost(places.lastIndex))
                    val center =
                        if (next != null) current + (next - current) * fraction else current
                    val progress = 1f - abs(2f * fraction - 1f)
                    val width = 24.dp.toPx() + 24.dp.toPx() * progress
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(center - width / 2f, 0f),
                        size = Size(width, size.height),
                    )
                }
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

/** One section tab: its icon and a long-press name. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun BottomTab(
    place: Place,
    factor: Float,
    ink: Color,
    rest: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(place.label)
    val tooltipState = rememberTooltipState()
    val scope = rememberCoroutineScope()
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        TooltipBox(
            positionProvider =
                TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(label) } },
            state = tooltipState,
            enableUserInput = false,
            focusable = false,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier.combinedClickable(
                        onClickLabel = label,
                        role = Role.Tab,
                        onLongClick = { scope.launch { tooltipState.show() } },
                        onClick = onClick,
                    )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                DmtIcon(icon = place.icon, tint = lerp(rest, ink, factor), size = 24.dp)
            }
        }
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
