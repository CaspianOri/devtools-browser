package id.devtools.browser.browser

/**
 * Built-in user-agent profiles for the UA & viewport switcher.
 */
enum class UserAgentProfile(val label: String, val userAgent: String) {
    ANDROID(
        "Android",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36",
    ),
    IPHONE(
        "iPhone",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 " +
            "Mobile/15E148 Safari/604.1",
    ),
    DESKTOP(
        "Desktop",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
    ),
}
