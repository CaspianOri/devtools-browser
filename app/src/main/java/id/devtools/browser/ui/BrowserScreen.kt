package id.devtools.browser.ui

import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.devtools.browser.R
import id.devtools.browser.browser.BrowserViewModel
import id.devtools.browser.browser.SslDecision
import id.devtools.browser.browser.TabWebView
import id.devtools.browser.browser.TabWebViewManager
import id.devtools.browser.devtools.DevToolsViewModel

/** Normalizes raw input into a loadable URL. */
fun normalizeUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    if (!trimmed.contains(".")) {
        // Plain words become a search query.
        return "https://www.google.com/search?q=${Uri.encode(trimmed)}"
    }
    // Domain-like input (even with spaces in the path) is treated as a URL.
    return "https://$trimmed"
}

@Composable
fun BrowserScreen(
    browserViewModel: BrowserViewModel,
    devToolsViewModel: DevToolsViewModel,
    webViewManager: TabWebViewManager,
    onOpenDevTools: () -> Unit,
    onShowFileChooser: (ValueCallback<Array<Uri>>, Intent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val tabs by browserViewModel.tabs.collectAsState()
    val activeTabId by browserViewModel.activeTabId.collectAsState()
    val activeTab = tabs.firstOrNull { it.id == activeTabId }
    val pageErrors by browserViewModel.pageErrors.collectAsState()
    val httpErrors by browserViewModel.httpErrors.collectAsState()
    val crashedTabs by browserViewModel.crashedTabs.collectAsState()
    val pendingSsl by browserViewModel.pendingSslError.collectAsState()

    var urlInput by remember { mutableStateOf("") }

    // Keep the URL bar in sync when switching tabs.
    LaunchedEffect(activeTabId, activeTab?.url) {
        urlInput = activeTab?.url ?: ""
    }

    fun goTo(raw: String) {
        val url = normalizeUrl(raw) ?: return
        val tab = activeTab ?: return
        browserViewModel.setPageError(tab.id, false)
        browserViewModel.setTabCrashed(tab.id, false)
        browserViewModel.updateTab(tab.id) { it.copy(url = url) }
        webViewManager.get(tab.id)?.loadUrl(url)
        urlInput = url
    }

    fun reloadTab() {
        val tab = activeTab ?: return
        browserViewModel.setPageError(tab.id, false)
        browserViewModel.setTabCrashed(tab.id, false)
        val wv = webViewManager.get(tab.id)
        if (wv == null || tab.url.isBlank()) {
            tab.url.takeIf { it.isNotBlank() }?.let { goTo(it) }
        } else {
            wv.reload()
        }
    }

    fun recoverCrashedTab() {
        val tab = activeTab ?: return
        webViewManager.destroy(tab.id)
        browserViewModel.setTabCrashed(tab.id, false)
        browserViewModel.bumpWebViewNonce(tab.id)
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ---- Tab strip (screenshot replica) ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabs.forEach { tab ->
                    TabChip(
                        title = tab.title.ifBlank { tab.url.ifBlank { stringResource(R.string.new_tab) } },
                        selected = tab.id == activeTabId,
                        onSelect = { browserViewModel.selectTab(tab.id) },
                        onClose = {
                            webViewManager.destroy(tab.id)
                            browserViewModel.closeTab(tab.id)
                        },
                    )
                }
            }
            IconButton(onClick = { browserViewModel.addTab() }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.new_tab),
                    tint = MaterialTheme.colorScheme.primary)
            }
        }

        // ---- URL bar ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                placeholder = { Text(stringResource(R.string.url_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { goTo(urlInput) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(stringResource(R.string.go))
            }
        }

        // ---- Navigation row ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { activeTab?.let { webViewManager.get(it.id)?.goBack() } },
                enabled = activeTab?.canGoBack == true,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
            }
            IconButton(
                onClick = { activeTab?.let { webViewManager.get(it.id)?.goForward() } },
                enabled = activeTab?.canGoForward == true,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.nav_forward))
            }
            IconButton(onClick = { reloadTab() }) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.nav_reload))
            }
            Spacer(Modifier.weight(1f))
            // "Buka aplikasi" — open current URL in an external app.
            Button(
                onClick = {
                    val url = activeTab?.url?.takeIf { it.isNotBlank() } ?: return@Button
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.no_app_handler), Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DevToolsColors.Blue),
            ) {
                Text(stringResource(R.string.open_in_app))
            }
        }

        // ---- Progress bar ----
        val progress = activeTab?.progress ?: 0
        if (activeTab?.isLoading == true && progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(3.dp),
            )
        }

        // ---- HTTP error banner ----
        val httpStatus = activeTabId?.let { httpErrors[it] }
        if (httpStatus != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF4A1F1A)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.http_error, httpStatus),
                        modifier = Modifier.weight(1f),
                        color = Color(0xFFFFB4A8),
                    )
                    TextButton(onClick = { reloadTab() }) { Text(stringResource(R.string.retry)) }
                }
            }
        }

        // ---- WebView area ----
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (activeTab != null) {
                val tab = activeTab!!
                TabWebView(
                    tab = tab,
                    browserViewModel = browserViewModel,
                    devToolsViewModel = devToolsViewModel,
                    webViewManager = webViewManager,
                    onShowFileChooser = onShowFileChooser,
                )

                val isCrashed = crashedTabs.contains(tab.id)
                val hasError = pageErrors.contains(tab.id)
                if (isCrashed) {
                    ErrorOverlay(
                        title = stringResource(R.string.tab_crashed),
                        actionLabel = stringResource(R.string.reload_tab),
                        onAction = { recoverCrashedTab() },
                    )
                } else if (hasError) {
                    ErrorOverlay(
                        title = stringResource(R.string.page_load_error),
                        actionLabel = stringResource(R.string.retry),
                        onAction = { reloadTab() },
                    )
                }
            }

            FloatingActionButton(
                onClick = onOpenDevTools,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Filled.Build, contentDescription = stringResource(R.string.devtools))
            }
        }
    }

    // ---- SSL warning dialog (spec section 14) ----
    pendingSsl?.let { request ->
        AlertDialog(
            onDismissRequest = { browserViewModel.resolveSslError(SslDecision.CANCEL) },
            title = { Text(stringResource(R.string.ssl_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.ssl_message,
                        request.error.primaryError.toString(),
                    ) + "\n\nHost: ${request.host}",
                )
            },
            confirmButton = {
                TextButton(onClick = { browserViewModel.resolveSslError(SslDecision.PROCEED_ONCE) }) {
                    Text(stringResource(R.string.ssl_proceed_once))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { browserViewModel.resolveSslError(SslDecision.CANCEL) }) {
                        Text(stringResource(R.string.ssl_cancel))
                    }
                    TextButton(onClick = { browserViewModel.resolveSslError(SslDecision.ALWAYS_FOR_HOST) }) {
                        Text(stringResource(R.string.ssl_always_host))
                    }
                }
            },
        )
    }
}

@Composable
private fun TabChip(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(90.dp),
        )
        IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.close),
                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ErrorOverlay(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
