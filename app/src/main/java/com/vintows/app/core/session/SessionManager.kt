package com.vintows.app.core.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vintows.app.core.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the logged-in user.
 *
 * Tokens are encrypted with [TokenCipher]; the remaining fields are plain DataStore
 * preferences. The session is kept in memory as well so the OkHttp interceptor can
 * read it synchronously. Call [restore] once at startup (splash) before any API call.
 */
@Singleton
class SessionManager @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val cipher: TokenCipher,
    @param:ApplicationScope private val appScope: CoroutineScope,
    private val clock: Clock,
) : SessionProvider {

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    override fun current(): Session? = _session.value

    override fun onUnauthorized() {
        if (_session.value == null) return
        appScope.launch {
            clear()
            _events.emit(SessionEvent.Expired)
        }
    }

    /** Loads the stored session. Returns null (and wipes storage) if it is missing, unreadable or expired. */
    suspend fun restore(): Session? {
        val prefs = dataStore.data.first()
        val token = prefs[Keys.ACCESS_TOKEN]?.let(cipher::decrypt)
        val userId = prefs[Keys.USER_ID]
        val email = prefs[Keys.EMAIL]

        if (token == null || userId == null || email == null) {
            if (prefs.asMap().isNotEmpty()) clear()
            return null
        }

        val session = Session(
            accessToken = token,
            refreshToken = prefs[Keys.REFRESH_TOKEN]?.let(cipher::decrypt),
            userId = userId,
            email = email,
            roleId = prefs[Keys.ROLE_ID],
            role = prefs[Keys.ROLE],
            scope = prefs[Keys.SCOPE],
            tenantId = prefs[Keys.TENANT_ID],
            dbName = prefs[Keys.DB_NAME],
            expiresAtEpochSeconds = prefs[Keys.EXPIRES_AT],
        )
        if (session.isExpired(clock.nowEpochSeconds())) {
            clear()
            return null
        }
        _session.value = session
        return session
    }

    suspend fun save(session: Session) {
        dataStore.edit { prefs ->
            prefs.clear()
            prefs[Keys.ACCESS_TOKEN] = cipher.encrypt(session.accessToken)
            session.refreshToken?.let { prefs[Keys.REFRESH_TOKEN] = cipher.encrypt(it) }
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.EMAIL] = session.email
            session.roleId?.let { prefs[Keys.ROLE_ID] = it }
            session.role?.let { prefs[Keys.ROLE] = it }
            session.scope?.let { prefs[Keys.SCOPE] = it }
            session.tenantId?.let { prefs[Keys.TENANT_ID] = it }
            session.dbName?.let { prefs[Keys.DB_NAME] = it }
            session.expiresAtEpochSeconds?.let { prefs[Keys.EXPIRES_AT] = it }
        }
        _session.value = session
    }

    suspend fun clear() {
        _session.value = null
        dataStore.edit { it.clear() }
    }

    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = intPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
        val ROLE_ID = intPreferencesKey("role_id")
        val ROLE = stringPreferencesKey("role")
        val SCOPE = stringPreferencesKey("scope")
        val TENANT_ID = stringPreferencesKey("tenant_id")
        val DB_NAME = stringPreferencesKey("db_name")
        val EXPIRES_AT = longPreferencesKey("expires_at")
    }
}

/** Injectable time source so expiry logic is testable. */
fun interface Clock {
    fun nowEpochSeconds(): Long
}
