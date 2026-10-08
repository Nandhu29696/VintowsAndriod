package com.vintows.app.core.push

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.vintows.app.BuildConfig
import com.vintows.app.core.network.ApiCaller
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.core.session.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** What the rest of the app needs from push. Small so it is easy to fake in tests. */
interface PushRegistrar {
    /** True when this build has a Firebase config (google-services.json). */
    val isAvailable: Boolean

    /** Send this device's FCM token to the backend for the logged-in user. */
    suspend fun registerDevice()

    /** Remove this device's token from the backend. Call while the session is still valid. */
    suspend fun unregisterDevice()
}

/**
 * FCM token lifecycle. Push stays off until the client provides google-services.json:
 * without it the Firebase plugin is not applied, FirebaseApp is never initialised and
 * every call here is a no-op.
 */
@Singleton
class PushManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: PushApi,
    private val apiCaller: ApiCaller,
    private val sessionManager: SessionManager,
) : PushRegistrar {

    override val isAvailable: Boolean
        get() = BuildConfig.PUSH_ENABLED && FirebaseApp.getApps(context).isNotEmpty()

    override suspend fun registerDevice() {
        if (!isAvailable || sessionManager.current() == null) return
        val token = currentToken() ?: return
        sendToken(token)
    }

    /** Called by [VintowsMessagingService] when FCM rotates the token. */
    suspend fun onNewToken(token: String) {
        if (sessionManager.current() != null) sendToken(token)
    }

    override suspend fun unregisterDevice() {
        if (!isAvailable || sessionManager.current() == null) return
        val token = currentToken() ?: return
        val result = apiCaller.callUnit { api.unregister(PushTokenRequest(token)) }
        if (result is NetworkResult.Error) Log.w(TAG, "Unregister token failed: ${result.message}")
        // A fresh token is issued on next login, so the old one can't reach this device again.
        @Suppress("DEPRECATION")
        FirebaseMessaging.getInstance().deleteToken().awaitOrNull()
    }

    private suspend fun sendToken(token: String) {
        val result = apiCaller.callUnit { api.register(PushTokenRequest(token, PushTokenRequest.PLATFORM_ANDROID)) }
        if (result is NetworkResult.Error) Log.w(TAG, "Register token failed: ${result.message}")
    }

    // FCM 26 deprecates getToken()/deleteToken() in favour of register()/unregister() with
    // FirebaseMessagingService.onRegistered(). We keep the long-standing token API until push can be
    // verified on a device with the client's google-services.json; onRegistered is handled as well.
    @Suppress("DEPRECATION")
    private suspend fun currentToken(): String? = FirebaseMessaging.getInstance().token.awaitOrNull()

    private companion object {
        const val TAG = "PushManager"
    }
}

/** Awaits a Play Services task without pulling in kotlinx-coroutines-play-services. */
private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task -> cont.resume(if (task.isSuccessful) task.result else null) }
}
