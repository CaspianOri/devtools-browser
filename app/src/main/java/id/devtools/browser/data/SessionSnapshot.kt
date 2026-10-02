package id.devtools.browser.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import id.devtools.browser.browser.UserAgentProfile

/**
 * Serializable session state persisted via DataStore (M4):
 * - ordered tab URL list (active tab loads eagerly; the rest are placeholders
 *   until selected — only the active tab's WebView is composed)
 * - index of the active tab
 * - UA profile + dark mode settings
 * - SSL "always for this host" allowlist
 *
 * WebViews themselves are never serialized; a tab is just its URL.
 */
data class SessionSnapshot(
    val tabUrls: List<String> = emptyList(),
    val activeTabIndex: Int = 0,
    val userAgentName: String = UserAgentProfile.ANDROID.name,
    val darkMode: Boolean = false,
    val sslWhitelist: Set<String> = emptySet(),
)

object SessionKeys {
    // Tab order matters, so URLs are stored as one unit-separated string
    // (stringSet would lose ordering).
    val TAB_URLS = stringPreferencesKey("tab_urls")
    val ACTIVE_TAB_INDEX = intPreferencesKey("active_tab_index")
    val USER_AGENT = stringPreferencesKey("user_agent")
    val DARK_MODE = booleanPreferencesKey("dark_mode")
    val SSL_WHITELIST = stringSetPreferencesKey("ssl_whitelist")
}

private const val URL_SEPARATOR = ""

/** Lenient enum parse: unknown names fall back to ANDROID. */
fun userAgentFromName(name: String): UserAgentProfile =
    runCatching { UserAgentProfile.valueOf(name) }.getOrDefault(UserAgentProfile.ANDROID)

fun SessionSnapshot.toPreferences(): Preferences = mutablePreferencesOf().apply {
    this[SessionKeys.TAB_URLS] = tabUrls.filter { it.isNotBlank() }.joinToString(URL_SEPARATOR)
    this[SessionKeys.ACTIVE_TAB_INDEX] = activeTabIndex
    this[SessionKeys.USER_AGENT] = userAgentName
    this[SessionKeys.DARK_MODE] = darkMode
    this[SessionKeys.SSL_WHITELIST] = sslWhitelist
}

fun Preferences.toSessionSnapshot(): SessionSnapshot = SessionSnapshot(
    tabUrls = (this[SessionKeys.TAB_URLS] ?: "")
        .split(URL_SEPARATOR)
        .filter { it.isNotBlank() },
    activeTabIndex = this[SessionKeys.ACTIVE_TAB_INDEX] ?: 0,
    userAgentName = this[SessionKeys.USER_AGENT] ?: UserAgentProfile.ANDROID.name,
    darkMode = this[SessionKeys.DARK_MODE] ?: false,
    sslWhitelist = this[SessionKeys.SSL_WHITELIST] ?: emptySet(),
)
