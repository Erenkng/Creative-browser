package com.oavi.browser.feature.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.oavi.browser.engine.TabState
import com.oavi.browser.model.LayoutStyle
import com.oavi.browser.model.UrlInput
import com.oavi.browser.ui.Oavi
import com.oavi.browser.ui.OaviIconButton
import com.oavi.browser.ui.OaviMotion
import com.oavi.browser.ui.R
import com.oavi.browser.ui.rememberHaptics
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Sizes of the bars each layout puts around the page. */
internal object Bars {
    val TopBar = 56.dp
    val TabStrip = 42.dp
    val Toolbar = 56.dp
    val DockAddress = 58.dp
    val DockButtons = 54.dp
    val DockStrip = 30.dp
    val Handle = 46.dp
    val FocusMargin = 8.dp
    val FocusRadius = 22.dp
}

/** The room the bars take at the top of the screen, below the status bar. The page starts under it. */
internal fun LayoutStyle.topSpace(): Dp = when (this) {
    LayoutStyle.CLASSIC -> Bars.TopBar
    LayoutStyle.STRIP -> Bars.TopBar + Bars.TabStrip
    LayoutStyle.FOCUS -> Bars.FocusMargin
    LayoutStyle.ISLAND, LayoutStyle.DOCK -> 0.dp
}

/**
 * How much of the bottom of the page the bars cover in [mode]. The engine keeps the page's
 * own bottom bars (cookie notices, "Accept" buttons) above this line.
 */
internal fun LayoutStyle.bottomCover(mode: IslandMode): Dp = when (this) {
    LayoutStyle.ISLAND -> if (mode == IslandMode.COMPACT) IslandCompactCover else IslandRestingCover
    LayoutStyle.CLASSIC -> 0.dp
    LayoutStyle.STRIP -> if (mode == IslandMode.COMPACT) 0.dp else Bars.Toolbar
    LayoutStyle.DOCK -> if (mode == IslandMode.COMPACT) Bars.DockStrip else Bars.DockAddress + Bars.DockButtons
    LayoutStyle.FOCUS -> Bars.Handle + Bars.FocusMargin
}

/** Room left free at the bottom of the new-tab page so nothing hides under the bars. */
internal fun LayoutStyle.homeBottomSpace(): Dp = when (this) {
    LayoutStyle.ISLAND -> 104.dp
    LayoutStyle.CLASSIC -> 24.dp
    LayoutStyle.STRIP -> Bars.Toolbar + 24.dp
    LayoutStyle.DOCK -> Bars.DockAddress + Bars.DockButtons + 20.dp
    LayoutStyle.FOCUS -> Bars.Handle + 28.dp
}

/** Whether the bars tuck themselves away while reading (when the user allows it). */
internal val LayoutStyle.collapsesWhileReading: Boolean
    get() = this == LayoutStyle.ISLAND || this == LayoutStyle.STRIP || this == LayoutStyle.DOCK

/** Address entry, menu and find open at the top of the screen in these layouts. */
internal val LayoutStyle.panelsOnTop: Boolean
    get() = this == LayoutStyle.CLASSIC || this == LayoutStyle.STRIP

/** The top of the page sits directly under the status bar, so the status bar takes the page's colour. */
val LayoutStyle.pageUnderStatusBar: Boolean
    get() = this == LayoutStyle.ISLAND || this == LayoutStyle.DOCK

/** How far above the bottom edge messages ("Link copied") appear, clear of the bars. */
fun LayoutStyle.messageLift(): Dp = when (this) {
    LayoutStyle.ISLAND -> 88.dp
    LayoutStyle.CLASSIC -> 20.dp
    LayoutStyle.STRIP -> Bars.Toolbar + 12.dp
    LayoutStyle.DOCK -> Bars.DockAddress + Bars.DockButtons + 12.dp
    LayoutStyle.FOCUS -> Bars.Handle + 24.dp
}

/** Everything the bars can ask the browser to do. */
internal class ChromeActions(
    val onEditAddress: () -> Unit,
    val onOpenProtection: () -> Unit,
    val onReloadOrStop: () -> Unit,
    val onReader: () -> Unit,
    val onOpenTabs: () -> Unit,
    val onNewTab: () -> Unit,
    val onOpenMenu: () -> Unit,
    val onSwitchTab: (forward: Boolean) -> Unit,
    val onCollapse: () -> Unit,
    val onExpand: () -> Unit,
    val onBack: () -> Unit,
    val onForward: () -> Unit,
    val onOpenLibrary: () -> Unit,
    val onSelectTab: (String) -> Unit,
    val onCloseTab: (String) -> Unit,
)

/** The reading direction of the language, kept for text inside mirrored (left-handed) bars. */
internal val LocalNaturalDirection = staticCompositionLocalOf { LayoutDirection.Ltr }

/** Lays [content] out mirrored when [mirrored], so the most used buttons land under the left thumb. */
@Composable
internal fun Mirrored(mirrored: Boolean, content: @Composable () -> Unit) {
    val natural = LocalNaturalDirection.current
    val direction = if (!mirrored) natural else if (natural == LayoutDirection.Ltr) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction, content = content)
}

/** Restores the language's own direction, for text and fields inside a mirrored row. */
@Composable
internal fun NaturalDirection(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LocalNaturalDirection.current, content = content)
}

/**
 * Oavi's bar gestures, shared by every layout: slide sideways to change tab, flick up or down
 * for [onUp] / [onDown]. [drag] follows the finger sideways so the bar visibly gives way.
 */
