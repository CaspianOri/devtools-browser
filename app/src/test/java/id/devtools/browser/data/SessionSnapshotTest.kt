package id.devtools.browser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED: SessionSnapshot mapping does not exist yet. Tab order must survive
 * the round trip (stringSet would lose it), and empty prefs yield defaults.
 */
class SessionSnapshotTest {

    @Test
    fun `round trip preserves urls order settings and whitelist`() {
        val original = SessionSnapshot(
            tabUrls = listOf(
                "https://example.com/",
                "https://example.com/search?q=hello+world&lang=en",
                "https://sub.example.com:8443/path#frag",
            ),
            activeTabIndex = 2,
            userAgentName = "DESKTOP",
            darkMode = true,
            sslWhitelist = setOf("lab.local", "192.168.1.10"),
        )
        assertEquals(original, original.toPreferences().toSessionSnapshot())
    }

    @Test
    fun `empty preferences yield defaults`() {
        val snapshot = androidx.datastore.preferences.core.mutablePreferencesOf()
            .toSessionSnapshot()
        assertEquals(SessionSnapshot(), snapshot)
        assertTrue(snapshot.tabUrls.isEmpty())
    }

    @Test
    fun `blank urls are dropped`() {
        val snapshot = SessionSnapshot(tabUrls = listOf("", "https://a.com", "  "))
            .toPreferences().toSessionSnapshot()
        assertEquals(listOf("https://a.com"), snapshot.tabUrls)
    }

    @Test
    fun `unknown user agent name falls back to ANDROID`() {
        assertEquals(
            id.devtools.browser.browser.UserAgentProfile.ANDROID,
            userAgentFromName("NOPE"),
        )
        assertEquals(
            id.devtools.browser.browser.UserAgentProfile.DESKTOP,
            userAgentFromName("DESKTOP"),
        )
    }
}
