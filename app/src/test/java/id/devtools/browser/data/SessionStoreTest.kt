package id.devtools.browser.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * RED: SessionStore does not exist yet. DataStore round trip on a real
 * (Robolectric) context.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionStoreTest {

    private lateinit var store: SessionStore

    @Before
    fun setup() {
        store = SessionStore(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `save then snapshot round trips`() = runTest {
        val snapshot = SessionSnapshot(
            tabUrls = listOf("https://a.com", "https://b.com/x?q=1"),
            activeTabIndex = 1,
            userAgentName = "IPHONE",
            darkMode = true,
            sslWhitelist = setOf("lab.local"),
        )
        store.save(snapshot)
        assertEquals(snapshot, store.snapshot.first())
    }

    @Test
    fun `later save overwrites earlier`() = runTest {
        store.save(SessionSnapshot(tabUrls = listOf("https://old.com")))
        store.save(SessionSnapshot(tabUrls = listOf("https://new.com"), darkMode = true))
        val loaded = store.snapshot.first()
        assertEquals(listOf("https://new.com"), loaded.tabUrls)
        assertEquals(true, loaded.darkMode)
    }
}
