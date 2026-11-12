package com.oavi.browser.feature.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oavi.browser.engine.BrowserState
import com.oavi.browser.engine.TabState
import com.oavi.browser.engine.TabsController
import com.oavi.browser.model.GroupColor
import com.oavi.browser.ui.ButtonKind
import com.oavi.browser.ui.Oavi
import com.oavi.browser.ui.OaviButton
import com.oavi.browser.ui.OaviChip
import com.oavi.browser.ui.OaviIconButton
import com.oavi.browser.ui.OaviMotion
import com.oavi.browser.ui.R
import com.oavi.browser.ui.glass
import com.oavi.browser.ui.rememberBackProgress
import com.oavi.browser.ui.rememberHaptics

internal fun GroupColor.toColor(): Color = when (this) {
    GroupColor.AQUA -> Color(0xFF12B5A6)
    GroupColor.CORAL -> Color(0xFFE0653A)
    GroupColor.VIOLET -> Color(0xFF8B6CFF)
    GroupColor.AMBER -> Color(0xFFC08A12)
    GroupColor.LEAF -> Color(0xFF4E9A3B)
    GroupColor.ROSE -> Color(0xFFD6457A)
    GroupColor.SLATE -> Color(0xFF5B6B8C)
}

private sealed interface TabFilter {
    data object All : TabFilter
    data object Archive : TabFilter
    data class Group(val id: String) : TabFilter
}

/**
 * Every open tab at a glance. Tap to go, swipe a card away to close it, press and hold for
 * grouping and archiving. Private tabs live on their own side and can be locked.
 *
 * @param privateLocked private tabs are behind the screen lock right now.
 */
