package com.oavi.browser.model

enum class TrackingProtection { OFF, STANDARD, STRICT }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ReaderTheme { AUTO, LIGHT, SEPIA, DARK }

/** Where the browser's controls sit on the screen. */
enum class LayoutStyle {
    /** Oavi's own: one floating capsule above the bottom edge that shrinks while reading. */
    ISLAND,

    /** The familiar address bar along the top, with the tab count and menu beside it. */
    CLASSIC,

    /** Address bar on top with a strip of open tabs under it, and a toolbar along the bottom. */
    STRIP,

    /** Address bar and toolbar together along the bottom, protection count always in sight. */
    DOCK,

    /**
     * Almost nothing but the page: the page sits as a rounded card, a small handle opens a
     * floating command bar, and tabs live in a side panel.
     */
    FOCUS,
}

/** The colour of buttons, switches and highlights. Sites with a colour of their own still tint it. */
enum class Accent { TEAL, BLUE, VIOLET, ROSE, AMBER }

/**
 * Resolving site names over an encrypted connection (DNS over HTTPS), so the network cannot see
 * or change which sites are opened. [OFF] uses the network's own resolver.
 */
enum class SecureDns(val uri: String?) {
    OFF(null),
    CLOUDFLARE("https://mozilla.cloudflare-dns.com/dns-query"),
    QUAD9("https://dns.quad9.net/dns-query"),

    /** Also refuses to resolve the names of known ad and tracker servers. */
    ADGUARD("https://dns.adguard-dns.com/dns-query"),
}

/** How long a tab can go untouched before it is moved to the archive. */
enum class ArchiveAfter(val days: Int?) {
    DAY(1),
    WEEK(7),
    TWO_WEEKS(14),
    NEVER(null),
}

data class Settings(
    val onboarded: Boolean = false,
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val adBlock: Boolean = true,
    val trackingProtection: TrackingProtection = TrackingProtection.STRICT,
    val httpsOnly: Boolean = false,
    val forceDarkPages: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val lockPrivateTabs: Boolean = false,
    val collapseIslandOnScroll: Boolean = true,
    val performanceBoost: Boolean = true,
    /** The user has switched tabs by swiping the island, or dismissed the tip about it. */
    val swipeTipDone: Boolean = false,
    /** Page text size as a factor of normal; 0 follows the phone's own font size. */
    val textScale: Float = 0f,
    /** Pinch to zoom even on sites that turn it off. */
    val zoomEverywhere: Boolean = true,
    val readerFontScale: Float = 1f,
    val readerTheme: ReaderTheme = ReaderTheme.AUTO,
    val layout: LayoutStyle = LayoutStyle.ISLAND,
    /** Mirrors the controls so the most used buttons sit under the left thumb. */
    val leftHanded: Boolean = false,
    val accent: Accent = Accent.TEAL,
    val secureDns: SecureDns = SecureDns.OFF,
    /** Pages load without their images, to save mobile data. */
    val dataSaver: Boolean = false,
    /** Answer cookie consent pop-ups with "reject" wherever a site offers it. */
    val rejectCookieBanners: Boolean = true,
    /** Remove tracking parameters (utm_source, fbclid …) from links. */
    val cleanLinks: Boolean = true,
    /** Darken pages automatically in the evening and at night. */
    val darkPagesAtNight: Boolean = false,
    val archiveAfter: ArchiveAfter = ArchiveAfter.TWO_WEEKS,
) {
    /** Whether pages are shown dark at [hourOfDay] (0-23). */
    fun darkPagesAt(hourOfDay: Int): Boolean =
        forceDarkPages || (darkPagesAtNight && (hourOfDay >= NIGHT_STARTS || hourOfDay < NIGHT_ENDS))

    companion object {
        const val NIGHT_STARTS = 20
        const val NIGHT_ENDS = 7
    }
}

enum class DayPart {
    MORNING, AFTERNOON, EVENING, NIGHT;

    companion object {
        fun of(hourOfDay: Int): DayPart = when (hourOfDay) {
            in 5..11 -> MORNING
            in 12..17 -> AFTERNOON
            in 18..22 -> EVENING
            else -> NIGHT
        }
    }
}
