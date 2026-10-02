# DevTools Browser — Design Spec

Date: 2026-10-02
Status: approved design (user 2026-10-02), incorporates external gap analysis
Project dir: `~/workspace/devtools-browser/`

## 1. Goal

A production-grade Android dev-tools browser for bug bounty work on mobile:
browse targets, inspect traffic, run JS payloads, and capture evidence —
all from the phone. Replicates the "Kiro Browser Dev" UI from the reference
screenshot, plus console log viewer, network inspector, and JS executor.

## 2. Non-goals (backlog)

Request throttling simulation, response mocking, JS breakpoints, cert-pinning
bypass info, QR scan. Full-page screenshot (needs CDP) stays backlog.

## 3. Language policy

- UI strings: Indonesian.
- Code: English only — identifiers, comments, KDoc, commit messages, log tags.

## 4. Features

### 4.1 Browser core (screenshot replica)
- Multi-tab WebView; tab strip with per-tab title; `+` adds tab; CLOSE per tab.
- URL bar with GO; back / forward / reload; horizontal scroll for 20+ tabs.
- "Buka aplikasi" button: fires `ACTION_VIEW` intent for the current URL.
- Per-tab loading progress bar.

### 4.2 DevTools bottom sheet (tabs: App / Perf / Device / Snip)
- **App**: user-agent & viewport switcher (Android / iPhone / Desktop UA
  strings); Toggle Eruda (injects bundled `assets/eruda.js`); force dark
  mode (`WebSettingsCompat.setAlgorithmicDarkeningAllowed`); viewport info
  (innerWidth/innerHeight, DPR, UA).
- **Perf**: page LoadTime via Navigation Timing API; network inspector list.
- **Device**: device info (model, Android release, WebView version, screen
  resolution); remote-debug hint text (`chrome://inspect`, USB).
- **Snip**: screenshot (PixelCopy of WebView viewport → MediaStore Downloads,
  filename `<timestamp>_<host>.png`); JS executor (multiline input, run via
  `evaluateJavascript`, output + errors shown); console log viewer.

### 4.3 Bug bounty extras
- Network inspector detail view per request: method, URL, status, duration,
  size, request/response headers, bodies (where captured).
- **Copy as cURL** per request (proper shell quoting).
- **Export HAR 1.2** of the session log (imports into Burp / HAR viewers).
- Screenshot files double as report evidence.

## 5. Architecture decisions

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | Network inspector = **hybrid**: `WebViewClient.shouldInterceptRequest` (request line, headers, timing, status, size for ALL requests) + injected JS wrapper around `fetch`/`XMLHttpRequest` (request/response bodies for API traffic) | CDP on Android WebView is fragile and heavy. Bounty-relevant traffic is XHR/fetch; static asset bodies are not needed. |
| 2 | Screenshot = **PixelCopy viewport only** | Full-page needs CDP (see #1). Viewport + URL + timestamp is standard bounty evidence. |
| 3 | Persistence = **in-memory for M1–M3**; M4 persists open tab URL list via Room/DataStore and reloads on start | Full WebView state restore is painful and edge-casey; URL-list restore covers real usage. |
| 4 | `usesCleartextTraffic=true` | Bounty targets are often plain HTTP. |
| 5 | SSL errors → **warn dialog** (proceed once / always for host / block) | Bounty labs use self-signed certs; silent bypass is unacceptable, hard block is unusable. |
| 6 | `shouldOverrideUrlLoading`: http/https stay in WebView; `tel:`, `mailto:`, `intent:`, custom schemes → system intent | Matches user expectation, avoids dead ends. |
| 7 | `onShowFileChooser` implemented | Upload forms appear in bounty targets. |
| 8 | Config changes handled via `android:configChanges="orientation\|screenSize\|keyboardHidden"` | Prevents WebView destruction on rotate. |
| 9 | `onRenderProcessGone` → destroy and recreate WebView, reload last URL | Crash recovery without losing the tab. |
| 10 | Console capture = `WebChromeClient.onConsoleMessage` + injected overrides of `console.*`, `window.onerror`, `window.onunhandledrejection`; injected at both `onPageStarted` and `onPageFinished` | `onConsoleMessage` alone misses workers and unhandled rejections; double injection covers SPAs. |
| 11 | Log caps: 500 network entries, 500 console entries; response/request bodies truncated at 256 KB | OOM protection on long sessions. |
| 12 | Eruda bundled in `assets/eruda.js`, injected on toggle | No network dependency. |

## 6. Tech stack

- Kotlin, Jetpack Compose + Material3 (dark theme, purple accent per screenshot).
- Single activity, MVVM: `BrowserViewModel` (tab state), `DevToolsViewModel`
  (console/network entries, JS output).
- `WebView` per tab via `AndroidView`; `WebView.setWebContentsDebuggingEnabled(true)`
  (dev build; release keeps it behind a settings toggle).
- Min SDK 26, target SDK 34, `applicationId` / namespace `id.devtools.browser`.
- Versioning: semantic, starting `0.1.0`.

## 7. Data models

- `Tab(id: String, url: String, title: String, history: List<String>)` —
  the `WebView` reference lives outside the serializable model, owned by the
  composable; restore = recreate WebView and load URL.
- `NetworkEntry(id, tabId, method, url, statusCode, requestHeaders,
  responseHeaders, requestBody?, responseBody?, startedAtMs, durationMs,
  bytesRead)` — bodies nullable (only for XHR/fetch captures).
- `ConsoleEntry(id, tabId, level, message, sourceId, lineNumber, timestamp)`.

## 8. Permissions

`INTERNET` (explicit). Screenshot export: MediaStore on API 29+ needs no
permission; on API 26–28 declare `WRITE_EXTERNAL_STORAGE` with
`maxSdkVersion=28` and request it at runtime only when the user takes a
screenshot. No other runtime permissions in MVP.

## 9. Build & distribution

- Toolchain setup on VM: Temurin JDK 17, Android SDK (platform 34,
  build-tools), Gradle wrapper pinned in repo.
- `./gradlew assembleDebug` → debug-signed APK, sideload-ready.
- Release signing with a dedicated keystore is a follow-up (not M1–M4).
- APK size budget: keep under 25 MB (Eruda ~150 KB is fine).

## 10. Testing

- Unit tests (JUnit + Robolectric where Android classes are involved):
  `CurlExporter`, HAR builder, log-cap logic, ViewModels.
- WebView flows cannot be unit-tested without instrumented tests; covered by
  a manual checklist instead (M4): facebook.com, x.com, instagram.com —
  every button from the screenshot plus new tools, on the user's Infinix
  (API level per device) and an API 26 emulator.
- Definition of done per milestone: builds clean, checklist passes, no
  crashes in logcat during a 10-minute session.

## 11. Milestones

- **M1**: browser MVP — tabs, navigation, URL bar, UA switcher, Eruda toggle,
  dark mode, viewport info, device info, console viewer.
- **M2**: network inspector — hybrid capture, list + detail, copy as cURL.
- **M3**: JS executor, PixelCopy screenshot, HAR 1.2 export.
- **M4**: session persistence (tab URL list), polish, full test checklist,
  release-ready debug APK.

## 12. Open items (none blocking M1)

- WebView debugging toggle in release builds: default off, settings switch.
- i18n beyond Indonesian: not planned.
