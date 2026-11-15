package com.oavi.browser.feature.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oavi.browser.engine.TabState
import com.oavi.browser.model.HistoryEntry
import com.oavi.browser.model.TabGroup
import com.oavi.browser.model.UrlInput
import com.oavi.browser.ui.Oavi
import com.oavi.browser.ui.OaviChip
import com.oavi.browser.ui.OaviIconButton
import com.oavi.browser.ui.OaviMotion
import com.oavi.browser.ui.R
import com.oavi.browser.ui.SiteIcon
import com.oavi.browser.ui.glass
import com.oavi.browser.ui.toColor

/** The soft colour wash the focus layout's page card rests on, leaning towards the accent. */
@Composable
internal fun focusBackdrop(): Brush {
    val colors = Oavi.colors
    val strong by animateColorAsState(
        colors.accent.copy(alpha = if (colors.isDark) 0.34f else 0.30f).compositeOver(colors.background),
        tween(450),
        label = "backdropStrong",
    )
    val soft by animateColorAsState(
        colors.accent.copy(alpha = if (colors.isDark) 0.12f else 0.10f).compositeOver(colors.background),
        tween(450),
        label = "backdropSoft",
    )
    return Brush.linearGradient(listOf(strong, colors.background, soft))
}

/**
 * Draws the backdrop around the page card, leaving a rounded window for the page. The page is
 * drawn by the engine underneath, so its corners are rounded by covering them rather than
 * clipping, which a hardware surface would ignore.
 */
@Composable
internal fun FocusFrame(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val brush = focusBackdrop()
    val outline = Oavi.colors.faint.copy(alpha = if (Oavi.colors.isDark) 0.25f else 0.7f)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
        val navBottom = WindowInsets.navigationBars.getBottom(density).toFloat()
        Canvas(Modifier.fillMaxSize()) {
            val margin = Bars.FocusMargin.toPx()
            val radius = Bars.FocusRadius.toPx()
            val card = RoundRect(
                left = margin,
                top = statusTop + margin,
                right = size.width - margin,
                bottom = size.height - navBottom - margin,
                cornerRadius = CornerRadius(radius, radius),
            )
            val frame = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                addRoundRect(card)
            }
            drawPath(frame, brush)
            drawPath(Path().apply { addRoundRect(card) }, outline, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
        }
    }
}

/**
 * The focus layout's only permanent control: a small handle floating over the bottom of the
 * page. Tap the name for the command bar, the panel icon for tabs, the dots for the menu;
 * slide it sideways to change tab, flick it up for the tab panel.
 */
@Composable
internal fun FocusHandle(
    tab: TabState?,
    leftHanded: Boolean,
    actions: ChromeActions,
    onOpenSidebar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Oavi.colors
    val drag = remember { Animatable(0f) }
    val blank = tab == null || tab.isBlank
    val host = if (tab == null) "" else UrlInput.displayHost(tab.url)
    Box(
        modifier
            .navigationBarsPadding()
            .padding(bottom = Bars.FocusMargin + 8.dp)
            .graphicsLayer { translationX = drag.value }
            .glass(RoundedCornerShape(Bars.Handle / 2), elevation = 12.dp)
            .loadingFill(tab)
            .barGestures(drag, actions.onSwitchTab, onUp = onOpenSidebar, onDown = null),
    ) {
        Mirrored(leftHanded) {
            Row(
                modifier = Modifier
                    .height(Bars.Handle)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OaviIconButton(R.drawable.ic_sidebar, stringResource(R.string.cd_tab_panel), onOpenSidebar, Modifier.size(40.dp))
                Row(
                    modifier = Modifier
                        .widthIn(min = 120.dp, max = 220.dp)
                        .height(Bars.Handle)
                        .clickable(role = Role.Button, onClick = actions.onEditAddress)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    NaturalDirection {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painterResource(if (blank) R.drawable.ic_search else R.drawable.ic_lock),
                                contentDescription = null,
                                tint = colors.muted,
                                modifier = Modifier.size(if (blank) 16.dp else 13.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (blank) stringResource(R.string.focus_search) else host,
                                style = Oavi.type.label,
                                color = if (blank) colors.muted else colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                OaviIconButton(R.drawable.ic_more, stringResource(R.string.cd_menu), actions.onOpenMenu, Modifier.size(40.dp))
            }
        }
    }
}

/**
 * The address entry of the focus layout: a command bar floating in the upper middle of the
 * screen, above the page, with suggestions opening downwards.
 */
@Composable
internal fun CommandBar(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize().statusBarsPadding()) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = maxHeight * 0.14f, start = 14.dp, end = 14.dp)
                .widthIn(max = 560.dp)
                .glass(RoundedCornerShape(26.dp), elevation = 26.dp),
        ) {
            content()
        }
    }
}

