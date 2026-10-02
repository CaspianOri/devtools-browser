package id.devtools.browser.browser

import android.net.Uri
import android.webkit.ValueCallback
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import id.devtools.browser.data.Tab
import id.devtools.browser.devtools.DevToolsViewModel
import id.devtools.browser.network.NetworkJsBridge

/**
 * Hosts the tab's WebView inside Compose. The WebView instance is owned by
 * [TabWebViewManager] and keyed by tab id (+ recreate nonce), so it survives
 * recomposition but is destroyed with the tab.
 */
@Composable
fun TabWebView(
    tab: Tab,
    browserViewModel: BrowserViewModel,
    devToolsViewModel: DevToolsViewModel,
    webViewManager: TabWebViewManager,
    onShowFileChooser: (ValueCallback<Array<Uri>>, android.content.Intent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val ua by devToolsViewModel.userAgent.collectAsState()
    val darkMode by devToolsViewModel.darkMode.collectAsState()
    val erudaEnabled by devToolsViewModel.erudaEnabled.collectAsState()
    val nonceMap by browserViewModel.webViewNonce.collectAsState()
    val nonce = nonceMap[tab.id] ?: 0

    val webView = remember(tab.id, nonce) {
        webViewManager.getOrCreate(tab.id, ua.userAgent)
    }

    // Attach clients once per (tab, nonce) lifetime.
    DisposableEffect(tab.id, nonce) {
        webView.webViewClient = buildWebViewClient(tab.id, browserViewModel, devToolsViewModel, webViewManager)
        webView.webChromeClient = buildWebChromeClient(tab.id, browserViewModel, devToolsViewModel, webViewManager, onShowFileChooser)
        // M2 lane B: JS bridge for the injected fetch/XHR wrapper. The remove
        // first keeps re-attachment idempotent across recompositions.
        webView.removeJavascriptInterface(NetworkJsBridge.INTERFACE_NAME)
        webView.addJavascriptInterface(
            NetworkJsBridge(tab.id, devToolsViewModel.networkCapture),
            NetworkJsBridge.INTERFACE_NAME,
        )
        if (webView.url == null && tab.url.isNotBlank()) {
            webView.loadUrl(tab.url)
        }
        onDispose { /* WebView destroyed via manager on tab close */ }
    }

    // UA profile changes reload the page with the new agent string.
    LaunchedEffect(ua) {
        if (webView.settings.userAgentString != ua.userAgent) {
            webView.settings.userAgentString = ua.userAgent
            if (webView.url != null) webView.reload()
        }
    }

    LaunchedEffect(darkMode) {
        applyDarkMode(webView, darkMode)
    }

    LaunchedEffect(erudaEnabled) {
        ErudaHelper.setEnabled(webView, context, erudaEnabled) {
            devToolsViewModel.setErudaEnabled(false)
            // Error toast is shown by the caller (AppPanel) via callback; the
            // ViewModel flag reset keeps the toggle visually consistent.
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier.fillMaxSize(),
    )
}
