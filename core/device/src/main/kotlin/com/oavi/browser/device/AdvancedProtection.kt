package com.oavi.browser.device

import android.content.Context
import android.security.advancedprotection.AdvancedProtectionManager

/**
 * Android's Advanced Protection Mode. When the user has opted in system-wide, Oavi follows
 * suit: HTTPS-only browsing and strict tracking protection regardless of its own settings.
 */
object AdvancedProtection {
    fun isEnabled(context: Context): Boolean = try {
        context.getSystemService(AdvancedProtectionManager::class.java)?.isAdvancedProtectionEnabled == true
    } catch (_: RuntimeException) {
        false
    }
}
