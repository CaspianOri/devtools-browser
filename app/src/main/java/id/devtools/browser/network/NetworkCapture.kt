package id.devtools.browser.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe ring buffer for captured network traffic.
 *
 * [shouldInterceptRequest] runs on a background thread while the JS bridge
 * callback runs on another, so every mutation is guarded by [lock].
 *
 * Dedup: the native lane fires for the same requests the injected fetch/XHR
 * wrapper reports. Entries with the same tab, method and URL that are still
 * incomplete (no status yet) and younger than [DEDUP_WINDOW_MS] are merged
 * into a single entry instead of being recorded twice.
 */
class NetworkCapture {

    private val lock = Any()
    private val idCounter = AtomicLong(0)

    private val _entries = MutableStateFlow<List<NetworkEntry>>(emptyList())
    val entries: StateFlow<List<NetworkEntry>> = _entries.asStateFlow()

    /**
     * Lane A: request skeleton from shouldInterceptRequest. Never blocks or
     * refetches; records metadata only and returns immediately.
     */
    fun recordNative(
        tabId: String,
        url: String,
        method: String,
        requestHeaders: Map<String, String> = emptyMap(),
        isMainFrame: Boolean = false,
        startTimeMs: Long = System.currentTimeMillis(),
    ): NetworkEntry = synchronized(lock) {
        val normalizedMethod = method.uppercase()
        val now = System.currentTimeMillis()

        // A JS-lane entry for the same request may already exist (the wrapper
        // runs before the network stack). Merge instead of duplicating.
        val existing = findMergeCandidate(tabId, normalizedMethod, url, now)
        val result = if (existing != null && existing.source == NetworkSource.JS) {
            existing.copy(
                requestHeaders = mergeHeaders(existing.requestHeaders, requestHeaders),
                source = NetworkSource.MERGED,
            )
        } else {
            NetworkEntry(
                id = idCounter.incrementAndGet(),
                tabId = tabId,
                url = url,
                method = normalizedMethod,
                resourceType = guessResourceType(url, isMainFrame),
                startTimeMs = startTimeMs,
                requestHeaders = requestHeaders,
                source = NetworkSource.NATIVE,
            )
        }
        replace(result)
        result
    }

    /** Lane B: events from the injected fetch/XHR wrapper via the JS bridge. */
    fun recordJsEvent(tabId: String, event: JsNetworkEvent): NetworkEntry =
        synchronized(lock) {
            val now = System.currentTimeMillis()
            when (event.kind) {
                JsEventKind.REQUEST -> {
                    val existing = findMergeCandidate(tabId, event.method, event.url, now)
                    val result = if (existing != null && existing.clientId == null) {
                        existing.copy(
                            clientId = event.clientId,
                            requestHeaders = mergeHeaders(existing.requestHeaders, event.requestHeaders),
                            requestBody = truncateBody(event.requestBody, null)
                                ?: existing.requestBody,
                            source = NetworkSource.MERGED,
                        )
                    } else {
                        NetworkEntry(
                            id = idCounter.incrementAndGet(),
                            tabId = tabId,
                            url = event.url,
                            method = event.method,
                            resourceType = NetworkResourceType.XHR,
                            requestHeaders = event.requestHeaders,
                            requestBody = truncateBody(event.requestBody, null),
                            source = NetworkSource.JS,
                            clientId = event.clientId,
                        )
                    }
                    replace(result)
                    result
                }
                JsEventKind.RESPONSE -> {
                    val target = findByClientId(tabId, event.clientId)
                        ?: findMergeCandidate(tabId, event.method, event.url, now)?.let {
                            it.copy(clientId = event.clientId)
                        }
                        ?: NetworkEntry(
                            id = idCounter.incrementAndGet(),
                            tabId = tabId,
                            url = event.url,
                            method = event.method,
                            resourceType = NetworkResourceType.XHR,
                            source = NetworkSource.JS,
                            clientId = event.clientId,
                        )
                    val result = target.copy(
                        statusCode = event.statusCode,
                        responseHeaders = event.responseHeaders,
                        responseBody = truncateBody(event.responseBody, event.mimeType),
                        endTimeMs = now,
                        source = if (target.source == NetworkSource.NATIVE) {
                            NetworkSource.MERGED
                        } else {
                            target.source
                        },
                    )
                    replace(result)
                    result
                }
                JsEventKind.ERROR -> {
                    val target = findByClientId(tabId, event.clientId)
                        ?: NetworkEntry(
                            id = idCounter.incrementAndGet(),
                            tabId = tabId,
                            url = event.url,
                            method = event.method,
                            resourceType = NetworkResourceType.XHR,
                            source = NetworkSource.JS,
                            clientId = event.clientId,
                        )
                    val result = target.copy(error = event.error, endTimeMs = now)
                    replace(result)
                    result
                }
            }
        }

    fun clear(tabId: String? = null) = synchronized(lock) {
        _entries.value = if (tabId == null) {
            emptyList()
        } else {
            _entries.value.filterNot { it.tabId == tabId }
        }
    }

    private fun findMergeCandidate(
        tabId: String,
        method: String,
        url: String,
        now: Long,
    ): NetworkEntry? = _entries.value.lastOrNull {
        it.tabId == tabId &&
            it.method == method &&
            it.url == url &&
            it.statusCode == null &&
            now - it.startTimeMs <= DEDUP_WINDOW_MS
    }

    private fun findByClientId(tabId: String, clientId: String): NetworkEntry? =
        _entries.value.lastOrNull { it.tabId == tabId && it.clientId == clientId }

    private fun replace(entry: NetworkEntry) {
        _entries.value = (_entries.value.filterNot { it.id == entry.id } + entry)
            .takeLast(MAX_ENTRIES)
    }

    private fun mergeHeaders(
        first: Map<String, String>,
        second: Map<String, String>,
    ): Map<String, String> = second + first

    companion object {
        const val MAX_ENTRIES = 500

        /** Per-body cap: 256 KB of text. */
        const val MAX_BODY_CHARS = 256 * 1024

        /** Native and JS sightings of one request merge within this window. */
        const val DEDUP_WINDOW_MS = 15_000L

        private val TEXT_MIME_PARTS = listOf(
            "json", "javascript", "ecmascript", "xml", "html", "text", "urlencoded",
        )

        /**
         * Returns the body truncated to [MAX_BODY_CHARS], or null when the
         * body is absent or not text-like (images, video, archives...).
         */
        fun truncateBody(body: String?, mimeType: String?): String? {
            if (body == null) return null
            if (mimeType != null &&
                TEXT_MIME_PARTS.none { mimeType.contains(it, ignoreCase = true) }
            ) {
                return null
            }
            return body.take(MAX_BODY_CHARS)
        }
    }
}
