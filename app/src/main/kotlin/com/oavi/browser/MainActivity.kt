package com.oavi.browser

import android.app.DownloadManager
import android.app.HandoffActivityData
import android.app.HandoffActivityDataRequestInfo
import android.app.HandoffActivityParams
import android.app.SearchManager
import android.app.role.RoleManager
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.oavi.browser.device.RefreshRate
import com.oavi.browser.model.UrlInput

class MainActivity : ComponentActivity() {
    private val container: AppContainer
        get() = (application as OaviApplication).container

    /** Private tabs are hidden until the screen lock is passed. */
    var privateLocked by mutableStateOf(false)

    var isDefaultBrowser by mutableStateOf(false)
        private set

    private val handoffParams: HandoffActivityParams by lazy {
        HandoffActivityParams.Builder().setAllowHandoffWithoutPackageInstalled(true).build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent { OaviApp(container, this) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        container.onConfigurationChanged(newConfig)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        container.tabs.setForeground(true)
        refreshDefaultBrowser()
        applyPerformance()
    }

    override fun onStop() {
        super.onStop()
        container.tabs.setForeground(false)
        val hasPrivateTabs = container.tabs.state.value.tabs.any { it.isPrivate }
        if (!isChangingConfigurations && hasPrivateTabs && container.settings.value.lockPrivateTabs) {
            privateLocked = true
        }
    }

    /**
     * Android 17 Handoff: lets the page being read continue in the browser of another of the
     * user's devices. Private tabs and empty tabs are never offered.
     */
    override fun onHandoffActivityDataRequested(requestInfo: HandoffActivityDataRequestInfo): HandoffActivityData? {
        val tab = container.tabs.state.value.selected ?: return null
        val shareable = !tab.isPrivate && (tab.url.startsWith("https://") || tab.url.startsWith("http://"))
        return if (shareable) HandoffActivityData.createWebHandoff(Uri.parse(tab.url)) else null
    }

    fun updateHandoff(enabled: Boolean) {
        try {
            setHandoffEnabled(enabled, handoffParams)
        } catch (_: RuntimeException) {
            // Handoff is optional; a phone without it simply does not offer the page elsewhere.
        }
    }

    /** Asks for the display's fastest mode and tunes the load boost to it, if the user wants that. */
    fun applyPerformance() {
        val boost = container.settings.value.performanceBoost
        if (boost && container.profile.preferHighRefreshRate) {
            RefreshRate.preferHighest(window)
        } else {
            RefreshRate.usePhoneDefault(window)
        }
        container.loadBoost?.start(RefreshRate.frameIntervalNanos(window))
    }

    /** Hides the status and navigation bars for full-screen video; a swipe brings them back briefly. */
    fun setFullscreen(immersive: Boolean) {
        val controller = window.insetsController ?: return
        if (immersive) {
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsets.Type.systemBars())
        } else {
            controller.show(WindowInsets.Type.systemBars())
        }
    }

    /** Dark icons on light backgrounds and the reverse, following Oavi's own theme. */
    fun setLightSystemBars(light: Boolean) {
        val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(if (light) mask else 0, mask)
    }

    /** Keeps private pages out of screenshots, the recent-apps preview and screen capture. */
    fun setSecure(secure: Boolean) {
        if (secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    fun refreshDefaultBrowser() {
        val roles = getSystemService(RoleManager::class.java)
        isDefaultBrowser = roles != null && roles.isRoleHeld(RoleManager.ROLE_BROWSER)
    }

    /** The system screen that offers Oavi as the default browser, or null when it cannot be offered. */
    fun defaultBrowserIntent(): Intent? {
        val roles = getSystemService(RoleManager::class.java) ?: return null
        if (!roles.isRoleAvailable(RoleManager.ROLE_BROWSER) || roles.isRoleHeld(RoleManager.ROLE_BROWSER)) return null
        return roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
    }

    fun openDownloads() {
        try {
            startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
        } catch (_: Exception) {
        }
    }

    /** Opens a finished download with whichever app handles that kind of file. */
    fun openFile(uri: Uri, mimeType: String?): Boolean = try {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType ?: contentResolver.getType(uri) ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(view)
        true
    } catch (_: Exception) {
        false
    }

    private fun handleIntent(intent: Intent?) {
        val url = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            Intent.ACTION_WEB_SEARCH -> intent.getStringExtra(SearchManager.QUERY)?.takeIf { it.isNotBlank() }?.let(::searchUrl)
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { shared ->
                LINK.find(shared)?.value ?: searchUrl(shared)
            }
            else -> null
        } ?: return
        container.tabs.openFromOutside(url)
    }

    private fun searchUrl(text: String): String =
        UrlInput.resolve(text, container.settings.value.searchEngine).url

    private companion object {
        val LINK = Regex("https?://\\S+")
    }
}
