package com.oavi.browser.engine

import android.net.Uri
import com.oavi.browser.model.ErrorPageText
import com.oavi.browser.model.LoadErrorKind
import com.oavi.browser.model.Tab
import com.oavi.browser.model.TabGroup

data class FindState(val query: String, val current: Int, val total: Int)

/** A tab as the interface sees it: the saved part plus what is happening in it right now. */
data class TabState(
    val tab: Tab,
    val loading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val secure: Boolean = false,
    val blockedCount: Int = 0,
    val readerAvailable: Boolean = false,
    val readerActive: Boolean = false,
    val themeColor: Int? = null,
    val fullscreen: Boolean = false,
    val desktopMode: Boolean = false,
    val crashed: Boolean = false,
    val protectionOff: Boolean = false,
    val find: FindState? = null,
    val thumbnailVersion: Int = 0,
) {
    val id: String get() = tab.id
    val url: String get() = tab.url
    val isPrivate: Boolean get() = tab.isPrivate
    val isBlank: Boolean get() = tab.isBlank
}

data class BrowserState(
    val tabs: List<TabState> = emptyList(),
    val groups: List<TabGroup> = emptyList(),
    val selectedId: String? = null,
    /** False until saved tabs have been read back at start-up. */
    val ready: Boolean = false,
    /** Changes whenever a page surface is created or dropped, so the view re-attaches. */
    val sessionEpoch: Int = 0,
) {
    val selected: TabState? get() = tabs.firstOrNull { it.id == selectedId }

    fun tab(id: String?): TabState? = if (id == null) null else tabs.firstOrNull { it.id == id }

    fun visibleTabs(private: Boolean): List<TabState> =
        tabs.filter { !it.tab.archived && it.isPrivate == private }

    val archivedTabs: List<TabState> get() = tabs.filter { it.tab.archived }
}

/** One-off things the interface should tell the user about. */
sealed interface BrowserEvent {
    data class TabsClosed(val count: Int) : BrowserEvent
    data class DownloadStarted(val name: String) : BrowserEvent
    data class DownloadFinished(val name: String, val uri: Uri, val mimeType: String?) : BrowserEvent
    data class DownloadFailed(val name: String) : BrowserEvent
    data object NoAppForLink : BrowserEvent
    /** The page finished loading; a good moment to refresh its preview image. */
    data class PageSettled(val tabId: String) : BrowserEvent
}

enum class ConfirmKind {
    /** A `confirm()` dialog from the page itself; the message is the page's. */
    PAGE,
    LEAVE_PAGE,
    RESUBMIT_FORM,
    OPEN_IN_APP,

    /** Android 17 asks before an app may reach devices on the user's network. */
    LOCAL_NETWORK,
    LOCATION,
    CAMERA,
    MICROPHONE,
    CAMERA_AND_MICROPHONE,
}

enum class DateTimeKind { DATE, TIME, DATE_TIME, MONTH, WEEK }

data class ChoiceOption(
    val id: String,
    val label: String,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val isHeader: Boolean = false,
    val isSeparator: Boolean = false,
)

data class Credentials(val username: String, val password: String)

/** Something a page asked for that needs an answer from the person using the browser. */
sealed interface PageRequest {
    val tabId: String

    data class Alert(
        override val tabId: String,
        val message: String,
        val onDone: () -> Unit,
    ) : PageRequest

    data class Confirm(
        override val tabId: String,
        val kind: ConfirmKind,
        val message: String,
        val host: String,
        val onResult: (Boolean) -> Unit,
    ) : PageRequest

    data class TextInput(
        override val tabId: String,
        val message: String,
        val defaultValue: String,
        val onResult: (String?) -> Unit,
    ) : PageRequest

    data class Choice(
        override val tabId: String,
        val multiple: Boolean,
        val options: List<ChoiceOption>,
        val onResult: (List<String>?) -> Unit,
    ) : PageRequest

    data class Login(
        override val tabId: String,
        val host: String,
        val passwordOnly: Boolean,
        val username: String,
        val onResult: (Credentials?) -> Unit,
    ) : PageRequest

    data class FilePick(
        override val tabId: String,
        val multiple: Boolean,
        val mimeTypes: List<String>,
        val onResult: (List<Uri>) -> Unit,
    ) : PageRequest

    data class DateTime(
        override val tabId: String,
        val kind: DateTimeKind,
        val value: String,
        val onResult: (String?) -> Unit,
    ) : PageRequest

    data class AndroidPermissions(
        override val tabId: String,
        val permissions: List<String>,
        val onResult: (Boolean) -> Unit,
    ) : PageRequest

    data class ContextMenu(
        override val tabId: String,
        val linkUrl: String?,
        val imageUrl: String?,
        val onDone: () -> Unit,
    ) : PageRequest
}

/** Words the engine needs from the app's resources (it has none of its own). */
fun interface EngineText {
    fun errorPage(kind: LoadErrorKind): ErrorPageText
}
