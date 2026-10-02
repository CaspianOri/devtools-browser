package id.devtools.browser.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Event kind reported by the injected fetch/XHR wrapper. */
enum class JsEventKind {
    REQUEST,
    RESPONSE,
    ERROR,
}

/**
 * Normalized form of one bridge payload. Produced by [parseJsNetworkEvent]
 * from the JSON our own injected wrapper emits.
 */
data class JsNetworkEvent(
    val kind: JsEventKind,
    val clientId: String,
    val url: String,
    val method: String,
    val statusCode: Int? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    val responseHeaders: Map<String, String> = emptyMap(),
    val requestBody: String? = null,
    val responseBody: String? = null,
    val mimeType: String? = null,
    val error: String? = null,
)

/**
 * Wire shape of the bridge payload. Extra fields the wrapper may add later
 * are ignored so old/new app versions stay compatible.
 */
@Serializable
private data class JsNetworkPayload(
    val kind: String = "",
    val id: String = "",
    val url: String = "",
    val method: String = "GET",
    val status: Int? = null,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val mime: String? = null,
    val error: String? = null,
)

private val lenientJson = Json { ignoreUnknownKeys = true }

/**
 * Parses one bridge payload. Returns null (never throws) for malformed,
 * incomplete or unknown payloads so a hostile page cannot crash the bridge.
 */
fun parseJsNetworkEvent(json: String): JsNetworkEvent? {
    if (json.isBlank()) return null
    val payload = try {
        lenientJson.decodeFromString<JsNetworkPayload>(json)
    } catch (e: Exception) {
        return null
    }
    val kind = when (payload.kind.lowercase()) {
        "request" -> JsEventKind.REQUEST
        "response" -> JsEventKind.RESPONSE
        "error" -> JsEventKind.ERROR
        else -> return null
    }
    if (payload.id.isBlank()) return null
    val method = payload.method.uppercase()
    return when (kind) {
        JsEventKind.REQUEST -> JsNetworkEvent(
            kind = kind,
            clientId = payload.id,
            url = payload.url,
            method = method,
            requestHeaders = payload.headers,
            requestBody = payload.body,
        )
        JsEventKind.RESPONSE -> JsNetworkEvent(
            kind = kind,
            clientId = payload.id,
            url = payload.url,
            method = method,
            statusCode = payload.status,
            responseHeaders = payload.headers,
            responseBody = payload.body,
            mimeType = payload.mime,
        )
        JsEventKind.ERROR -> JsNetworkEvent(
            kind = kind,
            clientId = payload.id,
            url = payload.url,
            method = method,
            error = payload.error,
        )
    }
}
