package id.devtools.browser.ui.panels

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.devtools.browser.R
import id.devtools.browser.browser.BrowserViewModel
import id.devtools.browser.devtools.DevToolsViewModel
import id.devtools.browser.network.CurlBuilder
import id.devtools.browser.network.NetworkEntry
import id.devtools.browser.network.NetworkResourceType

/**
 * Network inspector (M2): request list with type filter + URL search, detail
 * dialog per request, and copy-as-cURL. Lane A (native skeleton) and lane B
 * (JS fetch/XHR wrapper) are already deduplicated in [NetworkCapture].
 */
@Composable
fun NetworkPanel(
    devTools: DevToolsViewModel,
    browser: BrowserViewModel,
) {
    val entries by devTools.networkCapture.entries.collectAsState()
    val activeTabId by browser.activeTabId.collectAsState()
    var filter by remember { mutableStateOf<NetworkResourceType?>(null) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<NetworkEntry?>(null) }

    val visible = entries
        .filter { it.tabId == activeTabId }
        .filter { filter == null || it.resourceType == filter }
        .filter { query.isBlank() || it.url.contains(query, ignoreCase = true) }
        .takeLast(UI_ENTRY_LIMIT)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.network_title, visible.size),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = { devTools.clearNetwork(activeTabId) }) {
                Text(stringResource(R.string.console_clear))
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.network_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filter == null,
                onClick = { filter = null },
                label = { Text(stringResource(R.string.network_filter_all)) },
            )
            NetworkResourceType.entries.forEach { type ->
                FilterChip(
                    selected = filter == type,
                    onClick = { filter = type },
                    label = { Text(type.shortLabel()) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (visible.isEmpty()) {
            Text(
                stringResource(R.string.network_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.height(260.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(visible, key = { it.id }) { entry ->
                    NetworkRow(entry, onClick = { selected = entry })
                }
            }
        }
    }

    selected?.let { entry ->
        NetworkDetailDialog(entry = entry, onDismiss = { selected = null })
    }
}

private const val UI_ENTRY_LIMIT = 200

private fun NetworkResourceType.shortLabel(): String = when (this) {
    NetworkResourceType.DOCUMENT -> "Doc"
    NetworkResourceType.XHR -> "XHR"
    NetworkResourceType.SCRIPT -> "JS"
    NetworkResourceType.STYLESHEET -> "CSS"
    NetworkResourceType.IMAGE -> "Img"
    NetworkResourceType.MEDIA -> "Media"
    NetworkResourceType.FONT -> "Font"
    NetworkResourceType.OTHER -> "Other"
}

@Composable
private fun NetworkRow(entry: NetworkEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                entry.method,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = methodColor(entry.method),
            )
            Text(
                entry.statusCode?.toString() ?: "…",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = statusColor(entry.statusCode),
            )
            Text(
                entry.url,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun methodColor(method: String): Color = when (method.uppercase()) {
    "GET" -> Color(0xFF90CAF9)
    "POST" -> Color(0xFFA5D6A7)
    "PUT", "PATCH" -> Color(0xFFFFD54F)
    "DELETE" -> Color(0xFFFF8A80)
    else -> Color(0xFFB0BEC5)
}

private fun statusColor(status: Int?): Color = when {
    status == null -> Color(0xFFB0BEC5)
    status < 300 -> Color(0xFFA5D6A7)
    status < 400 -> Color(0xFFFFD54F)
    else -> Color(0xFFFF8A80)
}

@Composable
private fun NetworkDetailDialog(entry: NetworkEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val durationMs = entry.endTimeMs?.let { it - entry.startTimeMs }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.network_detail_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DetailLine(
                            stringResource(R.string.network_method),
                            entry.method,
                        )
                        DetailLine(
                            stringResource(R.string.network_status),
                            entry.statusCode?.toString()
                                ?: entry.error
                                ?: stringResource(R.string.network_pending),
                        )
                        DetailLine(stringResource(R.string.network_type), entry.resourceType.name)
                        durationMs?.let {
                            DetailLine(
                                stringResource(R.string.network_duration),
                                stringResource(R.string.network_duration_ms, it),
                            )
                        }
                        DetailLine(stringResource(R.string.network_url), entry.url)
                    }
                }
                BodySection(
                    title = stringResource(R.string.network_request),
                    headers = entry.requestHeaders,
                    body = entry.requestBody,
                )
                BodySection(
                    title = stringResource(R.string.network_response),
                    headers = entry.responseHeaders,
                    body = entry.responseBody,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(CurlBuilder.build(entry)))
                    Toast.makeText(
                        context,
                        context.getString(R.string.copied),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            ) {
                Text(stringResource(R.string.network_copy_curl))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Text(
        "$label: $value",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun BodySection(
    title: String,
    headers: Map<String, String>,
    body: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        if (headers.isNotEmpty()) {
            SelectionContainer {
                Text(
                    headers.entries.joinToString("\n") { "${it.key}: ${it.value}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SelectionContainer {
            Text(
                body ?: stringResource(R.string.network_no_body),
                style = MaterialTheme.typography.bodySmall,
                color = if (body == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}
