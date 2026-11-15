package com.oavi.browser.model

import java.net.URLEncoder

/**
 * Searching another site straight from the address field: "!w istanbul" searches Wikipedia,
 * "!yt lo-fi" YouTube. The shortcut may also come last ("istanbul !w"), the way it is often
 * added as an afterthought.
 */
object SearchShortcuts {
    class Shortcut(val keys: List<String>, val name: String, private val template: String) {
        /** The address that searches this site for [query], in [language] where the site has one. */
        fun url(query: String, language: String): String = template
            .replace("{lang}", language.ifBlank { "en" })
            .replace("{q}", URLEncoder.encode(query.trim(), "UTF-8"))
    }

    data class Match(val shortcut: Shortcut, val query: String) {
        fun url(language: String): String = shortcut.url(query, language)
    }

    val all: List<Shortcut> = listOf(
        Shortcut(listOf("w", "wiki"), "Wikipedia", "https://{lang}.wikipedia.org/w/index.php?search={q}"),
        Shortcut(listOf("yt", "youtube"), "YouTube", "https://www.youtube.com/results?search_query={q}"),
        Shortcut(listOf("m", "map", "maps", "harita"), "OpenStreetMap", "https://www.openstreetmap.org/search?query={q}"),
        Shortcut(listOf("tr", "translate", "çevir", "cevir"), "Google Translate", "https://translate.google.com/?sl=auto&tl={lang}&text={q}&op=translate"),
        Shortcut(listOf("gh", "github"), "GitHub", "https://github.com/search?q={q}"),
        Shortcut(listOf("so"), "Stack Overflow", "https://stackoverflow.com/search?q={q}"),
        Shortcut(listOf("r", "reddit"), "Reddit", "https://www.reddit.com/search/?q={q}"),
        Shortcut(listOf("g", "google"), "Google", "https://www.google.com/search?q={q}"),
        Shortcut(listOf("d", "ddg"), "DuckDuckGo", "https://duckduckgo.com/?q={q}"),
        Shortcut(listOf("b", "bing"), "Bing", "https://www.bing.com/search?q={q}"),
        Shortcut(listOf("y", "yandex"), "Yandex", "https://yandex.com/search/?text={q}"),
        Shortcut(listOf("br", "brave"), "Brave Search", "https://search.brave.com/search?q={q}"),
    )

    private val byKey: Map<String, Shortcut> = all.flatMap { s -> s.keys.map { it to s } }.toMap()

    /** The shortcut in [input] and what to search for, or null when there is none (or nothing to search). */
    fun match(input: String): Match? {
        val words = input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size < 2) return null
        val first = shortcutOf(words.first())
        if (first != null) return Match(first, words.drop(1).joinToString(" "))
        val last = shortcutOf(words.last())
        if (last != null) return Match(last, words.dropLast(1).joinToString(" "))
        return null
    }

    /**
     * The shortcut being typed, for a hint under the field: "!w" or "!w ist" both name
     * Wikipedia even before there is anything to search for.
     */
    fun typing(input: String): Shortcut? {
        val first = input.trimStart().substringBefore(' ')
        return shortcutOf(first)
    }

    private fun shortcutOf(word: String): Shortcut? {
        if (word.length < 2 || word[0] != '!') return null
        return byKey[word.substring(1).lowercase()]
    }
}
