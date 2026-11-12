package com.oavi.browser.feature.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oavi.browser.engine.TabState
import com.oavi.browser.ui.Oavi
import com.oavi.browser.ui.OaviChip
import com.oavi.browser.ui.OaviIconButton
import com.oavi.browser.ui.OaviMotion
import com.oavi.browser.ui.OaviRow
import com.oavi.browser.ui.R
import com.oavi.browser.ui.pressScale

internal class MenuActions(
    val onBack: () -> Unit,
    val onForward: () -> Unit,
    val onShare: () -> Unit,
    val onNewTab: () -> Unit,
    val onNewPrivateTab: () -> Unit,
    val onToggleBookmark: () -> Unit,
    val onReader: () -> Unit,
    val onToggleDarkPages: () -> Unit,
    val onFind: () -> Unit,
    val onLibrary: () -> Unit,
    val onDownloads: () -> Unit,
    val onToggleDesktop: () -> Unit,
    val onSettings: () -> Unit,
    val onClose: () -> Unit,
)

/**
 * The island as a menu. Six everyday actions are always in view; the occasional ones wait
 * behind "More" so a glance is enough to choose. Navigation sits on the bottom row,
 * directly under the thumb.
 */
@Composable
internal fun MenuPanel(
    tab: TabState?,
    bookmarked: Boolean,
    darkPages: Boolean,
    actions: MenuActions,
) {
    var expanded by remember { mutableStateOf(false) }
    val onPage = tab != null && !tab.isBlank

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            MenuTile(R.drawable.ic_plus, stringResource(R.string.menu_new_tab), onClick = actions.onNewTab)
            MenuTile(R.drawable.ic_private_mode, stringResource(R.string.menu_private_tab), onClick = actions.onNewPrivateTab)
            MenuTile(
                icon = if (bookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark,
                label = stringResource(if (bookmarked) R.string.menu_bookmarked else R.string.menu_bookmark),
                active = bookmarked,
                enabled = onPage && tab?.isPrivate == false,
                onClick = actions.onToggleBookmark,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            MenuTile(
                icon = R.drawable.ic_reader,
                label = stringResource(R.string.menu_reader),
                active = tab?.readerActive == true,
                enabled = tab != null && (tab.readerAvailable || tab.readerActive),
                onClick = actions.onReader,
            )
            MenuTile(
                icon = R.drawable.ic_moon,
                label = stringResource(R.string.menu_dark_pages),
                active = darkPages,
                onClick = actions.onToggleDarkPages,
            )
            MenuTile(R.drawable.ic_find, stringResource(R.string.menu_find), enabled = onPage, onClick = actions.onFind)
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(OaviMotion.spatial()) + fadeIn(OaviMotion.fade()),
            exit = shrinkVertically(OaviMotion.spatial()) + fadeOut(OaviMotion.fade()),
        ) {
            Column(Modifier.padding(top = 10.dp)) {
                OaviRow(stringResource(R.string.menu_library), icon = R.drawable.ic_history, onClick = actions.onLibrary)
                OaviRow(stringResource(R.string.menu_downloads), icon = R.drawable.ic_download, onClick = actions.onDownloads)
                OaviRow(
                    title = stringResource(R.string.menu_desktop_site),
                    icon = R.drawable.ic_desktop,
                    enabled = onPage,
                    onClick = actions.onToggleDesktop,
                    trailing = if (tab?.desktopMode == true) {
                        { Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Oavi.colors.accent, modifier = Modifier.size(22.dp)) }
                    } else {
                        null
                    },
                )
                OaviRow(stringResource(R.string.menu_settings), icon = R.drawable.ic_settings, onClick = actions.onSettings)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OaviIconButton(R.drawable.ic_back, stringResource(R.string.cd_back), actions.onBack, enabled = tab?.canGoBack == true)
            OaviIconButton(R.drawable.ic_forward, stringResource(R.string.cd_forward), actions.onForward, enabled = tab?.canGoForward == true)
            OaviIconButton(R.drawable.ic_share, stringResource(R.string.cd_share), actions.onShare, enabled = onPage)
            Spacer(Modifier.weight(1f))
            OaviChip(
                text = stringResource(if (expanded) R.string.menu_less else R.string.menu_more),
                selected = false,
                onClick = { expanded = !expanded },
            )
            OaviIconButton(R.drawable.ic_close, stringResource(R.string.cd_close), actions.onClose)
        }
    }
}

@Composable
private fun RowScope.MenuTile(
    icon: Int,
    label: String,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = Oavi.colors
    val container by animateColorAsState(if (active) colors.accent else colors.accentSoft, OaviMotion.fade(), label = "tile")
    val content by animateColorAsState(if (active) colors.onAccent else colors.onSurface, OaviMotion.fade(), label = "tileIcon")
    val source = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .weight(1f)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .pressScale(source)
            .clip(RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = Oavi.type.caption,
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
