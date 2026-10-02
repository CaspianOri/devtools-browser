package id.devtools.browser.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the v1.0.1 screenshot bug: the capture was launched in
 * the DevTools sheet's composition scope (`rememberCoroutineScope`).
 * Dismissing the sheet cancelled that scope, so the `delay(350)` never
 * completed and the PixelCopy capture silently never ran — no toast, no file.
 *
 * Contract: the delayed capture must run in a scope that outlives the sheet
 * (the Activity's lifecycleScope).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScreenshotScopeTest {

    @Test
    fun `dismissed sheet scope loses the delayed capture`() = runTest {
        var captureRan = false
        // A sheet-like child scope, cancelled the way dismissing the sheet
        // cancels rememberCoroutineScope().
        val sheetScope = CoroutineScope(coroutineContext + SupervisorJob())

        val job = sheetScope.async {
            delay(350)
            captureRan = true
        }
        sheetScope.cancel(CancellationException("sheet dismissed"))
        runCatching { job.await() }

        assertFalse("capture launched in the sheet scope must not run", captureRan)
    }

    @Test
    fun `activity scope survives sheet dismissal and runs the capture`() = runTest {
        var captureRan = false

        // Fixed shape: the capture lives in the activity scope (here, the
        // test scope itself); the sheet's own scope is cancelled independently.
        val sheetScope = CoroutineScope(coroutineContext + SupervisorJob())
        val job = async {
            delay(350)
            captureRan = true
        }
        sheetScope.cancel(CancellationException("sheet dismissed"))

        advanceTimeBy(400)
        job.await()

        assertTrue("activity-scoped capture must run", captureRan)
    }
}
