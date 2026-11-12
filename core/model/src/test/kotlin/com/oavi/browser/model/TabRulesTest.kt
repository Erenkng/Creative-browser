package com.oavi.browser.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabRulesTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private fun tab(
        id: String,
        url: String = "https://$id.example.com",
        private: Boolean = false,
        parent: String? = null,
        ageDays: Int = 0,
        archived: Boolean = false,
    ) = Tab(
        id = id, url = url, isPrivate = private, parentId = parent,
        createdAt = 0, lastAccessAt = now - ageDays * day, archived = archived,
    )

    @Test
    fun oldTabsAreArchivedButNotTheCurrentOrPrivateOnes() {
        val tabs = listOf(
            tab("fresh", ageDays = 1),
            tab("stale", ageDays = 15),
            tab("current", ageDays = 30),
            tab("secret", private = true, ageDays = 30),
            tab("already", ageDays = 30, archived = true),
            tab("edge", ageDays = 14),
        )
        assertEquals(listOf("stale"), TabRules.archiveCandidates(tabs, selectedId = "current", now = now))
    }

    @Test
    fun closingABackgroundTabKeepsTheSelection() {
        val tabs = listOf(tab("a"), tab("b"), tab("c"))
        assertEquals("a", TabRules.nextSelection(tabs, closingId = "c", selectedId = "a"))
    }

    @Test
    fun closingTheCurrentTabReturnsToItsParentThenNeighbours() {
        val tabs = listOf(tab("a"), tab("b"), tab("c", parent = "a"), tab("d"))
        assertEquals("a", TabRules.nextSelection(tabs, "c", "c"))
        assertEquals("c", TabRules.nextSelection(tabs, "b", "b"))
        assertEquals("c", TabRules.nextSelection(tabs, "d", "d"))
    }

    @Test
    fun closingTheLastPrivateTabFallsBackToNormalTabs() {
        val tabs = listOf(tab("a"), tab("b"), tab("p", private = true))
        assertEquals("b", TabRules.nextSelection(tabs, "p", "p"))
    }

    @Test
    fun closingTheOnlyTabLeavesNothingSelected() {
        assertNull(TabRules.nextSelection(listOf(tab("a")), "a", "a"))
    }

    @Test
    fun privateTabsStayAmongPrivateTabs() {
        val tabs = listOf(tab("a"), tab("p1", private = true), tab("b"), tab("p2", private = true))
        assertEquals("p2", TabRules.nextSelection(tabs, "p1", "p1"))
    }

    @Test
    fun resumeShowsRecentUnfinishedTabsOnly() {
        val tabs = listOf(
            tab("current"),
            tab("recent", ageDays = 1),
            tab("older", ageDays = 3),
            tab("ancient", ageDays = 9),
            tab("blank", url = "about:blank", ageDays = 1),
            tab("secret", private = true, ageDays = 1),
            tab("dup", url = "https://recent.example.com", ageDays = 2),
        )
        val result = TabRules.resumeCandidates(tabs, selectedId = "current", now = now)
        assertEquals(listOf("recent", "older"), result.map { it.id })
    }

    @Test
    fun groupNameComesFromTheMostCommonSite() {
        val tabs = listOf(
            tab("1", url = "https://www.wikipedia.org/a"),
            tab("2", url = "https://en.wikipedia.org/b"),
            tab("3", url = "https://example.com"),
        )
        assertEquals("Wikipedia", TabRules.suggestGroupName(tabs))
        assertEquals("", TabRules.suggestGroupName(listOf(tab("x", url = "about:blank"))))
    }

    @Test
    fun suggestionsPreferOpenTabsAndHostPrefixes() {
        val tabs = listOf(tab("t", url = "https://github.com/oavi"), tab("p", url = "https://github.com/secret", private = true))
        val bookmarks = listOf(Bookmark(1, "https://gitlab.com", "GitLab", 0))
        val history = listOf(
            HistoryEntry(1, "https://example.com/git-guide", "Git guide", 0, 3),
            HistoryEntry(2, "https://github.com/oavi/", "Oavi", 0, 9),
        )
        val result = Suggestions.rank("git", tabs, bookmarks, history)
        assertEquals(Suggestion.Kind.OPEN_TAB, result.first().kind)
        assertEquals("t", result.first().tabId)
        assertEquals(listOf("https://github.com/oavi", "https://gitlab.com", "https://example.com/git-guide"), result.map { it.url })
        assertEquals(emptyList<Suggestion>(), Suggestions.rank("  ", tabs, bookmarks, history))
    }
}
