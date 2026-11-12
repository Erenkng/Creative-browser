package com.oavi.browser.feature.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oavi.browser.engine.TabState
import com.oavi.browser.model.Settings
import com.oavi.browser.model.TrackingProtection
import com.oavi.browser.model.UrlInput
import com.oavi.browser.ui.Oavi
import com.oavi.browser.ui.OaviRow
import com.oavi.browser.ui.OaviSheet
import com.oavi.browser.ui.OaviSwitchRow
import com.oavi.browser.ui.R

/**
 * What Oavi is doing for the user on this page: how much it kept out, whether the
 * connection is private, and one switch to stand down when a site misbehaves.
 *
 * @param hardened Android's Advanced Protection Mode is on, so the strictest settings apply.
 */
@Composable
fun ProtectionSheet(
    visible: Boolean,
    tab: TabState?,
    settings: Settings,
    blockedTotal: Long,
    hardened: Boolean,
    onProtectSite: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Keep the last page's details on screen while the sheet slides away.
    var last by remember { mutableStateOf(tab) }
    if (tab != null) last = tab
    val shown = last

    OaviSheet(visible = visible && tab != null, onDismiss = onDismiss) {
        if (shown == null) return@OaviSheet
        val colors = Oavi.colors
        val blockingOn = settings.adBlock || settings.trackingProtection != TrackingProtection.OFF || hardened
        val insecure = !shown.secure && shown.url.startsWith("http://")

        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (shown.protectionOff || !blockingOn) R.drawable.ic_shield_off else R.drawable.ic_shield),
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(UrlInput.displayHost(shown.url), style = Oavi.type.title, color = colors.onSurface, maxLines = 1)
                Text(
                    text = when {
                        !blockingOn -> stringResource(R.string.protection_off_globally)
                        shown.blockedCount > 0 -> pluralStringResource(R.plurals.protection_blocked, shown.blockedCount, shown.blockedCount)
                        else -> stringResource(R.string.protection_nothing)
                    },
                    style = Oavi.type.body,
                    color = colors.muted,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        OaviRow(
            title = stringResource(if (insecure) R.string.protection_insecure else R.string.protection_secure),
            subtitle = stringResource(if (insecure) R.string.protection_insecure_body else R.string.protection_secure_body),
            icon = if (insecure) R.drawable.ic_warning else R.drawable.ic_lock,
            iconTint = if (insecure) colors.danger else colors.accent,
            titleColor = if (insecure) colors.danger else colors.onSurface,
        )
        if (blockingOn) {
            OaviSwitchRow(
                title = stringResource(R.string.protection_site_toggle),
                subtitle = stringResource(R.string.protection_site_toggle_hint),
                icon = R.drawable.ic_shield,
                checked = !shown.protectionOff,
                onCheckedChange = { protect -> onProtectSite(protect) },
            )
        }
        if (hardened) {
            Text(
                text = stringResource(R.string.protection_hardened),
                style = Oavi.type.caption,
                color = colors.muted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        if (blockedTotal > 0) {
            val total = blockedTotal.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            Text(
                text = pluralStringResource(R.plurals.protection_total, total, total),
                style = Oavi.type.caption,
                color = colors.muted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        OaviRow(
            title = stringResource(R.string.protection_open_settings),
            icon = R.drawable.ic_settings,
            onClick = onOpenSettings,
            trailing = {
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.muted, modifier = Modifier.size(20.dp))
            },
        )
    }
}
