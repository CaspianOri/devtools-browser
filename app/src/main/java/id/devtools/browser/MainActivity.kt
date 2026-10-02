package id.devtools.browser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
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
import id.devtools.browser.browser.BrowserViewModel
import id.devtools.browser.browser.TabWebViewManager
import id.devtools.browser.devtools.DevToolsViewModel
import id.devtools.browser.ui.BrowserScreen
import id.devtools.browser.ui.DevToolsBrowserTheme
import id.devtools.browser.ui.DevToolsSheet
import id.devtools.browser.ui.normalizeUrl

/**
 * Single activity. Owns the [TabWebViewManager] (WebViews must not outlive
 * the Activity) and the file-chooser contract; everything else lives in the
 * ViewModels.
 */
class MainActivity : ComponentActivity() {

    private val browserViewModel: BrowserViewModel by viewModels()
    private val devToolsViewModel: DevToolsViewModel by viewModels()
    private lateinit var webViewManager: TabWebViewManager

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
        handleViewIntent(intent)

        setContent {
            DevToolsBrowserTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var showDevTools by mutableStateOf(false)

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
                        )
                    }
                }
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
