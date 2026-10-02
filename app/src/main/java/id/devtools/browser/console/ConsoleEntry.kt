package id.devtools.browser.console

/**
 * One console message captured from a tab.
 */
data class ConsoleEntry(
    val id: Long,
    val tabId: String,
    val level: ConsoleLevel,
    val message: String,
    val sourceId: String? = null,
    val lineNumber: Int? = null,
    val timestampMs: Long = System.currentTimeMillis(),
)

enum class ConsoleLevel {
    LOG, INFO, WARN, ERROR, DEBUG;

    companion object {
        fun fromMessageLevel(level: Int): ConsoleLevel = when (level) {
            // android.webkit.ConsoleMessage.MessageLevel
            0 -> LOG      // TIP
            1 -> LOG      // LOG
            2 -> WARN     // WARNING
            3 -> ERROR    // ERROR
            4 -> DEBUG    // DEBUG
            else -> LOG
        }
    }
}
