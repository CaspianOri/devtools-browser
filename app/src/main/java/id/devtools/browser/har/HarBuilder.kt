package id.devtools.browser.har

import id.devtools.browser.network.NetworkEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * HAR 1.2 export (M3). Built from captured [NetworkEntry]s.
 *
 * Contract:
 * - unknown native status -> 0
 * - unknown sizes/timings -> -1
 * - bodies are included only when actually captured (never synthesized)
 */
@Serializable
private data class HarNameValue(val name: String, val value: String)

@Serializable
private data class HarPostData(val mimeType: String, val text: String)

@Serializable
private data class HarRequest(
    val method: String,
    val url: String,
    val httpVersion: String = "HTTP/1.1",
    val headers: List<HarNameValue>,
    val queryString: List<HarNameValue>,
    val postData: HarPostData? = null,
)

@Serializable
private data class HarContent(
    val size: Long,
    val mimeType: String,
    val text: String? = null,
)

@Serializable
private data class HarResponse(
    val status: Int,
    val statusText: String = "",
    val httpVersion: String = "HTTP/1.1",
    val headers: List<HarNameValue>,
    val content: HarContent,
)

@Serializable
private data class HarTimings(
    val send: Long = -1,
    val wait: Long = -1,
    val receive: Long = -1,
)

@Serializable
private data class HarEntry(
    val startedDateTime: String,
    val time: Long,
    val request: HarRequest,
    val response: HarResponse,
    val timings: HarTimings,
)

@Serializable
private data class HarCreator(
    val name: String = "DevTools Browser",
    val version: String,
)

@Serializable
private data class HarLog(
    val version: String = "1.2",
    val creator: HarCreator,
    val entries: List<HarEntry>,
)

@Serializable
private data class HarRoot(val log: HarLog)

// explicitNulls=false drops absent bodies; encodeDefaults=true keeps HAR
// contract fields (version "1.2", timings -1) which are Kotlin defaults.
private val harJson = Json {
    explicitNulls = false
    encodeDefaults = true
}

/** Serializes captured entries to a HAR 1.2 document. */
fun buildHar(entries: List<NetworkEntry>, creatorVersion: String): String =
    harJson.encodeToString(
        HarRoot(
            HarLog(
                creator = HarCreator(version = creatorVersion),
                entries = entries.map { it.toHarEntry() },
            ),
        ),
    )

/** Deterministic UTC filename, e.g. devtools-20261002-120000.har */
fun harFileName(timestampMs: Long): String =
    "devtools-" + utcFormat("yyyyMMdd-HHmmss").format(Date(timestampMs)) + ".har"

private fun NetworkEntry.toHarEntry(): HarEntry {
    val request = HarRequest(
        method = method,
        url = url,
        headers = requestHeaders.map { HarNameValue(it.key, it.value) },
        queryString = parseQuery(url),
        postData = requestBody?.let {
            HarPostData(mimeType = requestContentType(), text = it)
        },
    )
    val response = HarResponse(
        status = statusCode ?: 0,
        headers = responseHeaders.map { HarNameValue(it.key, it.value) },
        content = HarContent(
            size = responseBody?.length?.toLong() ?: -1,
            mimeType = responseContentType(),
            text = responseBody,
        ),
    )
    return HarEntry(
        startedDateTime = utcFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(Date(startTimeMs)),
        time = endTimeMs?.let { it - startTimeMs } ?: -1,
        request = request,
        response = response,
        timings = HarTimings(),
    )
}

private fun NetworkEntry.requestContentType(): String =
    requestHeaders.entries
        .firstOrNull { it.key.equals("content-type", ignoreCase = true) }
        ?.value?.substringBefore(';')?.trim().orEmpty()
        .ifEmpty { "application/octet-stream" }

private fun NetworkEntry.responseContentType(): String =
    responseHeaders.entries
        .firstOrNull { it.key.equals("content-type", ignoreCase = true) }
        ?.value?.substringBefore(';')?.trim().orEmpty()
        .ifEmpty { "application/octet-stream" }

private fun parseQuery(url: String): List<HarNameValue> {
    val raw = url.substringAfter('?', "").substringBefore('#')
    if (raw.isEmpty()) return emptyList()
    return raw.split('&').mapNotNull { pair ->
        val idx = pair.indexOf('=')
        if (idx < 0) {
            null
        } else {
            HarNameValue(decode(pair.substring(0, idx)), decode(pair.substring(idx + 1)))
        }
    }
}

private fun decode(value: String): String =
    try {
        URLDecoder.decode(value, "UTF-8")
    } catch (e: Exception) {
        value
    }

private fun utcFormat(pattern: String) =
    SimpleDateFormat(pattern, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
