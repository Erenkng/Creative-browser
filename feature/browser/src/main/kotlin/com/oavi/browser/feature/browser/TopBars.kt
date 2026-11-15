package com.oavi.browser.feature.browser

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oavi.browser.engine.TabState
import com.oavi.browser.model.UrlInput
import com.oavi.browser.ui.Oavi
import com.oavi.browser.ui.OaviIconButton
import com.oavi.browser.ui.OaviMotion
import com.oavi.browser.ui.R
import com.oavi.browser.ui.SiteIcon
import com.oavi.browser.ui.glass

/** The surface every top bar sits on: continues under the status bar, a hairline at the bottom. */
@Composable
private fun TopSurface(content: @Composable () -> Unit) {
    val colors = Oavi.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.raised)
            .drawBehind {
                drawLine(colors.faint, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1f)
            }
            .statusBarsPadding(),
    ) {
        content()
    }
}

/**
 * The classic layout: the address along the top with the tab count and menu beside it.
 * Oavi's touches: protection sits inside the address, reading view and reload are one tap
 * away, and the bar itself can be swiped sideways to change tab or pulled down for all tabs.
 */
@Composable
internal fun ClassicTopBar(tab: TabState?, tabCount: Int, actions: ChromeActions) {
    val drag = remember { Animatable(0f) }
    TopSurface {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Bars.TopBar)
                .barGestures(drag, actions.onSwitchTab, onUp = null, onDown = actions.onOpenTabs)
                .graphicsLayer { translationX = drag.value }
                .padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AddressPill(
                tab = tab,
                onEdit = actions.onEditAddress,
                onProtection = actions.onOpenProtection,
                modifier = Modifier.weight(1f),
            ) {
                ReaderButton(tab, actions.onReader)
                ReloadButton(tab, actions.onReloadOrStop)
            }
            Spacer(Modifier.width(2.dp))
            TabCountButton(tabCount, actions.onOpenTabs, actions.onNewTab)
            OaviIconButton(
                icon = R.drawable.ic_more,
                contentDescription = stringResource(R.string.cd_menu),
                onClick = actions.onOpenMenu,
                modifier = Modifier.size(44.dp),
            )
        }
        ProgressLine(tab)
    }
}

/**
 * The tab-strip layout: the address on top, every open tab as a chip right under it, and the
 * buttons along the bottom (see [StripToolbar]).
 */
@Composable
internal fun StripTopBar(
    tab: TabState?,
    tabs: List<TabState>,
    actions: ChromeActions,
) {
    TopSurface {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Bars.TopBar)
                .padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AddressPill(
                tab = tab,
                onEdit = actions.onEditAddress,
                onProtection = actions.onOpenProtection,
                modifier = Modifier.weight(1f),
            ) {
                ReaderButton(tab, actions.onReader)
                ReloadButton(tab, actions.onReloadOrStop)
            }
            OaviIconButton(
                icon = R.drawable.ic_more,
                contentDescription = stringResource(R.string.cd_menu),
                onClick = actions.onOpenMenu,
                modifier = Modifier.size(44.dp),
            )
        }
        TabStrip(tabs = tabs, selectedId = tab?.id, actions = actions)
        ProgressLine(tab)
    }
}

@Composable
private fun TabStrip(tabs: List<TabState>, selectedId: String?, actions: ChromeActions) {
    val list = rememberLazyListState()
    val selectedIndex = tabs.indexOfFirst { it.id == selectedId }
    LaunchedEffect(selectedIndex, tabs.size) {
        if (selectedIndex >= 0) list.animateScrollToItem(selectedIndex, scrollOffset = -48)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Bars.TabStrip),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            state = list,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
        ) {
            items(tabs, key = { it.id }) { item ->
                StripTab(
                    tab = item,
                    selected = item.id == selectedId,
                    onSelect = { actions.onSelectTab(item.id) },
                    onClose = { actions.onCloseTab(item.id) },
                )
            }
        }
        OaviIconButton(
            icon = R.drawable.ic_plus,
            contentDescription = stringResource(R.string.menu_new_tab),
            onClick = actions.onNewTab,
            modifier = Modifier.size(40.dp),
        )
    }
}

