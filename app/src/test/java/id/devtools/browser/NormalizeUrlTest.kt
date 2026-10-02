package id.devtools.browser

import id.devtools.browser.ui.normalizeUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NormalizeUrlTest {

    @Test
    fun `blank input returns null`() {
        assertNull(normalizeUrl(""))
        assertNull(normalizeUrl("   "))
    }

    @Test
    fun `keeps existing scheme`() {
        assertEquals("https://example.com", normalizeUrl("https://example.com"))
        assertEquals("http://example.com", normalizeUrl("http://example.com"))
    }

    @Test
    fun `adds https to bare domains`() {
        assertEquals("https://example.com", normalizeUrl("example.com"))
        assertEquals("https://example.com/a b", normalizeUrl("example.com/a b"))
    }

    @Test
    fun `plain words become search queries`() {
        assertEquals(
            "https://www.google.com/search?q=bug%20bounty",
            normalizeUrl("bug bounty"),
        )
    }
}
