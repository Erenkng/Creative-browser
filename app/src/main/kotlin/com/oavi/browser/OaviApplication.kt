package com.oavi.browser

import android.app.Application
import com.oavi.browser.device.StartupProfiling

class OaviApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // The engine runs web content in separate processes that also create this class.
        // Only the app's own process owns tabs, settings and the interface.
        if (getProcessName() != packageName) return
        container = AppContainer(this)
        if (BuildConfig.DEBUG) StartupProfiling.register(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::container.isInitialized) container.tabs.onTrimMemory(level)
    }
}
