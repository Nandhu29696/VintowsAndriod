package com.vintows.app.live

import com.vintows.app.BuildConfig
import com.vintows.app.core.network.ApiCaller
import com.vintows.app.core.network.AuthInterceptor
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.core.rbac.MenuApi
import com.vintows.app.core.rbac.MenuRepository
import com.vintows.app.core.rbac.homeTabs
import com.vintows.app.core.session.Clock
import com.vintows.app.core.session.JwtDecoder
import com.vintows.app.core.session.SessionManager
import com.vintows.app.feature.auth.data.AuthApi
import com.vintows.app.feature.auth.data.AuthRepositoryImpl
import com.vintows.app.testing.FakePushRegistrar
import com.vintows.app.testing.FakeTokenCipher
import com.vintows.app.testing.InMemoryPreferencesDataStore
import com.vintows.app.testing.testJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

/**
 * Opt-in end-to-end check against the real backend of the build flavour.
 * Skipped unless credentials are supplied, so it never runs in normal builds/CI:
 *
 *   VINTOWS_TEST_EMAIL=… VINTOWS_TEST_PASSWORD=… ./gradlew testQaDebugUnitTest --tests "*LiveQaSmokeTest*"
 *
 * Read-only apart from the login itself: POST auth/login, GET roleaccess/get.
 */
class LiveQaSmokeTest {

    @Test
    fun `login and load menus against the real API`() = runBlocking {
        val email = System.getenv("VINTOWS_TEST_EMAIL")
        val password = System.getenv("VINTOWS_TEST_PASSWORD")
        assumeTrue("Live test skipped: set VINTOWS_TEST_EMAIL / VINTOWS_TEST_PASSWORD", !email.isNullOrBlank() && !password.isNullOrBlank())

        val sessionManager = SessionManager(
            dataStore = InMemoryPreferencesDataStore(),
            cipher = FakeTokenCipher(),
            appScope = CoroutineScope(Dispatchers.Unconfined),
            clock = Clock { System.currentTimeMillis() / 1000 },
        )
        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(OkHttpClient.Builder().addInterceptor(AuthInterceptor(sessionManager)).build())
            .addConverterFactory(testJson.asConverterFactory("application/json".toMediaType()))
            .build()
        val apiCaller = ApiCaller(testJson)
        val menuRepository = MenuRepository(retrofit.create<MenuApi>(), apiCaller)
        val auth = AuthRepositoryImpl(
            retrofit.create<AuthApi>(), apiCaller, JwtDecoder(testJson), sessionManager, menuRepository,
            push = FakePushRegistrar(), appScope = CoroutineScope(Dispatchers.Unconfined),
        )

        val login = auth.login(email!!, password!!)
        assertTrue("Login failed: $login", login is NetworkResult.Success)
        val session = (login as NetworkResult.Success).data
        println("LIVE ${BuildConfig.BASE_URL} → userId=${session.userId} role=${session.role} roleId=${session.roleId} scope=${session.scope} tenant=${session.tenantId} tabs=${session.homeTabs()}")

        val menus = menuRepository.menus(requireNotNull(session.roleId))
        assertTrue("Menus failed: $menus", menus is NetworkResult.Success)
        (menus as NetworkResult.Success).data.forEach { println("LIVE menu: ${it.title} (${it.children.size} children) route=${it.webRoute}") }

        auth.logout()
    }
}
