package com.vintows.app.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.Preferences
import com.vintows.app.core.push.PushRegistrar
import com.vintows.app.core.session.TokenCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.Base64

/** Replaces Dispatchers.Main so viewModelScope works in JVM tests. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** Records push calls; push is "available" so register/unregister paths run. */
class FakePushRegistrar : PushRegistrar {
    var registered = 0
    var unregistered = 0
    override val isAvailable = true
    override suspend fun registerDevice() { registered++ }
    override suspend fun unregisterDevice() { unregistered++ }
}

/** Reversible stand-in for the Android Keystore cipher. */
class FakeTokenCipher : TokenCipher {
    override fun encrypt(plain: String) = "enc:" + plain.reversed()
    override fun decrypt(encoded: String) = encoded.removePrefix("enc:").reversed().takeIf { encoded.startsWith("enc:") }
}

/**
 * In-memory DataStore for JVM tests. The file-backed one can't be used here: on Windows
 * its temp-file rename fails on the second write ("Unable to rename …"). On Android it is fine.
 */
class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock { transform(state.value).also { state.value = it } }
}

val testJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}

/** Unsigned JWT with the given payload; the app only reads the payload. */
fun fakeJwt(payload: String): String {
    val enc = Base64.getUrlEncoder().withoutPadding()
    return enc.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray()) + "." +
        enc.encodeToString(payload.toByteArray()) + ".sig"
}

fun resource(name: String): String =
    requireNotNull(object {}.javaClass.classLoader?.getResource(name)) { "Missing test resource $name" }.readText()
