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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import id.devtools.browser.R
import id.devtools.browser.browser.BrowserViewModel
import id.devtools.browser.browser.ErudaHelper
import id.devtools.browser.browser.JsSnippets
import id.devtools.browser.browser.UserAgentProfile
import id.devtools.browser.browser.applyDarkMode
import id.devtools.browser.devtools.DevToolsViewModel
import id.devtools.browser.ui.DevToolsColors
import org.json.JSONObject

/**
 * App tab: user-agent & viewport switcher, Eruda toggle, dark mode,
 * viewport info and load-time measurement (reference screenshot replica).
 */
@Composable
fun AppPanel(
    devTools: DevToolsViewModel,
    browser: BrowserViewModel,
    activeWebView: () -> WebView?,
) {
    val context = LocalContext.current
    val ua by devTools.userAgent.collectAsState()
    val darkMode by devTools.darkMode.collectAsState()
    val erudaEnabled by devTools.erudaEnabled.collectAsState()
    val viewportInfo by devTools.viewportInfo.collectAsState()
    val activeTabId by browser.activeTabId.collectAsState()

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            stringResource(R.string.user_agent_viewport),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        // UA profile selector.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            UserAgentProfile.entries.forEach { profile ->
                val selected = profile == ua
                Button(
                    onClick = { devTools.setUserAgent(profile) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) DevToolsColors.Teal else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text(
                        when (profile) {
                            UserAgentProfile.ANDROID -> stringResource(R.string.ua_android)
                            UserAgentProfile.IPHONE -> stringResource(R.string.ua_iphone)
                            UserAgentProfile.DESKTOP -> stringResource(R.string.ua_desktop)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Toggle Eruda (full width, purple).
        Button(
            onClick = {
                val wv = activeWebView()
                if (wv == null) {
                    Toast.makeText(context, context.getString(R.string.no_active_tab), Toast.LENGTH_SHORT).show()
                    return@Button
                }
                val next = !erudaEnabled
                ErudaHelper.setEnabled(wv, context, next) {
                    Toast.makeText(context, context.getString(R.string.eruda_failed), Toast.LENGTH_SHORT).show()
                }
                devTools.setErudaEnabled(next)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text("✨ ${stringResource(R.string.toggle_eruda)}${if (erudaEnabled) " (ON)" else ""}")
        }
        Spacer(Modifier.height(12.dp))

        // Viewport / LoadTime / DarkMode / Screenshot grid.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    activeWebView()?.evaluateJavascript(JsSnippets.VIEWPORT_INFO) { raw ->
                        devTools.setViewportInfo(prettyViewport(raw))
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = DevToolsColors.Blue),
            ) {
                Text("◢ ${stringResource(R.string.viewport)}")
            }
            Button(
                onClick = {
                    activeWebView()?.evaluateJavascript(JsSnippets.LOAD_TIME) { raw ->
                        val ms = raw?.trim()?.toLongOrNull()
                        devTools.setLoadTimeMs(ms?.takeIf { it >= 0 })
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = DevToolsColors.Orange),
            ) {
                Text("⚡ ${stringResource(R.string.load_time)}")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🌙 ${stringResource(R.string.dark_mode)}", modifier = Modifier.weight(1f))
                    Switch(
                        checked = darkMode,
                        onCheckedChange = { enabled ->
                            devTools.setDarkMode(enabled)
                            activeWebView()?.let { applyDarkMode(it, enabled) }
                        },
                    )
                }
            }
            Button(
                onClick = {
                    Toast.makeText(context, context.getString(R.string.screenshot_coming_soon), Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = DevToolsColors.Red),
            ) {
                Text("📷 ${stringResource(R.string.screenshot)}")
            }
        }

        viewportInfo?.let { info ->
            Spacer(Modifier.height(8.dp))
            Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Turns the raw JSON-quoted evaluateJavascript result into a readable line. */
fun prettyViewport(raw: String?): String {
    if (raw.isNullOrBlank() || raw == "null") return "-"
    return try {
        // evaluateJavascript wraps the result in quotes; strip and unescape them.
        val unquoted = raw.trim().removeSurrounding("\"").replace("\\\"", "\"")
        val o = JSONObject(unquoted)
        "${o.optInt("w")}×${o.optInt("h")} @${o.optDouble("dpr")}x"
    } catch (e: Exception) {
        raw
    }
}
