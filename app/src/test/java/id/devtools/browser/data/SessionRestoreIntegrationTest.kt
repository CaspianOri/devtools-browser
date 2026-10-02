package id.devtools.browser.data

import androidx.test.core.app.ApplicationProvider
import id.devtools.browser.browser.BrowserViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Kill-and-restart simulation: exactly what MainActivity does on cold start —
 * persist a session, then read it back into a fresh BrowserViewModel.
 * Guards the M4 "exit the app, reopen, tabs are back" contract end to end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionRestoreIntegrationTest {

    @Test
    fun `killed process restores tabs url order and active index on next launch`() = runTest {
        val store = SessionStore(ApplicationProvider.getApplicationContext())
        store.save(
            SessionSnapshot(
                tabUrls = listOf(
                    "https://app.posone.id/register",
                    "https://example.com/search?q=hello",
                ),
                activeTabIndex = 0,
                userAgentName = "DESKTOP",
                darkMode = true,
                sslWhitelist = setOf("lab.local"),
            ),
        )

        // Simulate process death: a brand-new ViewModel reads the persisted snapshot.
        val freshVm = BrowserViewModel()
        val loaded = store.snapshot.first()
        freshVm.restoreSession(loaded)

        val tabs = freshVm.tabs.value
        assertEquals(2, tabs.size)
        assertEquals("https://app.posone.id/register", tabs[0].url)
        assertEquals("https://example.com/search?q=hello", tabs[1].url)
        assertEquals(tabs[0].id, freshVm.activeTabId.value)
        // Settings survive the round trip as well.
        assertEquals("DESKTOP", loaded.userAgentName)
        assertEquals(true, loaded.darkMode)
        assertEquals(setOf("lab.local"), loaded.sslWhitelist)
    }

    @Test
    fun `first launch with no stored session starts clean`() = runTest {
        val store = SessionStore(ApplicationProvider.getApplicationContext())
        val loaded = store.snapshot.first()

        val freshVm = BrowserViewModel()
        freshVm.restoreSession(loaded)

        // Either a pristine store (empty) or leftovers from another test —
        // restore must never crash and must always leave a usable tab.
        val tabs = freshVm.tabs.value
        assertEquals(true, tabs.isNotEmpty())
        assertEquals(tabs[loaded.activeTabIndex.coerceIn(tabs.indices)].id, freshVm.activeTabId.value)
    }
}
