package com.oavi.browser.engine

import android.content.ActivityNotFoundException
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.oavi.browser.data.DownloadWriter
import com.oavi.browser.data.LibraryStore
import com.oavi.browser.data.SettingsStore
import com.oavi.browser.data.TabStore
import com.oavi.browser.data.ThumbnailStore
import com.oavi.browser.device.LoadBoost
import com.oavi.browser.model.CssColor
import com.oavi.browser.model.DownloadNames
import com.oavi.browser.model.ErrorPages
import com.oavi.browser.model.GroupColor
import com.oavi.browser.model.LoadErrorKind
import com.oavi.browser.model.LocalNetwork
import com.oavi.browser.model.PerformanceProfile
import com.oavi.browser.model.ReaderTheme
import com.oavi.browser.model.ResolvedInput
import com.oavi.browser.model.Settings
import com.oavi.browser.model.Tab
import com.oavi.browser.model.TabGroup
import com.oavi.browser.model.TabRules
import com.oavi.browser.model.UrlInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse
import java.util.UUID

/**
 * Everything about tabs: which exist, which is showing, what each is doing, and the engine
 * sessions behind them. All methods must be called on the main thread.
 *
 * Only a limited number of tabs (set by the device profile) keep a live engine session; the
 * rest are stored as saved state and brought back when selected.
 */
