package com.oavi.browser.model

/** What the text typed into the address field turned out to be. */
sealed interface ResolvedInput {
    val url: String

    data class Navigate(override val url: String) : ResolvedInput
    data class Search(override val url: String, val query: String) : ResolvedInput
}

/**
 * Decides whether typed text is an address or a search, the way people expect:
 * anything that looks like a host goes there, everything else is searched.
 */
object UrlInput {
    const val BLANK = "about:blank"

    private val schemePattern = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):(.*)$", RegexOption.DOT_MATCHES_ALL)
    private val labelPattern = Regex("^[\\p{L}\\p{N}]([\\p{L}\\p{N}-]{0,61}[\\p{L}\\p{N}])?$")
    private val tldPattern = Regex("^(\\p{L}{2,63}|xn--[a-z0-9-]{2,59})$", RegexOption.IGNORE_CASE)
    private val ipv4Pattern = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
    private val portPattern = Regex("^\\d{1,5}$")

    /** Schemes the engine itself can load. Everything else is handed to other apps. */
    private val engineSchemes = setOf(
        "http", "https", "about", "data", "blob", "file", "view-source", "resource", "moz-extension",
    )

    fun resolve(raw: String, engine: SearchEngine): ResolvedInput {
        val text = raw.trim()
        if (text.isEmpty()) return ResolvedInput.Navigate(BLANK)

        val scheme = schemeOf(text)
        if (scheme != null) {
            return if (scheme in engineSchemes || !text.any { it.isWhitespace() }) {
                ResolvedInput.Navigate(text)
            } else {
                search(text, engine)
            }
        }
        if (text.any { it.isWhitespace() }) return search(text, engine)

        val hostEnd = text.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val authority = if (hostEnd >= 0) text.substring(0, hostEnd) else text
        val host = hostOfAuthority(authority) ?: return search(text, engine)
        return when {
            isLocalHost(host) -> ResolvedInput.Navigate("http://$text")
            isDomain(host) -> ResolvedInput.Navigate("https://$text")
            else -> search(text, engine)
        }
    }

    /**
     * The scheme of [text], or null when it has none. `localhost:8080` and `example.com:443`
     * are host:port pairs, not schemes.
     */
    fun schemeOf(text: String): String? {
        val match = schemePattern.find(text.trim()) ?: return null
        val scheme = match.groupValues[1].lowercase()
        val rest = match.groupValues[2]
        val portCandidate = rest.takeWhile { it != '/' && it != '?' && it != '#' }
        if (portCandidate.isNotEmpty() && portPattern.matches(portCandidate)) return null
        if (scheme in engineSchemes) return scheme
        // Unknown word followed by a colon: only a scheme if it reads like one ("mailto:x", "tel:+90").
        return if (rest.isNotEmpty() && !scheme.contains('.')) scheme else null
    }

    fun isEngineScheme(url: String): Boolean {
        val scheme = schemeOf(url) ?: return false
        return scheme in engineSchemes
    }

    /** True for addresses that should leave the browser (mailto:, tel:, intent:, market: …). */
    fun isExternal(url: String): Boolean {
        val scheme = schemeOf(url) ?: return false
        return scheme !in engineSchemes && scheme != "javascript"
    }

    /** Host without `www.`, for showing in the island. Empty when the address has no host. */
    fun displayHost(url: String): String {
        val host = hostOf(url) ?: return ""
        return host.removePrefix("www.")
    }

    fun hostOf(url: String): String? {
        val text = url.trim()
        val schemeEnd = text.indexOf("://")
        if (schemeEnd < 0) return null
        val afterScheme = text.substring(schemeEnd + 3)
        val hostEnd = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val authority = if (hostEnd >= 0) afterScheme.substring(0, hostEnd) else afterScheme
        return hostOfAuthority(authority)?.lowercase()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Approximate registrable domain (`news.example.co.uk` → `example.co.uk`). Good enough for
     * grouping tabs; not a substitute for the public suffix list.
     */
    fun baseDomain(host: String): String {
        val labels = host.lowercase().removePrefix("www.").split('.').filter { it.isNotEmpty() }
        if (labels.size <= 2) return labels.joinToString(".")
        val tld = labels.last()
        val second = labels[labels.size - 2]
        val compound = tld.length == 2 && second in setOf("com", "co", "net", "org", "gov", "edu", "ac")
        return labels.takeLast(if (compound) 3 else 2).joinToString(".")
    }

    fun isSecure(url: String): Boolean = url.trim().startsWith("https://", ignoreCase = true)

    fun isBlank(url: String): Boolean = url.isEmpty() || url == BLANK

    private fun search(text: String, engine: SearchEngine): ResolvedInput =
        ResolvedInput.Search(engine.searchUrl(text), text)

    private fun hostOfAuthority(authority: String): String? {
        if (authority.isEmpty()) return null
        val withoutUser = authority.substringAfterLast('@')
        if (withoutUser.startsWith("[")) {
            val end = withoutUser.indexOf(']')
            return if (end > 1) withoutUser.substring(0, end + 1) else null
        }
        val colon = withoutUser.lastIndexOf(':')
        if (colon < 0) return withoutUser
        val port = withoutUser.substring(colon + 1)
        return if (port.isEmpty() || portPattern.matches(port)) withoutUser.substring(0, colon) else null
    }

    private fun isLocalHost(host: String): Boolean {
        val lower = host.lowercase()
        if (lower == "localhost" || lower.endsWith(".localhost") || lower.endsWith(".local")) return true
        if (lower.startsWith("[") && lower.endsWith("]")) return true
        val match = ipv4Pattern.matchEntire(lower) ?: return false
        return match.groupValues.drop(1).all { it.toInt() <= 255 }
    }

    private fun isDomain(host: String): Boolean {
        val labels = host.split('.')
        if (labels.size < 2 || host.length > 253) return false
        if (!labels.all { labelPattern.matches(it) }) return false
        return tldPattern.matches(labels.last())
    }
}
