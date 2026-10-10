package com.vintows.app.feature.assessments.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vintows.app.core.session.SessionProvider
import com.vintows.app.feature.assessments.domain.SavedAttempt
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** The DataStore for running attempts. Separate from the session store, which is wiped on every login. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AttemptsDataStore

/**
 * Remembers a running attempt (id, start time, answers, flags) per user and test, so a learner whose app
 * was killed can resume instead of losing an attempt. The web has no student "resume" endpoint, so this
 * is kept on the device.
 */
@Singleton
class AttemptStore @Inject constructor(
    @param:AttemptsDataStore private val dataStore: DataStore<Preferences>,
    private val sessions: SessionProvider,
    private val json: Json,
) {
    suspend fun get(assessmentId: String): SavedAttempt? {
        val raw = dataStore.data.first()[key(assessmentId) ?: return null] ?: return null
        return try {
            json.decodeFromString<SavedAttempt>(raw)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    suspend fun save(assessmentId: String, attempt: SavedAttempt) {
        val key = key(assessmentId) ?: return
        dataStore.edit { it[key] = json.encodeToString(SavedAttempt.serializer(), attempt) }
    }

    suspend fun clear(assessmentId: String) {
        val key = key(assessmentId) ?: return
        dataStore.edit { it.remove(key) }
    }

    private fun key(assessmentId: String): Preferences.Key<String>? {
        val user = sessions.current()?.userId ?: return null
        return stringPreferencesKey("attempt_${user}_$assessmentId")
    }
}
