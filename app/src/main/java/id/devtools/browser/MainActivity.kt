package id.devtools.browser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import id.devtools.browser.browser.BrowserViewModel
import id.devtools.browser.browser.TabWebViewManager
import id.devtools.browser.data.SessionSnapshot
import id.devtools.browser.data.SessionStore
import id.devtools.browser.data.userAgentFromName
import id.devtools.browser.devtools.DevToolsViewModel
import id.devtools.browser.media.captureViewport
import id.devtools.browser.media.saveBitmapToPictures
import id.devtools.browser.media.screenshotFileName
import id.devtools.browser.ui.BrowserScreen
import id.devtools.browser.ui.DevToolsBrowserTheme
import id.devtools.browser.ui.DevToolsSheet
import id.devtools.browser.ui.normalizeUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Single activity. Owns the [TabWebViewManager] (WebViews must not outlive
 * the Activity) and the file-chooser contract; everything else lives in the
 * ViewModels.
 */
class MainActivity : ComponentActivity() {

    private val browserViewModel: BrowserViewModel by viewModels()
    private val devToolsViewModel: DevToolsViewModel by viewModels()
    private lateinit var webViewManager: TabWebViewManager
    private lateinit var sessionStore: SessionStore

    /** Hoisted so takeScreenshot() can dismiss the sheet from activity scope. */
    private var showDevTools by mutableStateOf(false)

    private var fileChooserCallback: ValueCallback<Array<Uri>>? by mutableStateOf(null)

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris = if (result.resultCode == RESULT_OK) {
                result.data?.let { data ->
                    if (data.clipData != null) {
                        Array(data.clipData!!.itemCount) { i -> data.clipData!!.getItemAt(i).uri }
                    } else {
                        data.data?.let { arrayOf(it) }
                    }
                }
            } else {
                null
            }
            fileChooserCallback?.onReceiveValue(uris)
            fileChooserCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webViewManager = TabWebViewManager(this)
        sessionStore = SessionStore(this)

        // M4 session restore runs before composition so the restored tabs
        // (active tab eager, the rest placeholders) are there from the start.
        // The read is bounded: a stuck store degrades to a fresh session
        // instead of black-screening startup.
        lifecycleScope.launch {
            val snapshot = try {
                withTimeout(3000) { sessionStore.snapshot.first() }
            } catch (e: Exception) {
                SessionSnapshot()
            }
            browserViewModel.restoreSession(snapshot)
            devToolsViewModel.setUserAgent(userAgentFromName(snapshot.userAgentName))
            devToolsViewModel.setDarkMode(snapshot.darkMode)
            browserViewModel.restoreSslWhitelist(snapshot.sslWhitelist)
            // A VIEW intent (shared link) still opens as a new active tab.
            handleViewIntent(intent)

            setContent {
                DevToolsBrowserTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        BrowserScreen(
                            browserViewModel = browserViewModel,
                            devToolsViewModel = devToolsViewModel,
                            webViewManager = webViewManager,
                            onOpenDevTools = { showDevTools = true },
                            onShowFileChooser = { callback, intent ->
                                fileChooserCallback?.onReceiveValue(null)
                                fileChooserCallback = callback
                                fileChooserLauncher.launch(intent)
                            },
                        )

                        if (showDevTools) {
                            DevToolsSheet(
                                browserViewModel = browserViewModel,
                                devToolsViewModel = devToolsViewModel,
                                activeWebView = {
                                    browserViewModel.activeTabId.value?.let { webViewManager.get(it) }
                                },
                                onDismiss = { showDevTools = false },
                                onTakeScreenshot = ::takeScreenshot,
                            )
                        }
                    }
                }
            }

            // Persist the session. distinctUntilChanged keeps progress ticks
            // and other non-session state from triggering writes.
            combine(
                browserViewModel.tabs,
                browserViewModel.activeTabId,
                devToolsViewModel.userAgent,
                devToolsViewModel.darkMode,
                browserViewModel.sslWhitelistFlow,
            ) { tabs, activeId, userAgent, darkMode, whitelist ->
                SessionSnapshot(
                    tabUrls = tabs.map { it.url },
                    activeTabIndex = tabs.indexOfFirst { it.id == activeId }.coerceAtLeast(0),
                    userAgentName = userAgent.name,
                    darkMode = darkMode,
                    sslWhitelist = whitelist,
                )
            }.distinctUntilChanged().collectLatest { sessionStore.save(it) }
        }
    }

    /**
     * M3 screenshot flow. Dismisses the DevTools sheet first so it is not part
     * of the capture, waits out the dismiss animation, then PixelCopies the
     * active tab's WebView into Pictures/DevToolsBrowser.
     *
     * Runs in [lifecycleScope] (activity-scoped) on purpose: the previous
     * implementation launched the delayed capture in the sheet's composition
     * scope, which is cancelled on dismiss — killing the capture before it ran
     * and leaving the user with no toast and no file.
     */
    private fun takeScreenshot() {
        showDevTools = false
        lifecycleScope.launch {
            delay(350)
            val webView = browserViewModel.activeTabId.value?.let { webViewManager.get(it) }
            if (webView == null) {
                Toast.makeText(this@MainActivity, getString(R.string.no_active_tab), Toast.LENGTH_SHORT).show()
                return@launch
            }
            captureViewport(webView) { bitmap ->
                if (bitmap == null) {
                    Toast.makeText(this@MainActivity, getString(R.string.screenshot_failed), Toast.LENGTH_SHORT).show()
                    return@captureViewport
                }
                val uri = saveBitmapToPictures(
                    this@MainActivity,
                    bitmap,
                    screenshotFileName(System.currentTimeMillis()),
                )
                val msg = if (uri != null) {
                    getString(R.string.screenshot_saved, uri.lastPathSegment ?: "?")
                } else {
                    getString(R.string.screenshot_failed)
                }
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleViewIntent(intent)
    }

    /** Opens http(s) VIEW intents (e.g. links shared to the app) in a new tab. */
    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.toString()?.let { url ->
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    browserViewModel.addTab(url)
                }
            }
        }
    }

    override fun onDestroy() {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        if (::webViewManager.isInitialized) {
            webViewManager.destroyAll()
        }
        super.onDestroy()
    }
}
