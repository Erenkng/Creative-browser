package com.oavi.browser.device

import android.view.Window

object RefreshRate {
    /**
     * Asks for the display's fastest mode at the current resolution (120 Hz on a Galaxy A55).
     * The system still decides; on panels with a single mode this changes nothing.
     */
    fun preferHighest(window: Window) {
        try {
            val display = window.context.display
            val current = display.mode
            val fastest = display.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .maxByOrNull { it.refreshRate }
                ?: return
            val attributes = window.attributes
            if (attributes.preferredDisplayModeId != fastest.modeId) {
                attributes.preferredDisplayModeId = fastest.modeId
                window.attributes = attributes
            }
        } catch (_: RuntimeException) {
            // No display attached to this context yet; the default mode is kept.
        }
    }

    /** Hands the choice of refresh rate back to the phone. */
    fun usePhoneDefault(window: Window) {
        val attributes = window.attributes
        if (attributes.preferredDisplayModeId != 0) {
            attributes.preferredDisplayModeId = 0
            window.attributes = attributes
        }
    }

    /** Duration of one frame at the display's current rate, in nanoseconds. */
    fun frameIntervalNanos(window: Window): Long {
        val rate = try {
            window.context.display.refreshRate
        } catch (_: RuntimeException) {
            60f
        }
        return (1_000_000_000.0 / rate.coerceIn(30f, 240f)).toLong()
    }
}
