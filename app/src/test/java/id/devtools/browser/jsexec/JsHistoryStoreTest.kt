package id.devtools.browser.jsexec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED: JsHistoryStore does not exist yet. Execution history is a capped
 * ring buffer like the console log.
 */
class JsHistoryStoreTest {

    private fun result(v: String = "1") = JsExecResult(ok = true, value = v, error = null)

    @Test
    fun `history caps at 100 dropping oldest`() {
        val store = JsHistoryStore()
        repeat(JsHistoryStore.MAX_HISTORY + 20) { i ->
            store.add("code-$i", result("$i"))
        }
        val items = store.items.value
        assertEquals(JsHistoryStore.MAX_HISTORY, items.size)
        assertEquals("code-20", items.first().code)
        assertEquals("code-119", items.last().code)
    }

    @Test
    fun `items keep code result and timestamp order`() {
        val store = JsHistoryStore()
        store.add("1+1", result("2"))
        val item = store.items.value.single()
        assertEquals("1+1", item.code)
        assertEquals("2", item.result.value)
        assertTrue(item.timestampMs > 0)
    }

    @Test
    fun `clear empties history`() {
        val store = JsHistoryStore()
        store.add("1", result())
        store.clear()
        assertTrue(store.items.value.isEmpty())
    }
}
