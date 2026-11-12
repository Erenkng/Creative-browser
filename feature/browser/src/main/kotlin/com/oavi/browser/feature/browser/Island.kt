package com.oavi.browser.feature.browser

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oavi.browser.engine.TabState
import com.oavi.browser.model.UrlInput
import com.oavi.browser.ui.Oavi
import com.oavi.browser.ui.OaviIconButton
import com.oavi.browser.ui.OaviMotion
import com.oavi.browser.ui.R
import com.oavi.browser.ui.glass
import com.oavi.browser.ui.rememberHaptics
import kotlinx.coroutines.launch
import kotlin.math.abs

/** The shapes the island takes. It is one object that reshapes itself, never several bars. */
enum class IslandMode { RESTING, COMPACT, SEARCH, MENU, FIND }

/**
 * The island: Oavi's single control, floating at the bottom of the screen where the thumb
 * rests. It morphs between a resting capsule, a small pill while reading, the address
 * entry, the menu and the find bar.
 */
@Composable
internal fun Island(
    mode: IslandMode,
    modifier: Modifier = Modifier,
    resting: @Composable () -> Unit,
    compact: @Composable () -> Unit,
    search: @Composable () -> Unit,
    menu: @Composable () -> Unit,
    find: @Composable () -> Unit,
) {
    val radius by animateDpAsState(
        targetValue = if (mode == IslandMode.COMPACT) 18.dp else 30.dp,
        animationSpec = OaviMotion.spatial(),
        label = "islandRadius",
    )
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .widthIn(max = 600.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(Modifier.glass(RoundedCornerShape(radius))) {
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    (fadeIn(tween(140, delayMillis = 70)) togetherWith fadeOut(tween(90))) using
                        SizeTransform(clip = false) { _, _ -> OaviMotion.spatial() }
                },
                contentAlignment = Alignment.BottomCenter,
                label = "island",
            ) { current ->
                when (current) {
                    IslandMode.RESTING -> resting()
                    IslandMode.COMPACT -> compact()
                    IslandMode.SEARCH -> search()
                    IslandMode.MENU -> menu()
                    IslandMode.FIND -> find()
                }
            }
        }
    }
}

/** A wash of the accent colour that fills the island from the left as the page loads. */
@Composable
private fun Modifier.loadingFill(tab: TabState?): Modifier {
    val target = if (tab != null && tab.loading) (tab.progress / 100f).coerceIn(0.08f, 1f) else 0f
    val fill by animateFloatAsState(target, OaviMotion.gentle(), label = "loadingFill")
    val visible by animateFloatAsState(if (tab?.loading == true) 1f else 0f, tween(260), label = "loadingAlpha")
    val color = Oavi.colors.accentSoft
    return this.drawBehind {
        if (visible > 0f && fill > 0f) {
            drawRect(color, size = Size(size.width * fill, size.height), alpha = visible)
        }
    }
}

