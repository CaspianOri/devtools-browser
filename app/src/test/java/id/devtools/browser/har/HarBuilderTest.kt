package id.devtools.browser.har

import id.devtools.browser.network.NetworkEntry
import id.devtools.browser.network.NetworkResourceType
import id.devtools.browser.network.NetworkSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED: HarBuilder does not exist yet. HAR 1.2 contract per spec:
 * unknown native status -> 0, unknown sizes/timings -> -1, bodies only
 * when actually captured.
 */
class HarBuilderTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun entry(
        url: String = "https://example.com/api?q=1",
        method: String = "POST",
        status: Int? = 200,
        reqHeaders: Map<String, String> = mapOf("Content-Type" to "application/json"),
        resHeaders: Map<String, String> = mapOf("content-type" to "application/json; charset=utf-8"),
        reqBody: String? = """{"a":1}""",
        resBody: String? = """{"ok":true}""",
        source: NetworkSource = NetworkSource.MERGED,
    ) = NetworkEntry(
        id = 1,
        tabId = "t1",
        url = url,
        method = method,
        resourceType = NetworkResourceType.XHR,
        startTimeMs = 1_728_000_000_000L,
        endTimeMs = 1_728_000_000_150L,
        statusCode = status,
        requestHeaders = reqHeaders,
        responseHeaders = resHeaders,
        requestBody = reqBody,
        responseBody = resBody,
        source = source,
    )

    private fun logOf(vararg entries: NetworkEntry) =
        json.parseToJsonElement(buildHar(entries.toList(), "0.3.0"))
            .jsonObject["log"]!!.jsonObject

    @Test
    fun `log version is 1_2 with creator`() {
        val log = logOf(entry())
        assertEquals("1.2", log["version"]!!.jsonPrimitive.content)
        val creator = log["creator"]!!.jsonObject
        assertEquals("DevTools Browser", creator["name"]!!.jsonPrimitive.content)
        assertEquals("0.3.0", creator["version"]!!.jsonPrimitive.content)
    }

    @Test
    fun `request maps method url headers query and post data`() {
        val harEntry = logOf(entry())["entries"]!!.jsonArray.single().jsonObject
        val req = harEntry["request"]!!.jsonObject
        assertEquals("POST", req["method"]!!.jsonPrimitive.content)
        assertEquals("https://example.com/api?q=1", req["url"]!!.jsonPrimitive.content)
        val headers = req["headers"]!!.jsonArray
        assertTrue(headers.any {
            it.jsonObject["name"]!!.jsonPrimitive.content == "Content-Type"
        })
        val query = req["queryString"]!!.jsonArray.single().jsonObject
        assertEquals("q", query["name"]!!.jsonPrimitive.content)
        assertEquals("1", query["value"]!!.jsonPrimitive.content)
        val post = req["postData"]!!.jsonObject
        assertEquals("""{"a":1}""", post["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `unknown native status becomes 0`() {
        val harEntry = logOf(entry(status = null, source = NetworkSource.NATIVE))["entries"]!!
            .jsonArray.single().jsonObject
        assertEquals(0, harEntry["response"]!!.jsonObject["status"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `unknown timings are -1`() {
        val harEntry = logOf(entry())["entries"]!!.jsonArray.single().jsonObject
        val timings = harEntry["timings"]!!.jsonObject
        assertEquals(-1, timings["send"]!!.jsonPrimitive.content.toInt())
        assertEquals(-1, timings["wait"]!!.jsonPrimitive.content.toInt())
        assertEquals(-1, timings["receive"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `missing bodies are omitted not empty`() {
        val harEntry = logOf(entry(reqBody = null, resBody = null))["entries"]!!
            .jsonArray.single().jsonObject
        val req = harEntry["request"]!!.jsonObject
        val res = harEntry["response"]!!.jsonObject
        assertFalse(req.containsKey("postData"))
        val content = res["content"]!!.jsonObject
        assertFalse(content.containsKey("text"))
        assertEquals(-1, content["size"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `captured body sets text and size`() {
        val harEntry = logOf(entry())["entries"]!!.jsonArray.single().jsonObject
        val content = harEntry["response"]!!.jsonObject["content"]!!.jsonObject
        assertEquals("""{"ok":true}""", content["text"]!!.jsonPrimitive.content)
        assertEquals("""{"ok":true}""".length, content["size"]!!.jsonPrimitive.content.toInt())
        assertEquals("application/json", content["mimeType"]!!.jsonPrimitive.content)
    }

    @Test
    fun `startedDateTime is ISO-8601`() {
        val harEntry = logOf(entry())["entries"]!!.jsonArray.single().jsonObject
        val started = harEntry["startedDateTime"]!!.jsonPrimitive.content
        assertTrue(started.startsWith("2024-10-04T"))
    }

    @Test
    fun `empty entry list yields empty entries array`() {
        val log = logOf()
        assertTrue(log["entries"]!!.jsonArray.isEmpty())
    }
}
