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
- **WebView flows — automated** via Espresso-Web (`androidx.test.espresso.web`):
  instrumented tests that load real pages, find/click/type web elements, and
  assert on content. Runs on emulator or a real device via adb.
- **E2E — automated** as orchestrated instrumented scenarios (Android Test
  Orchestrator): launch → open URL → open DevTools → toggle Eruda →
  capture console entry → assert. Same device requirement as above.
- **Compose UI regression — automated on JVM** via Paparazzi screenshot
  tests (no device needed): BrowserScreen, DevToolsSheet panels.
- Manual checklist remains only for what automation cannot cover: real
  device quirks (rotate physics, OEM WebView versions), `chrome://inspect`
  from a desktop, and store listing screenshots.
- Honest constraint: this VM cannot run an Android emulator (no KVM, 2
  CPUs). Instrumented tests are written CI/device-ready; execution happens
  on the user's phone via adb or on his PC emulator. Paparazzi + unit tests
  run on the VM.
- Definition of done per milestone: builds clean, `./gradlew test`
  green, Paparazzi snapshots approved, instrumented suite written
  (executed on device before release), no crashes in logcat during a
  10-minute session.

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

## 13. Network capture pipeline (P0)

Two lanes, one ring buffer:

- **Lane A (native)** — `InterceptingWebViewClient.shouldInterceptRequest`
  (runs on a background thread): records skeleton entries
  (`type=RESOURCE`) with method, URL, request headers, start timestamp.
  Response status/size are NOT observable here — displayed as unknown.
  Returns null (WebView handles the load normally).
- **Lane B (JS)** — injected script wraps `window.fetch` and
  `XMLHttpRequest`: captures full entries (`type=API`) with method, URL,
  request headers/body, status, response headers/body, and timing. Emits
  start/end events to the app via `@JavascriptInterface`
  (`onFetchStart/onFetchEnd`, `onXhrStart/onXhrEnd`); bodies are truncated
  to 256 KB before crossing the bridge. Callbacks arrive on a background
  thread.
- **No refetch**: Lane A never re-executes requests. Double-fetching would
  duplicate POSTs against bounty targets and corrupt state; API bodies are
  covered by Lane B.
- **Dedup**: a Lane B entry supersedes the Lane A skeleton with the same
  method+URL within a 5 s window; the skeleton is dropped.
- **Threading**: single `NetworkLog` ring buffer (mutex-protected),
  cap 500 entries, oldest dropped silently.
- **Body policy**: capture only text-ish content types
  (`json`, `text/*`, `xml`, `x-www-form-urlencoded`, empty); truncate bodies
  at 256 KB with a `[truncated]` marker.
- Display order: `startedAtMs` ascending.

## 14. Error handling matrix (P0)

| Failure | UX | Recovery |
|---------|----|----------|
| SSL error (`onReceivedSslError`) | Dialog with primary error detail | [Lanjutkan sekali] / [Selalu untuk host ini] (in-memory allowlist) / [Batal] |
| Offline / DNS / timeout, main frame (`onReceivedError`) | Full-page error view | [Coba lagi] |
| Sub-frame load error | Ignored (logged only) | — |
| HTTP 4xx/5xx main frame (`onReceivedHttpError`) | Banner with status code | [Muat ulang] |
| Render process gone (`onRenderProcessGone`) | "Tab crash" placeholder | [Muat ulang tab] recreates WebView |
| File chooser cancelled | No-op | — |
| JS executor throws | Error text in output panel | — |
| Eruda asset missing / inject fail | Toast "Eruda gagal dimuat" | Toggle stays off |
| PixelCopy screenshot fail | Toast + log | — |
| HAR export I/O fail | Snackbar with reason | Retry button |
| Log buffer full | Drop oldest silently | — |
| WebView not installed / init fail | Blocking dialog | Directs to Play Store (Android System WebView) |

## 15. HAR export contract (P1)

HAR 1.2. `log.creator` = `{name: "DevTools Browser", version: <app>}`.
`log.pages`: one per tab (`id`, `title`, `startedDateTime`).
`log.entries[]` mapping:
- `startedDateTime` ISO-8601, `time` = measured duration ms.
- `request`: `method`, `url`, `httpVersion: ""` (unknown), `headers[]`,
  `queryString[]` (parsed), `headersSize: -1`, `bodySize`, `postData`
  (`{mimeType, text}` when a body was captured).
- `response`: `status` (0 when unknown — Lane A skeletons), `statusText`,
  `headers[]`, `content: {size, mimeType, text?}`, `redirectURL: ""`,
  `headersSize/bodySize: -1` when unknown.
- `timings`: total duration in `wait`; `send`/`receive` = -1.
Single `.har` file exported to Downloads via MediaStore.

## 16. Persistence decision (P1): DataStore, not Room

M4 needs only: open tab URL list, active tab index, default UA choice.
**DataStore Preferences** wins — no relations, no queries; Room would add
KSP + compiler weight for a string list. Keys: `open_tabs` (JSON array),
`active_tab` (int), `default_user_agent` (string). Dependency:
`androidx.datastore:datastore-preferences`.

## 17. Testing checklist (P2)

- **M1**: install; open facebook.com (matches reference screenshot); add
  3 tabs; UA Android→iPhone→Desktop verified via `https://httpbin.org/user-agent`;
  Eruda toggle on/off; dark-mode toggle; `console.log` from JS executor
  appears in console viewer; rotate device — WebView survives; back/forward.
- **M2**: XHR via JS executor to httpbin.org — entry appears with bodies;
  copy as cURL → run in Termux → identical response; heavy page —
  buffer stays at 500, no OOM.
- **M3**: JS executor returns `document.title`; syntax error shows error
  text; screenshot lands in Downloads as `<timestamp>_<host>.png`;
  exported HAR imports into a HAR viewer.
- **M4**: force-stop app → relaunch → tabs restored; 10-minute soak,
  logcat clean.
- **Devices**: Infinix HOT 60 Pro (primary); API 26 emulator (min SDK).

## 18. Copy-as-cURL format (P2)

```
curl -X POST 'https://example.com/api' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer xxx' \
  --data-binary '{"a":1}'
```

Rules: `-X` only for non-GET; URL single-quoted (`'` escaped as `'\''`);
one `-H 'Name: value'` per captured header, excluding `Content-Length`
(curl recalculates); body via `--data-binary` with the same quoting;
bodies over the capture cap get a trailing `# body truncated at 256 KB`
comment; line continuations with `\` for readability.
