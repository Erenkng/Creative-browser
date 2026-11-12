package com.oavi.browser.engine

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension

/**
 * The bundled "page tools" extension (assets/extensions/oavi): ad blocking in its background
 * script, and reader view / dark pages / theme colour in its content script. This class
 * installs it and carries messages between the app and those scripts.
 */
internal class PageTools(private val runtime: GeckoRuntime) {
    private var extension: WebExtension? = null
    private var backgroundPort: WebExtension.Port? = null
    private var blockerConfig: JSONObject? = null
    private val waiting = ArrayList<Pair<GeckoSession, WebExtension.MessageDelegate>>()

    /** Called with the number of requests the blocker stopped since the last report. */
    var onBlocked: ((Int) -> Unit)? = null

    private val backgroundDelegate = object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            backgroundPort = port
            port.setDelegate(object : WebExtension.PortDelegate {
                override fun onPortMessage(message: Any, port: WebExtension.Port) {
                    val json = message as? JSONObject ?: return
                    if (json.optString("type") == "blocked") {
                        onBlocked?.invoke(json.optInt("count"))
                    }
                }

                override fun onDisconnect(port: WebExtension.Port) {
                    if (backgroundPort === port) backgroundPort = null
                }
            })
            blockerConfig?.let { port.postMessage(it) }
        }
    }

    fun install() {
        runtime.webExtensionController.ensureBuiltIn(LOCATION, ID).accept(
            { installed ->
                if (installed != null) {
                    extension = installed
                    installed.setMessageDelegate(backgroundDelegate, BACKGROUND_APP)
                    for ((session, delegate) in waiting) {
                        session.webExtensionController.setMessageDelegate(installed, delegate, PAGE_APP)
                    }
                    waiting.clear()
                }
            },
            { error -> Log.e(TAG, "Page tools could not be installed", error) },
        )
    }

    /** Routes messages from the content script running in [session] to [delegate]. */
    fun attach(session: GeckoSession, delegate: WebExtension.MessageDelegate) {
        val installed = extension
        if (installed == null) {
            waiting += session to delegate
        } else {
            session.webExtensionController.setMessageDelegate(installed, delegate, PAGE_APP)
        }
    }

    fun detach(session: GeckoSession) {
        waiting.removeAll { it.first === session }
    }

    fun setBlockerConfig(adBlock: Boolean, allowHosts: Collection<String>) {
        val config = JSONObject()
            .put("type", "config")
            .put("adBlock", adBlock)
            .put("allowHosts", JSONArray(allowHosts))
        blockerConfig = config
        backgroundPort?.postMessage(config)
    }

    private companion object {
        const val TAG = "OaviPageTools"
        const val LOCATION = "resource://android/assets/extensions/oavi/"
        const val ID = "tools@oavi.browser"
        const val BACKGROUND_APP = "oaviBackground"
        const val PAGE_APP = "oaviPage"
    }
}
