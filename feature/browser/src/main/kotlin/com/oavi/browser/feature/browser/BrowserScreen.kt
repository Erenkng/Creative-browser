package com.oavi.browser.feature.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.oavi.browser.data.LibraryStore
import com.oavi.browser.engine.BrowserEvent
import com.oavi.browser.engine.BrowserState
import com.oavi.browser.engine.EngineView
import com.oavi.browser.engine.TabsController
import com.oavi.browser.model.HistoryEntry
import com.oavi.browser.model.LayoutStyle
import com.oavi.browser.model.LinkCleaner
import com.oavi.browser.model.Settings
import com.oavi.browser.model.TabRules
import com.oavi.browser.ui.Oavi
import com.oavi.browser.ui.OaviMotion
import com.oavi.browser.ui.R
import com.oavi.browser.ui.SnackbarState
import com.oavi.browser.ui.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where the menu and bars send the user beyond the page itself. */
class BrowserNavigation(
    val onOpenTabs: () -> Unit,
    val onOpenLibrary: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenProtection: () -> Unit,
    val onOpenReaderControls: () -> Unit,
    val onOpenDownloads: () -> Unit,
    val onPageTools: PageToolActions = PageToolActions(),
)

/** Things done with the whole page that need the activity: saving, printing, reading aloud, QR code. */
class PageToolActions(
    val onSavePdf: () -> Unit = {},
    val onPrint: () -> Unit = {},
    val onReadAloud: () -> Unit = {},
    val onShowQr: () -> Unit = {},
)

private val IslandMode.isPanel: Boolean
    get() = this == IslandMode.SEARCH || this == IslandMode.MENU || this == IslandMode.FIND

/** The last panel shown, so a closing panel keeps its content while it animates away. */
private class LastPanel(var mode: IslandMode = IslandMode.SEARCH)

/**
 * The browser itself: the page, and the bars of the chosen layout around it.
 *
 * @param covered another full screen (tabs, settings …) is on top, so the bars step aside.
 * @param pageCover drawn over the page but under the bars, e.g. the lock for private tabs.
 */
@Composable
fun BrowserScreen(
    controller: TabsController,
    state: BrowserState,
    settings: Settings,
    library: LibraryStore,
    blockedThisWeek: Long,
    blockedTotal: Long,
    snackbar: SnackbarState,
    covered: Boolean,
    navigation: BrowserNavigation,
    onUpdateSettings: ((Settings) -> Settings) -> Unit,
    modifier: Modifier = Modifier,
    pageCover: @Composable () -> Unit = {},
) {
    CompositionLocalProvider(LocalNaturalDirection provides LocalLayoutDirection.current) {
        BrowserContent(
            controller, state, settings, library, blockedThisWeek, blockedTotal, snackbar, covered, navigation,
            onUpdateSettings, modifier, pageCover,
        )
    }
}

