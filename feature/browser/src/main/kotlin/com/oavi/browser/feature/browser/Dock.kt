package com.oavi.browser.feature.browser

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oavi.browser.engine.TabState
import com.oavi.browser.model.UrlInput
import com.oavi.browser.ui.Oavi
import com.oavi.browser.ui.OaviIconButton
import com.oavi.browser.ui.R
import com.oavi.browser.ui.glass

private val DockShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)

/**
 * The dock: address and toolbar together along the bottom edge, both within the thumb's
 * reach. The shield beside the address counts what this page tried to load and did not get.
 * While reading it sinks into a thin strip (see [DockStrip]).
 */
@Composable
internal fun Dock(
    tab: TabState?,
    tabCount: Int,
    leftHanded: Boolean,
    actions: ChromeActions,
) {
    val drag = remember { Animatable(0f) }
    Column(
        Modifier
            .fillMaxWidth()
            .glass(DockShape, elevation = 14.dp, opacity = 0.97f)
            .navigationBarsPadding(),
    ) {
        ProgressLine(tab)
        Mirrored(leftHanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Bars.DockAddress - 2.5.dp)
                    .barGestures(drag, actions.onSwitchTab, onUp = actions.onOpenTabs, onDown = actions.onCollapse)
                    .graphicsLayer { translationX = drag.value }
                    .padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AddressPill(
                    tab = tab,
                    onEdit = actions.onEditAddress,
                    onProtection = actions.onOpenProtection,
                    modifier = Modifier.weight(1f),
                    showProtection = false,
                ) {
                    ReaderButton(tab, actions.onReader)
                    ReloadButton(tab, actions.onReloadOrStop)
                }
                Spacer(Modifier.width(4.dp))
                ShieldCount(tab, actions.onOpenProtection)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Bars.DockButtons)
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OaviIconButton(R.drawable.ic_back, stringResource(R.string.cd_back), actions.onBack, Modifier.size(48.dp), enabled = tab?.canGoBack == true)
                OaviIconButton(R.drawable.ic_bookmark, stringResource(R.string.menu_library), actions.onOpenLibrary, Modifier.size(48.dp))
                AccentRoundButton(R.drawable.ic_plus, stringResource(R.string.menu_new_tab), actions.onNewTab)
                TabCountButton(tabCount, actions.onOpenTabs, actions.onNewTab, size = 48.dp)
                OaviIconButton(R.drawable.ic_more, stringResource(R.string.cd_menu), actions.onOpenMenu, Modifier.size(48.dp))
            }
        }
    }
}

/** The protection shield with the number of ads and trackers stopped on this page. */
@Composable
private fun ShieldCount(tab: TabState?, onClick: () -> Unit) {
    val colors = Oavi.colors
    val count = tab?.blockedCount ?: 0
    val off = tab?.protectionOff == true
    Box(contentAlignment = Alignment.Center) {
        OaviIconButton(
            icon = if (off) R.drawable.ic_shield_off else R.drawable.ic_shield,
            contentDescription = pluralStringResource(R.plurals.cd_protection_blocked, count, count),
            onClick = onClick,
            tint = if (off) colors.muted else colors.accent,
            background = colors.accentSoft,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape),
        )
        if (count > 0 && !off) {
            Text(
                text = if (count > 99) "99+" else count.toString(),
                style = Oavi.type.caption.copy(fontSize = 10.sp, lineHeight = 12.sp),
                color = colors.onAccent,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-2).dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.accent)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

/** The dock while reading: a thin strip with the site's name. Tap it to bring the dock back. */
@Composable
internal fun DockStrip(tab: TabState?, onExpand: () -> Unit) {
    val colors = Oavi.colors
    val host = if (tab == null) "" else UrlInput.displayHost(tab.url)
    val insecure = tab?.secure == false && tab.url.startsWith("http://")
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.raised.copy(alpha = 0.97f))
            .clickable(role = Role.Button, onClick = onExpand)
            .navigationBarsPadding()
            .height(Bars.DockStrip),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(if (insecure) R.drawable.ic_warning else R.drawable.ic_lock),
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = host,
                style = Oavi.type.caption,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 260.dp),
            )
        }
        ProgressLine(tab, Modifier.align(Alignment.TopCenter))
    }
}