@Composable
private fun StripTab(tab: TabState, selected: Boolean, onSelect: () -> Unit, onClose: () -> Unit) {
    val colors = Oavi.colors
    val fill by animateColorAsState(if (selected) colors.accentSoft else Color.Transparent, tween(200), label = "stripTab")
    val host = UrlInput.displayHost(tab.url)
    val title = when {
        tab.isBlank -> stringResource(R.string.tabs_new_tab)
        else -> tab.tab.label
    }
    Row(
        modifier = Modifier
            .height(32.dp)
            .widthIn(min = 72.dp, max = 168.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(fill)
            .drawBehind {
                if (selected) {
                    val inset = 10.dp.toPx()
                    drawLine(
                        colors.accent,
                        Offset(inset, size.height - 1.5.dp.toPx()),
                        Offset(size.width - inset, size.height - 1.5.dp.toPx()),
                        strokeWidth = 2.5.dp.toPx(),
                    )
                }
            }
            .clickable(role = Role.Tab, onClick = onSelect)
            .padding(start = 8.dp, end = if (selected) 0.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteIcon(host = host, size = 18.dp, label = host.ifEmpty { title })
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            style = Oavi.type.caption,
            color = if (selected) colors.onSurface else colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (selected) {
            OaviIconButton(
                icon = R.drawable.ic_close,
                contentDescription = stringResource(R.string.cd_close_tab),
                onClick = onClose,
                tint = colors.muted,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/**
 * The bottom toolbar of the tab-strip layout: library, back, search, forward and tabs, the
 * search in the middle where the thumb lands first. Mirrored for left hands.
 */
@Composable
internal fun StripToolbar(
    tab: TabState?,
    tabCount: Int,
    leftHanded: Boolean,
    actions: ChromeActions,
    modifier: Modifier = Modifier,
) {
    val colors = Oavi.colors
    val drag = remember { Animatable(0f) }
    Box(
        modifier
            .fillMaxWidth()
            .background(colors.raised)
            .drawBehind { drawLine(colors.faint, Offset(0f, 0.5f), Offset(size.width, 0.5f), strokeWidth = 1f) }
            .navigationBarsPadding(),
    ) {
        Mirrored(leftHanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Bars.Toolbar)
                    .barGestures(drag, actions.onSwitchTab, onUp = actions.onOpenTabs, onDown = actions.onCollapse)
                    .graphicsLayer { translationX = drag.value },
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OaviIconButton(R.drawable.ic_bookmark, stringResource(R.string.menu_library), actions.onOpenLibrary, Modifier.size(48.dp))
                OaviIconButton(R.drawable.ic_back, stringResource(R.string.cd_back), actions.onBack, Modifier.size(48.dp), enabled = tab?.canGoBack == true)
                AccentRoundButton(R.drawable.ic_search, stringResource(R.string.cd_search), actions.onEditAddress)
                OaviIconButton(R.drawable.ic_forward, stringResource(R.string.cd_forward), actions.onForward, Modifier.size(48.dp), enabled = tab?.canGoForward == true)
                TabCountButton(tabCount, actions.onOpenTabs, actions.onNewTab, size = 48.dp)
            }
        }
    }
}

/**
 * Where the address entry, menu and find bar open in the top-bar layouts: a card hanging
 * from the top of the screen, over the bar.
 */
@Composable
internal fun TopPanel(
    mode: IslandMode,
    modifier: Modifier = Modifier,
    search: @Composable () -> Unit,
    menu: @Composable () -> Unit,
    find: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .widthIn(max = 640.dp)
            .heightIn(min = 56.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.glass(RoundedCornerShape(26.dp))) {
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    (fadeIn(tween(140, delayMillis = 60)) togetherWith fadeOut(tween(90))) using
                        SizeTransform(clip = false) { _, _ -> OaviMotion.spatial() }
                },
                contentAlignment = Alignment.TopCenter,
                label = "topPanel",
            ) { current ->
                when (current) {
                    IslandMode.SEARCH -> search()
                    IslandMode.MENU -> menu()
                    IslandMode.FIND -> find()
                    else -> Spacer(Modifier.height(0.dp))
                }
            }
        }
    }
}