@Composable
private fun BrowserContent(
    controller: TabsController,
    state: BrowserState,
    settings: Settings,
    library: LibraryStore,
    blockedThisWeek: Long,
    blockedTotal: Long,
    snackbar: SnackbarState,
    covered: Boolean,
    navigation: BrowserNavigation,
    onUpdateSettings: ((Settings) -> Settings) -> Unit,
    modifier: Modifier,
    pageCover: @Composable () -> Unit,
) {
    val colors = Oavi.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val layout = settings.layout
    val leftHanded = settings.leftHanded
    val selected = state.selected
    val fullscreen = selected?.fullscreen == true
    val blank = selected == null || selected.isBlank

    var mode by rememberSaveable { mutableStateOf(IslandMode.RESTING) }
    var sidebarOpen by rememberSaveable { mutableStateOf(false) }
    var sidebarPrivate by rememberSaveable { mutableStateOf(false) }
    val lastPanel = remember { LastPanel() }
    if (mode.isPanel) lastPanel.mode = mode

    // Pages are translated into the language the app is shown in.
    val appLocale = LocalConfiguration.current.locales[0]
    val appLanguage = appLocale.language
    val appLanguageName = appLocale.getDisplayLanguage(appLocale).replaceFirstChar { it.titlecase(appLocale) }
    var engineView by remember { mutableStateOf<EngineView?>(null) }
    val currentMode by rememberUpdatedState(mode)
    val currentLayout by rememberUpdatedState(layout)
    val collapseOnScroll by rememberUpdatedState(settings.collapseIslandOnScroll && !blank && layout.collapsesWhileReading)

    val linkCopied = stringResource(R.string.snack_link_copied)
    val bookmarkAdded = stringResource(R.string.snack_bookmark_added)
    val bookmarkRemoved = stringResource(R.string.snack_bookmark_removed)
    val shareTitle = stringResource(R.string.cd_share)
    val searchSelection = stringResource(R.string.selection_search)

    fun shareable(url: String): String = if (settings.cleanLinks) LinkCleaner.clean(url) else url

    fun copy(text: String) {
        copyToClipboard(context, shareable(text))
        haptics.confirm()
        snackbar.show(linkCopied)
    }

    fun share(url: String) = shareLink(context, shareable(url), shareTitle)

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
        if (layout == LayoutStyle.FOCUS) {
            sidebarPrivate = selected?.isPrivate == true
            sidebarOpen = true
        } else {
            navigation.onOpenTabs()
        }
    }

    // The bars return to rest whenever the context changes under them.
    LaunchedEffect(state.selectedId) {
        if (mode == IslandMode.COMPACT || mode == IslandMode.FIND) mode = IslandMode.RESTING
    }
    LaunchedEffect(blank, selected?.loading) {
        if (mode == IslandMode.COMPACT && (blank || selected?.loading == true)) mode = IslandMode.RESTING
    }
    LaunchedEffect(layout) {
        if (mode == IslandMode.COMPACT && !layout.collapsesWhileReading) mode = IslandMode.RESTING
        if (layout != LayoutStyle.FOCUS) sidebarOpen = false
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
    val wantTopSites = blank || layout == LayoutStyle.FOCUS
    val topSites by produceState(emptyList<HistoryEntry>(), wantTopSites, libraryRevision) {
        value = if (wantTopSites) topSiteShortcuts(library.topSites()) else emptyList()
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
    BackHandler(enabled = mode.isPanel) { mode = IslandMode.RESTING }
    BackHandler(enabled = fullscreen) { controller.exitFullscreen() }
    BackHandler(enabled = sidebarOpen) { sidebarOpen = false }

    val tabCount = state.visibleTabs(selected?.isPrivate == true).size
    val actions = ChromeActions(
        onEditAddress = { mode = IslandMode.SEARCH },
        onOpenProtection = navigation.onOpenProtection,
        onReloadOrStop = { if (selected?.loading == true) controller.stop() else controller.reload() },
        onReader = { if (selected?.readerActive == true) navigation.onOpenReaderControls() else controller.toggleReader() },
        onOpenTabs = ::openTabs,
        onNewTab = { controller.newTab(private = selected?.isPrivate == true) },
        onOpenMenu = { mode = IslandMode.MENU },
        onSwitchTab = { forward ->
            capturePreview()
            if (controller.selectNeighbour(forward)) {
                haptics.confirm()
                if (!settings.swipeTipDone) onUpdateSettings { it.copy(swipeTipDone = true) }
            } else {
                haptics.reject()
            }
        },
        onCollapse = { if (!blank && layout.collapsesWhileReading) mode = IslandMode.COMPACT },
        onExpand = { mode = IslandMode.RESTING },
        onBack = controller::goBack,
        onForward = controller::goForward,
        onOpenLibrary = navigation.onOpenLibrary,
        onSelectTab = { id ->
            capturePreview()
            controller.select(id)
        },
        onCloseTab = { id -> controller.close(id) },
    )

    val searchPanel: @Composable (Boolean) -> Unit = { onTop ->
        SearchPanel(
            initialText = if (blank) "" else selected?.url.orEmpty(),
            openTabs = remember(state.tabs) { state.tabs.map { it.tab } },
            library = library,
            fieldOnTop = onTop,
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
    }
    val menuPanel: @Composable () -> Unit = {
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
                onToggleBookmark = close {
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
                onTranslate = close {
                    if (selected?.translatedTo != null) controller.showOriginal() else controller.translate(appLanguage)
                },
                onSavePdf = close(navigation.onPageTools.onSavePdf),
                onPrint = close(navigation.onPageTools.onPrint),
                onReadAloud = close(navigation.onPageTools.onReadAloud),
                onShowQr = close(navigation.onPageTools.onShowQr),
                onSettings = close(navigation.onOpenSettings),
                onClose = { mode = IslandMode.RESTING },
            ),
        )
    }
    val findPanel: @Composable () -> Unit = {
        val find = selected?.find
        FindBar(
            current = find?.current ?: 0,
            total = find?.total ?: 0,
            onQuery = { controller.find(it) },
            onNext = { find?.let { controller.find(it.query, forward = true) } },
            onPrevious = { find?.let { controller.find(it.query, forward = false) } },
            onClose = { mode = IslandMode.RESTING },
        )
    }

    val focusLayout = layout == LayoutStyle.FOCUS && !fullscreen
    Box(
        modifier
            .fillMaxSize()
            .then(if (focusLayout) Modifier.background(focusBackdrop()) else Modifier.background(colors.background)),
    ) {
        val keepSize = mode == IslandMode.SEARCH || mode == IslandMode.FIND
        val pageModifier = if (fullscreen) {
            Modifier.fillMaxSize()
        } else {
            pageArea(layout, ime = !keepSize)
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
                        if (collapseOnScroll && currentLayout.collapsesWhileReading) {
                            if (travelled < -collapseAfter && currentMode == IslandMode.RESTING) {
                                mode = IslandMode.COMPACT
                            } else if (travelled > collapseAfter && currentMode == IslandMode.COMPACT) {
                                mode = IslandMode.RESTING
                            }
                        }
                    }
                    view.onSearchSelection = { text -> controller.searchInNewTab(text) }
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
                view.searchSelectionLabel = searchSelection
                // `epoch` is read so the view re-attaches when the tab's page is created or dropped.
                if (epoch >= 0) view.show(controller, selectedId)
            },
        )

        // The strip under the status bar continues the top of the page, so there is no seam
        // between the system bar and a white (or dark, or coloured) page.
        if (!fullscreen && layout.pageUnderStatusBar) {
            StatusStrip(
                color = selected?.topColor?.takeIf { !blank && mode != IslandMode.SEARCH }?.let { Color(it) }
                    ?: colors.background,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        AnimatedVisibility(visible = blank && !fullscreen, enter = fadeIn(OaviMotion.fade()), exit = fadeOut(OaviMotion.fade())) {
            HomePage(
                private = selected?.isPrivate == true,
                resume = remember(state.tabs, state.selectedId) {
                    TabRules.resumeCandidates(
                        state.tabs.filter { !it.errorPage }.map { it.tab },
                        state.selectedId,
                        System.currentTimeMillis(),
                    )
                },
                topSites = topSites,
                blockedThisWeek = blockedThisWeek,
                blockedTotal = blockedTotal,
                layout = layout,
                onOpenUrl = { url -> selected?.let { controller.loadUrl(it.id, url) } ?: controller.newTab(url) },
                onSelectTab = controller::select,
                onSearch = { mode = IslandMode.SEARCH },
                showSwipeTip = layout == LayoutStyle.ISLAND && !settings.swipeTipDone && state.visibleTabs(private = false).size >= 2,
                onDismissSwipeTip = { onUpdateSettings { it.copy(swipeTipDone = true) } },
                modifier = if (focusLayout) pageArea(layout, ime = false).clip(RoundedCornerShape(Bars.FocusRadius)) else Modifier,
            )
        }

        if (selected?.crashed == true && !blank) {
            CrashedPage(onReload = controller::reload, modifier = if (fullscreen) Modifier else pageArea(layout, ime = false))
        }

        pageCover()

        if (focusLayout) FocusFrame()

        // While the keyboard is up for a field on the page, the bottom bars would sit on top of
        // what is being typed, so they step aside and return when the keyboard closes.
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

        // The page knows how much of its bottom the bars cover, so its own bottom bars sit
        // above them. The value follows the bars with the same spring, so those bars glide
        // down as the bars shrink rather than jumping.
        val bottomShown = !fullscreen && !covered && !typingOnPage
        val targetCover = if (bottomShown) layout.bottomCover(mode) else 0.dp
        val restingCover = layout.bottomCover(IslandMode.RESTING)
        val cover = remember { Animatable(restingCover.value) }
        LaunchedEffect(targetCover) { cover.animateTo(targetCover.value, OaviMotion.spatial()) }
        val maxCoverPx = with(density) { restingCover.roundToPx() }
        LaunchedEffect(engineView, maxCoverPx) {
            val view = engineView ?: return@LaunchedEffect
            snapshotFlow { cover.value }.collect { value ->
                view.setBottomCover(maxCoverPx, with(density) { value.dp.roundToPx() })
            }
        }

        // ---- bars that stay under the dimming while a panel is open
        val barsShown = !fullscreen && !covered
        when (layout) {
            LayoutStyle.CLASSIC -> if (barsShown) {
                Box(Modifier.align(Alignment.TopCenter)) { ClassicTopBar(selected, tabCount, actions) }
            }
            LayoutStyle.STRIP -> {
                if (barsShown) {
                    Box(Modifier.align(Alignment.TopCenter)) {
                        StripTopBar(selected, state.visibleTabs(selected?.isPrivate == true), actions)
                    }
                }
                AnimatedVisibility(
                    visible = bottomShown && mode != IslandMode.COMPACT,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(OaviMotion.spatial()) { it },
                    exit = slideOutVertically(OaviMotion.gentle()) { it },
                ) {
                    StripToolbar(selected, tabCount, leftHanded, actions)
                }
            }
            LayoutStyle.DOCK -> {
                AnimatedVisibility(
                    visible = bottomShown && (mode == IslandMode.RESTING || mode.isPanel),
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(OaviMotion.spatial()) { it },
                    exit = slideOutVertically(OaviMotion.gentle()) { it },
                ) {
                    Dock(selected, tabCount, leftHanded, actions)
                }
                AnimatedVisibility(
                    visible = bottomShown && mode == IslandMode.COMPACT,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn(OaviMotion.fade()) + slideInVertically(OaviMotion.spatial()) { it },
                    exit = fadeOut(OaviMotion.fade()),
                ) {
                    DockStrip(selected, actions.onExpand)
                }
            }
            LayoutStyle.FOCUS -> AnimatedVisibility(
                visible = bottomShown && !mode.isPanel,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(OaviMotion.fade()) + slideInVertically(OaviMotion.spatial()) { it / 2 },
                exit = fadeOut(OaviMotion.fade()) + slideOutVertically(OaviMotion.gentle()) { it / 2 },
            ) {
                FocusHandle(selected, leftHanded, actions, onOpenSidebar = ::openTabs)
            }
            LayoutStyle.ISLAND -> Unit
        }

        AnimatedVisibility(
            visible = mode.isPanel && (mode != IslandMode.FIND) && !covered,
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

        // ---- the island and the panels, over the dimming
        when (layout) {
            LayoutStyle.ISLAND -> AnimatedVisibility(
                visible = bottomShown || mode.isPanel && !covered && !fullscreen,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(OaviMotion.spatial()) { it } + fadeIn(OaviMotion.fade()),
                exit = slideOutVertically(OaviMotion.gentle()) { it } + fadeOut(OaviMotion.fade()),
            ) {
                Island(
                    mode = mode,
                    resting = { RestingIsland(selected, tabCount, leftHanded, actions) },
                    compact = { CompactIsland(tab = selected, onExpand = actions.onExpand) },
                    search = { searchPanel(false) },
                    menu = menuPanel,
                    find = findPanel,
                )
            }
            LayoutStyle.CLASSIC, LayoutStyle.STRIP -> AnimatedVisibility(
                visible = mode.isPanel && !covered && !fullscreen,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(OaviMotion.fade()) + slideInVertically(OaviMotion.spatial()) { -it / 5 },
                exit = fadeOut(OaviMotion.fade()) + slideOutVertically(OaviMotion.gentle()) { -it / 5 },
            ) {
                TopPanel(
                    mode = lastPanel.mode,
                    search = { searchPanel(true) },
                    menu = menuPanel,
                    find = findPanel,
                )
            }
            LayoutStyle.DOCK, LayoutStyle.FOCUS -> {
                val bottomPanel = if (layout == LayoutStyle.FOCUS) {
                    mode == IslandMode.MENU || mode == IslandMode.FIND
                } else {
                    mode.isPanel
                }
                AnimatedVisibility(
                    visible = bottomPanel && !covered && !fullscreen,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(OaviMotion.spatial()) { it / 3 } + fadeIn(OaviMotion.fade()),
                    exit = slideOutVertically(OaviMotion.gentle()) { it / 3 } + fadeOut(OaviMotion.fade()),
                ) {
                    Island(
                        mode = lastPanel.mode,
                        resting = {},
                        compact = {},
                        search = { searchPanel(false) },
                        menu = menuPanel,
                        find = findPanel,
                    )
                }
                if (layout == LayoutStyle.FOCUS) {
                    AnimatedVisibility(
                        visible = mode == IslandMode.SEARCH && !covered && !fullscreen,
                        enter = fadeIn(OaviMotion.fade()) + scaleIn(OaviMotion.spatial(), initialScale = 0.94f),
                        exit = fadeOut(OaviMotion.fade()) + scaleOut(OaviMotion.gentle(), targetScale = 0.96f),
                    ) {
                        CommandBar { searchPanel(true) }
                    }
                }
            }
        }

        // A page in another language gets a quiet offer to translate it into the app's language.
        val offerTranslation = selected != null && !blank && !fullscreen && !covered && !typingOnPage && mode == IslandMode.RESTING &&
            selected.translatedTo == null && (selected.translateOffered || selected.translating) &&
            selected.pageLanguage != appLanguage
        TranslatePill(
            visible = offerTranslation,
            translating = selected?.translating == true,
            languageName = appLanguageName,
            onTranslate = { controller.translate(appLanguage) },
            onDismiss = controller::dismissTranslateOffer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = translateLift(layout)),
        )

        if (layout == LayoutStyle.FOCUS) {
            TabSidebar(
                visible = sidebarOpen && !covered && !fullscreen,
                tabs = state.visibleTabs(sidebarPrivate),
                groups = state.groups,
                selectedId = state.selectedId,
                privateMode = sidebarPrivate,
                pinned = topSites,
                onShowPrivate = { sidebarPrivate = it },
                onSelect = { id ->
                    sidebarOpen = false
                    actions.onSelectTab(id)
                },
                onCloseTab = actions.onCloseTab,
                onNewTab = {
                    sidebarOpen = false
                    controller.newTab(private = sidebarPrivate)
                },
                onOpenPinned = { url ->
                    sidebarOpen = false
                    controller.newTab(url)
                },
                onOpenAllTabs = {
                    sidebarOpen = false
                    navigation.onOpenTabs()
                },
                onDismiss = { sidebarOpen = false },
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

/** The part of the screen the page takes in [layout]: below the top bars, inset as a card in the focus layout. */
@Composable
private fun pageArea(layout: LayoutStyle, ime: Boolean): Modifier {
    val focus = layout == LayoutStyle.FOCUS
    return Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.statusBars)
        .padding(top = layout.topSpace())
        .padding(horizontal = if (focus) Bars.FocusMargin else 0.dp)
        .windowInsetsPadding(WindowInsets.navigationBars)
        .padding(bottom = if (focus) Bars.FocusMargin else 0.dp)
        .then(if (ime) Modifier.imePadding() else Modifier)
}

/** Above the bottom bars, measured from the navigation bar (the focus card ends a margin above it). */
private fun translateLift(layout: LayoutStyle): Dp = when (layout) {
    LayoutStyle.CLASSIC -> 16.dp
    LayoutStyle.FOCUS -> layout.bottomCover(IslandMode.RESTING) + Bars.FocusMargin + 6.dp
    else -> layout.bottomCover(IslandMode.RESTING) + 6.dp
}

@Composable
private fun StatusStrip(color: Color, modifier: Modifier = Modifier) {
    val animated by animateColorAsState(color, tween(220), label = "statusStrip")
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .drawBehind { drawRect(animated) },
    )
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
