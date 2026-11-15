package com.oavi.browser.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturesTest {
    @Test
    fun searchShortcutsLeadingOrTrailing() {
        val leading = UrlInput.resolve("!w İstanbul", SearchEngine.GOOGLE, "tr")
        assertEquals("https://tr.wikipedia.org/w/index.php?search=%C4%B0stanbul", leading.url)
        assertTrue(leading is ResolvedInput.Search)

        val trailing = UrlInput.resolve("lo-fi beats !yt", SearchEngine.GOOGLE)
        assertEquals("https://www.youtube.com/results?search_query=lo-fi+beats", trailing.url)

        val engine = UrlInput.resolve("!ddg kotlin flow", SearchEngine.GOOGLE)
        assertEquals("https://duckduckgo.com/?q=kotlin+flow", engine.url)
    }

    @Test
    fun searchShortcutsNeedSomethingToSearch() {
        assertNull(SearchShortcuts.match("!w"))
        assertNull(SearchShortcuts.match("!unknown thing"))
        assertNull(SearchShortcuts.match("say !hello"))
        // An unknown shortcut is just words for the search engine.
        assertEquals("https://www.google.com/search?q=%21unknown+thing", UrlInput.resolve("!unknown thing", SearchEngine.GOOGLE).url)
        assertEquals("Wikipedia", SearchShortcuts.typing("!w")?.name)
        assertEquals("OpenStreetMap", SearchShortcuts.typing("!harita kadıköy")?.name)
        assertNull(SearchShortcuts.typing("w istanbul"))
    }

    @Test
    fun linkCleanerRemovesTrackingOnly() {
        assertEquals(
            "https://shop.example/item?id=7#reviews",
            LinkCleaner.clean("https://shop.example/item?id=7&utm_source=mail&utm_medium=x&fbclid=abc#reviews"),
        )
        assertEquals("https://news.example/story", LinkCleaner.clean("https://news.example/story?gclid=1&msclkid=2"))
        // Site-specific: "si" tracks on YouTube but is left alone elsewhere.
        assertEquals("https://youtu.be/abc", LinkCleaner.clean("https://youtu.be/abc?si=XyZ"))
        assertEquals("https://example.com/?si=1", LinkCleaner.clean("https://example.com/?si=1"))
        val untouched = "https://example.com/search?q=utm&page=2"
        assertEquals(untouched, LinkCleaner.clean(untouched))
        assertEquals("mailto:someone@example.com?subject=hi", LinkCleaner.clean("mailto:someone@example.com?subject=hi"))
    }

    @Test
    fun pdfNamesComeFromTitles() {
        assertEquals("How coffee reached Istanbul.pdf", DownloadNames.pdfName("How coffee reached Istanbul", "https://x.example/a"))
        assertEquals("A-B testing.pdf", DownloadNames.pdfName("A/B testing", "https://x.example/a"))
        assertEquals("x.example.pdf", DownloadNames.pdfName("   ", "https://www.x.example/a"))
        assertEquals("page.pdf", DownloadNames.pdfName("", "about:blank"))
    }

    @Test
    fun darkPagesAtNight() {
        val night = Settings(darkPagesAtNight = true)
        assertTrue(night.darkPagesAt(22))
        assertTrue(night.darkPagesAt(3))
        assertFalse(night.darkPagesAt(12))
        assertFalse(night.darkPagesAt(7))
        assertTrue(night.darkPagesAt(20))
        assertTrue(Settings(forceDarkPages = true).darkPagesAt(12))
        assertFalse(Settings().darkPagesAt(23))
    }

    @Test
    fun speechChunksEndAtSentences() {
        val sentence = "Coffee reached Istanbul in the sixteenth century. "
        val text = sentence.repeat(60) + "\n\nA second paragraph."
        val chunks = SpeechText.chunks(text, max = 400)
        assertTrue(chunks.all { it.length <= 400 })
        assertTrue(chunks.dropLast(1).all { it.endsWith(".") })
        assertEquals("A second paragraph.", chunks.last())
        assertEquals(text.replace(Regex("\\s+"), " ").trim(), chunks.joinToString(" "))
        assertEquals(listOf("x".repeat(10), "x".repeat(5)), SpeechText.chunks("x".repeat(15), max = 10))
        assertTrue(SpeechText.chunks("   ").isEmpty())
    }

    @Test
    fun archiveChoices() {
        assertEquals(1, ArchiveAfter.DAY.days)
        assertNull(ArchiveAfter.NEVER.days)
        assertEquals(TabRules.ARCHIVE_AFTER_DAYS, Settings().archiveAfter.days)
    }
}
