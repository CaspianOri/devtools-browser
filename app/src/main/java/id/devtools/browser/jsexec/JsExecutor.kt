package id.devtools.browser.jsexec

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * JS executor core (M3). User code is embedded as a JSON string literal and
 * run through indirect eval, so arbitrary code (quotes, newlines, unicode)
 * cannot break the wrapper. The wrapper always returns a JSON string, which
 * [parseJsResult] decodes from evaluateJavascript's double-encoded callback.
 */
@Serializable
private data class JsExecPayload(
    val ok: Boolean,
    val value: String? = null,
    val error: String? = null,
)

data class JsExecResult(
    val ok: Boolean,
    val value: String?,
    val error: String?,
)

data class JsHistoryItem(
    val code: String,
    val result: JsExecResult,
    val timestampMs: Long = System.currentTimeMillis(),
)

/** Builds the self-contained snippet evaluated in the page. */
fun buildJsWrapper(code: String): String {
    val encoded = Json.encodeToString(code)
    return "(function(){var src=JSON.parse($encoded);" +
        "try{var v=(0,eval)(src);var s;" +
        "try{s=(typeof v==='undefined')?'undefined':(typeof v==='string'?v:JSON.stringify(v));}" +
        "catch(x){s=String(v);}" +
        "return JSON.stringify({ok:true,value:s});}" +
        "catch(e){return JSON.stringify({ok:false,error:String((e&&e.stack)||e)});}})()"
}

/**
 * Decodes the evaluateJavascript callback. Never throws: any undecodable
 * callback becomes an error result.
 */
fun parseJsResult(rawCallback: String?): JsExecResult {
    if (rawCallback.isNullOrBlank()) {
        return JsExecResult(ok = false, value = null, error = "no result")
    }
    return try {
        val inner = Json.decodeFromString<String>(rawCallback)
        val payload = Json.decodeFromString<JsExecPayload>(inner)
        JsExecResult(ok = payload.ok, value = payload.value, error = payload.error)
    } catch (e: Exception) {
        JsExecResult(ok = false, value = null, error = "Failed to parse result: ${e.message}")
    }
}

/** Capped execution history, newest last. */
class JsHistoryStore {

    private val _items = MutableStateFlow<List<JsHistoryItem>>(emptyList())
    val items: StateFlow<List<JsHistoryItem>> = _items.asStateFlow()

    fun add(code: String, result: JsExecResult) {
        _items.value = (_items.value + JsHistoryItem(code, result)).takeLast(MAX_HISTORY)
    }

    fun clear() {
        _items.value = emptyList()
    }

    companion object {
        const val MAX_HISTORY = 100
    }
}