/**
 * Tabs as a vertical list in a panel from the side, grouped like workspaces, with the most
 * visited sites pinned on top. Tap outside or slide it back to close.
 */
@Composable
internal fun TabSidebar(
    visible: Boolean,
    tabs: List<TabState>,
    groups: List<TabGroup>,
    selectedId: String?,
    privateMode: Boolean,
    pinned: List<HistoryEntry>,
    onShowPrivate: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onOpenPinned: (String) -> Unit,
    onOpenAllTabs: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Oavi.colors
    val dismiss by rememberUpdatedState(onDismiss)
    AnimatedVisibility(visible = visible, enter = fadeIn(OaviMotion.fade()), exit = fadeOut(OaviMotion.fade())) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .pointerInput(Unit) { detectTapGestures { dismiss() } },
        )
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(OaviMotion.spatial()) { -it } + fadeIn(OaviMotion.fade()),
        exit = slideOutHorizontally(OaviMotion.gentle()) { -it } + fadeOut(OaviMotion.fade()),
    ) {
        BoxWithConstraints(Modifier.fillMaxHeight()) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .width(minOf(maxWidth * 0.84f, 340.dp))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 8.dp, top = 8.dp, bottom = 8.dp)
                    .glass(RoundedCornerShape(26.dp), elevation = 24.dp)
                    .pointerInput(Unit) {
                        var travelled = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { travelled = 0f },
                            onDragEnd = { if (travelled < -80.dp.toPx()) dismiss() },
                        ) { change, amount ->
                            travelled += amount
                            if (travelled < 0f) change.consume()
                        }
                    },
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OaviChip(stringResource(R.string.focus_tabs), selected = !privateMode, onClick = { onShowPrivate(false) })
                    Spacer(Modifier.width(6.dp))
                    OaviChip(stringResource(R.string.focus_private), selected = privateMode, onClick = { onShowPrivate(true) })
                    Spacer(Modifier.weight(1f))
                    OaviIconButton(R.drawable.ic_tab, stringResource(R.string.focus_all_tabs), onOpenAllTabs, Modifier.size(40.dp))
                }

                if (!privateMode && pinned.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (site in pinned.take(4)) {
                            val host = UrlInput.displayHost(site.url)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colors.accentSoft)
                                    .clickable(role = Role.Button) { onOpenPinned(site.url) },
                                contentAlignment = Alignment.Center,
                            ) {
                                SiteIcon(host = host, size = 26.dp, label = host)
                            }
                        }
                        repeat(4 - pinned.take(4).size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    val byGroup = tabs.groupBy { it.tab.groupId }
                    val ordered = groups.filter { byGroup.containsKey(it.id) }
                    for (group in ordered) {
                        item(key = "group-" + group.id) {
                            SidebarGroupTitle(group.name, group.color.toColor())
                        }
                        items(byGroup[group.id].orEmpty(), key = { it.id }) { tab ->
                            SidebarTab(tab, tab.id == selectedId, { onSelect(tab.id) }, { onCloseTab(tab.id) })
                        }
                    }
                    val loose = tabs.filter { tab -> tab.tab.groupId == null || ordered.none { it.id == tab.tab.groupId } }
                    if (ordered.isNotEmpty() && loose.isNotEmpty()) {
                        item(key = "loose") { Spacer(Modifier.height(10.dp)) }
                    }
                    items(loose, key = { it.id }) { tab ->
                        SidebarTab(tab, tab.id == selectedId, { onSelect(tab.id) }, { onCloseTab(tab.id) })
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(role = Role.Button, onClick = onNewTab)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_plus), contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(if (privateMode) R.string.menu_private_tab else R.string.menu_new_tab),
                        style = Oavi.type.label,
                        color = colors.accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarGroupTitle(name: String, color: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        Text(name, style = Oavi.type.caption, color = Oavi.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SidebarTab(tab: TabState, selected: Boolean, onSelect: () -> Unit, onClose: () -> Unit) {
    val colors = Oavi.colors
    val fill by animateColorAsState(if (selected) colors.accentSoft else Color.Transparent, tween(200), label = "sidebarTab")
    val host = UrlInput.displayHost(tab.url)
    val title = if (tab.isBlank) stringResource(R.string.tabs_new_tab) else tab.tab.label
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(fill)
            .clickable(role = Role.Tab, onClick = onSelect)
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteIcon(host = host, size = 22.dp, label = host.ifEmpty { title })
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = Oavi.type.label,
            color = if (selected) colors.onSurface else colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        OaviIconButton(
            icon = R.drawable.ic_close,
            contentDescription = stringResource(R.string.cd_close_tab),
            onClick = onClose,
            tint = colors.muted,
            modifier = Modifier.size(40.dp),
        )
    }
}
