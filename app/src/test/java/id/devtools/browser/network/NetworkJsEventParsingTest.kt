package id.devtools.browser.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * RED: parseJsNetworkEvent does not exist yet. The payload is produced by our
 * own injected fetch/XHR wrapper; parsing must be strict enough to reject
 * garbage without crashing the bridge.
 */
class NetworkJsEventParsingTest {

    @Test
    fun `parses a full response event`() {
        val json = """
            {
              "kind": "response",
              "id": "f1_1728000000000",
              "url": "https://example.com/api",
              "method": "post",
              "status": 201,
              "headers": {"content-type": "application/json"},
              "body": "{\"ok\":true}",
              "mime": "application/json; charset=utf-8",
              "api": "fetch"
            }
        """.trimIndent()
        val event = parseJsNetworkEvent(json)!!
        assertEquals(JsEventKind.RESPONSE, event.kind)
        assertEquals("f1_1728000000000", event.clientId)
        assertEquals("https://example.com/api", event.url)
        assertEquals("POST", event.method)
        assertEquals(201, event.statusCode)
        assertEquals("application/json", event.responseHeaders["content-type"])
        assertEquals("""{"ok":true}""", event.responseBody)
        assertEquals("application/json; charset=utf-8", event.mimeType)
    }

    @Test
    fun `parses a request event with headers and body`() {
        val json = """
            {"kind":"request","id":"x2_1","url":"https://example.com/s","method":"PUT",
             "headers":{"X-A":"b"},"body":"payload","api":"xhr"}
        """.trimIndent()
        val event = parseJsNetworkEvent(json)!!
        assertEquals(JsEventKind.REQUEST, event.kind)
        assertEquals(mapOf("X-A" to "b"), event.requestHeaders)
        assertEquals("payload", event.requestBody)
    }

    @Test
    fun `parses an error event`() {
        val json = """{"kind":"error","id":"f3","url":"https://example.com/e","method":"GET","error":"boom"}"""
        val event = parseJsNetworkEvent(json)!!
        assertEquals(JsEventKind.ERROR, event.kind)
        assertEquals("boom", event.error)
    }

    @Test
    fun `missing kind or id returns null`() {
        assertNull(parseJsNetworkEvent("""{"url":"https://example.com/"}"""))
        assertNull(parseJsNetworkEvent("""{"kind":"request"}"""))
    }

    @Test
    fun `malformed json returns null instead of throwing`() {
        assertNull(parseJsNetworkEvent("not json at all {{{"))
        assertNull(parseJsNetworkEvent(""))
    }

    @Test
    fun `unknown kind returns null`() {
        assertNull(parseJsNetworkEvent("""{"kind":"teleport","id":"z1"}"""))
    }
}
