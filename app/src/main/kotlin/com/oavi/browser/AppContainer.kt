package com.oavi.browser

import android.app.Application
import com.oavi.browser.data.BlockedStats
import com.oavi.browser.data.DownloadWriter
import com.oavi.browser.data.LibraryStore
import com.oavi.browser.data.OaviDatabase
import com.oavi.browser.data.SettingsStore
import com.oavi.browser.data.TabStore
import com.oavi.browser.data.ThumbnailStore
import com.oavi.browser.device.AdvancedProtection
import com.oavi.browser.device.DeviceInfo
import com.oavi.browser.device.LoadBoost
import com.oavi.browser.engine.EngineText
import com.oavi.browser.engine.OaviEngine
import com.oavi.browser.engine.TabsController
import com.oavi.browser.model.ErrorPageText
import com.oavi.browser.model.LoadErrorKind
import com.oavi.browser.model.PerformanceProfile
import com.oavi.browser.model.Settings
import com.oavi.browser.ui.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The long-lived parts of the app, created once when the process starts. Building this
 * starts the web engine and begins restoring tabs, so both are under way before the first
 * frame is drawn.
 */
class AppContainer(private val app: Application) {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val profile: PerformanceProfile = DeviceInfo.profile(app)
    val hardened: Boolean = AdvancedProtection.isEnabled(app)

    private val database = OaviDatabase(app)
    val library = LibraryStore(database)
    private val settingsStore = SettingsStore(app)

    /** The saved settings, or null for the instant before they have been read. */
    val loadedSettings: StateFlow<Settings?> = settingsStore.settings.stateIn(scope, SharingStarted.Eagerly, null)
    val settings: StateFlow<Settings> = settingsStore.settings.stateIn(scope, SharingStarted.Eagerly, Settings())
    val blockedStats: StateFlow<BlockedStats> = settingsStore.blockedStats.stateIn(scope, SharingStarted.Eagerly, BlockedStats())

    val loadBoost: LoadBoost? = if (profile.useLoadBoost) LoadBoost(app) else null

    private val engine = OaviEngine(app, profile, debug = BuildConfig.DEBUG, hardened = hardened)

    val tabs = TabsController(
        context = app,
        engine = engine,
        tabStore = TabStore(database),
        library = library,
        thumbnails = ThumbnailStore(app),
        downloads = DownloadWriter(app),
        settingsStore = settingsStore,
        settings = settings,
        text = EngineText(::errorText),
        profile = profile,
        boost = loadBoost,
        scope = scope,
    )

    init {
        scope.launch { tabs.restore() }
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        scope.launch { settingsStore.update(transform) }
    }

    private fun errorText(kind: LoadErrorKind): ErrorPageText {
        val (title, body) = when (kind) {
            LoadErrorKind.OFFLINE -> R.string.error_offline_title to R.string.error_offline_body
            LoadErrorKind.NOT_FOUND -> R.string.error_not_found_title to R.string.error_not_found_body
            LoadErrorKind.TIMEOUT -> R.string.error_timeout_title to R.string.error_timeout_body
            LoadErrorKind.SECURITY -> R.string.error_security_title to R.string.error_security_body
            LoadErrorKind.CONNECTION -> R.string.error_connection_title to R.string.error_connection_body
            LoadErrorKind.LOCAL_NETWORK -> R.string.error_local_network_title to R.string.error_local_network_body
            LoadErrorKind.UNKNOWN -> R.string.error_unknown_title to R.string.error_unknown_body
        }
        return ErrorPageText(app.getString(title), app.getString(body), app.getString(R.string.error_retry))
    }
}
