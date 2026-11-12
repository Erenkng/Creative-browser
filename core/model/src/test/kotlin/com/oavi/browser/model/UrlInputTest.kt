package com.oavi.browser.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlInputTest {
    private val engine = SearchEngine.DUCKDUCKGO

    private fun navigate(text: String): String {
        val result = UrlInput.resolve(text, engine)
        assertTrue("expected navigation for '$text' but got $result", result is ResolvedInput.Navigate)
        return result.url
    }

    private fun search(text: String): ResolvedInput.Search {
        val result = UrlInput.resolve(text, engine)
        assertTrue("expected search for '$text' but got $result", result is ResolvedInput.Search)
        return result as ResolvedInput.Search
    }

    @Test
    fun bareDomainsGetHttps() {
        assertEquals("https://example.com", navigate("example.com"))
        assertEquals("https://example.com/a?b=c#d", navigate("  example.com/a?b=c#d "))
        assertEquals("https://sub.example.co.uk", navigate("sub.example.co.uk"))
        assertEquals("https://example.com:8443/x", navigate("example.com:8443/x"))
        assertEquals("https://türkiye.gov.tr", navigate("türkiye.gov.tr"))
    }

    @Test
    fun fullAddressesAreKept() {
        assertEquals("http://example.com", navigate("http://example.com"))
        assertEquals("https://example.com/q?a=b c", navigate("https://example.com/q?a=b c"))
        assertEquals("about:config", navigate("about:config"))
        assertEquals("view-source:https://example.com", navigate("view-source:https://example.com"))
    }

    @Test
    fun localAddressesUsePlainHttp() {
        assertEquals("http://localhost:3000", navigate("localhost:3000"))
        assertEquals("http://192.168.1.1", navigate("192.168.1.1"))
        assertEquals("http://192.168.1.1/admin", navigate("192.168.1.1/admin"))
        assertEquals("http://printer.local", navigate("printer.local"))
        assertEquals("http://[::1]:8080", navigate("[::1]:8080"))
    }

    @Test
    fun wordsAndSentencesAreSearched() {
        assertEquals("hava durumu", search("hava durumu").query)
        assertEquals("kotlin", search("kotlin").query)
        assertEquals("3.14", search("3.14").query)
        assertEquals("a.b", search("a.b").query)
        assertEquals("what is example.com", search("what is example.com").query)
        assertEquals("999.999.999.999", search("999.999.999.999").query)
        assertEquals("https://duckduckgo.com/?q=c%2B%2B+nedir", search("c++ nedir").url)
    }

    @Test
    fun externalSchemesAreRecognised() {
        assertTrue(UrlInput.isExternal("mailto:someone@example.com"))
        assertTrue(UrlInput.isExternal("tel:+905551112233"))
        assertTrue(UrlInput.isExternal("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(UrlInput.isExternal("https://example.com"))
        assertFalse(UrlInput.isExternal("example.com:443"))
        assertFalse(UrlInput.isExternal("about:blank"))
        assertFalse(UrlInput.isExternal("javascript:alert(1)"))
    }

    @Test
    fun emptyInputIsBlankPage() {
        assertEquals(UrlInput.BLANK, navigate("   "))
        assertTrue(UrlInput.isBlank(""))
        assertTrue(UrlInput.isBlank("about:blank"))
    }

    @Test
    fun hostHelpers() {
        assertEquals("example.com", UrlInput.displayHost("https://www.example.com/path"))
        assertEquals("example.com", UrlInput.displayHost("https://user:pw@www.example.com:8080/"))
        assertEquals("", UrlInput.displayHost("about:blank"))
        assertNull(UrlInput.hostOf("not a url"))
        assertEquals("example.co.uk", UrlInput.baseDomain("news.example.co.uk"))
        assertEquals("example.com", UrlInput.baseDomain("a.b.example.com"))
        assertEquals("hurriyet.com.tr", UrlInput.baseDomain("www.hurriyet.com.tr"))
        assertTrue(UrlInput.isSecure("HTTPS://example.com"))
        assertFalse(UrlInput.isSecure("http://example.com"))
    }

    @Test
    fun everySearchEngineEncodesQueries() {
        for (engine in SearchEngine.entries) {
            val url = engine.searchUrl(" a&b c ")
            assertTrue(url, url.startsWith("https://"))
            assertTrue(url, url.endsWith("a%26b+c"))
            assertEquals(engine, SearchEngine.fromId(engine.id))
        }
        assertNull(SearchEngine.fromId("nope"))
        assertNull(SearchEngine.fromId(null))
    }
}
