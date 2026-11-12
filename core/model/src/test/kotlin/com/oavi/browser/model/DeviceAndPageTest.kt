package com.oavi.browser.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAndPageTest {
    private val a55 = SocInfo(manufacturer = "Samsung", model = "s5e8845", hardware = "s5e8845", board = "s5e8845")

    @Test
    fun galaxyA55GetsTheExynos1480Profile() {
        val profile = PerformanceProfiles.select(a55, totalRamMb = 7_600)
        assertEquals(ProfileKind.EXYNOS_1480, profile.kind)
        assertEquals(4, profile.contentProcesses)
        assertEquals(8, profile.maxLiveSessions)
        assertTrue(profile.useLoadBoost)
        assertTrue(profile.preferHighRefreshRate)
        assertEquals(4, profile.enginePrefs()["dom.ipc.processCount"])
        assertEquals(true, profile.enginePrefs()["dom.ipc.processPrelaunch.enabled"])
    }

    @Test
    fun sixGigabyteA55IsSizedDown() {
        val profile = PerformanceProfiles.select(a55, totalRamMb = 5_600)
        assertEquals(ProfileKind.EXYNOS_1480, profile.kind)
        assertEquals(3, profile.contentProcesses)
        assertEquals(5, profile.maxLiveSessions)
    }

    @Test
    fun exynos1480IsRecognisedHoweverItIsSpelled() {
        assertTrue(PerformanceProfiles.isExynos1480(SocInfo("Samsung", "S5E8845")))
        assertTrue(PerformanceProfiles.isExynos1480(SocInfo("", "", hardware = "s5e8845")))
        assertTrue(PerformanceProfiles.isExynos1480(SocInfo("Samsung", "Exynos 1480")))
        assertFalse(PerformanceProfiles.isExynos1480(SocInfo("Samsung", "s5e8835")))
        assertFalse(PerformanceProfiles.isExynos1480(SocInfo("QTI", "SM7675")))
    }

    @Test
    fun otherExynosChipsGetLoadBoostButEngineDefaults() {
        val profile = PerformanceProfiles.select(SocInfo("Samsung", "s5e9945"), totalRamMb = 11_500)
        assertEquals(ProfileKind.EXYNOS, profile.kind)
        assertTrue(profile.useLoadBoost)
        assertNull(profile.contentProcesses)
        assertFalse(profile.enginePrefs().containsKey("dom.ipc.processCount"))
    }

    @Test
    fun otherPhonesGetTheStandardProfile() {
        val snapdragon = PerformanceProfiles.select(SocInfo("QTI", "SM8650"), totalRamMb = 11_500)
        assertEquals(ProfileKind.STANDARD, snapdragon.kind)
        assertFalse(snapdragon.useLoadBoost)
        assertEquals(8, snapdragon.maxLiveSessions)

        val tensor = PerformanceProfiles.select(SocInfo("Google", "Tensor G4"), totalRamMb = 7_500)
        assertEquals(ProfileKind.STANDARD, tensor.kind)
        assertEquals(6, tensor.maxLiveSessions)

        val small = PerformanceProfiles.select(SocInfo("Mediatek", "MT6789"), totalRamMb = 3_700)
        assertEquals(ProfileKind.LOW_MEMORY, small.kind)
        assertEquals(2, small.maxLiveSessions)
    }

    @Test
    fun downloadNames() {
        assertEquals("report.pdf", DownloadNames.guess("https://x.com/files/report.pdf?dl=1", null, null))
        assertEquals("özet raporu.pdf", DownloadNames.guess("https://x.com/a", "attachment; filename*=UTF-8''%C3%B6zet%20raporu.pdf", null))
        assertEquals("a b.zip", DownloadNames.guess("https://x.com/a", "attachment; filename=\"a b.zip\"", null))
        assertEquals("plain.txt", DownloadNames.guess("https://x.com/a", "attachment; filename=plain.txt", null))
        assertEquals("download.pdf", DownloadNames.guess("https://x.com/", null, "application/pdf; charset=binary"))
        assertEquals("image.png", DownloadNames.guess("https://x.com/image", null, "image/png"))
        assertEquals("passwd", DownloadNames.guess("https://x.com/a", "attachment; filename=\"../../etc/passwd\"", null))
        assertEquals("download", DownloadNames.guess("data:text/plain,hello", null, null))
        assertEquals("my file.jpg", DownloadNames.guess("https://x.com/my%20file.jpg", null, null))
        assertTrue(DownloadNames.guess("https://x.com/" + "a".repeat(300) + ".tar", null, null).length <= 120)
    }

    @Test
    fun cssColours() {
        assertEquals(0xFFFF0000.toInt(), CssColor.parse("#f00"))
        assertEquals(0xFF12B5A6.toInt(), CssColor.parse(" #12B5A6 "))
        assertEquals(0x8012B5A6.toInt(), CssColor.parse("#12b5a680"))
        assertEquals(0xFF0A141E.toInt(), CssColor.parse("rgb(10, 20, 30)"))
        assertEquals(0xFF0A141E.toInt(), CssColor.parse("rgba(10,20,30,0.5)"))
        assertNull(CssColor.parse("rebeccapurple"))
        assertNull(CssColor.parse("#12"))
        assertNull(CssColor.parse("rgb(300,0,0)"))
        assertNull(CssColor.parse(null))
    }

    @Test
    fun onlyColourfulSiteColoursTintTheInterface() {
        assertTrue(CssColor.usableAsAccent(0xFF1DA1F2.toInt()))
        assertTrue(CssColor.usableAsAccent(0xFFE60023.toInt()))
        assertFalse(CssColor.usableAsAccent(0xFFFFFFFF.toInt()))
        assertFalse(CssColor.usableAsAccent(0xFF000000.toInt()))
        assertFalse(CssColor.usableAsAccent(0xFF808080.toInt()))
        assertFalse(CssColor.usableAsAccent(0xFFFFF9C4.toInt()))
    }

    @Test
    fun errorPageEscapesAndRoundTrips() {
        val html = ErrorPages.html(
            ErrorPageText("No <connection>", "Check \"Wi-Fi\" & try again", "Try again"),
            "https://www.example.com/?a=1&b=\"2\"",
        )
        assertTrue(html.contains("No &lt;connection&gt;"))
        assertTrue(html.contains("Check &quot;Wi-Fi&quot; &amp; try again"))
        assertTrue(html.contains("href=\"https://www.example.com/?a=1&amp;b=&quot;2&quot;\""))
        assertTrue(html.contains(">example.com<"))
        assertFalse(html.contains("<script"))
        val uri = ErrorPages.dataUri(html)
        assertTrue(uri.startsWith("data:text/html;charset=utf-8;base64,"))
        val decoded = String(java.util.Base64.getDecoder().decode(uri.substringAfter("base64,")), Charsets.UTF_8)
        assertEquals(html, decoded)
    }

    @Test
    fun dayParts() {
        assertEquals(DayPart.MORNING, DayPart.of(8))
        assertEquals(DayPart.AFTERNOON, DayPart.of(12))
        assertEquals(DayPart.EVENING, DayPart.of(21))
        assertEquals(DayPart.NIGHT, DayPart.of(2))
        assertEquals(DayPart.NIGHT, DayPart.of(23))
    }
}