@Composable
fun TabsScreen(
    visible: Boolean,
    controller: TabsController,
    state: BrowserState,
    privateLocked: Boolean,
    onUnlock: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Oavi.colors
    val haptics = rememberHaptics()
    val back by rememberBackProgress(visible, onClose)

    var showPrivate by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf<TabFilter>(TabFilter.All) }
    var actionsFor by remember { mutableStateOf<String?>(null) }
    var newGroupFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(visible) {
        if (visible) {
            showPrivate = state.selected?.isPrivate == true
            filter = TabFilter.All
        } else {
            actionsFor = null
            newGroupFor = null
        }
    }
    // A group that lost its last tab disappears; fall back to showing everything.
    val activeFilter = filter.let { current ->
        if (current is TabFilter.Group && state.groups.none { it.id == current.id }) TabFilter.All else current
    }

    val normalTabs = state.visibleTabs(private = false)
    val privateTabs = state.visibleTabs(private = true)
    val archived = state.archivedTabs
    val shown: List<TabState> = when {
        showPrivate -> privateTabs
        activeFilter is TabFilter.Archive -> archived
        activeFilter is TabFilter.Group -> normalTabs.filter { it.tab.groupId == activeFilter.id }
        else -> normalTabs
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(OaviMotion.fade()) + scaleIn(OaviMotion.spatial(), initialScale = 1.06f),
        exit = fadeOut(OaviMotion.fade()) + scaleOut(OaviMotion.gentle(), targetScale = 1.04f),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1f - back * 0.08f
                    scaleX = scale
                    scaleY = scale
                    shape = RoundedCornerShape((36 * back).dp)
                    clip = back > 0f
                }
                .background(colors.background)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OaviChip(
                        text = "${stringResource(R.string.tabs_normal)} · ${normalTabs.size}",
                        selected = !showPrivate,
                        onClick = { showPrivate = false },
                    )
                    Spacer(Modifier.width(8.dp))
                    OaviChip(
                        text = "${stringResource(R.string.tabs_private)} · ${privateTabs.size}",
                        selected = showPrivate,
                        onClick = { showPrivate = true },
                    )
                }

                AnimatedVisibility(visible = !showPrivate && (state.groups.isNotEmpty() || archived.isNotEmpty())) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 16.dp, end = 16.dp, top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OaviChip(stringResource(R.string.tabs_all), selected = activeFilter is TabFilter.All, onClick = { filter = TabFilter.All })
                        for (group in state.groups) {
                            OaviChip(
                                text = group.name,
                                selected = activeFilter is TabFilter.Group && activeFilter.id == group.id,
                                dot = group.color.toColor(),
                                onClick = { filter = TabFilter.Group(group.id) },
                            )
                        }
                        if (archived.isNotEmpty()) {
                            OaviChip(
                                text = "${stringResource(R.string.tabs_archive)} · ${archived.size}",
                                selected = activeFilter is TabFilter.Archive,
                                onClick = { filter = TabFilter.Archive },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                when {
                    showPrivate && privateLocked -> LockedPrivateTabs(onUnlock)
                    shown.isEmpty() -> EmptyTabs(
                        body = when {
                            showPrivate -> stringResource(R.string.tabs_empty_private_body)
                            activeFilter is TabFilter.Archive -> stringResource(R.string.tabs_empty_archive_body)
                            else -> stringResource(R.string.tabs_empty_group_body)
                        },
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 156.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(shown, key = { it.id }) { tab ->
                            TabCard(
                                tab = tab,
                                selected = tab.id == state.selectedId,
                                groupColor = state.groups.firstOrNull { it.id == tab.tab.groupId }?.color?.toColor(),
                                controller = controller,
                                modifier = Modifier.animateItem(),
                                onOpen = {
                                    controller.select(tab.id)
                                    onClose()
                                },
                                onClose = {
                                    haptics.tick()
                                    controller.close(tab.id)
                                },
                                onHold = {
                                    haptics.hold()
                                    actionsFor = tab.id
                                },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(32.dp))
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OaviIconButton(
                    icon = R.drawable.ic_trash,
                    contentDescription = stringResource(R.string.tabs_close_all),
                    enabled = shown.isNotEmpty() && !(showPrivate && privateLocked),
                    tint = colors.danger,
                    onClick = {
                        haptics.confirm()
                        controller.closeAll(shown.map { it.id })
                    },
                )
                Spacer(Modifier.weight(1f))
                OaviButton(
                    text = stringResource(if (showPrivate) R.string.tabs_new_private_tab else R.string.tabs_new_tab),
                    icon = R.drawable.ic_plus,
                    onClick = {
                        controller.newTab(private = showPrivate)
                        onClose()
                    },
                )
                Spacer(Modifier.weight(1f))
                OaviIconButton(
                    icon = R.drawable.ic_check,
                    contentDescription = stringResource(R.string.tabs_done),
                    onClick = onClose,
                )
            }

            TabActionsSheet(
                tab = state.tab(actionsFor),
                state = state,
                controller = controller,
                onNewGroup = { id ->
                    actionsFor = null
                    newGroupFor = id
                },
                onDismiss = { actionsFor = null },
            )
            NewGroupPrompt(
                tab = state.tab(newGroupFor),
                onCreate = { name, color ->
                    val id = newGroupFor
                    newGroupFor = null
                    if (id != null) filter = TabFilter.Group(controller.createGroup(name, color, listOf(id)))
                },
                onDismiss = { newGroupFor = null },
            )
        }
    }
}

@Composable
private fun EmptyTabs(body: String) {
    val colors = Oavi.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(stringResource(R.string.tabs_empty_title), style = Oavi.type.title, color = colors.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(body, style = Oavi.type.body, color = colors.muted)
    }
}

@Composable
private fun LockedPrivateTabs(onUnlock: () -> Unit) {
    val colors = Oavi.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.lock_title), style = Oavi.type.title, color = colors.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.lock_body), style = Oavi.type.body, color = colors.muted)
        Spacer(Modifier.height(20.dp))
        OaviButton(stringResource(R.string.lock_unlock), onClick = onUnlock, kind = ButtonKind.TONAL, icon = R.drawable.ic_lock)
    }
}
