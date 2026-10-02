package id.devtools.browser.media

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RED: screenshotFileName does not exist yet. Filenames must be deterministic
 * (UTC) so exports sort chronologically and tests are stable.
 */
class ScreenshotFileNameTest {

    @Test
    fun `epoch zero formats in UTC`() {
        assertEquals("devtools-19700101-000000.png", screenshotFileName(0L))
    }

    @Test
    fun `known timestamp formats correctly`() {
        // 2026-10-02 12:00:00 UTC
        assertEquals("devtools-20261002-120000.png", screenshotFileName(1_790_942_400_000L))
    }
}
