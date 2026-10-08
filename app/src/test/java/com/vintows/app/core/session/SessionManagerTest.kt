package com.vintows.app.core.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.vintows.app.testing.FakeTokenCipher
import com.vintows.app.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SessionManagerTest {

    private var now = 1_000L
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var manager: SessionManager

    private val session = Session(
        accessToken = "access",
        refreshToken = "refresh",
        userId = 3,
        email = "admin@example.com",
        roleId = 1,
        role = "SuperAdmin",
        scope = "admin",
        tenantId = "7",
        dbName = "trainer_db",
        expiresAtEpochSeconds = 2_000,
    )

    private fun newManager() = SessionManager(
        dataStore = dataStore,
        cipher = FakeTokenCipher(),
        appScope = CoroutineScope(Dispatchers.Unconfined),
        clock = Clock { now },
    )

    @Before
    fun setUp() {
        dataStore = InMemoryPreferencesDataStore()
        manager = newManager()
    }

    @Test
    fun `save then restore round-trips every field`() = runTest {
        manager.save(session)

        // A new manager over the same file = cold start after process death.
        assertEquals(session, newManager().restore())
    }

    @Test
    fun `expired session is wiped on restore`() = runTest {
        manager.save(session)
        now = 2_000

        assertNull(manager.restore())
        assertNull(manager.current())
    }

    @Test
    fun `nothing stored restores to null`() = runTest {
        assertNull(manager.restore())
    }

    @Test
    fun `onUnauthorized clears the session and emits Expired`() = runTest {
        manager.save(session)

        manager.events.test {
            manager.onUnauthorized()
            assertEquals(SessionEvent.Expired, awaitItem())
        }
        assertNull(manager.current())
        assertNull(manager.restore())
    }

    @Test
    fun `onUnauthorized without a session does nothing`() = runTest {
        manager.events.test {
            manager.onUnauthorized()
            expectNoEvents()
        }
    }
}
