package com.oavi.browser.model

/**
 * Removes the parameters that only exist to follow people from link to link (utm_source,
 * fbclid, gclid …), so a copied or shared link says where it goes and nothing about who
 * shared it or where they found it.
 */
object LinkCleaner {
    /** Parameters removed wherever they appear. */
    val trackingParameters: Set<String> = setOf(
        "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "msclkid", "twclid", "ttclid",
        "li_fat_id", "igshid", "igsh", "yclid", "ysclid", "mc_cid", "mc_eid", "_hsenc", "_hsmi",
        "mkt_tok", "oly_anon_id", "oly_enc_id", "vero_id", "vero_conv", "__s", "rb_clickid",
        "s_cid", "srsltid", "_openstat", "wickedid", "epik", "ndclid", "sc_cid", "irclickid",
    )

    /** Prefixes of parameter families removed wherever they appear (utm_source, utm_medium …). */
    private val trackingPrefixes = listOf("utm_", "pk_", "mtm_", "hsa_", "stm_")

    /** Parameters that only track on particular sites, and mean something elsewhere. */
    private val perSite: Map<String, Set<String>> = mapOf(
        "youtube.com" to setOf("si", "pp", "feature"),
        "youtu.be" to setOf("si", "feature"),
        "amazon.com" to setOf("ref", "ref_", "pd_rd_r", "pd_rd_w", "pd_rd_wg", "pf_rd_p", "pf_rd_r", "content-id"),
        "twitter.com" to setOf("s", "t", "ref_src"),
        "x.com" to setOf("s", "t", "ref_src"),
        "instagram.com" to setOf("igsh", "igshid", "img_index"),
        "tiktok.com" to setOf("_r", "_t", "is_from_webapp", "sender_device"),
        "spotify.com" to setOf("si", "context"),
    )

    /** [url] without its tracking parameters; any other address comes back unchanged. */
    fun clean(url: String): String {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            return url
        }
        val fragmentStart = trimmed.indexOf('#')
        val beforeFragment = if (fragmentStart >= 0) trimmed.substring(0, fragmentStart) else trimmed
        val fragment = if (fragmentStart >= 0) trimmed.substring(fragmentStart) else ""
        val queryStart = beforeFragment.indexOf('?')
        if (queryStart < 0) return url
        val base = beforeFragment.substring(0, queryStart)
        val query = beforeFragment.substring(queryStart + 1)

        val host = UrlInput.hostOf(trimmed)?.let(UrlInput::baseDomain).orEmpty()
        val siteParameters = perSite[host].orEmpty()
        val parts = query.split('&').filter { it.isNotEmpty() }
        val kept = parts.filter { part ->
            val name = part.substringBefore('=').lowercase()
            name !in trackingParameters && name !in siteParameters && trackingPrefixes.none { name.startsWith(it) }
        }
        if (kept.size == parts.size) return url
        val rebuilt = if (kept.isEmpty()) base else base + "?" + kept.joinToString("&")
        return rebuilt + fragment
    }
}
