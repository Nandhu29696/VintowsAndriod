package com.vintows.app.core.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vintows.app.core.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class VintowsMessagingService : FirebaseMessagingService() {

    @Inject lateinit var pushManager: PushManager

    @Inject @field:ApplicationScope lateinit var appScope: CoroutineScope

    @Deprecated("FCM 26 reports tokens via onRegistered; kept for devices/paths that still call this.")
    override fun onNewToken(token: String) {
        appScope.launch { pushManager.onNewToken(token) }
    }

    /** FCM 26+ registration callback (see PushManager for why both are handled). */
    override fun onRegistered(token: String) {
        appScope.launch { pushManager.onNewToken(token) }
    }

    /**
     * Data-only messages and messages received while the app is in the foreground arrive here.
     * Background "notification" messages are shown by the system automatically.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "Vintows"
        val body = message.notification?.body ?: message.data["body"] ?: message.data["message"] ?: return
        NotificationHelper.show(this, (message.messageId ?: System.currentTimeMillis().toString()).hashCode(), title, body)
    }
}
