package id.devtools.browser.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import id.devtools.browser.R
import id.devtools.browser.browser.BrowserViewModel
import id.devtools.browser.console.ConsoleEntry
import id.devtools.browser.console.ConsoleLevel
import id.devtools.browser.devtools.DevToolsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Console log viewer (M1 basic): entries captured via
 * WebChromeClient.onConsoleMessage for the active tab.
 */
@Composable
fun ConsolePanel(
    devTools: DevToolsViewModel,
    browser: BrowserViewModel,
) {
    val entries by devTools.consoleEntries.collectAsState()
    val activeTabId by browser.activeTabId.collectAsState()
    val visible = entries.filter { it.tabId == activeTabId }.takeLast(100)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.console_title, visible.size), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { devTools.clearConsole(activeTabId) }) {
                Text(stringResource(R.string.console_clear))
            }
        }
        if (visible.isEmpty()) {
            Text(
                stringResource(R.string.console_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.height(220.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(visible, key = { it.id }) { entry ->
                    ConsoleRow(entry)
                }
            }
        }
    }
}

@Composable
private fun ConsoleRow(entry: ConsoleEntry) {
    val color = when (entry.level) {
        ConsoleLevel.ERROR -> Color(0xFFFF8A80)
        ConsoleLevel.WARN -> Color(0xFFFFD54F)
        ConsoleLevel.DEBUG -> Color(0xFF80CBC4)
        ConsoleLevel.INFO -> Color(0xFF90CAF9)
        ConsoleLevel.LOG -> MaterialTheme.colorScheme.onSurface
    }
    val time = rememberTime(entry.timestampMs)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.level.name, color = color, style = MaterialTheme.typography.labelSmall)
                Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                entry.sourceId?.let {
                    Text(
                        "${it.substringAfterLast('/')} :${entry.lineNumber ?: "?"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(entry.message, style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}

@Composable
private fun rememberTime(timestampMs: Long): String {
    return androidx.compose.runtime.remember(timestampMs) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))
    }
}
