# DevTools Browser

An Android developer-tools browser inspired by bug-bounty workflows — built for
security researchers and developers who don't have a laptop or PC at hand.
Browse targets, inspect traffic, execute JavaScript, and capture evidence,
all from your phone.

## Features

- **Multi-tab WebView browser** — tabs, back/forward/reload, URL/search bar,
  file chooser, popup handling, dark mode, SSL warning flow
- **Console (App tab)** — page console messages and errors, per tab
- **Network inspector (Net tab)** — captures fetch/XHR plus native requests,
  filter by URL and type (Doc/XHR/JS/CSS/Img/Media/Font/Other), view headers
  and bodies, copy any request as cURL
- **JS executor (Snip tab)** — run arbitrary JavaScript in the page context,
  see results/errors, browse execution history
- **HAR 1.2 export** — dump the active tab's traffic to a `.har` file
  (Downloads), ready for external analysis tools
- **Viewport screenshot** — one-tap PNG capture to `Pictures/DevToolsBrowser`
- **User-agent & viewport switcher** — Android / iPhone / Desktop profiles
- **Eruda integration** — toggle the mobile devtools console overlay
- **Perf & Device tabs** — navigation timing, viewport metrics, device/WebView
  info, remote-debugging hint (`chrome://inspect`)
- **Session persistence** — tabs, active tab, UA profile, dark mode, and the
  SSL host allowlist survive app restarts (DataStore)
- **Localization** — English + Indonesian

## Tech stack

- Kotlin, Jetpack Compose (Material3)
- Android WebView with injected JS hooks (fetch/XHR wrappers, console capture)
- DataStore Preferences for session persistence
- kotlinx.serialization for HAR 1.2 output
- JUnit4 + Robolectric + kotlinx-coroutines-test for unit tests

## Building

Requirements: JDK 17, Android SDK (platform 34, build-tools 34.0.0).

```bash
./gradlew assembleDebug        # APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests
```

GitHub Actions (`.github/workflows/android.yml`) builds and tests every push
to `main` and uploads the debug APK as an artifact.

## Project structure

```
app/src/main/java/id/devtools/browser/
├── MainActivity.kt            # entry point, session restore wiring
├── browser/                   # BrowserViewModel, WebView clients/manager
├── data/                      # Tab model, SessionSnapshot, SessionStore
├── devtools/                  # DevToolsViewModel (console, UA, toggles)
├── har/                       # HAR 1.2 builder + MediaStore export
├── jsexec/                    # JS executor, result parsing, history
├── media/                     # screenshot (PixelCopy) via MediaStore
└── network/                   # capture buffer, cURL builder, JS bridge
```

## License

MIT — see [LICENSE](LICENSE).
