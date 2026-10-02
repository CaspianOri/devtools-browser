package id.devtools.browser.browser

import android.net.http.SslError
import android.webkit.SslErrorHandler

/** User decision for an SSL error on a host. */
enum class SslDecision {
    PROCEED_ONCE,
    ALWAYS_FOR_HOST,
    CANCEL,
}

/**
 * A pending SSL error awaiting user decision. The [handler] must be resolved
 * exactly once on the UI thread.
 */
data class SslErrorRequest(
    val host: String,
    val error: SslError,
    val handler: SslErrorHandler,
)
