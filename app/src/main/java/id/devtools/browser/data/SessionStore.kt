package id.devtools.browser.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "devtools_session")

/**
 * DataStore Preferences persistence for the browser session (M4).
 * A corrupted store can never crash startup: read errors fall back to a
 * default (empty) snapshot.
 */
class SessionStore(private val context: Context) {

    val snapshot: Flow<SessionSnapshot> =
        context.sessionDataStore.data
            .map { it.toSessionSnapshot() }
            .catch { emit(SessionSnapshot()) }

    /** Replaces the stored snapshot wholesale. */
    suspend fun save(snapshot: SessionSnapshot) {
        context.sessionDataStore.updateData { snapshot.toPreferences() }
    }
}
