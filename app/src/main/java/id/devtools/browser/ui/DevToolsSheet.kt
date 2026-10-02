package id.devtools.browser.ui

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import id.devtools.browser.R
import id.devtools.browser.browser.BrowserViewModel
import id.devtools.browser.devtools.DevToolsViewModel
import id.devtools.browser.ui.panels.AppPanel
import id.devtools.browser.ui.panels.ConsolePanel
import id.devtools.browser.ui.panels.DevicePanel
import id.devtools.browser.ui.panels.NetworkPanel
import id.devtools.browser.ui.panels.PerfPanel
import id.devtools.browser.ui.panels.SnipPanel

/**
 * DevTools bottom sheet with the tabs from the reference screenshot plus the
 * M2 network inspector: App / Perf / Device / Snip / Net. The console viewer
 * lives inside the App tab flow (its own section below the toggles).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevToolsSheet(
    browserViewModel: BrowserViewModel,
    devToolsViewModel: DevToolsViewModel,
    activeWebView: () -> WebView?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }
    val titles = listOf(
        stringResource(R.string.tab_app),
        stringResource(R.string.tab_perf),
        stringResource(R.string.tab_device),
        stringResource(R.string.tab_snip),
        stringResource(R.string.tab_net),
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            TabRow(selectedTabIndex = selectedTab) {
                titles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }
            when (selectedTab) {
                0 -> AppPanel(devToolsViewModel, browserViewModel, activeWebView)
                1 -> PerfPanel(devToolsViewModel, activeWebView)
                2 -> DevicePanel()
                3 -> SnipPanel()
                4 -> NetworkPanel(devToolsViewModel, browserViewModel)
            }
            // Console viewer is always one swipe away in M1: shown under App.
            if (selectedTab == 0) {
                ConsolePanel(devToolsViewModel, browserViewModel)
            }
        }
    }
}
