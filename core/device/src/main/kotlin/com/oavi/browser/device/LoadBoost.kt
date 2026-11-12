package com.oavi.browser.device

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PerformanceHintManager
import android.os.Process
import android.os.SystemClock
import java.io.File

/**
 * Tells the scheduler when a burst of work is under way (a page load, a gesture) so the
 * threads that matter are moved to the fast cores and clocked up, then released again.
 *
 * This uses Android's performance hint sessions (ADPF). The session covers the UI thread
 * and the engine's threads that live in this process. While a boost is active the session
 * reports work running behind its frame budget, which is the signal big.LITTLE schedulers
 * such as the Exynos 1480's act on; afterwards it reports slack and prefers efficiency.
 *
 * Devices without hint-session support return no session and every call becomes a no-op.
 */
class LoadBoost(context: Context) {
    private val manager: PerformanceHintManager? = context.getSystemService(PerformanceHintManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var session: PerformanceHintManager.Session? = null
    private var targetNanos = 8_333_333L
    private var boostUntil = 0L
    private var ticking = false
    private var lastThreadRefresh = 0L

    var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) calm()
        }

    private val tick = object : Runnable {
        override fun run() {
            val active = session
            if (active == null || !enabled || SystemClock.uptimeMillis() >= boostUntil) {
                ticking = false
                settle()
                return
            }
            report { active.reportActualWorkDuration(targetNanos * 2) }
            handler.postDelayed(this, TICK_MS)
        }
    }

    /** Must be called on the main thread once the window knows its frame interval. */
    fun start(frameIntervalNanos: Long) {
        targetNanos = frameIntervalNanos.coerceAtLeast(1_000_000L)
        if (session != null) {
            report { session?.updateTargetWorkDuration(targetNanos) }
            return
        }
        val hints = manager ?: return
        session = try {
            hints.createHintSession(threadIds(), targetNanos)
        } catch (_: RuntimeException) {
            null
        }
        lastThreadRefresh = SystemClock.uptimeMillis()
        settle()
    }

    /** Request fast cores for the next [durationMs]. Cheap to call repeatedly. */
    fun boost(durationMs: Long = 1_500L) {
        val active = session ?: return
        if (!enabled) return
        val now = SystemClock.uptimeMillis()
        boostUntil = maxOf(boostUntil, now + durationMs)
        if (now - lastThreadRefresh > THREAD_REFRESH_MS) {
            // Engine threads start after the session is created; pick them up as they appear.
            lastThreadRefresh = now
            report { active.setThreads(threadIds()) }
        }
        if (!ticking) {
            ticking = true
            report { active.setPreferPowerEfficiency(false) }
            handler.post(tick)
        }
    }

    /** End a boost early, e.g. when the page finished loading. */
    fun calm() {
        boostUntil = 0L
    }

    fun close() {
        handler.removeCallbacks(tick)
        ticking = false
        report { session?.close() }
        session = null
    }

    private fun settle() {
        val active = session ?: return
        report {
            active.reportActualWorkDuration(targetNanos / 2)
            active.setPreferPowerEfficiency(true)
        }
    }

    private inline fun report(block: () -> Unit) {
        try {
            block()
        } catch (_: RuntimeException) {
            // A hint is advisory; a rejected one must never disturb browsing.
        }
    }

    /** The UI thread plus the engine's main, network and compositing threads in this process. */
    private fun threadIds(): IntArray {
        val ids = linkedSetOf(Process.myTid())
        try {
            File("/proc/self/task").listFiles()?.forEach { task ->
                val tid = task.name.toIntOrNull() ?: return@forEach
                val name = try {
                    File(task, "comm").readText().trim()
                } catch (_: Exception) {
                    return@forEach
                }
                if (ENGINE_THREADS.any { name.startsWith(it) }) ids += tid
            }
        } catch (_: Exception) {
            // Thread names are a refinement; the UI thread alone is still a valid session.
        }
        return ids.toIntArray()
    }

    private companion object {
        const val TICK_MS = 100L
        const val THREAD_REFRESH_MS = 10_000L
        val ENGINE_THREADS = listOf("Gecko", "Socket Thread", "Compositor", "Renderer", "IPC I/O")
    }
}
