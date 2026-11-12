package com.oavi.browser.engine

import android.content.Context
import android.util.Log
import com.oavi.browser.model.PerformanceProfile
import com.oavi.browser.model.Settings
import com.oavi.browser.model.ThemeMode
import com.oavi.browser.model.TrackingProtection
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.StorageController
import java.io.File

/**
 * Owns the web engine. Created once, as early as possible, so the engine is already warm
 * when the first page is requested.
 *
 * @param hardened true when Android's Advanced Protection Mode is on; the strictest
 *   protections then apply whatever the app's own settings say.
 */
class OaviEngine(
    context: Context,
    private val profile: PerformanceProfile,
    private val debug: Boolean,
    private val hardened: Boolean,
) {
    private val appContext = context.applicationContext

    internal val runtime: GeckoRuntime = GeckoRuntime.create(appContext, buildRuntimeSettings())
    internal val pageTools = PageTools(runtime)

    private var current = Settings()

    init {
        pageTools.install()
    }

    /** Applies the user's settings to the running engine. Safe to call on every change. */
    fun apply(settings: Settings) {
        current = settings
        val protection = effectiveProtection(settings)
        val blocking = runtime.settings.contentBlocking
        blocking.setAntiTracking(antiTracking(protection))
        blocking.setEnhancedTrackingProtectionLevel(etpLevel(protection))
        blocking.setStrictSocialTrackingProtection(protection == TrackingProtection.STRICT)
        runtime.settings.setPreferredColorScheme(colorScheme(settings.themeMode))
        runtime.settings.setAllowInsecureConnections(
            if (settings.httpsOnly || hardened) GeckoRuntimeSettings.HTTPS_ONLY else GeckoRuntimeSettings.ALLOW_ALL,
        )
    }

    /** Removes cookies, caches and site storage. [onDone] runs when the engine has finished. */
    fun clearSiteData(onDone: () -> Unit) {
        runtime.storageController.clearData(StorageController.ClearFlags.ALL).accept(
            { onDone() },
            { error ->
                Log.w(TAG, "Clearing site data failed", error)
                onDone()
            },
        )
    }

    internal fun trackingEnabled(): Boolean = effectiveProtection(current) != TrackingProtection.OFF

    internal fun newSession(private: Boolean, desktop: Boolean, trackingProtection: Boolean): GeckoSession {
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(private)
            .useTrackingProtection(trackingProtection)
            .userAgentMode(userAgentMode(desktop))
            .viewportMode(viewportMode(desktop))
            .suspendMediaWhenInactive(true)
            .build()
        return GeckoSession(settings)
    }

    internal fun userAgentMode(desktop: Boolean): Int =
        if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE

    internal fun viewportMode(desktop: Boolean): Int =
        if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE

    private fun effectiveProtection(settings: Settings): TrackingProtection =
        if (hardened) TrackingProtection.STRICT else settings.trackingProtection

    private fun buildRuntimeSettings(): GeckoRuntimeSettings {
        val defaults = Settings()
        val protection = effectiveProtection(defaults)
        val blocking = ContentBlocking.Settings.Builder()
            .antiTracking(antiTracking(protection))
            .enhancedTrackingProtectionLevel(etpLevel(protection))
            .strictSocialTrackingProtection(protection == TrackingProtection.STRICT)
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .build()
        val builder = GeckoRuntimeSettings.Builder()
            .contentBlocking(blocking)
            .consoleOutput(debug)
            .remoteDebuggingEnabled(debug)
            .aboutConfigEnabled(debug)
        writeEnginePrefs()?.let { builder.configFilePath(it) }
        return builder.build()
    }

    /**
     * The engine reads start-up preferences from a small YAML file. This is where the
     * device profile (process count, cache sizes, speculative connections) is handed over.
     */
    private fun writeEnginePrefs(): String? = try {
        val file = File(appContext.filesDir, "engine-prefs.yaml")
        val body = buildString {
            append("prefs:\n")
            for ((key, value) in profile.enginePrefs()) {
                append("  ").append(key).append(": ").append(value.toString()).append('\n')
            }
        }
        if (!file.exists() || file.readText() != body) file.writeText(body)
        file.absolutePath
    } catch (e: Exception) {
        Log.w(TAG, "Could not write engine preferences; using engine defaults", e)
        null
    }

    private fun antiTracking(protection: TrackingProtection): Int = when (protection) {
        TrackingProtection.OFF -> ContentBlocking.AntiTracking.NONE
        TrackingProtection.STANDARD -> ContentBlocking.AntiTracking.DEFAULT
        TrackingProtection.STRICT -> ContentBlocking.AntiTracking.STRICT
    }

    private fun etpLevel(protection: TrackingProtection): Int = when (protection) {
        TrackingProtection.OFF -> ContentBlocking.EtpLevel.NONE
        TrackingProtection.STANDARD -> ContentBlocking.EtpLevel.DEFAULT
        TrackingProtection.STRICT -> ContentBlocking.EtpLevel.STRICT
    }

    private fun colorScheme(mode: ThemeMode): Int = when (mode) {
        ThemeMode.SYSTEM -> GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
        ThemeMode.LIGHT -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
        ThemeMode.DARK -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
    }

    private companion object {
        const val TAG = "OaviEngine"
    }
}
