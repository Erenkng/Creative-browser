package com.oavi.browser.model

/** Identifiers Android exposes about the chip (android.os.Build.SOC_MODEL and friends). */
data class SocInfo(
    val manufacturer: String,
    val model: String,
    val hardware: String = "",
    val board: String = "",
)

enum class ProfileKind { EXYNOS_1480, EXYNOS, STANDARD, LOW_MEMORY }

/**
 * Everything that differs between devices, in one place. An app cannot reach into the kernel
 * or the GPU driver; what it can do is size the engine for the chip, ask for the display's
 * fast mode, and tell the scheduler when a burst of work is coming. That is what this holds.
 */
data class PerformanceProfile(
    val kind: ProfileKind,
    /** Web content processes. null keeps the engine's own default. */
    val contentProcesses: Int?,
    /** How many tabs stay loaded in memory; the rest are restored on demand. */
    val maxLiveSessions: Int,
    /** In-memory HTTP cache, in KB. null keeps the engine's automatic sizing. */
    val memoryCacheKb: Int?,
    /** Pages kept ready for instant back/forward. null keeps the engine default. */
    val sessionHistoryViewers: Int?,
    /** Keep a spare content process warm so a new tab starts without a process launch. */
    val prelaunchContentProcess: Boolean,
    /** Parallel speculative connections opened ahead of a navigation. */
    val speculativeConnections: Int?,
    /** Ask the display for its highest refresh rate. */
    val preferHighRefreshRate: Boolean,
    /** Send ADPF hints so page loads and gestures land on the big cores. */
    val useLoadBoost: Boolean,
) {
    /** Engine preferences for this profile. Only keys that differ from engine defaults. */
    fun enginePrefs(): Map<String, Any> = buildMap {
        contentProcesses?.let { put("dom.ipc.processCount", it) }
        put("dom.ipc.processPrelaunch.enabled", prelaunchContentProcess)
        memoryCacheKb?.let { put("browser.cache.memory.capacity", it) }
        sessionHistoryViewers?.let { put("browser.sessionhistory.max_total_viewers", it) }
        speculativeConnections?.let { put("network.http.speculative-parallel-limit", it) }
        put("network.dns.disablePrefetch", false)
        put("network.predictor.enabled", true)
    }
}

object PerformanceProfiles {
    /** Samsung's part number for the Exynos 1480 (Galaxy A55 5G). */
    private const val EXYNOS_1480_PART = "s5e8845"

    fun isExynos1480(soc: SocInfo): Boolean = fields(soc).any {
        it.contains(EXYNOS_1480_PART) || it.replace(" ", "").contains("exynos1480")
    }

    fun isExynos(soc: SocInfo): Boolean {
        if (isExynos1480(soc)) return true
        val samsungSilicon = soc.manufacturer.lowercase().let { it.contains("samsung") || it.contains("s.lsi") }
        val exynosName = fields(soc).any { it.startsWith("s5e") || it.contains("exynos") || it.startsWith("universal") }
        return exynosName && (samsungSilicon || soc.manufacturer.isBlank())
    }

    fun select(soc: SocInfo, totalRamMb: Int): PerformanceProfile = when {
        isExynos1480(soc) -> exynos1480(totalRamMb)
        totalRamMb < LOW_MEMORY_MB -> lowMemory()
        isExynos(soc) -> standard(totalRamMb).copy(
            kind = ProfileKind.EXYNOS,
            useLoadBoost = true,
            prelaunchContentProcess = totalRamMb >= ROOMY_MB,
        )
        else -> standard(totalRamMb)
    }

    /**
     * Exynos 1480: 4× Cortex-A78 (2.75 GHz) + 4× Cortex-A55, Xclipse 530, 120 Hz panel.
     * Four fast cores carry four content processes without contention, and with 8 GB there
     * is room to keep more tabs alive and a spare process warm.
     */
    private fun exynos1480(totalRamMb: Int): PerformanceProfile {
        val roomy = totalRamMb >= ROOMY_MB
        return PerformanceProfile(
            kind = ProfileKind.EXYNOS_1480,
            contentProcesses = if (roomy) 4 else 3,
            maxLiveSessions = if (roomy) 8 else 5,
            // Android 17 caps app memory by device RAM; the cache lives in the app's own
            // process, so it stays modest. Pages themselves render in separate processes.
            memoryCacheKb = if (roomy) 65_536 else 32_768,
            sessionHistoryViewers = if (roomy) 6 else 4,
            prelaunchContentProcess = true,
            speculativeConnections = 10,
            preferHighRefreshRate = true,
            useLoadBoost = true,
        )
    }

    private fun standard(totalRamMb: Int): PerformanceProfile = PerformanceProfile(
        kind = ProfileKind.STANDARD,
        contentProcesses = null,
        maxLiveSessions = when {
            totalRamMb >= 11_000 -> 8
            totalRamMb >= ROOMY_MB -> 6
            else -> 4
        },
        memoryCacheKb = null,
        sessionHistoryViewers = null,
        prelaunchContentProcess = false,
        speculativeConnections = null,
        preferHighRefreshRate = true,
        useLoadBoost = false,
    )

    private fun lowMemory(): PerformanceProfile = PerformanceProfile(
        kind = ProfileKind.LOW_MEMORY,
        contentProcesses = 2,
        maxLiveSessions = 2,
        memoryCacheKb = 16_384,
        sessionHistoryViewers = 1,
        prelaunchContentProcess = false,
        speculativeConnections = null,
        preferHighRefreshRate = true,
        useLoadBoost = false,
    )

    private fun fields(soc: SocInfo): List<String> =
        listOf(soc.model, soc.hardware, soc.board).map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    /** Devices sold as "8 GB" report a little less; treat ≥ 7.2 GB as roomy. */
    private const val ROOMY_MB = 7_200
    private const val LOW_MEMORY_MB = 5_200
}
