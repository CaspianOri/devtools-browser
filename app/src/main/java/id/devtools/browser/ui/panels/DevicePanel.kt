package id.devtools.browser.ui.panels

import android.os.Build
import android.util.DisplayMetrics
import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import id.devtools.browser.R

/**
 * Device tab: device and WebView info (reference screenshot replica).
 */
@Composable
fun DevicePanel() {
    val context = LocalContext.current
    val labelModel = stringResource(R.string.device_model)
    val labelAndroid = stringResource(R.string.device_android)
    val labelWebView = stringResource(R.string.device_webview)
    val labelScreen = stringResource(R.string.device_screen)
    val info = remember(labelModel, labelAndroid, labelWebView, labelScreen) {
        val metrics: DisplayMetrics = context.resources.displayMetrics
        val webViewPackage = try {
            WebView.getCurrentWebViewPackage()?.let { "${it.packageName} ${it.versionName}" } ?: "-"
        } catch (e: Exception) {
            "-"
        }
        listOf(
            labelModel to "${Build.MANUFACTURER} ${Build.MODEL}",
            labelAndroid to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            labelWebView to webViewPackage,
            labelScreen to "${metrics.widthPixels}×${metrics.heightPixels} @${metrics.density}x",
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            stringResource(R.string.device_info_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        info.forEach { (label, value) ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "💡 ${stringResource(R.string.remote_debug_hint)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}
