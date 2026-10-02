package id.devtools.browser.ui.panels

import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.devtools.browser.R
import id.devtools.browser.devtools.DevToolsViewModel
import id.devtools.browser.jsexec.JsHistoryItem
import id.devtools.browser.jsexec.buildJsWrapper
import id.devtools.browser.jsexec.parseJsResult

/**
 * Snip tab (M3): JS executor against the active tab's page, plus viewport
 * screenshot capture to Pictures/DevToolsBrowser.
 */
@Composable
fun SnipPanel(
    devTools: DevToolsViewModel,
    activeWebView: () -> WebView?,
    onTakeScreenshot: () -> Unit,
) {
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    val history by devTools.jsHistory.items.collectAsState()

    fun runCode() {
        val webView = activeWebView()
        if (webView == null) {
            Toast.makeText(context, context.getString(R.string.no_active_tab), Toast.LENGTH_SHORT).show()
            return
        }
        if (code.isBlank()) return
        val snippet = code
        webView.evaluateJavascript(buildJsWrapper(snippet)) { raw ->
            devTools.jsHistory.add(snippet, parseJsResult(raw))
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("</> ${stringResource(R.string.js_title)}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            placeholder = { Text(stringResource(R.string.js_hint)) },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = ::runCode, modifier = Modifier.weight(1f)) {
                Text("▶ ${stringResource(R.string.js_run)}")
            }
            TextButton(onClick = { devTools.jsHistory.clear() }) {
                Text(stringResource(R.string.console_clear))
            }
        }
        Spacer(Modifier.height(8.dp))
        if (history.isEmpty()) {
            Text(
                stringResource(R.string.js_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            LazyColumn(
                modifier = Modifier.height(200.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(history.reversed(), key = { it.timestampMs }) { item ->
                    JsHistoryRow(item)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("📷 ${stringResource(R.string.screenshot)}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onTakeScreenshot,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("📷 ${stringResource(R.string.screenshot)}")
        }
    }
}

@Composable
private fun JsHistoryRow(item: JsHistoryItem) {
    val result = item.result
    val color = if (result.ok) Color(0xFFA5D6A7) else Color(0xFFFF8A80)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                item.code,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
            )
            SelectionContainer {
                Text(
                    result.value ?: result.error ?: "-",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = color,
                )
            }
        }
    }
}
