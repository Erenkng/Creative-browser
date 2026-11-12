package com.oavi.browser.model

enum class TrackingProtection { OFF, STANDARD, STRICT }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ReaderTheme { AUTO, LIGHT, SEPIA, DARK }

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
    val readerFontScale: Float = 1f,
    val readerTheme: ReaderTheme = ReaderTheme.AUTO,
)

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