@Composable
internal fun Modifier.barGestures(
    drag: Animatable<Float, AnimationVector1D>,
    onSwitchTab: (forward: Boolean) -> Unit,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
): Modifier {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val switchTab by rememberUpdatedState(onSwitchTab)
    val up by rememberUpdatedState(onUp)
    val down by rememberUpdatedState(onDown)
    return this.pointerInput(Unit) {
        val switchAt = 64.dp.toPx()
        val verticalAt = 30.dp.toPx()
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
                    horizontal == false && totalY < -verticalAt -> up?.invoke()
                    horizontal == false && totalY > verticalAt -> down?.invoke()
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
    }
}

/** A thin accent line that runs across the bar while the page loads. */
@Composable
internal fun ProgressLine(tab: TabState?, modifier: Modifier = Modifier) {
    val target = if (tab != null && tab.loading) (tab.progress / 100f).coerceIn(0.06f, 1f) else 0f
    val fill by animateFloatAsState(target, OaviMotion.gentle(), label = "progressLine")
    val visible by animateFloatAsState(if (tab?.loading == true) 1f else 0f, tween(300), label = "progressAlpha")
    val color = Oavi.colors.accent
    Box(
        modifier
            .fillMaxWidth()
            .height(2.5.dp)
            .drawBehind {
                if (visible > 0f && fill > 0f) {
                    drawRect(color, topLeft = Offset.Zero, size = Size(size.width * fill, size.height), alpha = visible)
                }
            },
    )
}

/** The fill behind an address that is not being edited. */
@Composable
internal fun pillColor(): Color {
    val colors = Oavi.colors
    return if (colors.isDark) Color.White.copy(alpha = 0.08f) else colors.faint.copy(alpha = 0.55f)
}

/**
 * The address as a rounded field: the site's state on the left, the host in the middle,
 * [trailing] actions on the right. Tap the host to type a new address.
 *
 * @param showProtection the leading icon opens the protection sheet; otherwise it only shows
 *   whether the connection is private.
 */
@Composable
internal fun AddressPill(
    tab: TabState?,
    onEdit: () -> Unit,
    onProtection: () -> Unit,
    modifier: Modifier = Modifier,
    showProtection: Boolean = true,
    height: Dp = 44.dp,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = Oavi.colors
    val blank = tab == null || tab.isBlank
    val host = if (tab == null) "" else UrlInput.displayHost(tab.url).ifEmpty { tab.url }
    val insecure = tab != null && !blank && !tab.secure && tab.url.startsWith("http://")
    NaturalDirection {
        Row(
            modifier = modifier
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(pillColor())
                .padding(end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (blank) {
                Spacer(Modifier.width(14.dp))
                Icon(
                    painterResource(if (tab?.isPrivate == true) R.drawable.ic_private_mode else R.drawable.ic_search),
                    contentDescription = null,
                    tint = colors.muted,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
            } else if (showProtection) {
                OaviIconButton(
                    icon = when {
                        insecure -> R.drawable.ic_warning
                        tab?.protectionOff == true -> R.drawable.ic_shield_off
                        else -> R.drawable.ic_shield
                    },
                    contentDescription = stringResource(R.string.cd_protection),
                    onClick = onProtection,
                    tint = if (insecure) colors.danger else colors.accent,
                    modifier = Modifier.size(40.dp),
                )
            } else {
                Spacer(Modifier.width(12.dp))
                Icon(
                    painterResource(if (insecure) R.drawable.ic_warning else R.drawable.ic_lock),
                    contentDescription = null,
                    tint = if (insecure) colors.danger else colors.muted,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onEdit,
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = if (blank) stringResource(R.string.island_placeholder) else host,
                    style = Oavi.type.label,
                    color = if (blank) colors.muted else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
            trailing()
        }
    }
}

/** The reading-view button, shown only on pages that can be read that way. */
@Composable
internal fun ReaderButton(tab: TabState?, onClick: () -> Unit, size: Dp = 40.dp) {
    val colors = Oavi.colors
    AnimatedVisibility(
        visible = tab != null && (tab.readerAvailable || tab.readerActive),
        enter = fadeIn(OaviMotion.fade()) + expandHorizontally(OaviMotion.spatial()),
        exit = fadeOut(OaviMotion.fade()) + shrinkHorizontally(OaviMotion.spatial()),
    ) {
        OaviIconButton(
            icon = R.drawable.ic_reader,
            contentDescription = stringResource(R.string.cd_reader),
            onClick = onClick,
            tint = if (tab?.readerActive == true) colors.onAccent else colors.onSurface,
            background = if (tab?.readerActive == true) colors.accent else Color.Transparent,
            modifier = Modifier.size(size),
        )
    }
}

@Composable
internal fun ReloadButton(tab: TabState?, onClick: () -> Unit, size: Dp = 40.dp) {
    if (tab == null || tab.isBlank) return
    OaviIconButton(
        icon = if (tab.loading) R.drawable.ic_close else R.drawable.ic_reload,
        contentDescription = stringResource(if (tab.loading) R.string.cd_stop else R.string.cd_reload),
        onClick = onClick,
        modifier = Modifier.size(size),
    )
}

/** The number of open tabs in a rounded square. Tap for the tabs, hold for a new one. */
@Composable
internal fun TabCountButton(count: Int, onClick: () -> Unit, onLongClick: () -> Unit, size: Dp = 44.dp) {
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
            modifier = Modifier.size(size),
        )
        Text(
            text = if (count > 99) "∞" else count.toString(),
            style = Oavi.type.caption,
            color = Oavi.colors.onSurface,
        )
    }
}

/** A round accent button, the one action a toolbar is built around. */
@Composable
internal fun AccentRoundButton(icon: Int, contentDescription: String, onClick: () -> Unit, size: Dp = 44.dp) {
    val colors = Oavi.colors
    OaviIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        tint = colors.onAccent,
        background = colors.accent,
        modifier = Modifier
            .size(size)
            .clip(CircleShape),
    )
}
