package com.oavi.browser.feature.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.oavi.browser.data.LibraryStore
import com.oavi.browser.engine.BrowserEvent
import com.oavi.browser.engine.BrowserState
import com.oavi.browser.engine.EngineView
import com.oavi.browser.engine.TabsController
import com.oavi.browser.model.HistoryEntry
import com.oavi.browser.model.Settings
import com.oavi.browser.model.TabRules
import com.oavi.browser.ui.Oavi
import com.oavi.browser.ui.OaviMotion
import com.oavi.browser.ui.R
import com.oavi.browser.ui.SnackbarState
import com.oavi.browser.ui.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where the menu and island send the user beyond the page itself. */
class BrowserNavigation(
    val onOpenTabs: () -> Unit,
    val onOpenLibrary: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenProtection: () -> Unit,
    val onOpenReaderControls: () -> Unit,
    val onOpenDownloads: () -> Unit,
)

/**
 * The browser itself: the page, and the island floating over it.
 *
 * @param covered another full screen (tabs, settings …) is on top, so the island steps aside.
 * @param pageCover drawn over the page but under the island, e.g. the lock for private tabs.
 */
@Composable
fun BrowserScreen(
    controller: TabsController,
    state: BrowserState,
    settings: Settings,
    library: LibraryStore,
    blockedThisWeek: Long,
    snackbar: SnackbarState,
    covered: Boolean,
    navigation: BrowserNavigation,
    onUpdateSettings: ((Settings) -> Settings) -> Unit,
    modifier: Modifier = Modifier,
    pageCover: @Composable () -> Unit = {},
) {
    val colors = Oavi.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val selected = state.selected
    val fullscreen = selected?.fullscreen == true
    val blank = selected == null || selected.isBlank

    var mode by rememberSaveable { mutableStateOf(IslandMode.RESTING) }
    var engineView by remember { mutableStateOf<EngineView?>(null) }
    val currentMode by rememberUpdatedState(mode)
    val collapseOnScroll by rememberUpdatedState(settings.collapseIslandOnScroll && !blank)

    val linkCopied = stringResource(R.string.snack_link_copied)
    val bookmarkAdded = stringResource(R.string.snack_bookmark_added)
    val bookmarkRemoved = stringResource(R.string.snack_bookmark_removed)
    val shareTitle = stringResource(R.string.cd_share)

    fun copy(text: String) {
        copyToClipboard(context, text)
        haptics.confirm()
        snackbar.show(linkCopied)
    }

    fun share(url: String) = shareLink(context, url, shareTitle)

    /** Keeps the tab grid's picture of this page fresh. */
    fun capturePreview() {
        val tabId = state.selectedId ?: return
        if (blank) return
        engineView?.capture { bitmap ->
            if (bitmap != null) controller.savePreview(tabId, bitmap)
        }
    }

    fun openTabs() {
        capturePreview()
        mode = IslandMode.RESTING
        navigation.onOpenTabs()
    }

    // The island returns to rest whenever the context changes under it.
    LaunchedEffect(state.selectedId) {
        if (mode == IslandMode.COMPACT || mode == IslandMode.FIND) mode = IslandMode.RESTING
    }
    LaunchedEffect(blank, selected?.loading) {
        if (mode == IslandMode.COMPACT && (blank || selected?.loading == true)) mode = IslandMode.RESTING
    }
    LaunchedEffect(mode) {
        if (mode != IslandMode.SEARCH && mode != IslandMode.FIND) focusManager.clearFocus()
        if (mode != IslandMode.FIND && selected?.find != null) controller.clearFind()
    }
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            if (event is BrowserEvent.PageSettled && event.tabId == controller.state.value.selectedId) {
                // Give the page a moment to paint before taking its picture.
                delay(400)
                if (event.tabId == controller.state.value.selectedId) capturePreview()
            }
        }
    }

    val libraryRevision by library.revision.collectAsState()
    val bookmarked by produceState(false, selected?.url, libraryRevision) {
        val url = selected?.url
        value = url != null && !blank && library.isBookmarked(url)
    }
    val topSites by produceState(emptyList<HistoryEntry>(), blank, libraryRevision) {
        value = if (blank) topSiteShortcuts(library.topSites()) else emptyList()
    }

    // A tab opened from a link has nowhere to go back to inside itself; going back closes it
    // and returns to the page it came from, as people expect.
    val opener = selected?.tab?.parentId?.let(state::tab)
    BackHandler(enabled = !covered && selected != null && !selected.canGoBack && opener != null) {
        selected?.let { controller.close(it.id) }
    }
    BackHandler(enabled = !covered && selected?.canGoBack == true && mode != IslandMode.SEARCH && mode != IslandMode.MENU) {
        controller.goBack()
    }
    BackHandler(enabled = mode == IslandMode.SEARCH || mode == IslandMode.MENU || mode == IslandMode.FIND) {
        mode = IslandMode.RESTING
    }
    BackHandler(enabled = fullscreen) { controller.exitFullscreen() }

    Box(modifier.fillMaxSize().background(colors.background)) {
        val pageModifier = when {
            fullscreen -> Modifier.fillMaxSize()
            // While the address field has the keyboard, the page keeps its size instead of jumping.
            mode == IslandMode.SEARCH || mode == IslandMode.FIND -> Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
            else -> Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding()
        }
        val collapseAfter = with(density) { 28.dp.toPx() }
        val coverColor = colors.background.toArgb()
        val selectedId = state.selectedId
        val epoch = state.sessionEpoch

        AndroidView(
            factory = { viewContext ->
                EngineView(viewContext).also { view ->
                    var travelled = 0f
                    view.onTouchStart = {
                        travelled = 0f
                        if (currentMode == IslandMode.SEARCH || currentMode == IslandMode.MENU) mode = IslandMode.RESTING
                    }
                    view.onScrollGesture = { delta ->
                        // Keep adding while the finger keeps its direction; start over when it turns.
                        travelled = if (travelled != 0f && (travelled > 0f) != (delta > 0f)) delta else travelled + delta
                        if (collapseOnScroll) {
                            if (travelled < -collapseAfter && currentMode == IslandMode.RESTING) {
                                mode = IslandMode.COMPACT
                            } else if (travelled > collapseAfter && currentMode == IslandMode.COMPACT) {
                                mode = IslandMode.RESTING
                            }
                        }
                    }
                    engineView = view
                }
            },
            modifier = pageModifier,
            onRelease = { view ->
                view.release()
                if (engineView === view) engineView = null
            },
            update = { view ->
                view.setCoverColor(coverColor)
                // `epoch` is read so the view re-attaches when the tab's page is created or dropped.
                if (epoch >= 0) view.show(controller, selectedId)
            },
        )

        AnimatedVisibility(visible = blank && !fullscreen, enter = fadeIn(OaviMotion.fade()), exit = fadeOut(OaviMotion.fade())) {
            HomePage(
                private = selected?.isPrivate == true,
                resume = remember(state.tabs, state.selectedId) {
                    TabRules.resumeCandidates(state.tabs.map { it.tab }, state.selectedId, System.currentTimeMillis())
                },
                topSites = topSites,
                blockedThisWeek = blockedThisWeek,
                onOpenUrl = { url -> selected?.let { controller.loadUrl(it.id, url) } ?: controller.newTab(url) },
                onSelectTab = controller::select,
            )
        }

        if (selected?.crashed == true && !blank) {
            CrashedPage(onReload = controller::reload)
        }

        pageCover()

        AnimatedVisibility(
            visible = (mode == IslandMode.SEARCH || mode == IslandMode.MENU) && !covered,
            enter = fadeIn(OaviMotion.fade()),
            exit = fadeOut(OaviMotion.fade()),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.scrim)
                    .pointerInput(Unit) { detectTapGestures { mode = IslandMode.RESTING } },
            )
        }

        // While the keyboard is up for a field on the page, the island would sit on top of what
        // is being typed, so it steps aside and returns when the keyboard closes.
        // The short wait ignores the moment when the address field's own keyboard is closing.
        // Only the open/closed flip is observed, so the keyboard's animation does not redraw
        // this whole screen on every frame.
        val imeInsets = WindowInsets.ime
        val keyboardOpen by remember(imeInsets, density) { derivedStateOf { imeInsets.getBottom(density) > 0 } }
        var typingOnPage by remember { mutableStateOf(false) }
        LaunchedEffect(keyboardOpen, mode) {
            if (keyboardOpen && (mode == IslandMode.RESTING || mode == IslandMode.COMPACT)) {
                delay(600)
                typingOnPage = true
            } else {
                typingOnPage = false
            }
        }

        AnimatedVisibility(
            visible = !fullscreen && !covered && !typingOnPage,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(OaviMotion.spatial()) { it } + fadeIn(OaviMotion.fade()),
            exit = slideOutVertically(OaviMotion.gentle()) { it } + fadeOut(OaviMotion.fade()),
        ) {
            Island(
                mode = mode,
                resting = {
                    RestingIsland(
                        tab = selected,
                        tabCount = state.visibleTabs(selected?.isPrivate == true).size,
                        onEditAddress = { mode = IslandMode.SEARCH },
                        onOpenProtection = navigation.onOpenProtection,
                        onReloadOrStop = { if (selected?.loading == true) controller.stop() else controller.reload() },
                        onReader = {
                            if (selected?.readerActive == true) navigation.onOpenReaderControls() else controller.toggleReader()
                        },
                        onOpenTabs = ::openTabs,
                        onNewTab = { controller.newTab(private = selected?.isPrivate == true) },
                        onOpenMenu = { mode = IslandMode.MENU },
                        onSwitchTab = { forward ->
                            capturePreview()
                            if (controller.selectNeighbour(forward)) haptics.confirm() else haptics.reject()
                        },
                        onCollapse = { if (!blank) mode = IslandMode.COMPACT },
                    )
                },
                compact = { CompactIsland(tab = selected, onExpand = { mode = IslandMode.RESTING }) },
                search = {
                    SearchPanel(
                        initialText = if (blank) "" else selected?.url.orEmpty(),
                        openTabs = remember(state.tabs) { state.tabs.map { it.tab } },
                        library = library,
                        onSubmit = { text ->
                            mode = IslandMode.RESTING
                            controller.submit(text)
                        },
                        onSelectTab = { id ->
                            mode = IslandMode.RESTING
                            controller.select(id)
                        },
                        onCopy = { text ->
                            mode = IslandMode.RESTING
                            copy(text)
                        },
                        onShare = { text ->
                            mode = IslandMode.RESTING
                            share(text)
                        },
                    )
                },
                menu = {
                    fun close(action: () -> Unit): () -> Unit = {
                        mode = IslandMode.RESTING
                        action()
                    }
                    MenuPanel(
                        tab = selected,
                        bookmarked = bookmarked,
                        darkPages = settings.forceDarkPages,
                        actions = MenuActions(
                            onBack = close(controller::goBack),
                            onForward = close(controller::goForward),
                            onShare = close { selected?.let { share(it.url) } },
                            onNewTab = close { controller.newTab() },
                            onNewPrivateTab = close { controller.newTab(private = true) },
                            onToggleBookmark = {
                                val tab = selected
                                if (tab != null && !tab.isBlank) {
                                    haptics.confirm()
                                    scope.launch {
                                        if (bookmarked) {
                                            library.removeBookmark(tab.url)
                                            snackbar.show(bookmarkRemoved)
                                        } else {
                                            library.addBookmark(tab.url, tab.tab.label, System.currentTimeMillis())
                                            snackbar.show(bookmarkAdded)
                                        }
                                    }
                                }
                            },
                            onReader = close {
                                if (selected?.readerActive == true) navigation.onOpenReaderControls() else controller.toggleReader()
                            },
                            onToggleDarkPages = {
                                haptics.tick()
                                onUpdateSettings { it.copy(forceDarkPages = !it.forceDarkPages) }
                            },
                            onFind = { mode = IslandMode.FIND },
                            onLibrary = close(navigation.onOpenLibrary),
                            onDownloads = close(navigation.onOpenDownloads),
                            onToggleDesktop = close(controller::toggleDesktopMode),
                            onSettings = close(navigation.onOpenSettings),
                            onClose = { mode = IslandMode.RESTING },
                        ),
                    )
                },
                find = {
                    val find = selected?.find
                    FindBar(
                        current = find?.current ?: 0,
                        total = find?.total ?: 0,
                        onQuery = { controller.find(it) },
                        onNext = { find?.let { controller.find(it.query, forward = true) } },
                        onPrevious = { find?.let { controller.find(it.query, forward = false) } },
                        onClose = { mode = IslandMode.RESTING },
                    )
                },
            )
        }

        if (!covered) {
            val requests by controller.requests.collectAsState()
            PageRequestHost(
                request = requests.firstOrNull { it.tabId == state.selectedId },
                links = LinkActions(
                    onOpenInNewTab = { url -> controller.newTab(url, private = selected?.isPrivate == true, parentId = state.selectedId) },
                    onOpenInPrivateTab = { url -> controller.newTab(url, private = true) },
                    onCopy = ::copy,
                    onShare = ::share,
                ),
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("link", text))
}

private fun shareLink(context: Context, url: String, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, url)
    try {
        context.startActivity(Intent.createChooser(send, chooserTitle))
    } catch (_: Exception) {
        // No app can receive a link; nothing to share with.
    }
}
