package id.devtools.browser.browser

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import id.devtools.browser.console.ConsoleEntry
import id.devtools.browser.console.ConsoleLevel
import id.devtools.browser.devtools.DevToolsViewModel
import java.io.IOException

/** Small JS snippets evaluated against the page. Kept inline; Eruda lives in assets. */
object JsSnippets {
    /** Returns JSON: {"w":innerWidth,"h":innerHeight,"dpr":devicePixelRatio,"ua":navigator.userAgent} */
    const val VIEWPORT_INFO =
        "(function(){return JSON.stringify({w:window.innerWidth,h:window.innerHeight," +
            "dpr:window.devicePixelRatio,ua:navigator.userAgent});})();"

    /** Returns page load time in ms via Navigation Timing, or -1 if unavailable. */
    const val LOAD_TIME =
        "(function(){try{var t=performance.getEntriesByType('navigation')[0];" +
            "return Math.round(t.loadEventEnd-t.startTime);}catch(e){return -1;}})();"

    const val ERUDA_INIT = "eruda.init();"
    const val ERUDA_DESTROY = "try{eruda.destroy();}catch(e){}"
}

/** Loads bundled eruda.js and toggles the Eruda console overlay. */
object ErudaHelper {
    @Volatile
    private var cachedScript: String? = null

    @Synchronized
    fun script(context: Context): String? {
        cachedScript?.let { return it }
        return try {
            context.assets.open("eruda.js").bufferedReader().use { it.readText() }
                .also { cachedScript = it }
        } catch (e: IOException) {
            null
        }
    }

    fun setEnabled(webView: WebView, context: Context, enabled: Boolean, onError: () -> Unit) {
        if (enabled) {
            val lib = script(context)
            if (lib == null) {
                onError()
                return
            }
            webView.evaluateJavascript(lib, null)
            webView.evaluateJavascript(JsSnippets.ERUDA_INIT, null)
        } else {
            webView.evaluateJavascript(JsSnippets.ERUDA_DESTROY, null)
        }
    }
}

/**
 * Builds the per-tab WebViewClient. Error policy follows spec section 14:
 * every failure surfaces visible UI — nothing fails silently.
 */
fun buildWebViewClient(
    tabId: String,
    viewModel: BrowserViewModel,
    devTools: DevToolsViewModel,
    webViewManager: TabWebViewManager,
): WebViewClient = object : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url ?: return false
        val scheme = url.scheme?.lowercase()
        // http(s) stays in the WebView; everything else goes to the system.
        if (scheme == "http" || scheme == "https") return false
        return try {
            view.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())))
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        viewModel.updateTab(tabId) { it.copy(isLoading = true, progress = 0) }
    }

    override fun onPageFinished(view: WebView, url: String?) {
        viewModel.updateTab(tabId) {
            it.copy(
                isLoading = false,
                progress = 100,
                url = url ?: it.url,
                title = view.title ?: it.title,
                canGoBack = view.canGoBack(),
                canGoForward = view.canGoForward(),
            )
        }
        // Re-apply per-tab toggles after navigation (covers SPAs).
        if (devTools.darkMode.value) {
            applyDarkMode(view, true)
        }
    }

    // NOTE: onReceivedIcon belongs to WebChromeClient; favicon display is backlog.

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        // Sub-frame failures are logged only; main-frame failures get UI.
        if (request.isForMainFrame) {
            viewModel.updateTab(tabId) { it.copy(isLoading = false) }
            viewModel.setPageError(tabId, true)
        }
    }

    @Suppress("OverridingDeprecatedMember")
    override fun onReceivedError(
        view: WebView,
        errorCode: Int,
        description: String?,
        failingUrl: String?,
    ) {
        viewModel.updateTab(tabId) { it.copy(isLoading = false) }
        viewModel.setPageError(tabId, true)
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        if (request.isForMainFrame) {
            viewModel.setHttpError(tabId, errorResponse.statusCode)
        }
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        val host = error.url?.let { Uri.parse(it).host } ?: ""
        if (viewModel.isHostWhitelisted(host)) {
            handler.proceed()
        } else {
            viewModel.setPendingSslError(SslErrorRequest(host, error, handler))
            // The handler is resolved when the user answers the dialog.
            // Do NOT call handler.cancel() here: that would break the dialog flow.
        }
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        viewModel.setTabCrashed(tabId, true)
        return true // We handle the crash; the system must not kill the app.
    }
}

/** Applies WebView algorithmic darkening when supported. */
fun applyDarkMode(view: WebView, enabled: Boolean) {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(view.settings, enabled)
    }
}

/**
 * Builds the per-tab WebChromeClient: progress, console capture (M1 basic),
 * file chooser, and new-window requests.
 */
fun buildWebChromeClient(
    tabId: String,
    viewModel: BrowserViewModel,
    devTools: DevToolsViewModel,
    webViewManager: TabWebViewManager,
    onShowFileChooser: (ValueCallback<Array<Uri>>, Intent) -> Unit,
): WebChromeClient = object : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        viewModel.updateTab(tabId) { it.copy(progress = newProgress) }
    }

    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
        devTools.addConsole(
            ConsoleEntry(
                id = 0, // replaced by DevToolsViewModel counter
                tabId = tabId,
                level = ConsoleLevel.fromMessageLevel(message.messageLevel().ordinal),
                message = "${message.message()}",
                sourceId = message.sourceId(),
                lineNumber = message.lineNumber(),
            ),
        )
        return true
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean {
        return try {
            onShowFileChooser(filePathCallback, fileChooserParams.createIntent())
            true
        } catch (e: Exception) {
            filePathCallback.onReceiveValue(null)
            false
        }
    }

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: android.os.Message,
    ): Boolean {
        // Popups open as new tabs. The WebView is created eagerly so the
        // transport has a target immediately; the composable reuses the same
        // instance (keyed by tab id) and attaches the clients.
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        val newTabId = viewModel.addTab()
        val popupWebView = webViewManager.getOrCreate(
            newTabId,
            devTools.userAgent.value.userAgent,
        )
        transport.webView = popupWebView
        resultMsg.sendToTarget()
        return true
    }
}