class TabsController(
    private val context: Context,
    private val engine: OaviEngine,
    private val tabStore: TabStore,
    private val library: LibraryStore,
    private val thumbnails: ThumbnailStore,
    private val downloads: DownloadWriter,
    private val settingsStore: SettingsStore,
    private val settings: StateFlow<Settings>,
    private val text: EngineText,
    private val profile: PerformanceProfile,
    private val boost: LoadBoost?,
    private val scope: CoroutineScope,
) {
    private class LiveSession(val tabId: String, val gecko: GeckoSession) {
        var pagePort: WebExtension.Port? = null
    }

    private class ClosedTab(val tab: Tab, val sessionState: String?, val index: Int, val wasSelected: Boolean)

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    private val _requests = MutableStateFlow<List<PageRequest>>(emptyList())
    val requests: StateFlow<List<PageRequest>> = _requests.asStateFlow()

    private val _events = MutableSharedFlow<BrowserEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<BrowserEvent> = _events.asSharedFlow()

    private val live = LinkedHashMap<String, LiveSession>()
    private val sessionStates = HashMap<String, String>()
    private val stateSaveJobs = HashMap<String, Job>()
    private val privateThumbnails = HashMap<String, Bitmap>()
    private var protectionOffHosts: Set<String> = emptySet()
    private var lastClosed: List<ClosedTab> = emptyList()
    private var pendingUrl: String? = null
    private var pendingBlocked = 0
    private var foreground = true

    /** Local hosts the user has already been asked about in this run. */
    private val localNetworkAsked = HashSet<String>()

    init {
        engine.pageTools.onBlocked = { count -> countBlocked(count) }
        scope.launch {
            settings.collect { current -> applySettings(current) }
        }
        scope.launch {
            while (true) {
                delay(2_000)
                val count = pendingBlocked
                if (count > 0) {
                    pendingBlocked = 0
                    settingsStore.addBlocked(count)
                }
            }
        }
    }

    // ---------------------------------------------------------------- start-up

    /** Reads saved tabs back. Until this finishes [BrowserState.ready] is false. */
    suspend fun restore() {
        if (_state.value.ready) return
        protectionOffHosts = library.protectionOffHosts()
        val saved = tabStore.load()
        sessionStates.putAll(saved.sessionStates)

        val now = now()
        val toArchive = TabRules.archiveCandidates(saved.tabs, saved.selectedId, now).toSet()
        val tabs = saved.tabs.map { if (it.id in toArchive) it.copy(archived = true) else it }
        val selected = saved.selectedId?.takeIf { id -> tabs.any { it.id == id && !it.archived } }
            ?: tabs.lastOrNull { !it.archived }?.id

        _state.value = BrowserState(
            tabs = tabs.map { TabState(it, protectionOff = isProtectionOff(it.url)) },
            groups = saved.groups,
            selectedId = selected,
            ready = true,
        )
        for (id in toArchive) persistTab(id)
        pushBlockerConfig()

        val incoming = pendingUrl
        pendingUrl = null
        when {
            incoming != null -> newTab(incoming)
            selected == null -> newTab()
            else -> activate(selected)
        }
    }

    /** Opens an address that arrived from another app (a tapped link, a share, a web search). */
    fun openFromOutside(url: String) {
        if (!_state.value.ready) {
            pendingUrl = url
            return
        }
        val blankSelected = _state.value.selected?.takeIf { it.isBlank && !it.isPrivate }
        if (blankSelected != null) loadUrl(blankSelected.id, url) else newTab(url)
    }

    // ---------------------------------------------------------------- tabs

    fun newTab(
        url: String? = null,
        private: Boolean = false,
        parentId: String? = null,
        select: Boolean = true,
    ): String {
        val now = now()
        val parent = _state.value.tab(parentId)
        val tab = Tab(
            id = UUID.randomUUID().toString(),
            url = url ?: UrlInput.BLANK,
            isPrivate = private,
            groupId = parent?.tab?.groupId?.takeIf { parent.isPrivate == private },
            parentId = parentId,
            createdAt = now,
            lastAccessAt = now,
        )
        _state.update { current ->
            val parentIndex = current.tabs.indexOfFirst { it.id == parentId }
            val insertAt = if (parentIndex >= 0) parentIndex + 1 else current.tabs.size
            val tabs = current.tabs.toMutableList().apply {
                add(insertAt, TabState(tab, protectionOff = isProtectionOff(tab.url)))
            }
            current.copy(tabs = tabs)
        }
        persistTab(tab.id)
        persistOrder()
        if (select) select(tab.id)
        return tab.id
    }

    fun select(id: String) {
        if (_state.value.tab(id) == null) return
        val now = now()
        _state.update { current ->
            current.copy(
                selectedId = id,
                tabs = current.tabs.map {
                    if (it.id == id) it.copy(tab = it.tab.copy(lastAccessAt = now, archived = false)) else it
                },
            )
        }
        persistTab(id)
        persistSelection()
        activate(id)
    }

    /** Moves to the tab before or after the current one, as a sideways swipe on the island does. */
    fun selectNeighbour(forward: Boolean): Boolean {
        val current = _state.value
        val selected = current.selected ?: return false
        val peers = current.visibleTabs(selected.isPrivate)
        val index = peers.indexOfFirst { it.id == selected.id }
        val target = peers.getOrNull(if (forward) index + 1 else index - 1) ?: return false
        select(target.id)
        return true
    }

    fun close(id: String) = closeAll(listOf(id))

    /** Closes every open tab of one kind (normal or private). */
    fun closeAllTabs(private: Boolean) = closeAll(_state.value.visibleTabs(private).map { it.id })

    fun closeAll(ids: List<String>) {
        val before = _state.value
        val closing = ids.mapNotNull { before.tab(it) }
        if (closing.isEmpty()) return
        val closingIds = closing.map { it.id }.toSet()

        lastClosed = closing.map { tab ->
            ClosedTab(
                tab = tab.tab,
                sessionState = sessionStates[tab.id],
                index = before.tabs.indexOfFirst { it.id == tab.id },
                wasSelected = tab.id == before.selectedId,
            )
        }

        var nextSelected = before.selectedId
        var remaining = before.tabs.map { it.tab }
        for (id in closingIds) {
            nextSelected = TabRules.nextSelection(remaining, id, nextSelected)
            remaining = remaining.filterNot { it.id == id }
        }

        for (id in closingIds) {
            dropSession(id)
            sessionStates.remove(id)
            privateThumbnails.remove(id)
            stateSaveJobs.remove(id)?.cancel()
        }
        _requests.update { pending -> pending.filterNot { it.tabId in closingIds } }
        _state.update { current ->
            val tabs = current.tabs.filterNot { it.id in closingIds }
            val usedGroups = tabs.mapNotNull { it.tab.groupId }.toSet()
            current.copy(
                tabs = tabs,
                groups = current.groups.filter { it.id in usedGroups },
                selectedId = nextSelected,
                sessionEpoch = current.sessionEpoch + 1,
            )
        }
        val removedGroups = before.groups.map { it.id } - _state.value.groups.map { it.id }.toSet()
        scope.launch {
            for (id in closingIds) {
                tabStore.delete(id)
                thumbnails.delete(id)
            }
            for (groupId in removedGroups) tabStore.deleteGroup(groupId)
        }
        persistOrder()

        when {
            nextSelected == null -> newTab()
            nextSelected != before.selectedId -> select(nextSelected)
        }
        _events.tryEmit(BrowserEvent.TabsClosed(closing.size))
    }

    /** Brings back the tabs closed by the most recent close action. */
    fun undoClose() {
        val closed = lastClosed
        if (closed.isEmpty()) return
        lastClosed = emptyList()
        _state.update { current ->
            val tabs = current.tabs.toMutableList()
            for (entry in closed.sortedBy { it.index }) {
                if (tabs.any { it.id == entry.tab.id }) continue
                tabs.add(entry.index.coerceIn(0, tabs.size), TabState(entry.tab, protectionOff = isProtectionOff(entry.tab.url)))
            }
            current.copy(tabs = tabs)
        }
        for (entry in closed) {
            entry.sessionState?.let { sessionStates[entry.tab.id] = it }
            persistTab(entry.tab.id)
            if (!entry.tab.isPrivate && entry.sessionState != null) {
                scope.launch { tabStore.saveSessionState(entry.tab.id, entry.sessionState) }
            }
        }
        persistOrder()
        val reselect = closed.firstOrNull { it.wasSelected } ?: closed.last()
        select(reselect.tab.id)
    }

    val canUndoClose: Boolean get() = lastClosed.isNotEmpty()

    // ---------------------------------------------------------------- navigation

    /** Handles text entered in the address field: goes to an address or searches for words. */
    fun submit(input: String) {
        val resolved = UrlInput.resolve(input, settings.value.searchEngine)
        val selected = _state.value.selected
        if (resolved is ResolvedInput.Navigate && UrlInput.isExternal(resolved.url)) {
            if (selected != null) askOpenInApp(selected.id, resolved.url, input) else newTab()
            return
        }
        if (selected == null) newTab(resolved.url) else loadUrl(selected.id, resolved.url)
    }

    fun loadUrl(id: String, url: String) {
        val tab = _state.value.tab(id) ?: return
        updateTab(id) {
            it.copy(tab = it.tab.copy(url = url, title = ""), crashed = false, protectionOff = isProtectionOff(url))
        }
        persistTab(id)
        val session = live[id]
        if (session == null) {
            sessionStates.remove(id)
            ensureSession(_state.value.tab(id) ?: tab)
        } else {
            session.gecko.loadUri(url)
        }
        if (_state.value.selectedId == id) live[id]?.gecko?.setActive(foreground)
    }

    fun goBack() {
        selectedSession()?.gecko?.goBack()
    }

    fun goForward() {
        selectedSession()?.gecko?.goForward()
    }

    fun reload() {
        val selected = _state.value.selected ?: return
        if (selected.isBlank) return
        val session = live[selected.id]
        if (session == null || selected.crashed) {
            updateTab(selected.id) { it.copy(crashed = false) }
            dropSession(selected.id)
            ensureSession(_state.value.tab(selected.id) ?: selected).gecko.setActive(foreground)
        } else {
            session.gecko.reload()
        }
    }

    fun stop() {
        selectedSession()?.gecko?.stop()
    }

    fun exitFullscreen() {
        selectedSession()?.gecko?.exitFullScreen()
    }

    // ---------------------------------------------------------------- page tools

    fun toggleDesktopMode() {
        val selected = _state.value.selected ?: return
        val desktop = !selected.desktopMode
        updateTab(selected.id) { it.copy(desktopMode = desktop) }
        val session = live[selected.id] ?: return
        session.gecko.settings.setUserAgentMode(engine.userAgentMode(desktop))
        session.gecko.settings.setViewportMode(engine.viewportMode(desktop))
        session.gecko.reload()
    }

    fun toggleReader() {
        val selected = _state.value.selected ?: return
        if (!selected.readerActive && !selected.readerAvailable) return
        sendReader(selected.id, on = !selected.readerActive)
    }

    /** Switches ad and tracker blocking off (or back on) for the site in the current tab. */
    fun setProtectionOff(off: Boolean) {
        val selected = _state.value.selected ?: return
        val host = UrlInput.hostOf(selected.url)?.removePrefix("www.") ?: return
        protectionOffHosts = if (off) protectionOffHosts + host else protectionOffHosts - host
        scope.launch { library.setProtectionOff(host, off) }
        _state.update { current ->
            current.copy(tabs = current.tabs.map { it.copy(protectionOff = isProtectionOff(it.url)) })
        }
        pushBlockerConfig()
        val session = live[selected.id] ?: return
        session.gecko.settings.setUseTrackingProtection(engine.trackingEnabled() && !off)
        session.gecko.reload()
    }

    fun find(query: String, forward: Boolean = true) {
        val selected = _state.value.selected ?: return
        val session = live[selected.id] ?: return
        if (query.isEmpty()) {
            clearFind()
            return
        }
        val flags = if (forward) 0 else GeckoSession.FINDER_FIND_BACKWARDS
        session.gecko.finder.find(query, flags).accept(
            { result ->
                if (result != null) {
                    updateTab(selected.id) { it.copy(find = FindState(query, result.current, result.total)) }
                }
            },
            { },
        )
    }

    fun clearFind() {
        val selected = _state.value.selected ?: return
        live[selected.id]?.gecko?.finder?.clear()
        updateTab(selected.id) { it.copy(find = null) }
    }

    // ---------------------------------------------------------------- groups and archive

    fun createGroup(name: String, color: GroupColor, tabIds: List<String>): String {
        val group = TabGroup(UUID.randomUUID().toString(), name.trim(), color, now())
        _state.update { current ->
            current.copy(
                groups = current.groups + group,
                tabs = current.tabs.map {
                    if (it.id in tabIds && !it.isPrivate) it.copy(tab = it.tab.copy(groupId = group.id)) else it
                },
            )
        }
        scope.launch { tabStore.saveGroup(group) }
        tabIds.forEach(::persistTab)
        return group.id
    }

    fun moveToGroup(tabId: String, groupId: String?) {
        updateTab(tabId) { it.copy(tab = it.tab.copy(groupId = groupId)) }
        persistTab(tabId)
        pruneEmptyGroups()
    }

    fun renameGroup(groupId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        _state.update { current ->
            current.copy(groups = current.groups.map { if (it.id == groupId) it.copy(name = trimmed) else it })
        }
        _state.value.groups.firstOrNull { it.id == groupId }?.let { group ->
            scope.launch { tabStore.saveGroup(group) }
        }
    }

    fun setArchived(tabId: String, archived: Boolean) {
        val before = _state.value
        val tab = before.tab(tabId) ?: return
        if (tab.isPrivate) return
        updateTab(tabId) { it.copy(tab = it.tab.copy(archived = archived, lastAccessAt = if (archived) it.tab.lastAccessAt else now())) }
        persistTab(tabId)
        if (!archived) return
        dropSession(tabId)
        if (before.selectedId == tabId) {
            val next = TabRules.nextSelection(before.tabs.map { it.tab }, tabId, tabId)
            if (next == null) newTab() else select(next)
        }
    }

    // ---------------------------------------------------------------- previews

    /** Stores a picture of the page for the tab grid. Private tabs keep theirs in memory only. */
    fun savePreview(tabId: String, bitmap: Bitmap) {
        val tab = _state.value.tab(tabId) ?: return
        if (tab.isPrivate) {
            privateThumbnails[tabId] = bitmap
            updateTab(tabId) { it.copy(thumbnailVersion = it.thumbnailVersion + 1) }
        } else {
            scope.launch {
                thumbnails.save(tabId, bitmap)
                updateTab(tabId) { it.copy(thumbnailVersion = it.thumbnailVersion + 1) }
            }
        }
    }

    suspend fun preview(tabId: String): Bitmap? = privateThumbnails[tabId] ?: thumbnails.load(tabId)

    // ---------------------------------------------------------------- lifecycle

    /** The app came to the front or went to the background. */
    fun setForeground(visible: Boolean) {
        foreground = visible
        selectedSession()?.gecko?.setActive(visible)
    }

    /** Frees memory when the system asks for it by parking every tab except the current one. */
    fun onTrimMemory(level: Int) {
        if (level < ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) return
        val keep = _state.value.selectedId
        for (id in live.keys.toList()) {
            if (id != keep) dropSession(id)
        }
    }

    /** Clears cookies, caches and site storage, then reloads the current page. */
    fun clearSiteData(onDone: () -> Unit) {
        engine.clearSiteData {
            reload()
            onDone()
        }
    }

    internal fun geckoFor(tabId: String?): GeckoSession? = if (tabId == null) null else live[tabId]?.gecko

    // ---------------------------------------------------------------- sessions

    private fun activate(id: String) {
        val tab = _state.value.tab(id) ?: return
        for ((tabId, session) in live) {
            if (tabId != id) session.gecko.setActive(false)
        }
        if (!tab.isBlank) ensureSession(tab).gecko.setActive(foreground)
        trimLiveSessions(keep = id)
    }

    private fun ensureSession(tab: TabState): LiveSession {
        live[tab.id]?.let { return it }
        val session = createSession(tab.id, tab.isPrivate, tab.desktopMode, tab.url)
        session.gecko.open(engine.runtime)
        val saved = sessionStates[tab.id]?.let { state ->
            try {
                GeckoSession.SessionState.fromString(state)
            } catch (_: Exception) {
                null
            }
        }
        if (saved != null) session.gecko.restoreState(saved) else session.gecko.loadUri(tab.url)
        return session
    }

    /** Creates a session and wires it to this controller; the caller decides how it is opened. */
    private fun createSession(tabId: String, private: Boolean, desktop: Boolean, url: String): LiveSession {
        val gecko = engine.newSession(
            private = private,
            desktop = desktop,
            trackingProtection = engine.trackingEnabled() && !isProtectionOff(url),
        )
        val session = LiveSession(tabId, gecko)
        live[tabId] = session
        bind(session)
        bumpEpoch()
        return session
    }

    private fun dropSession(tabId: String) {
        val session = live.remove(tabId) ?: return
        engine.pageTools.detach(session.gecko)
        try {
            session.gecko.close()
        } catch (_: RuntimeException) {
        }
        bumpEpoch()
    }

    private fun trimLiveSessions(keep: String) {
        while (live.size > profile.maxLiveSessions) {
            val state = _state.value
            val oldest = live.keys
                .filter { it != keep }
                .minByOrNull { state.tab(it)?.tab?.lastAccessAt ?: 0L }
                ?: return
            dropSession(oldest)
        }
    }

    private fun selectedSession(): LiveSession? = _state.value.selectedId?.let { live[it] }

    private fun bind(session: LiveSession) {
        val id = session.tabId
        val gecko = session.gecko

        gecko.setNavigationDelegate(object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                val address = url ?: return
                val tab = _state.value.tab(id) ?: return
                if (address == UrlInput.BLANK && !tab.isBlank) return
                if (address.startsWith("data:text/html;charset=utf-8;base64,")) return
                updateTab(id) {
                    it.copy(
                        tab = it.tab.copy(url = address),
                        protectionOff = isProtectionOff(address),
                        readerActive = false,
                        readerAvailable = false,
                        themeColor = null,
                        find = null,
                    )
                }
                persistTab(id)
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                updateTab(id) { it.copy(canGoBack = canGoBack) }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                updateTab(id) { it.copy(canGoForward = canGoForward) }
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny>? {
                val target = request.uri
                if (UrlInput.isExternal(target)) {
                    askOpenInApp(id, target, null)
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                if (blockedByLocalNetwork(target)) {
                    val host = UrlInput.hostOf(target).orEmpty()
                    if (localNetworkAsked.add(host)) {
                        askLocalNetwork(id, target)
                    } else {
                        // Android would hold the connection until it times out; say why at once.
                        scope.launch { showErrorPage(id, target, LoadErrorKind.LOCAL_NETWORK) }
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                session.settings.setUseTrackingProtection(engine.trackingEnabled() && !isProtectionOff(target))
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                val opener = _state.value.tab(id) ?: return null
                val now = now()
                val tab = Tab(
                    id = UUID.randomUUID().toString(),
                    url = uri,
                    isPrivate = opener.isPrivate,
                    groupId = opener.tab.groupId,
                    parentId = id,
                    createdAt = now,
                    lastAccessAt = now,
                )
                _state.update { current ->
                    val index = current.tabs.indexOfFirst { it.id == id }
                    val tabs = current.tabs.toMutableList().apply { add(index + 1, TabState(tab, desktopMode = opener.desktopMode)) }
                    current.copy(tabs = tabs, selectedId = tab.id)
                }
                // The engine opens this session itself, so it is created but not opened here.
                val created = createSession(tab.id, tab.isPrivate, opener.desktopMode, uri)
                persistTab(tab.id)
                persistOrder()
                persistSelection()
                for ((tabId, other) in live) {
                    if (tabId != tab.id) other.gecko.setActive(false)
                }
                created.gecko.setActive(foreground)
                trimLiveSessions(keep = tab.id)
                return GeckoResult.fromValue(created.gecko)
            }

            override fun onLoadError(
                session: GeckoSession,
                uri: String?,
                error: WebRequestError,
            ): GeckoResult<String>? {
                val failed = uri ?: return null
                val kind = when {
                    blockedByLocalNetwork(failed) -> LoadErrorKind.LOCAL_NETWORK
                    error.code == WebRequestError.ERROR_OFFLINE -> LoadErrorKind.OFFLINE
                    error.code == WebRequestError.ERROR_UNKNOWN_HOST -> LoadErrorKind.NOT_FOUND
                    error.code == WebRequestError.ERROR_NET_TIMEOUT -> LoadErrorKind.TIMEOUT
                    error.category == WebRequestError.ERROR_CATEGORY_SECURITY -> LoadErrorKind.SECURITY
                    error.category == WebRequestError.ERROR_CATEGORY_NETWORK -> LoadErrorKind.CONNECTION
                    else -> LoadErrorKind.UNKNOWN
                }
                val page = ErrorPages.html(text.errorPage(kind), failed)
                return GeckoResult.fromValue(ErrorPages.dataUri(page))
            }
        })

        gecko.setProgressDelegate(object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                updateTab(id) { it.copy(loading = true, progress = 5, blockedCount = 0, crashed = false) }
                if (_state.value.selectedId == id) boost?.boost(4_000)
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                updateTab(id) { it.copy(loading = false, progress = 100) }
                boost?.calm()
                val tab = _state.value.tab(id) ?: return
                if (success && !tab.isPrivate && isWebPage(tab.url)) {
                    val visitedAt = now()
                    scope.launch { library.recordVisit(tab.url, tab.tab.title, visitedAt) }
                }
                _events.tryEmit(BrowserEvent.PageSettled(id))
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                updateTab(id) { it.copy(progress = progress) }
            }

            override fun onSecurityChange(
                session: GeckoSession,
                securityInfo: GeckoSession.ProgressDelegate.SecurityInformation,
            ) {
                updateTab(id) { it.copy(secure = securityInfo.isSecure) }
            }

            override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
                val serialized = sessionState.toString()
                sessionStates[id] = serialized
                val tab = _state.value.tab(id) ?: return
                if (tab.isPrivate) return
                stateSaveJobs.remove(id)?.cancel()
                stateSaveJobs[id] = scope.launch {
                    delay(800)
                    tabStore.saveSessionState(id, serialized)
                }
            }
        })

        gecko.setContentDelegate(object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                val value = title.orEmpty()
                val tab = _state.value.tab(id) ?: return
                if (tab.isBlank) return
                updateTab(id) { it.copy(tab = it.tab.copy(title = value)) }
                persistTab(id)
                if (!tab.isPrivate && value.isNotBlank()) {
                    scope.launch { library.updateTitle(tab.url, value) }
                }
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                updateTab(id) { it.copy(fullscreen = fullScreen) }
            }

            override fun onContextMenu(
                session: GeckoSession,
                screenX: Int,
                screenY: Int,
                element: GeckoSession.ContentDelegate.ContextElement,
            ) {
                val link = element.linkUri
                val image = element.srcUri.takeIf { element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE }
                if (link == null && image == null) return
                lateinit var request: PageRequest
                request = PageRequest.ContextMenu(id, link, image) { finish(request) }
                post(request)
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                download(response)
            }

            override fun onCrash(session: GeckoSession) {
                markCrashed(id)
            }

            override fun onKill(session: GeckoSession) {
                markCrashed(id)
            }

            override fun onCloseRequest(session: GeckoSession) {
                close(id)
            }

            override fun onFocusRequest(session: GeckoSession) {
                if (_state.value.selectedId != id) select(id)
            }
        })

        gecko.setContentBlockingDelegate(object : ContentBlocking.Delegate {
            override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
                updateTab(id) { it.copy(blockedCount = it.blockedCount + 1) }
                pendingBlocked++
            }
        })

        gecko.setPromptDelegate(Prompts(context, id, ::post, ::finish))
        gecko.setPermissionDelegate(Permissions(id, ::post, ::finish))

        engine.pageTools.attach(gecko, object : WebExtension.MessageDelegate {
            override fun onConnect(port: WebExtension.Port) {
                session.pagePort = port
                port.setDelegate(object : WebExtension.PortDelegate {
                    override fun onPortMessage(message: Any, port: WebExtension.Port) {
                        onPageMessage(id, message)
                    }

                    override fun onDisconnect(port: WebExtension.Port) {
                        if (session.pagePort === port) session.pagePort = null
                    }
                })
                sendPageConfig(session)
            }
        })
    }

    private fun markCrashed(id: String) {
        val session = live.remove(id)
        if (session != null) {
            engine.pageTools.detach(session.gecko)
            bumpEpoch()
        }
        updateTab(id) { it.copy(crashed = true, loading = false) }
    }

    // ---------------------------------------------------------------- page messages

    private fun onPageMessage(tabId: String, message: Any) {
        val json = message as? JSONObject ?: return
        if (json.optString("type") != "page") return
        val color = CssColor.parse(if (json.isNull("themeColor")) null else json.optString("themeColor"))
            ?.takeIf { CssColor.usableAsAccent(it) }
        updateTab(tabId) {
            it.copy(
                themeColor = color,
                readerAvailable = json.optBoolean("readerable"),
                readerActive = json.optBoolean("reader"),
            )
        }
    }

    private fun sendPageConfig(session: LiveSession) {
        val config = JSONObject()
            .put("type", "config")
            .put("forceDark", settings.value.forceDarkPages)
        try {
            session.pagePort?.postMessage(config)
        } catch (_: RuntimeException) {
        }
    }

    private fun sendReader(tabId: String, on: Boolean) {
        val current = settings.value
        val theme = when (current.readerTheme) {
            ReaderTheme.AUTO -> "auto"
            ReaderTheme.LIGHT -> "light"
            ReaderTheme.SEPIA -> "sepia"
            ReaderTheme.DARK -> "dark"
        }
        val message = JSONObject()
            .put("type", "reader")
            .put("on", on)
            .put("theme", theme)
            .put("fontScale", current.readerFontScale.toDouble())
        try {
            live[tabId]?.pagePort?.postMessage(message)
        } catch (_: RuntimeException) {
        }
    }

    private fun applySettings(current: Settings) {
        engine.apply(current)
        boost?.enabled = current.performanceBoost && profile.useLoadBoost
        pushBlockerConfig()
        for (session in live.values) sendPageConfig(session)
        for (tab in _state.value.tabs) {
            if (tab.readerActive) sendReader(tab.id, on = true)
        }
    }

    private fun pushBlockerConfig() {
        engine.pageTools.setBlockerConfig(settings.value.adBlock, protectionOffHosts)
    }

    private fun countBlocked(count: Int) {
        if (count <= 0) return
        pendingBlocked += count
        val selected = _state.value.selectedId ?: return
        updateTab(selected) { it.copy(blockedCount = it.blockedCount + count) }
    }

    // ---------------------------------------------------------------- downloads and other apps

    private fun download(response: WebResponse) {
        val body = response.body ?: return
        val headers = response.headers
        val disposition = headers.entries.firstOrNull { it.key.equals("content-disposition", ignoreCase = true) }?.value
        val mimeType = headers.entries.firstOrNull { it.key.equals("content-type", ignoreCase = true) }?.value
            ?.substringBefore(';')?.trim()
        val name = DownloadNames.guess(response.uri, disposition, mimeType)
        _events.tryEmit(BrowserEvent.DownloadStarted(name))
        scope.launch {
            val saved = downloads.save(name, mimeType, body)
            _events.emit(
                if (saved != null) BrowserEvent.DownloadFinished(name, saved, mimeType) else BrowserEvent.DownloadFailed(name),
            )
        }
    }

    // ---------------------------------------------------------------- local network (Android 17)

    private fun localNetworkAllowed(): Boolean =
        context.checkSelfPermission(LocalNetwork.PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** True when Android 17 would stop Oavi from reaching [url] because it is on the local network. */
    private fun blockedByLocalNetwork(url: String): Boolean {
        val host = UrlInput.hostOf(url) ?: return false
        return LocalNetwork.isLocalHost(host) && !localNetworkAllowed()
    }

    /**
     * Explains why the permission is needed, then shows the system prompt. The page is loaded
     * once the user has answered; if access was refused it gets the explanatory error page.
     */
    private fun askLocalNetwork(tabId: String, url: String) {
        updateTab(tabId) { it.copy(tab = it.tab.copy(url = url)) }
        lateinit var explain: PageRequest
        explain = PageRequest.Confirm(tabId, ConfirmKind.LOCAL_NETWORK, "", UrlInput.displayHost(url)) { agreed ->
            finish(explain)
            if (!agreed) {
                showErrorPage(tabId, url, LoadErrorKind.LOCAL_NETWORK)
                return@Confirm
            }
            lateinit var ask: PageRequest
            ask = PageRequest.AndroidPermissions(tabId, listOf(LocalNetwork.PERMISSION)) { granted ->
                finish(ask)
                if (granted) navigate(tabId, url) else showErrorPage(tabId, url, LoadErrorKind.LOCAL_NETWORK)
            }
            post(ask)
        }
        post(explain)
    }

    /** Loads [url] in the tab's live page, starting one if needed. */
    private fun navigate(tabId: String, url: String) {
        val session = live[tabId]
        if (session != null) session.gecko.loadUri(url) else loadUrl(tabId, url)
    }

    /** Shows Oavi's error page for [url] while the island keeps showing that address. */
    private fun showErrorPage(tabId: String, url: String, kind: LoadErrorKind) {
        updateTab(tabId) { it.copy(tab = it.tab.copy(url = url), loading = false) }
        val page = ErrorPages.dataUri(ErrorPages.html(text.errorPage(kind), url))
        val session = live[tabId] ?: run {
            // A fresh page that loads only the error, not the address that cannot be reached.
            val tab = _state.value.tab(tabId) ?: return
            createSession(tab.id, tab.isPrivate, tab.desktopMode, url).also { it.gecko.open(engine.runtime) }
        }
        session.gecko.loadUri(page)
    }

    /** Links such as mailto: or a shop's app link leave the browser only after the user agrees. */
    private fun askOpenInApp(tabId: String, uri: String, typedText: String?) {
        val intent = externalIntent(uri)
        if (intent == null) {
            if (typedText != null) loadUrl(tabId, settings.value.searchEngine.searchUrl(typedText))
            return
        }
        lateinit var request: PageRequest
        request = PageRequest.Confirm(tabId, ConfirmKind.OPEN_IN_APP, "", UrlInput.schemeOf(uri).orEmpty()) { agreed ->
            finish(request)
            if (agreed) {
                try {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: ActivityNotFoundException) {
                    val fallback = intent.getStringExtra("browser_fallback_url")
                    when {
                        fallback != null && UrlInput.isEngineScheme(fallback) -> loadUrl(tabId, fallback)
                        typedText != null -> loadUrl(tabId, settings.value.searchEngine.searchUrl(typedText))
                        else -> _events.tryEmit(BrowserEvent.NoAppForLink)
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "Not allowed to open $uri", e)
                    _events.tryEmit(BrowserEvent.NoAppForLink)
                }
            }
        }
        post(request)
    }

    private fun externalIntent(uri: String): Intent? = try {
        if (uri.startsWith("intent:", ignoreCase = true)) {
            Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                component = null
                selector = null
            }
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addCategory(Intent.CATEGORY_BROWSABLE)
        }
    } catch (_: Exception) {
        null
    }

    // ---------------------------------------------------------------- helpers

    private fun post(request: PageRequest) {
        _requests.update { it + request }
    }

    private fun finish(request: PageRequest) {
        _requests.update { pending -> pending.filterNot { it === request } }
    }

    private fun updateTab(id: String, transform: (TabState) -> TabState) {
        _state.update { current ->
            var changed = false
            val tabs = current.tabs.map {
                if (it.id == id) {
                    val next = transform(it)
                    if (next != it) changed = true
                    next
                } else {
                    it
                }
            }
            if (changed) current.copy(tabs = tabs) else current
        }
    }

    private fun bumpEpoch() {
        _state.update { it.copy(sessionEpoch = it.sessionEpoch + 1) }
    }

    private fun pruneEmptyGroups() {
        val current = _state.value
        val used = current.tabs.mapNotNull { it.tab.groupId }.toSet()
        val empty = current.groups.filter { it.id !in used }
        if (empty.isEmpty()) return
        _state.update { it.copy(groups = it.groups.filter { group -> group.id in used }) }
        scope.launch { empty.forEach { tabStore.deleteGroup(it.id) } }
    }

    private fun persistTab(id: String) {
        val current = _state.value
        val index = current.tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        val tab = current.tabs[index].tab
        if (tab.isPrivate) return
        scope.launch { tabStore.save(tab, index) }
    }

    private fun persistOrder() {
        val ids = _state.value.tabs.filter { !it.isPrivate }.map { it.id }
        scope.launch { tabStore.saveOrder(ids) }
    }

    private fun persistSelection() {
        val selected = _state.value.selected ?: return
        if (selected.isPrivate) return
        scope.launch { tabStore.saveSelected(selected.id) }
    }

    private fun isProtectionOff(url: String): Boolean {
        val host = UrlInput.hostOf(url)?.removePrefix("www.") ?: return false
        return host in protectionOffHosts
    }

    private fun isWebPage(url: String): Boolean = url.startsWith("http://") || url.startsWith("https://")

    private fun now(): Long = System.currentTimeMillis()

    private companion object {
        const val TAG = "OaviTabs"
    }
}
