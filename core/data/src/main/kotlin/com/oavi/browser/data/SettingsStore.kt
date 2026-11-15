package com.oavi.browser.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.oavi.browser.model.Accent
import com.oavi.browser.model.ArchiveAfter
import com.oavi.browser.model.LayoutStyle
import com.oavi.browser.model.ReaderTheme
import com.oavi.browser.model.SearchEngine
import com.oavi.browser.model.SecureDns
import com.oavi.browser.model.Settings
import com.oavi.browser.model.ThemeMode
import com.oavi.browser.model.TrackingProtection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.oaviPreferences: DataStore<Preferences> by preferencesDataStore(name = "oavi_settings")

/** How many trackers and ads were stopped, in total and during the current week. */
data class BlockedStats(val total: Long = 0, val thisWeek: Long = 0)

class SettingsStore(context: Context) {
    private val store = context.applicationContext.oaviPreferences

    val settings: Flow<Settings> = store.data.map(::read)

    val blockedStats: Flow<BlockedStats> = store.data.map { prefs ->
        val week = currentWeek()
        BlockedStats(
            total = prefs[Keys.blockedTotal] ?: 0,
            thisWeek = if (prefs[Keys.blockedWeekIndex] == week) prefs[Keys.blockedWeek] ?: 0 else 0,
        )
    }

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { prefs ->
            val next = transform(read(prefs))
            prefs[Keys.onboarded] = next.onboarded
            prefs[Keys.searchEngine] = next.searchEngine.id
            prefs[Keys.adBlock] = next.adBlock
            prefs[Keys.trackingProtection] = next.trackingProtection.name
            prefs[Keys.httpsOnly] = next.httpsOnly
            prefs[Keys.forceDarkPages] = next.forceDarkPages
            prefs[Keys.themeMode] = next.themeMode.name
            prefs[Keys.lockPrivateTabs] = next.lockPrivateTabs
            prefs[Keys.collapseIsland] = next.collapseIslandOnScroll
            prefs[Keys.performanceBoost] = next.performanceBoost
            prefs[Keys.swipeTipDone] = next.swipeTipDone
            prefs[Keys.textScale] = next.textScale
            prefs[Keys.zoomEverywhere] = next.zoomEverywhere
            prefs[Keys.readerFontScale] = next.readerFontScale
            prefs[Keys.readerTheme] = next.readerTheme.name
            prefs[Keys.layout] = next.layout.name
            prefs[Keys.leftHanded] = next.leftHanded
            prefs[Keys.accent] = next.accent.name
            prefs[Keys.secureDns] = next.secureDns.name
            prefs[Keys.dataSaver] = next.dataSaver
            prefs[Keys.rejectCookieBanners] = next.rejectCookieBanners
            prefs[Keys.cleanLinks] = next.cleanLinks
            prefs[Keys.darkPagesAtNight] = next.darkPagesAtNight
            prefs[Keys.archiveAfter] = next.archiveAfter.name
        }
    }

    suspend fun addBlocked(count: Int) {
        if (count <= 0) return
        store.edit { prefs ->
            val week = currentWeek()
            val sameWeek = prefs[Keys.blockedWeekIndex] == week
            prefs[Keys.blockedTotal] = (prefs[Keys.blockedTotal] ?: 0) + count
            prefs[Keys.blockedWeek] = (if (sameWeek) prefs[Keys.blockedWeek] ?: 0 else 0) + count
            prefs[Keys.blockedWeekIndex] = week
        }
    }

    private fun read(prefs: Preferences): Settings {
        val defaults = Settings()
        return Settings(
            onboarded = prefs[Keys.onboarded] ?: defaults.onboarded,
            searchEngine = SearchEngine.fromId(prefs[Keys.searchEngine]) ?: defaults.searchEngine,
            adBlock = prefs[Keys.adBlock] ?: defaults.adBlock,
            trackingProtection = enumOr(prefs[Keys.trackingProtection], defaults.trackingProtection),
            httpsOnly = prefs[Keys.httpsOnly] ?: defaults.httpsOnly,
            forceDarkPages = prefs[Keys.forceDarkPages] ?: defaults.forceDarkPages,
            themeMode = enumOr(prefs[Keys.themeMode], defaults.themeMode),
            lockPrivateTabs = prefs[Keys.lockPrivateTabs] ?: defaults.lockPrivateTabs,
            collapseIslandOnScroll = prefs[Keys.collapseIsland] ?: defaults.collapseIslandOnScroll,
            performanceBoost = prefs[Keys.performanceBoost] ?: defaults.performanceBoost,
            swipeTipDone = prefs[Keys.swipeTipDone] ?: defaults.swipeTipDone,
            textScale = prefs[Keys.textScale] ?: defaults.textScale,
            zoomEverywhere = prefs[Keys.zoomEverywhere] ?: defaults.zoomEverywhere,
            readerFontScale = prefs[Keys.readerFontScale] ?: defaults.readerFontScale,
            readerTheme = enumOr(prefs[Keys.readerTheme], defaults.readerTheme),
            layout = enumOr(prefs[Keys.layout], defaults.layout),
            leftHanded = prefs[Keys.leftHanded] ?: defaults.leftHanded,
            accent = enumOr(prefs[Keys.accent], defaults.accent),
            secureDns = enumOr(prefs[Keys.secureDns], defaults.secureDns),
            dataSaver = prefs[Keys.dataSaver] ?: defaults.dataSaver,
            rejectCookieBanners = prefs[Keys.rejectCookieBanners] ?: defaults.rejectCookieBanners,
            cleanLinks = prefs[Keys.cleanLinks] ?: defaults.cleanLinks,
            darkPagesAtNight = prefs[Keys.darkPagesAtNight] ?: defaults.darkPagesAtNight,
            archiveAfter = enumOr(prefs[Keys.archiveAfter], defaults.archiveAfter),
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback

    private fun currentWeek(): Long = System.currentTimeMillis() / WEEK_MS

    private object Keys {
        val onboarded = booleanPreferencesKey("onboarded")
        val searchEngine = stringPreferencesKey("search_engine")
        val adBlock = booleanPreferencesKey("ad_block")
        val trackingProtection = stringPreferencesKey("tracking_protection")
        val httpsOnly = booleanPreferencesKey("https_only")
        val forceDarkPages = booleanPreferencesKey("force_dark_pages")
        val themeMode = stringPreferencesKey("theme_mode")
        val lockPrivateTabs = booleanPreferencesKey("lock_private_tabs")
        val collapseIsland = booleanPreferencesKey("collapse_island")
        val performanceBoost = booleanPreferencesKey("performance_boost")
        val swipeTipDone = booleanPreferencesKey("swipe_tip_done")
        val textScale = floatPreferencesKey("text_scale")
        val zoomEverywhere = booleanPreferencesKey("zoom_everywhere")
        val readerFontScale = floatPreferencesKey("reader_font_scale")
        val readerTheme = stringPreferencesKey("reader_theme")
        val layout = stringPreferencesKey("layout")
        val leftHanded = booleanPreferencesKey("left_handed")
        val accent = stringPreferencesKey("accent")
        val secureDns = stringPreferencesKey("secure_dns")
        val dataSaver = booleanPreferencesKey("data_saver")
        val rejectCookieBanners = booleanPreferencesKey("reject_cookie_banners")
        val cleanLinks = booleanPreferencesKey("clean_links")
        val darkPagesAtNight = booleanPreferencesKey("dark_pages_at_night")
        val archiveAfter = stringPreferencesKey("archive_after")
        val blockedTotal = longPreferencesKey("blocked_total")
        val blockedWeek = longPreferencesKey("blocked_week")
        val blockedWeekIndex = longPreferencesKey("blocked_week_index")
    }

    private companion object {
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    }
}
