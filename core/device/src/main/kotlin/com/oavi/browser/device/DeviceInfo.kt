package com.oavi.browser.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.oavi.browser.model.PerformanceProfile
import com.oavi.browser.model.PerformanceProfiles
import com.oavi.browser.model.SocInfo

/** Reads what Android reports about the chip and memory, and picks the matching profile. */
object DeviceInfo {
    fun soc(): SocInfo = SocInfo(
        manufacturer = Build.SOC_MANUFACTURER.orEmpty(),
        model = Build.SOC_MODEL.orEmpty(),
        hardware = Build.HARDWARE.orEmpty(),
        board = Build.BOARD.orEmpty(),
    )

    fun totalRamMb(context: Context): Int {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return 0
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        val bytes = if (info.advertisedMem > 0) info.advertisedMem else info.totalMem
        return (bytes / (1024L * 1024L)).toInt()
    }

    fun profile(context: Context): PerformanceProfile =
        PerformanceProfiles.select(soc(), totalRamMb(context))

    /** Human-readable chip name for the settings screen, e.g. "Samsung s5e8845". */
    fun chipLabel(): String {
        val soc = soc()
        val name = listOf(soc.manufacturer, soc.model)
            .filter { it.isNotBlank() && !it.equals(Build.UNKNOWN, ignoreCase = true) }
            .joinToString(" ")
        return name.ifBlank { soc.hardware }
    }
}
