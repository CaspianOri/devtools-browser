package id.devtools.browser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import id.devtools.browser.BuildConfig

/**
 * Owns one WebView per tab, keyed by tab id. Created and destroyed alongside
 * the Activity; never held by a ViewModel (WebViews are not serializable and
 * leak when retained past the Activity).
 */
class TabWebViewManager(context: Context) {

    // Activity-scoped context per design spec (themed WebViews, dialogs);
    // all WebViews are destroyed in MainActivity.onDestroy.
    private val activityContext = context
    private val webViews = mutableMapOf<String, WebView>()

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreate(tabId: String, userAgent: String): WebView =
        webViews.getOrPut(tabId) {
            WebView(activityContext).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    userAgentString = userAgent
                    cacheMode = WebSettings.LOAD_DEFAULT
                }
            }
        }

    fun get(tabId: String): WebView? = webViews[tabId]

    fun destroy(tabId: String) {
        webViews.remove(tabId)?.let {
            it.stopLoading()
            it.destroy()
        }
    }

    fun destroyAll() {
        val ids = webViews.keys.toList()
        ids.forEach(::destroy)
    }

    companion object {
        init {
            // Debug builds only; release gates this behind a settings toggle (M4).
            WebView.setWebContentsDebuggingEnabled(BuildConfig.WEBVIEW_DEBUG)
        }
    }
}
