package com.oavi.browser.model

data class HistoryEntry(
    val id: Long,
    val url: String,
    val title: String,
    val visitedAt: Long,
    val visitCount: Int,
)

data class Bookmark(
    val id: Long,
    val url: String,
    val title: String,
    val createdAt: Long,
)

/** A row offered while typing in the address field. */
data class Suggestion(
    val kind: Kind,
    val title: String,
    val url: String,
    val tabId: String? = null,
) {
    enum class Kind { OPEN_TAB, BOOKMARK, HISTORY }
}

object Suggestions {
    /**
     * Ranks local matches for [query]. Nothing typed here leaves the device: suggestions come
     * only from open tabs, bookmarks and history.
     */
    fun rank(
        query: String,
        openTabs: List<Tab>,
        bookmarks: List<Bookmark>,
        history: List<HistoryEntry>,
        limit: Int = 6,
    ): List<Suggestion> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()

        val scored = ArrayList<Pair<Int, Suggestion>>()
        for (tab in openTabs) {
            if (tab.isPrivate || tab.archived || tab.isBlank) continue
            val score = score(needle, tab.url, tab.title) ?: continue
            scored += (score + 30) to Suggestion(Suggestion.Kind.OPEN_TAB, tab.label, tab.url, tab.id)
        }
        for (bookmark in bookmarks) {
            val score = score(needle, bookmark.url, bookmark.title) ?: continue
            scored += (score + 20) to Suggestion(Suggestion.Kind.BOOKMARK, bookmark.title, bookmark.url)
        }
        for (entry in history) {
            val score = score(needle, entry.url, entry.title) ?: continue
            val frequency = minOf(entry.visitCount, 10)
            scored += (score + frequency) to Suggestion(Suggestion.Kind.HISTORY, entry.title, entry.url)
        }
        return scored
            .sortedByDescending { it.first }
            .map { it.second }
            .distinctBy { it.url.trimEnd('/') }
            .take(limit)
    }

    private fun score(needle: String, url: String, title: String): Int? {
        val host = UrlInput.displayHost(url)
        return when {
            host.startsWith(needle) -> 100
            host.contains(needle) -> 60
            title.lowercase().contains(needle) -> 40
            url.lowercase().contains(needle) -> 20
            else -> null
        }
    }
}