@Composable
internal fun RestingIsland(
    tab: TabState?,
    tabCount: Int,
    onEditAddress: () -> Unit,
    onOpenProtection: () -> Unit,
    onReloadOrStop: () -> Unit,
    onReader: () -> Unit,
    onOpenTabs: () -> Unit,
    onNewTab: () -> Unit,
    onOpenMenu: () -> Unit,
    onSwitchTab: (forward: Boolean) -> Unit,
    onCollapse: () -> Unit,
) {
    val colors = Oavi.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    val switchTab by rememberUpdatedState(onSwitchTab)
    val openTabs by rememberUpdatedState(onOpenTabs)
    val collapse by rememberUpdatedState(onCollapse)

    val blank = tab == null || tab.isBlank
    val host = if (tab == null) "" else UrlInput.displayHost(tab.url).ifEmpty { tab.url }
    val insecure = tab != null && !blank && !tab.secure && tab.url.startsWith("http://")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .loadingFill(tab)
            .pointerInput(Unit) {
                val switchAt = 64.dp.toPx()
                val openAt = 36.dp.toPx()
                val collapseAt = 24.dp.toPx()
                var totalX = 0f
                var totalY = 0f
                var horizontal: Boolean? = null
                var armed = false
                detectDragGestures(
                    onDragStart = {
                        totalX = 0f
                        totalY = 0f
                        horizontal = null
                        armed = false
                    },
                    onDragEnd = {
                        when {
                            horizontal == true && abs(totalX) > switchAt -> switchTab(totalX < 0)
                            horizontal == false && totalY < -openAt -> openTabs()
                            horizontal == false && totalY > collapseAt -> collapse()
                        }
                        scope.launch { drag.animateTo(0f, OaviMotion.lively()) }
                    },
                    onDragCancel = { scope.launch { drag.animateTo(0f, OaviMotion.spatial()) } },
                ) { change, amount ->
                    change.consume()
                    totalX += amount.x
                    totalY += amount.y
                    if (horizontal == null) horizontal = abs(totalX) >= abs(totalY)
                    if (horizontal == true) {
                        scope.launch { drag.snapTo(totalX * 0.45f) }
                        val past = abs(totalX) > switchAt
                        if (past != armed) {
                            armed = past
                            if (past) haptics.threshold()
                        }
                    }
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp)
                .graphicsLayer { translationX = drag.value },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!blank) {
                OaviIconButton(
                    icon = when {
                        insecure -> R.drawable.ic_warning
                        tab?.protectionOff == true -> R.drawable.ic_shield_off
                        else -> R.drawable.ic_shield
                    },
                    contentDescription = stringResource(R.string.cd_protection),
                    onClick = onOpenProtection,
                    tint = if (insecure) colors.danger else colors.accent,
                    modifier = Modifier.size(44.dp),
                )
            } else {
                Spacer(Modifier.width(14.dp))
                Icon(
                    painterResource(if (tab?.isPrivate == true) R.drawable.ic_private_mode else R.drawable.ic_search),
                    contentDescription = null,
                    tint = colors.muted,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onEditAddress,
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = if (blank) stringResource(R.string.island_placeholder) else host,
                    style = Oavi.type.headline,
                    color = if (blank) colors.muted else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            AnimatedVisibility(
                visible = tab != null && (tab.readerAvailable || tab.readerActive),
                enter = fadeIn(OaviMotion.fade()) + expandHorizontally(OaviMotion.spatial()),
                exit = fadeOut(OaviMotion.fade()) + shrinkHorizontally(OaviMotion.spatial()),
            ) {
                OaviIconButton(
                    icon = R.drawable.ic_reader,
                    contentDescription = stringResource(R.string.cd_reader),
                    onClick = onReader,
                    tint = if (tab?.readerActive == true) colors.onAccent else colors.onSurface,
                    background = if (tab?.readerActive == true) colors.accent else androidx.compose.ui.graphics.Color.Transparent,
                    modifier = Modifier.size(44.dp),
                )
            }
            if (!blank) {
                OaviIconButton(
                    icon = if (tab?.loading == true) R.drawable.ic_close else R.drawable.ic_reload,
                    contentDescription = stringResource(if (tab?.loading == true) R.string.cd_stop else R.string.cd_reload),
                    onClick = onReloadOrStop,
                    modifier = Modifier.size(44.dp),
                )
            }
            TabCountButton(count = tabCount, onClick = onOpenTabs, onLongClick = onNewTab)
            OaviIconButton(
                icon = R.drawable.ic_more,
                contentDescription = stringResource(R.string.cd_menu),
                onClick = onOpenMenu,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

@Composable
private fun TabCountButton(count: Int, onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptics = rememberHaptics()
    Box(contentAlignment = Alignment.Center) {
        OaviIconButton(
            icon = R.drawable.ic_tab,
            contentDescription = pluralStringResource(R.plurals.cd_tabs, count, count),
            onClick = onClick,
            onLongClick = {
                haptics.hold()
                onLongClick()
            },
            modifier = Modifier.size(44.dp),
        )
        Text(
            text = if (count > 99) "∞" else count.toString(),
            style = Oavi.type.caption,
            color = Oavi.colors.onSurface,
        )
    }
}

/** The island while reading: just the site name, small enough to forget. Tap to bring it back. */
@Composable
internal fun CompactIsland(tab: TabState?, onExpand: () -> Unit) {
    val colors = Oavi.colors
    val host = if (tab == null) "" else UrlInput.displayHost(tab.url)
    Row(
        modifier = Modifier
            .height(34.dp)
            .loadingFill(tab)
            .clickable(role = Role.Button, onClick = onExpand)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(if (tab?.secure == false && tab.url.startsWith("http://")) R.drawable.ic_warning else R.drawable.ic_lock),
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = host,
            style = Oavi.type.caption,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp),
        )
    }
}
