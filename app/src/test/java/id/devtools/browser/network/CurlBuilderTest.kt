package id.devtools.browser.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED: CurlBuilder does not exist yet. Copy-as-cURL must reproduce the
 * request faithfully with safe shell quoting.
 */
class CurlBuilderTest {

    private fun entry(
        method: String = "GET",
        url: String = "https://example.com/api",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ) = NetworkEntry(
        id = 1,
        tabId = "t1",
        url = url,
        method = method,
        resourceType = NetworkResourceType.XHR,
        startTimeMs = 0L,
        requestHeaders = headers,
        requestBody = body,
    )

    @Test
    fun `simple GET produces quoted url only`() {
        val curl = CurlBuilder.build(entry(url = "https://example.com/"))
        assertEquals("curl 'https://example.com/' --compressed", curl)
    }

    @Test
    fun `POST adds method flag and data`() {
        val curl = CurlBuilder.build(
            entry(
                method = "POST",
                url = "https://example.com/login",
                headers = mapOf("Content-Type" to "application/json"),
                body = """{"u":"a"}""",
            ),
        )
        assertTrue(curl.contains("-X POST"))
        assertTrue(curl.contains("-H 'Content-Type: application/json'"))
        assertTrue(curl.contains("--data-raw '{\"u\":\"a\"}'"))
    }

    @Test
    fun `single quotes in values are shell escaped`() {
        val curl = CurlBuilder.build(
            entry(
                url = "https://example.com/?q=a'b",
                headers = mapOf("X-Test" to "it's"),
            ),
        )
        assertTrue(curl.contains("'https://example.com/?q=a'\\''b'"))
        assertTrue(curl.contains("-H 'X-Test: it'\\''s'"))
    }

    @Test
    fun `content-length header is skipped`() {
        val curl = CurlBuilder.build(
            entry(
                method = "POST",
                headers = mapOf("Content-Length" to "12", "Accept" to "*/*"),
                body = "hello world!",
            ),
        )
        assertTrue(!curl.contains("Content-Length"))
        assertTrue(curl.contains("-H 'Accept: */*'"))
        assertTrue(curl.contains("--data-raw 'hello world!'"))
    }

    @Test
    fun `header names are matched case insensitively for skipping`() {
        val curl = CurlBuilder.build(
            entry(headers = mapOf("content-length" to "5")),
        )
        assertTrue(!curl.contains("content-length"))
    }
}
