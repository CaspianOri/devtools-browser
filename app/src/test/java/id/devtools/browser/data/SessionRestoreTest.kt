package id.devtools.browser.data

import id.devtools.browser.browser.BrowserViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * RED: BrowserViewModel.restoreSession does not exist yet. Restore replaces
 * the tab list; the active tab loads eagerly while the rest stay placeholders
 * (their WebViews are only composed when selected).
 */
class SessionRestoreTest {

    @Test
    fun `restore replaces tabs and selects saved index`() {
        val vm = BrowserViewModel()
        vm.restoreSession(
            SessionSnapshot(
                tabUrls = listOf("https://a.com", "https://b.com", "https://c.com"),
                activeTabIndex = 1,
            ),
        )
        val tabs = vm.tabs.value
        assertEquals(3, tabs.size)
        assertEquals(listOf("https://a.com", "https://b.com", "https://c.com"), tabs.map { it.url })
        assertEquals(tabs[1].id, vm.activeTabId.value)
        assertFalse(tabs.any { it.isLoading })
    }

    @Test
    fun `empty snapshot restores single home tab`() {
        val vm = BrowserViewModel()
        vm.addTab("https://a.com")
        vm.restoreSession(SessionSnapshot())
        val tabs = vm.tabs.value
        assertEquals(1, tabs.size)
        assertEquals(BrowserViewModel.HOME_URL, tabs.single().url)
        assertEquals(tabs.single().id, vm.activeTabId.value)
    }

    @Test
    fun `out of range active index is coerced`() {
        val vm = BrowserViewModel()
        vm.restoreSession(SessionSnapshot(tabUrls = listOf("https://a.com"), activeTabIndex = 99))
        assertEquals(vm.tabs.value.single().id, vm.activeTabId.value)

        vm.restoreSession(SessionSnapshot(tabUrls = listOf("https://a.com", "https://b.com"), activeTabIndex = -5))
        assertEquals(vm.tabs.value.first().id, vm.activeTabId.value)
    }

    @Test
    fun `ssl whitelist restores and observes`() {
        val vm = BrowserViewModel()
        vm.restoreSslWhitelist(setOf("lab.local"))
        assertEquals(setOf("lab.local"), vm.sslWhitelistFlow.value)
        assertEquals(true, vm.isHostWhitelisted("lab.local"))
    }
}
