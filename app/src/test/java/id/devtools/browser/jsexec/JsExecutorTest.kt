package id.devtools.browser.jsexec

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED: JsExecutor helpers do not exist yet. The wrapper must embed arbitrary
 * user code safely; the callback parser must decode evaluateJavascript's
 * double-JSON without ever throwing.
 */
class JsExecutorTest {

    @Test
    fun `wrapper embeds code as JSON string and uses indirect eval`() {
        val code = "alert(\"x\");\n document.title"
        val wrapper = buildJsWrapper(code)
        assertTrue(wrapper.contains(Json.encodeToString(code)))
        assertTrue(wrapper.contains("(0,eval)"))
        assertTrue(wrapper.contains("JSON.stringify"))
    }

    @Test
    fun `wrapper handles empty code`() {
        val wrapper = buildJsWrapper("")
        assertTrue(wrapper.contains("\"\""))
    }

    private fun callbackOf(innerJson: String): String = Json.encodeToString(innerJson)

    @Test
    fun `parses successful result`() {
        val result = parseJsResult(callbackOf("""{"ok":true,"value":"42"}"""))
        assertTrue(result.ok)
        assertEquals("42", result.value)
    }

    @Test
    fun `parses error result`() {
        val result = parseJsResult(callbackOf("""{"ok":false,"error":"ReferenceError: foo"}"""))
        assertFalse(result.ok)
        assertEquals("ReferenceError: foo", result.error)
    }

    @Test
    fun `garbage callback becomes error result not exception`() {
        val result = parseJsResult("definitely not json")
        assertFalse(result.ok)
        assertTrue(result.error!!.isNotBlank())
    }

    @Test
    fun `null callback becomes error result`() {
        val result = parseJsResult(null)
        assertFalse(result.ok)
    }
}
