package id.devtools.browser.browser

import androidx.lifecycle.ViewModel
import id.devtools.browser.data.SessionSnapshot
import id.devtools.browser.data.Tab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Owns tab list state. WebView instances are managed by the composition and
 * keyed by tab id; this ViewModel only tracks serializable state.
 */
class BrowserViewModel : ViewModel() {

    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs: StateFlow<List<Tab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    /** Tabs currently showing a main-frame load error. */
    private val _pageErrors = MutableStateFlow<Set<String>>(emptySet())
    val pageErrors: StateFlow<Set<String>> = _pageErrors.asStateFlow()

    /** Main-frame HTTP error status per tab (null = none). */
    private val _httpErrors = MutableStateFlow<Map<String, Int>>(emptyMap())
    val httpErrors: StateFlow<Map<String, Int>> = _httpErrors.asStateFlow()

    /** Tabs whose renderer process died. */
    private val _crashedTabs = MutableStateFlow<Set<String>>(emptySet())
    val crashedTabs: StateFlow<Set<String>> = _crashedTabs.asStateFlow()

    /** SSL error waiting for the user's decision (null = none). */
    private val _pendingSslError = MutableStateFlow<SslErrorRequest?>(null)
    val pendingSslError: StateFlow<SslErrorRequest?> = _pendingSslError.asStateFlow()

    /** In-memory host allowlist from "always for this host" (persisted in M4). */
    private val sslWhitelist = mutableSetOf<String>()

    private val _sslWhitelistFlow = MutableStateFlow<Set<String>>(emptySet())
    val sslWhitelistFlow: StateFlow<Set<String>> = _sslWhitelistFlow.asStateFlow()

    /**
     * Recreate counter per tab. Bumped when a crashed WebView must be
     * rebuilt; the composable keys the WebView instance on it.
     */
    private val _webViewNonce = MutableStateFlow<Map<String, Int>>(emptyMap())
    val webViewNonce: StateFlow<Map<String, Int>> = _webViewNonce.asStateFlow()

    fun bumpWebViewNonce(tabId: String) {
        _webViewNonce.update { it + (tabId to ((it[tabId] ?: 0) + 1)) }
    }

    init {
        addTab(HOME_URL)
    }

    fun addTab(url: String = ""): String {
        val tab = Tab(url = url)
        _tabs.update { it + tab }
        _activeTabId.value = tab.id
        return tab.id
    }

    fun closeTab(id: String) {
        val remaining = _tabs.value.filterNot { it.id == id }
        _tabs.value = remaining
        if (_activeTabId.value == id) {
            _activeTabId.value = remaining.lastOrNull()?.id
        }
        if (remaining.isEmpty()) {
            addTab(HOME_URL)
        }
    }

    fun selectTab(id: String) {
        if (_tabs.value.any { it.id == id }) {
            _activeTabId.value = id
        }
    }

    fun updateTab(id: String, transform: (Tab) -> Tab) {
        _tabs.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    /**
     * Replaces the tab list from a persisted snapshot (M4). The WebViews are
     * not restored — only the active tab's WebView is composed, so restored
     * tabs are lightweight placeholders until selected.
     */
    fun restoreSession(snapshot: SessionSnapshot) {
        val urls = snapshot.tabUrls.filter { it.isNotBlank() }
        _tabs.value = if (urls.isEmpty()) {
            listOf(Tab(url = HOME_URL))
        } else {
            urls.map { Tab(url = it) }
        }
        _activeTabId.value = _tabs.value[snapshot.activeTabIndex.coerceIn(_tabs.value.indices)].id
    }

    /** Restores the persisted SSL allowlist (M4). */
    fun restoreSslWhitelist(hosts: Set<String>) {
        sslWhitelist.clear()
        sslWhitelist.addAll(hosts)
        _sslWhitelistFlow.value = sslWhitelist.toSet()
    }

    fun activeTab(): Tab? = _tabs.value.firstOrNull { it.id == _activeTabId.value }

    fun setPageError(tabId: String, hasError: Boolean) {
        _pageErrors.update { if (hasError) it + tabId else it - tabId }
        if (!hasError) {
            _httpErrors.update { it - tabId }
        }
    }

    fun setHttpError(tabId: String, statusCode: Int) {
        _httpErrors.update { it + (tabId to statusCode) }
    }

    fun setTabCrashed(tabId: String, crashed: Boolean = true) {
        _crashedTabs.update { if (crashed) it + tabId else it - tabId }
    }

    fun setPendingSslError(request: SslErrorRequest?) {
        _pendingSslError.value = request
    }

    fun isHostWhitelisted(host: String): Boolean = sslWhitelist.contains(host)

    /** Resolves the pending SSL dialog decision. Must run on the UI thread. */
    fun resolveSslError(decision: SslDecision) {
        val request = _pendingSslError.value ?: return
        _pendingSslError.value = null
        when (decision) {
            SslDecision.PROCEED_ONCE -> request.handler.proceed()
            SslDecision.ALWAYS_FOR_HOST -> {
                if (request.host.isNotBlank()) {
                    sslWhitelist.add(request.host)
                    _sslWhitelistFlow.value = sslWhitelist.toSet()
                }
                request.handler.proceed()
            }
            SslDecision.CANCEL -> request.handler.cancel()
        }
    }

    companion object {
        const val HOME_URL = "https://www.google.com"
    }
}
