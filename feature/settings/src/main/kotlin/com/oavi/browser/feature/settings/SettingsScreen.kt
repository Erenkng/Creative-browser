package com.oavi.browser.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oavi.browser.model.ProfileKind
import com.oavi.browser.model.SearchEngine
import com.oavi.browser.model.Settings
import com.oavi.browser.model.ThemeMode
import com.oavi.browser.model.TrackingProtection
import com.oavi.browser.ui.Oavi
import com.oavi.browser.ui.OaviChip
import com.oavi.browser.ui.OaviPrompt
import com.oavi.browser.ui.OaviRow
import com.oavi.browser.ui.OaviScreen
import com.oavi.browser.ui.OaviScreenBottomInset
import com.oavi.browser.ui.OaviSwitchRow
import com.oavi.browser.ui.R
import com.oavi.browser.ui.SectionTitle

/** Facts about this phone and install that the settings screen shows but does not change. */
data class DeviceSummary(
    val profile: ProfileKind,
    val chip: String,
    val version: String,
    /** Android's Advanced Protection Mode is on; some protections cannot be relaxed. */
    val hardened: Boolean,
    val screenLockAvailable: Boolean,
    val isDefaultBrowser: Boolean,
)

@Composable
fun SettingsScreen(
    visible: Boolean,
    settings: Settings,
    device: DeviceSummary,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onRequestDefaultBrowser: () -> Unit,
    onClearSiteData: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by remember { mutableStateOf(false) }

    OaviScreen(
        visible = visible,
        onClose = onClose,
        title = stringResource(R.string.settings_title),
        closeLabel = stringResource(R.string.tabs_done),
        modifier = modifier,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = OaviScreenBottomInset),
        ) {
            SectionTitle(stringResource(R.string.settings_section_search))
            ChipRow {
                for (engine in SearchEngine.entries) {
                    OaviChip(
                        text = engine.displayName,
                        selected = settings.searchEngine == engine,
                        onClick = { onUpdate { it.copy(searchEngine = engine) } },
                    )
                }
            }

            SectionTitle(stringResource(R.string.settings_section_privacy))
            OaviSwitchRow(
                title = stringResource(R.string.settings_ad_block),
                subtitle = stringResource(R.string.settings_ad_block_body),
                icon = R.drawable.ic_shield,
                checked = settings.adBlock,
                onCheckedChange = { value -> onUpdate { it.copy(adBlock = value) } },
            )
            OaviRow(
                title = stringResource(R.string.settings_tracking),
                subtitle = stringResource(if (device.hardened) R.string.protection_hardened else R.string.settings_tracking_body),
                icon = R.drawable.ic_private_mode,
            )
            ChipRow {
                val effective = if (device.hardened) TrackingProtection.STRICT else settings.trackingProtection
                TrackingChip(TrackingProtection.OFF, R.string.settings_tracking_off, effective, !device.hardened, onUpdate)
                TrackingChip(TrackingProtection.STANDARD, R.string.settings_tracking_standard, effective, !device.hardened, onUpdate)
                TrackingChip(TrackingProtection.STRICT, R.string.settings_tracking_strict, effective, true, onUpdate)
            }
            OaviSwitchRow(
                title = stringResource(R.string.settings_https_only),
                subtitle = stringResource(R.string.settings_https_only_body),
                icon = R.drawable.ic_lock,
                checked = settings.httpsOnly || device.hardened,
                enabled = !device.hardened,
                onCheckedChange = { value -> onUpdate { it.copy(httpsOnly = value) } },
            )
            OaviSwitchRow(
                title = stringResource(R.string.settings_lock_private),
                subtitle = stringResource(R.string.settings_lock_private_body),
                icon = R.drawable.ic_lock,
                checked = settings.lockPrivateTabs && device.screenLockAvailable,
                enabled = device.screenLockAvailable,
                onCheckedChange = { value -> onUpdate { it.copy(lockPrivateTabs = value) } },
            )
            OaviRow(
                title = stringResource(R.string.settings_clear_data),
                subtitle = stringResource(R.string.settings_clear_data_body),
                icon = R.drawable.ic_trash,
                onClick = { confirmClear = true },
            )

            SectionTitle(stringResource(R.string.settings_section_appearance))
            ChipRow {
                ThemeChip(ThemeMode.SYSTEM, R.string.settings_theme_system, settings, onUpdate)
                ThemeChip(ThemeMode.LIGHT, R.string.settings_theme_light, settings, onUpdate)
                ThemeChip(ThemeMode.DARK, R.string.settings_theme_dark, settings, onUpdate)
            }
            Spacer(Modifier.height(6.dp))
            OaviSwitchRow(
                title = stringResource(R.string.settings_dark_pages),
                subtitle = stringResource(R.string.settings_dark_pages_body),
                icon = R.drawable.ic_moon,
                checked = settings.forceDarkPages,
                onCheckedChange = { value -> onUpdate { it.copy(forceDarkPages = value) } },
            )
            OaviSwitchRow(
                title = stringResource(R.string.settings_collapse_island),
                subtitle = stringResource(R.string.settings_collapse_island_body),
                icon = R.drawable.ic_chevron_down,
                checked = settings.collapseIslandOnScroll,
                onCheckedChange = { value -> onUpdate { it.copy(collapseIslandOnScroll = value) } },
            )

            SectionTitle(stringResource(R.string.settings_section_performance))
            OaviRow(
                title = stringResource(
                    when (device.profile) {
                        ProfileKind.EXYNOS_1480 -> R.string.settings_profile_exynos_1480
                        ProfileKind.EXYNOS -> R.string.settings_profile_exynos
                        ProfileKind.STANDARD -> R.string.settings_profile_standard
                        ProfileKind.LOW_MEMORY -> R.string.settings_profile_low_memory
                    },
                ),
                subtitle = if (device.chip.isBlank()) null else stringResource(R.string.settings_chip, device.chip),
                icon = R.drawable.ic_bolt,
                iconTint = Oavi.colors.accent,
            )
            OaviSwitchRow(
                title = stringResource(R.string.settings_boost),
                subtitle = stringResource(R.string.settings_boost_body),
                icon = R.drawable.ic_bolt,
                checked = settings.performanceBoost,
                onCheckedChange = { value -> onUpdate { it.copy(performanceBoost = value) } },
            )

            SectionTitle(stringResource(R.string.settings_section_general))
            if (!device.isDefaultBrowser) {
                OaviRow(
                    title = stringResource(R.string.settings_default_browser),
                    subtitle = stringResource(R.string.settings_default_browser_body),
                    icon = R.drawable.ic_link,
                    onClick = onRequestDefaultBrowser,
                )
            }
            Text(
                text = stringResource(R.string.settings_version, device.version),
                style = Oavi.type.caption,
                color = Oavi.colors.muted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            )
        }
    }

    OaviPrompt(
        visible = visible && confirmClear,
        onDismiss = { confirmClear = false },
        title = stringResource(R.string.settings_clear_data_confirm_title),
        message = stringResource(R.string.settings_clear_data_confirm_body),
        confirmLabel = stringResource(R.string.settings_clear_data_confirm),
        dismissLabel = stringResource(R.string.request_cancel),
        danger = true,
        onConfirm = {
            confirmClear = false
            onClearSiteData()
        },
    )
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun TrackingChip(
    level: TrackingProtection,
    label: Int,
    current: TrackingProtection,
    selectable: Boolean,
    onUpdate: ((Settings) -> Settings) -> Unit,
) {
    OaviChip(
        text = stringResource(label),
        selected = current == level,
        onClick = { if (selectable) onUpdate { it.copy(trackingProtection = level) } },
    )
}

@Composable
private fun ThemeChip(
    mode: ThemeMode,
    label: Int,
    settings: Settings,
    onUpdate: ((Settings) -> Settings) -> Unit,
) {
    OaviChip(
        text = stringResource(label),
        selected = settings.themeMode == mode,
        onClick = { onUpdate { it.copy(themeMode = mode) } },
    )
}
