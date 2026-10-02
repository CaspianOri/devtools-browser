package id.devtools.browser

import id.devtools.browser.console.ConsoleEntry
import id.devtools.browser.console.ConsoleLevel
import id.devtools.browser.devtools.DevToolsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevToolsViewModelTest {

    private fun entry(tab: String = "t1", msg: String = "hello") = ConsoleEntry(
        id = 0,
        tabId = tab,
        level = ConsoleLevel.LOG,
        message = msg,
    )

    @Test
    fun `console ring buffer caps at 500 dropping oldest`() {
        val vm = DevToolsViewModel()
        repeat(DevToolsViewModel.MAX_CONSOLE_ENTRIES + 50) { i ->
            vm.addConsole(entry(msg = "msg-$i"))
        }
        val entries = vm.consoleEntries.value
        assertEquals(DevToolsViewModel.MAX_CONSOLE_ENTRIES, entries.size)
        assertEquals("msg-50", entries.first().message)
        assertEquals("msg-549", entries.last().message)
    }

    @Test
    fun `console ids are unique and increasing`() {
        val vm = DevToolsViewModel()
        repeat(5) { vm.addConsole(entry()) }
        val ids = vm.consoleEntries.value.map { it.id }
        assertEquals(5, ids.toSet().size)
        assertTrue(ids.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun `clearConsole filters by tab`() {
        val vm = DevToolsViewModel()
        vm.addConsole(entry(tab = "t1"))
        vm.addConsole(entry(tab = "t2"))
        vm.clearConsole("t1")
        val remaining = vm.consoleEntries.value
        assertEquals(1, remaining.size)
        assertEquals("t2", remaining.first().tabId)
    }

    @Test
    fun `clearConsole without tab clears all`() {
        val vm = DevToolsViewModel()
        vm.addConsole(entry(tab = "t1"))
        vm.addConsole(entry(tab = "t2"))
        vm.clearConsole()
        assertTrue(vm.consoleEntries.value.isEmpty())
    }
}
