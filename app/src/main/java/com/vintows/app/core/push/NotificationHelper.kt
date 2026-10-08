package com.vintows.app.core.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vintows.app.MainActivity
import com.vintows.app.R

/** Shows incoming pushes as system notifications. Tapping one opens the Notifications screen. */
object NotificationHelper {
    const val CHANNEL_ID = "vintows_general"
    const val EXTRA_OPEN = "com.vintows.app.OPEN"
    const val OPEN_NOTIFICATIONS = "notifications"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "General", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Course updates, test reminders and announcements"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(context: Context, id: Int, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, OPEN_NOTIFICATIONS)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
