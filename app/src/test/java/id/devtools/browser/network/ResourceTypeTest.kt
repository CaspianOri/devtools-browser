package id.devtools.browser.network

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RED: guessResourceType does not exist yet. Native lane has no content-type
 * at intercept time, so the type is inferred from the URL.
 */
class ResourceTypeTest {

    @Test
    fun `document for main frame and html urls`() {
        assertEquals(NetworkResourceType.DOCUMENT, guessResourceType("https://a.com/", true))
        assertEquals(NetworkResourceType.DOCUMENT, guessResourceType("https://a.com/index.html", true))
    }

    @Test
    fun `known extensions map correctly`() {
        assertEquals(NetworkResourceType.SCRIPT, guessResourceType("https://a.com/app.js?v=1", false))
        assertEquals(NetworkResourceType.STYLESHEET, guessResourceType("https://a.com/a.css", false))
        assertEquals(NetworkResourceType.IMAGE, guessResourceType("https://a.com/i.png", false))
        assertEquals(NetworkResourceType.IMAGE, guessResourceType("https://a.com/i.webp", false))
        assertEquals(NetworkResourceType.MEDIA, guessResourceType("https://a.com/v.mp4", false))
        assertEquals(NetworkResourceType.FONT, guessResourceType("https://a.com/f.woff2", false))
        assertEquals(NetworkResourceType.XHR, guessResourceType("https://a.com/api/data.json", false))
    }

    @Test
    fun `unknown falls back to other`() {
        assertEquals(NetworkResourceType.OTHER, guessResourceType("https://a.com/blob", false))
        assertEquals(NetworkResourceType.OTHER, guessResourceType("not a url", false))
    }

    @Test
    fun `query strings do not confuse extension detection`() {
        assertEquals(NetworkResourceType.SCRIPT, guessResourceType("https://a.com/x.mjs?h=1", false))
    }
}
