package com.oavi.browser.device

import android.content.Context
import android.os.ProfilingManager
import android.os.ProfilingResult
import android.os.ProfilingTrigger
import android.util.Log
import java.util.concurrent.Executors
import java.util.function.Consumer

/**
 * Android 17 trigger-based profiling: the system records a trace when the app cold-starts or
 * trips an anomaly (excess memory, binder spam) and hands the file back. Used in debug builds
 * to see where start-up time goes on a real device; traces stay on the device.
 */
object StartupProfiling {
    private const val TAG = "OaviProfiling"

    fun register(context: Context) {
        try {
            val manager = context.getSystemService(ProfilingManager::class.java) ?: return
            val triggers = listOf(
                ProfilingTrigger.Builder(ProfilingTrigger.TRIGGER_TYPE_COLD_START).build(),
                ProfilingTrigger.Builder(ProfilingTrigger.TRIGGER_TYPE_ANOMALY).build(),
            )
            val callback = Consumer<ProfilingResult> { result ->
                if (result.errorCode == ProfilingResult.ERROR_NONE) {
                    Log.i(TAG, "Profile written: ${result.resultFilePath}")
                } else {
                    Log.w(TAG, "Profiling failed: ${result.errorCode} ${result.errorMessage}")
                }
            }
            manager.registerForAllProfilingResults(Executors.newSingleThreadExecutor(), callback)
            manager.addProfilingTriggers(triggers)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Profiling triggers unavailable", e)
        }
    }
}
