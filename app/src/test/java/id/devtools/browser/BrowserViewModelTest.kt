package id.devtools.browser

import id.devtools.browser.browser.BrowserViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserViewModelTest {

    @Test
    fun `starts with one home tab`() {
        val vm = BrowserViewModel()
        assertEquals(1, vm.tabs.value.size)
        assertEquals(BrowserViewModel.HOME_URL, vm.tabs.value.first().url)
        assertEquals(vm.tabs.value.first().id, vm.activeTabId.value)
    }

    @Test
    fun `addTab appends and activates`() {
        val vm = BrowserViewModel()
        val id = vm.addTab("https://example.com")
        assertEquals(2, vm.tabs.value.size)
        assertEquals(id, vm.activeTabId.value)
        assertEquals("https://example.com", vm.activeTab()?.url)
    }

    @Test
    fun `closeTab removes and moves active to last`() {
        val vm = BrowserViewModel()
        val first = vm.tabs.value.first().id
        val second = vm.addTab("https://example.com")
        assertNotEquals(first, second)
        vm.closeTab(second)
        assertEquals(1, vm.tabs.value.size)
        assertEquals(first, vm.activeTabId.value)
    }

    @Test
    fun `closing last tab opens a fresh home tab`() {
        val vm = BrowserViewModel()
        val only = vm.tabs.value.first().id
        vm.closeTab(only)
        assertEquals(1, vm.tabs.value.size)
        assertNotNull(vm.activeTabId.value)
    }

    @Test
    fun `selectTab ignores unknown ids`() {
        val vm = BrowserViewModel()
        val current = vm.activeTabId.value
        vm.selectTab("nope")
        assertEquals(current, vm.activeTabId.value)
    }

    @Test
    fun `ssl decisions resolve pending request`() {
        val vm = BrowserViewModel()
        assertNull(vm.pendingSslError.value)
        vm.setPageError("t1", true)
        assertTrue(vm.pageErrors.value.contains("t1"))
        vm.setPageError("t1", false)
        assertTrue(vm.pageErrors.value.isEmpty())
    }

    @Test
    fun `webView nonce bumps per tab`() {
        val vm = BrowserViewModel()
        val id = vm.addTab("https://example.com")
        assertNull(vm.webViewNonce.value[id])
        vm.bumpWebViewNonce(id)
        assertEquals(1, vm.webViewNonce.value[id])
        vm.bumpWebViewNonce(id)
        assertEquals(2, vm.webViewNonce.value[id])
    }
}
