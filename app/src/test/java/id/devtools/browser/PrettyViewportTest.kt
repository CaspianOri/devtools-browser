package id.devtools.browser

import id.devtools.browser.ui.panels.prettyViewport
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * prettyViewport parses the JSON-quoted evaluateJavascript result, which
 * needs org.json — provided by Robolectric's android-all jar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrettyViewportTest {

    @Test
    fun `parses viewport json`() {
        // evaluateJavascript wraps the JSON payload in quotes with escaping.
        val raw = "\"{\\\"w\\\":412,\\\"h\\\":892,\\\"dpr\\\":2.625,\\\"ua\\\":\\\"x\\\"}\""
        assertEquals("412×892 @2.625x", prettyViewport(raw))
    }

    @Test
    fun `null and blank yield dash`() {
        assertEquals("-", prettyViewport(null))
        assertEquals("-", prettyViewport(""))
        assertEquals("-", prettyViewport("null"))
    }

    @Test
    fun `garbage passes through`() {
        assertEquals("garbage", prettyViewport("garbage"))
    }
}
