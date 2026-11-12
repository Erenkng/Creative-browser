package com.oavi.browser.model

import java.net.URLEncoder

/** Search engines offered on first launch. The user picks one; nothing is preselected. */
enum class SearchEngine(
    val id: String,
    val displayName: String,
    private val template: String,
) {
    GOOGLE("google", "Google", "https://www.google.com/search?q=%s"),
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    BING("bing", "Bing", "https://www.bing.com/search?q=%s"),
    YANDEX("yandex", "Yandex", "https://yandex.com/search/?text=%s"),
    BRAVE("brave", "Brave Search", "https://search.brave.com/search?q=%s");

    fun searchUrl(query: String): String =
        template.replace("%s", URLEncoder.encode(query.trim(), "UTF-8"))

    companion object {
        fun fromId(id: String?): SearchEngine? = entries.firstOrNull { it.id == id }
    }
}
