package id.devtools.browser.data

import java.util.UUID

/**
 * Serializable browser tab state. The WebView instance itself is owned by the
 * composition (keyed by [id]) and never stored here, so tabs survive
 * ViewModel retention but not process death (session restore lands in M4).
 */
data class Tab(
    val id: String = UUID.randomUUID().toString(),
    val url: String = "",
    val title: String = "",
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoading: Boolean = false,
) {
    /** Title for UI display; the composable layer substitutes the localized fallback. */
    val hasDisplayableTitle: Boolean get() = title.isNotBlank() || url.isNotBlank()
}
