package com.oavi.browser

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oavi.browser.device.DeviceInfo
import com.oavi.browser.engine.BrowserEvent
import com.oavi.browser.feature.browser.BrowserNavigation
import com.oavi.browser.feature.browser.BrowserScreen
import com.oavi.browser.feature.onboarding.OnboardingScreen
import com.oavi.browser.feature.privacy.PrivateLockCover
import com.oavi.browser.feature.privacy.ProtectionSheet
import com.oavi.browser.feature.privacy.ScreenLock
import com.oavi.browser.feature.reader.ReaderControls
import com.oavi.browser.feature.settings.DeviceSummary
import com.oavi.browser.feature.settings.LibraryScreen
import com.oavi.browser.feature.settings.SettingsScreen
import com.oavi.browser.feature.tabs.TabsScreen
import com.oavi.browser.model.ThemeMode
import com.oavi.browser.ui.Oavi
import com.oavi.browser.ui.OaviSnackbarHost
import com.oavi.browser.ui.OaviTheme
import com.oavi.browser.ui.R
import com.oavi.browser.ui.SnackbarState

/** The full screens that can sit on top of the browser. Only one at a time. */
private enum class Overlay { NONE, TABS, LIBRARY, SETTINGS }

/** The whole interface: first-run, the browser, and the screens and sheets laid over it. */
@Composable
fun OaviApp(container: AppContainer, activity: MainActivity) {
    val loaded by container.loadedSettings.collectAsState()
    val state by container.tabs.state.collectAsState()
    val stats by container.blockedStats.collectAsState()
    val settings = loaded
    val selected = state.selected
    val controller = container.tabs

    val dark = when (settings?.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
    }
    val onPrivateTab = selected?.isPrivate == true && settings?.onboarded == true

    var overlay by rememberSaveable { mutableStateOf(Overlay.NONE) }
    var protectionOpen by remember { mutableStateOf(false) }
    var readerOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarState() }
    val context = LocalContext.current

    val defaultBrowserRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        activity.refreshDefaultBrowser()
    }
    val requestDefaultBrowser: () -> Unit = {
        activity.defaultBrowserIntent()?.let(defaultBrowserRequest::launch)
    }

    OaviTheme(
        dark = dark,
        private = onPrivateTab,
        siteAccent = selected?.themeColor?.let { Color(it) },
    ) {
        val colors = Oavi.colors

        LaunchedEffect(colors.isDark) { activity.setLightSystemBars(!colors.isDark) }
        LaunchedEffect(selected?.fullscreen) { activity.setFullscreen(selected?.fullscreen == true) }
        LaunchedEffect(onPrivateTab) { activity.setSecure(onPrivateTab) }
        LaunchedEffect(selected?.isPrivate, selected?.isBlank) {
            activity.updateHandoff(selected != null && !selected.isPrivate && !selected.isBlank)
        }
        LaunchedEffect(settings?.performanceBoost) { activity.applyPerformance() }

        LaunchedEffect(controller) {
            controller.events.collect { event ->
                when (event) {
                    is BrowserEvent.TabsClosed -> snackbar.show(
                        context.resources.getQuantityString(R.plurals.snack_tabs_closed, event.count, event.count),
                        context.getString(R.string.snack_undo),
                    ) { controller.undoClose() }

                    is BrowserEvent.DownloadStarted ->
                        snackbar.show(context.getString(R.string.snack_download_started, event.name))

                    is BrowserEvent.DownloadFinished -> snackbar.show(
                        context.getString(R.string.snack_download_done, event.name),
                        context.getString(R.string.snack_open),
                    ) { activity.openFile(event.uri, event.mimeType) }

                    is BrowserEvent.DownloadFailed ->
                        snackbar.show(context.getString(R.string.snack_download_failed, event.name))

                    BrowserEvent.NoAppForLink -> snackbar.show(context.getString(R.string.snack_no_app))
                    is BrowserEvent.PageSettled -> Unit
                }
            }
        }

        when {
            // The instant between process start and the saved state being read: just the canvas.
            settings == null || !state.ready -> Box(Modifier.fillMaxSize().background(colors.background))

            !settings.onboarded -> OnboardingScreen(
                isDefaultBrowser = activity.isDefaultBrowser,
                onRequestDefaultBrowser = requestDefaultBrowser,
                onFinish = { engine -> container.updateSettings { it.copy(searchEngine = engine, onboarded = true) } },
            )

            else -> {
                val locked = activity.privateLocked && settings.lockPrivateTabs
                val unlockTitle = stringResource(R.string.lock_prompt_title)
                val unlock: () -> Unit = {
                    ScreenLock.authenticate(activity, unlockTitle) { passed ->
                        if (passed) activity.privateLocked = false
                    }
                }
                val lockShowing = locked && onPrivateTab && overlay == Overlay.NONE
                // Returning to a locked private page asks straight away instead of waiting for a tap.
                LaunchedEffect(lockShowing) {
                    if (lockShowing) unlock()
                }

                Box(Modifier.fillMaxSize()) {
                    BrowserScreen(
                        controller = controller,
                        state = state,
                        settings = settings,
                        library = container.library,
                        blockedThisWeek = stats.thisWeek,
                        snackbar = snackbar,
                        covered = overlay != Overlay.NONE,
                        navigation = BrowserNavigation(
                            onOpenTabs = { overlay = Overlay.TABS },
                            onOpenLibrary = { overlay = Overlay.LIBRARY },
                            onOpenSettings = { overlay = Overlay.SETTINGS },
                            onOpenProtection = { protectionOpen = true },
                            onOpenReaderControls = { readerOpen = true },
                            onOpenDownloads = activity::openDownloads,
                        ),
                        onUpdateSettings = container::updateSettings,
                        pageCover = { PrivateLockCover(visible = locked && onPrivateTab, onUnlock = unlock) },
                    )

                    TabsScreen(
                        visible = overlay == Overlay.TABS,
                        controller = controller,
                        state = state,
                        privateLocked = locked,
                        onUnlock = unlock,
                        onClose = { overlay = Overlay.NONE },
                    )
                    LibraryScreen(
                        visible = overlay == Overlay.LIBRARY,
                        library = container.library,
                        snackbar = snackbar,
                        onOpen = { url ->
                            overlay = Overlay.NONE
                            controller.openFromOutside(url)
                        },
                        onClose = { overlay = Overlay.NONE },
                    )
                    val clearedMessage = stringResource(R.string.snack_site_data_cleared)
                    SettingsScreen(
                        visible = overlay == Overlay.SETTINGS,
                        settings = settings,
                        device = DeviceSummary(
                            profile = container.profile.kind,
                            chip = remember { DeviceInfo.chipLabel() },
                            version = BuildConfig.VERSION_NAME,
                            hardened = container.hardened,
                            screenLockAvailable = remember(overlay) { ScreenLock.isAvailable(context) },
                            isDefaultBrowser = activity.isDefaultBrowser,
                        ),
                        onUpdate = container::updateSettings,
                        onRequestDefaultBrowser = requestDefaultBrowser,
                        onClearSiteData = { controller.clearSiteData { snackbar.show(clearedMessage) } },
                        onClose = { overlay = Overlay.NONE },
                    )

                    ProtectionSheet(
                        visible = protectionOpen && overlay == Overlay.NONE,
                        tab = selected,
                        settings = settings,
                        blockedTotal = stats.total,
                        hardened = container.hardened,
                        onProtectSite = { protect -> controller.setProtectionOff(!protect) },
                        onOpenSettings = {
                            protectionOpen = false
                            overlay = Overlay.SETTINGS
                        },
                        onDismiss = { protectionOpen = false },
                    )
                    ReaderControls(
                        visible = readerOpen && selected?.readerActive == true && overlay == Overlay.NONE,
                        settings = settings,
                        onFontScale = { scale -> container.updateSettings { it.copy(readerFontScale = scale) } },
                        onTheme = { theme -> container.updateSettings { it.copy(readerTheme = theme) } },
                        onExit = {
                            readerOpen = false
                            controller.toggleReader()
                        },
                        onDismiss = { readerOpen = false },
                    )

                    OaviSnackbarHost(
                        state = snackbar,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(bottom = 88.dp),
                    )
                }
            }
        }
    }
}
