package id.devtools.browser.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * RED (stress): concurrent hammering of the capture buffer must never
 * corrupt state, exceed the cap, or duplicate ids.
 */
class NetworkCaptureStressTest {

    @Test
    fun `concurrent records from 8 threads respect cap and id uniqueness`() {
        val capture = NetworkCapture()
        val threads = 8
        val perThread = 250
        val pool = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)
        repeat(threads) { t ->
            pool.execute {
                try {
                    repeat(perThread) { i ->
                        capture.recordNative(
                            tabId = "t$t",
                            url = "https://example.com/$t/$i",
                            method = "GET",
                        )
                    }
                } finally {
                    latch.countDown()
                }
            }
        }
        assertTrue(latch.await(30, TimeUnit.SECONDS))
        pool.shutdown()

        val entries = capture.entries.value
        assertEquals(NetworkCapture.MAX_ENTRIES, entries.size)
        val ids = entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `interleaved record and clear never corrupts`() {
        val capture = NetworkCapture()
        runBlocking(Dispatchers.Default) {
            val jobs = List(4) {
                launch {
                    repeat(200) { i ->
                        capture.recordNative(tabId = "t", url = "https://e.com/$i", method = "GET")
                        if (i % 50 == 0) capture.clear()
                    }
                }
            }
            jobs.forEach { it.join() }
        }
        val entries = capture.entries.value
        assertTrue(entries.size <= NetworkCapture.MAX_ENTRIES)
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
    }
}
