package com.oavi.browser.feature.browser

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
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

/** The shapes the island takes. It is one object that reshapes itself, never several bars. */
enum class IslandMode { RESTING, COMPACT, SEARCH, MENU, FIND }

private val IslandMargin = 10.dp
private val RestingHeight = 60.dp
private val CompactHeight = 34.dp

/** How much of the bottom of the page the resting island covers, margins included. */
internal val IslandRestingCover = IslandMargin * 2 + RestingHeight

/** The same for the small pill shown while reading. */
internal val IslandCompactCover = IslandMargin * 2 + CompactHeight

/**
 * The island: Oavi's single control, floating at the bottom of the screen where the thumb
 * rests. It morphs between a resting capsule, a small pill while reading, the address
 * entry, the menu and the find bar. Other layouts borrow it for their menu and find bar.
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
            .padding(horizontal = 12.dp, vertical = IslandMargin)
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

/** A wash of the accent colour that fills a bar from the left as the page loads. */
@Composable
internal fun Modifier.loadingFill(tab: TabState?): Modifier {
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
    leftHanded: Boolean,
    actions: ChromeActions,
) {
    val colors = Oavi.colors
    val drag = remember { Animatable(0f) }
    val blank = tab == null || tab.isBlank
    val host = if (tab == null) "" else UrlInput.displayHost(tab.url).ifEmpty { tab.url }
    val insecure = tab != null && !blank && !tab.secure && tab.url.startsWith("http://")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(RestingHeight)
            .loadingFill(tab)
            .barGestures(
                drag = drag,
                onSwitchTab = actions.onSwitchTab,
                onUp = actions.onOpenTabs,
                onDown = actions.onCollapse,
            ),
    ) {
        Mirrored(leftHanded) {
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
                        onClick = actions.onOpenProtection,
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
                            onClick = actions.onEditAddress,
                        ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    NaturalDirection {
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
                }

                ReaderButton(tab, actions.onReader, size = 44.dp)
                ReloadButton(tab, actions.onReloadOrStop, size = 44.dp)
                TabCountButton(count = tabCount, onClick = actions.onOpenTabs, onLongClick = actions.onNewTab)
                OaviIconButton(
                    icon = R.drawable.ic_more,
                    contentDescription = stringResource(R.string.cd_menu),
                    onClick = actions.onOpenMenu,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
    }
}

/** The island while reading: just the site name, small enough to forget. Tap to bring it back. */
@Composable
internal fun CompactIsland(tab: TabState?, onExpand: () -> Unit) {
    val colors = Oavi.colors
    val host = if (tab == null) "" else UrlInput.displayHost(tab.url)
    Row(
        modifier = Modifier
            .height(CompactHeight)
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
