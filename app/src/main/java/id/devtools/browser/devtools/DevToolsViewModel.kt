package id.devtools.browser.devtools

import androidx.lifecycle.ViewModel
import id.devtools.browser.browser.UserAgentProfile
import id.devtools.browser.console.ConsoleEntry
import id.devtools.browser.jsexec.JsHistoryStore
import id.devtools.browser.network.NetworkCapture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * State for the DevTools bottom sheet: console log ring buffer, network
 * capture buffer, UA profile, dark mode, Eruda toggle, viewport info and
 * measured load time.
 */
class DevToolsViewModel : ViewModel() {

    private val idCounter = AtomicLong(0)

    private val _consoleEntries = MutableStateFlow<List<ConsoleEntry>>(emptyList())
    val consoleEntries: StateFlow<List<ConsoleEntry>> = _consoleEntries.asStateFlow()

    /** M2 network capture store (native lane + JS lane, deduplicated). */
    val networkCapture = NetworkCapture()

    /** M3 JS executor history (capped ring buffer). */
    val jsHistory = JsHistoryStore()

    private val _userAgent = MutableStateFlow(UserAgentProfile.ANDROID)
    val userAgent: StateFlow<UserAgentProfile> = _userAgent.asStateFlow()

    private val _darkMode = MutableStateFlow(false)
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    private val _erudaEnabled = MutableStateFlow(false)
    val erudaEnabled: StateFlow<Boolean> = _erudaEnabled.asStateFlow()

    private val _viewportInfo = MutableStateFlow<String?>(null)
    val viewportInfo: StateFlow<String?> = _viewportInfo.asStateFlow()

    private val _loadTimeMs = MutableStateFlow<Long?>(null)
    val loadTimeMs: StateFlow<Long?> = _loadTimeMs.asStateFlow()

    fun addConsole(entry: ConsoleEntry) {
        val withId = entry.copy(id = idCounter.incrementAndGet())
        _consoleEntries.update { (it + withId).takeLast(MAX_CONSOLE_ENTRIES) }
    }

    fun clearConsole(tabId: String? = null) {
        _consoleEntries.update { list ->
            if (tabId == null) emptyList() else list.filterNot { it.tabId == tabId }
        }
    }

    fun clearNetwork(tabId: String? = null) {
        networkCapture.clear(tabId)
    }

    fun setUserAgent(profile: UserAgentProfile) {
        _userAgent.value = profile
    }

    fun setDarkMode(enabled: Boolean) {
        _darkMode.value = enabled
    }

    fun setErudaEnabled(enabled: Boolean) {
        _erudaEnabled.value = enabled
    }

    fun setViewportInfo(info: String?) {
        _viewportInfo.value = info
    }

    fun setLoadTimeMs(ms: Long?) {
        _loadTimeMs.value = ms
    }

    companion object {
        const val MAX_CONSOLE_ENTRIES = 500
    }
}
