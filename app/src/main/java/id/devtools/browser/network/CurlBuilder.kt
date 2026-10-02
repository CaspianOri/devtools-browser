package id.devtools.browser.network

/**
 * Builds a `curl` command that replays a captured request. Used by the
 * "Copy as cURL" action in the network inspector; handy for bug-bounty
 * workflows where a request must be replayed or shared verbatim.
 */
object CurlBuilder {

    /** Headers curl manages itself; passing them can corrupt the replay. */
    private val SKIPPED_HEADERS = setOf("content-length")

    fun build(entry: NetworkEntry): String = buildString {
        append("curl")
        val method = entry.method.uppercase()
        if (method != "GET") {
            append(" -X ").append(method)
        }
        append(' ').append(shellQuote(entry.url))
        for ((name, value) in entry.requestHeaders) {
            if (name.lowercase() in SKIPPED_HEADERS) continue
            append(" -H ").append(shellQuote("$name: $value"))
        }
        entry.requestBody?.let { body ->
            append(" --data-raw ").append(shellQuote(body))
        }
        append(" --compressed")
    }

    /** POSIX single-quote escaping: ' -> '\'' */
    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
