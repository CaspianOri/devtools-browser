package id.devtools.browser.ui.panels

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import id.devtools.browser.R
import id.devtools.browser.browser.JsSnippets
import id.devtools.browser.devtools.DevToolsViewModel
import id.devtools.browser.ui.DevToolsColors

/**
 * Perf tab (M1): page load-time measurement. The network inspector list
 * lands here in M2.
 */
@Composable
fun PerfPanel(
    devTools: DevToolsViewModel,
    activeWebView: () -> WebView?,
) {
    val loadTimeMs by devTools.loadTimeMs.collectAsState()

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("⚡ ${stringResource(R.string.load_time)}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    loadTimeMs?.let { "$it ms" } ?: "—",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        activeWebView()?.evaluateJavascript(JsSnippets.LOAD_TIME) { raw ->
                            devTools.setLoadTimeMs(raw?.trim()?.toLongOrNull()?.takeIf { it >= 0 })
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DevToolsColors.Orange),
                ) {
                    Text(stringResource(R.string.load_time))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text(
                "Network inspector hadir di M2.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
