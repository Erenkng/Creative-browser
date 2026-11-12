package com.oavi.browser.feature.browser

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oavi.browser.model.DayPart
import com.oavi.browser.model.HistoryEntry
import com.oavi.browser.model.Tab
import com.oavi.browser.model.UrlInput
import com.oavi.browser.ui.ButtonKind
import com.oavi.browser.ui.LetterTile
import com.oavi.browser.ui.Oavi
import com.oavi.browser.ui.OaviButton
import com.oavi.browser.ui.OaviMotion
import com.oavi.browser.ui.R
import com.oavi.browser.ui.pressScale
import java.util.Calendar

/** One shortcut per site, most visited first. */
internal fun topSiteShortcuts(entries: List<HistoryEntry>, limit: Int = 8): List<HistoryEntry> {
    val seen = HashSet<String>()
    val result = ArrayList<HistoryEntry>()
    for (entry in entries) {
        val host = UrlInput.hostOf(entry.url)?.let(UrlInput::baseDomain) ?: continue
        if (seen.add(host)) result += entry
        if (result.size == limit) break
    }
    return result
}

/**
 * What a new tab shows. Everything hangs from the bottom of the screen, just above the
 * island, so the things worth tapping are already within reach.
 */
@Composable
internal fun HomePage(
    private: Boolean,
    resume: List<Tab>,
    topSites: List<HistoryEntry>,
    blockedThisWeek: Long,
    onOpenUrl: (String) -> Unit,
    onSelectTab: (String) -> Unit,
    modifier: Modifier = Modifier,
    showSwipeTip: Boolean = false,
    onDismissSwipeTip: () -> Unit = {},
) {
    val colors = Oavi.colors
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState(), reverseScrolling = true)
                // At least as tall as the screen, so short content still rests at the bottom.
                .heightIn(min = maxHeight)
                .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (private) {
                PrivateIntro()
            } else {
                HomeContent(resume, topSites, blockedThisWeek, onOpenUrl, onSelectTab, showSwipeTip, onDismissSwipeTip)
            }
        }
    }
}

@Composable
private fun HomeContent(
    resume: List<Tab>,
    topSites: List<HistoryEntry>,
    blockedThisWeek: Long,
    onOpenUrl: (String) -> Unit,
    onSelectTab: (String) -> Unit,
    showSwipeTip: Boolean,
    onDismissSwipeTip: () -> Unit,
) {
    val colors = Oavi.colors
    Column {

        val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
        Text(
            text = stringResource(
                when (DayPart.of(hour)) {
                    DayPart.MORNING -> R.string.home_morning
                    DayPart.AFTERNOON -> R.string.home_afternoon
                    DayPart.EVENING -> R.string.home_evening
                    DayPart.NIGHT -> R.string.home_night
                },
            ),
            style = Oavi.type.display,
            color = colors.onSurface,
        )
        if (blockedThisWeek > 0) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_shield), contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                val count = blockedThisWeek.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                Text(
                    text = pluralStringResource(R.plurals.home_blocked_week, count, count),
                    style = Oavi.type.label,
                    color = colors.muted,
                )
            }
        }

        if (resume.isNotEmpty()) {
            Spacer(Modifier.height(30.dp))
            Text(stringResource(R.string.home_resume_title), style = Oavi.type.caption, color = colors.muted)
            Spacer(Modifier.height(10.dp))
            for (tab in resume) {
                ResumeCard(tab) { onSelectTab(tab.id) }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (topSites.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Text(stringResource(R.string.home_top_sites), style = Oavi.type.caption, color = colors.muted)
            Spacer(Modifier.height(12.dp))
            for (row in topSites.chunked(4)) {
                Row(Modifier.fillMaxWidth()) {
                    for (site in row) {
                        SiteShortcut(site, Modifier.weight(1f)) { onOpenUrl(site.url) }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(14.dp))
            }
        }

        // Closest to the island, because it is about the island.
        AnimatedVisibility(
            visible = showSwipeTip,
            enter = expandVertically(OaviMotion.spatial()) + fadeIn(OaviMotion.fade()),
            exit = shrinkVertically(OaviMotion.spatial()) + fadeOut(OaviMotion.fade()),
        ) {
            SwipeTip(onDismissSwipeTip)
        }
    }
}

/**
 * The island's swipes are invisible until someone shows them. Shown once there are two tabs
 * to swipe between, and gone for good after the first swipe or a tap on "Got it".
 */
@Composable
private fun SwipeTip(onDismiss: () -> Unit) {
    val colors = Oavi.colors
    Row(
        modifier = Modifier
            .padding(top = 18.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.accentSoft)
            .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_tab), contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.home_tip_title), style = Oavi.type.label, color = colors.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(R.string.home_tip_body), style = Oavi.type.caption, color = colors.muted)
        }
        OaviButton(
            text = stringResource(R.string.home_tip_dismiss),
            onClick = onDismiss,
            kind = ButtonKind.QUIET,
            modifier = Modifier.height(44.dp),
        )
    }
}

@Composable
private fun ResumeCard(tab: Tab, onClick: () -> Unit) {
    val colors = Oavi.colors
    val source = remember { MutableInteractionSource() }
    val host = UrlInput.displayHost(tab.url)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(source, pressed = 0.97f)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surface)
            .clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LetterTile(host.ifEmpty { tab.title })
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(tab.label, style = Oavi.type.body, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(host, style = Oavi.type.caption, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SiteShortcut(site: HistoryEntry, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val host = UrlInput.displayHost(site.url)
    Column(
        modifier = modifier
            .pressScale(source)
            .clip(RoundedCornerShape(18.dp))
            .clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LetterTile(host, size = 52.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = UrlInput.baseDomain(host).substringBefore('.'),
            style = Oavi.type.caption,
            color = Oavi.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 76.dp),
        )
    }
}

@Composable
private fun PrivateIntro() {
    val colors = Oavi.colors
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(colors.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_private_mode), contentDescription = null, tint = colors.accent, modifier = Modifier.size(30.dp))
    }
    Spacer(Modifier.height(20.dp))
    Text(stringResource(R.string.home_private_title), style = Oavi.type.display, color = colors.onSurface)
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.home_private_not_saved), style = Oavi.type.body, color = colors.onSurface)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.home_private_still_visible), style = Oavi.type.body, color = colors.muted)
}

/** Shown in place of a page whose content process stopped. */
@Composable
internal fun CrashedPage(onReload: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Oavi.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(stringResource(R.string.crash_title), style = Oavi.type.display, color = colors.onSurface)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.crash_body), style = Oavi.type.body, color = colors.muted)
        Spacer(Modifier.height(22.dp))
        com.oavi.browser.ui.OaviButton(stringResource(R.string.crash_reload), onClick = onReload, icon = R.drawable.ic_reload)
    }
}
