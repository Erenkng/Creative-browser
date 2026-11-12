package com.oavi.browser.model

/** The part of a tab that is saved and survives the app being closed. */
data class Tab(
    val id: String,
    val url: String,
    val title: String = "",
    val isPrivate: Boolean = false,
    val groupId: String? = null,
    val parentId: String? = null,
    val createdAt: Long,
    val lastAccessAt: Long,
    val archived: Boolean = false,
) {
    val isBlank: Boolean get() = UrlInput.isBlank(url)

    /** Title to show: the page title, else the host, else empty (callers supply "New tab"). */
    val label: String
        get() = title.ifBlank { UrlInput.displayHost(url) }
}

enum class GroupColor { AQUA, CORAL, VIOLET, AMBER, LEAF, ROSE, SLATE }

data class TabGroup(
    val id: String,
    val name: String,
    val color: GroupColor,
    val createdAt: Long,
)

/** Pure rules about tabs, kept apart from the engine so they can be tested on any machine. */
object TabRules {
    const val ARCHIVE_AFTER_DAYS = 14
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Tabs left untouched for [days] are tucked into the archive instead of piling up. */
    fun archiveCandidates(
        tabs: List<Tab>,
        selectedId: String?,
        now: Long,
        days: Int = ARCHIVE_AFTER_DAYS,
    ): List<String> {
        val cutoff = now - days * DAY_MS
        return tabs
            .filter { !it.archived && !it.isPrivate && it.id != selectedId && it.lastAccessAt < cutoff }
            .map { it.id }
    }

    /**
     * Which tab to show after [closingId] goes away. People expect to land where they came
     * from (the parent), otherwise on the neighbour that slides into the gap.
     */
    fun nextSelection(tabs: List<Tab>, closingId: String, selectedId: String?): String? {
        if (selectedId != closingId) return selectedId?.takeIf { id -> tabs.any { it.id == id && it.id != closingId } }
        val closing = tabs.firstOrNull { it.id == closingId } ?: return null
        val peers = tabs.filter { !it.archived && it.isPrivate == closing.isPrivate }
        val index = peers.indexOfFirst { it.id == closingId }
        val parent = closing.parentId?.let { pid -> peers.firstOrNull { it.id == pid } }
        if (parent != null) return parent.id
        val right = peers.getOrNull(index + 1)
        val left = peers.getOrNull(index - 1)
        (right ?: left)?.let { return it.id }
        return tabs.lastOrNull { !it.archived && !it.isPrivate && it.id != closingId }?.id
    }

    /** Unfinished business worth surfacing on a new tab: recent tabs the user walked away from. */
    fun resumeCandidates(
        tabs: List<Tab>,
        selectedId: String?,
        now: Long,
        limit: Int = 3,
        maxAgeDays: Int = 7,
    ): List<Tab> {
        val cutoff = now - maxAgeDays * DAY_MS
        return tabs
            .asSequence()
            .filter { !it.isPrivate && !it.archived && it.id != selectedId }
            .filter { !it.isBlank && it.lastAccessAt >= cutoff }
            .sortedByDescending { it.lastAccessAt }
            .distinctBy { it.url }
            .take(limit)
            .toList()
    }

    /** A name for a new group, taken from the site most of its tabs share. */
    fun suggestGroupName(tabs: List<Tab>): String {
        val domain = tabs
            .mapNotNull { UrlInput.hostOf(it.url) }
            .map { UrlInput.baseDomain(it) }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: return ""
        val first = domain.substringBefore('.')
        return first.replaceFirstChar { it.titlecase() }
    }

    fun visible(tabs: List<Tab>, private: Boolean): List<Tab> =
        tabs.filter { !it.archived && it.isPrivate == private }
}
