package id.devtools.browser.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED: NetworkCapture does not exist yet. These tests define M2's capture
 * contract: 500-entry ring buffer, native+JS dedup, 256 KB text-only bodies.
 */
class NetworkCaptureTest {

    private fun native(
        capture: NetworkCapture,
        tab: String = "t1",
        url: String = "https://example.com/api",
        method: String = "GET",
    ) = capture.recordNative(
        tabId = tab,
        url = url,
        method = method,
        requestHeaders = mapOf("Accept" to "*/*"),
        isMainFrame = false,
    )

    @Test
    fun `ring buffer caps at 500 dropping oldest`() {
        val capture = NetworkCapture()
        repeat(NetworkCapture.MAX_ENTRIES + 50) { i ->
            native(capture, url = "https://example.com/r$i")
        }
        val entries = capture.entries.value
        assertEquals(NetworkCapture.MAX_ENTRIES, entries.size)
        assertEquals("https://example.com/r50", entries.first().url)
        assertEquals("https://example.com/r549", entries.last().url)
    }

    @Test
    fun `entry ids are unique and increasing`() {
        val capture = NetworkCapture()
        repeat(5) { native(capture) }
        val ids = capture.entries.value.map { it.id }
        assertEquals(5, ids.toSet().size)
        assertTrue(ids.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun `js response merges into earlier native skeleton`() {
        val capture = NetworkCapture()
        native(capture, url = "https://example.com/api/data", method = "POST")
        val event = JsNetworkEvent(
            kind = JsEventKind.RESPONSE,
            clientId = "f1",
            url = "https://example.com/api/data",
            method = "POST",
            statusCode = 200,
            responseHeaders = mapOf("content-type" to "application/json"),
            responseBody = """{"ok":true}""",
            mimeType = "application/json",
        )
        capture.recordJsEvent("t1", event)

        val entries = capture.entries.value
        assertEquals(1, entries.size)
        val merged = entries.single()
        assertEquals(200, merged.statusCode)
        assertEquals("""{"ok":true}""", merged.responseBody)
        assertEquals(NetworkSource.MERGED, merged.source)
    }

    @Test
    fun `native arriving after js request merges into js entry`() {
        val capture = NetworkCapture()
        capture.recordJsEvent(
            "t1",
            JsNetworkEvent(
                kind = JsEventKind.REQUEST,
                clientId = "f9",
                url = "https://example.com/api/late",
                method = "GET",
            ),
        )
        native(capture, url = "https://example.com/api/late", method = "GET")

        val entries = capture.entries.value
        assertEquals(1, entries.size)
        assertEquals(NetworkSource.MERGED, entries.single().source)
        assertEquals("GET", entries.single().method)
    }

    @Test
    fun `different urls do not merge`() {
        val capture = NetworkCapture()
        native(capture, url = "https://example.com/a")
        capture.recordJsEvent(
            "t1",
            JsNetworkEvent(
                kind = JsEventKind.RESPONSE,
                clientId = "f2",
                url = "https://example.com/b",
                method = "GET",
                statusCode = 200,
            ),
        )
        assertEquals(2, capture.entries.value.size)
    }

    @Test
    fun `response body is truncated at 256 KB`() {
        val capture = NetworkCapture()
        val big = "x".repeat(NetworkCapture.MAX_BODY_CHARS + 1000)
        capture.recordJsEvent(
            "t1",
            JsNetworkEvent(
                kind = JsEventKind.RESPONSE,
                clientId = "f3",
                url = "https://example.com/big",
                method = "GET",
                statusCode = 200,
                responseBody = big,
                mimeType = "application/json",
            ),
        )
        val body = capture.entries.value.single().responseBody
        assertEquals(NetworkCapture.MAX_BODY_CHARS, body!!.length)
    }

    @Test
    fun `non text bodies are dropped`() {
        val capture = NetworkCapture()
        capture.recordJsEvent(
            "t1",
            JsNetworkEvent(
                kind = JsEventKind.RESPONSE,
                clientId = "f4",
                url = "https://example.com/img.png",
                method = "GET",
                statusCode = 200,
                responseBody = "binary-garbage",
                mimeType = "image/png",
            ),
        )
        assertNull(capture.entries.value.single().responseBody)
    }

    @Test
    fun `js error marks the entry`() {
        val capture = NetworkCapture()
        capture.recordJsEvent(
            "t1",
            JsNetworkEvent(
                kind = JsEventKind.REQUEST,
                clientId = "f5",
                url = "https://example.com/flaky",
                method = "GET",
            ),
        )
        capture.recordJsEvent(
            "t1",
            JsNetworkEvent(
                kind = JsEventKind.ERROR,
                clientId = "f5",
                url = "https://example.com/flaky",
                method = "GET",
                error = "TypeError: Failed to fetch",
            ),
        )
        val entry = capture.entries.value.single()
        assertEquals("TypeError: Failed to fetch", entry.error)
        assertTrue(entry.endTimeMs != null)
    }

    @Test
    fun `clear by tab keeps other tabs`() {
        val capture = NetworkCapture()
        native(capture, tab = "t1")
        native(capture, tab = "t2")
        capture.clear("t1")
        val remaining = capture.entries.value
        assertEquals(1, remaining.size)
        assertEquals("t2", remaining.first().tabId)
    }

    @Test
    fun `clear all empties the buffer`() {
        val capture = NetworkCapture()
        native(capture, tab = "t1")
        capture.clear()
        assertTrue(capture.entries.value.isEmpty())
    }
}
