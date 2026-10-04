package com.Siren.Siren.data

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.resqlink.emergency.SirenApplication
import com.resqlink.emergency.ui.SosAlertActivity

/**
 * When app is closed / backgrounded, FCM still arrives here.
 * We show a high-priority notification AND launch full-screen SOS activity
 * (with siren) so the user is alerted even if the app was killed.
 */
class ResQMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Token is saved on next login / registerOrUpdateProfile
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: "🚨 SOS ALERT"
        val body = data["body"] ?: message.notification?.body ?: "Someone needs help – open Siren"
        val lat = data["lat"]?.toDoubleOrNull() ?: 0.0
        val lng = data["lng"]?.toDoubleOrNull() ?: 0.0
        val fromPhone = data["fromPhone"] ?: ""
        val fromUid = data["fromUid"] ?: ""

        // 1) High-priority system notification (works when app is closed)
        showHeadsUpNotification(title, body, lat, lng, fromPhone, fromUid)

        // 2) Try to open full-screen SOS activity with siren
        val alert = Intent(this, SosAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("lat", lat)
            putExtra("lng", lng)
            putExtra("fromPhone", fromPhone)
            putExtra("fromUid", fromUid)
            putExtra("playSiren", true)
        }
        try {
            startActivity(alert)
        } catch (_: Exception) {
            // On some OEMs background activity start is blocked; notification still shows
        }
    }

    private fun showHeadsUpNotification(
        title: String,
        body: String,
        lat: Double,
        lng: Double,
        fromPhone: String,
        fromUid: String
    ) {
        val fullScreen = Intent(this, SosAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("lat", lat)
            putExtra("lng", lng)
            putExtra("fromPhone", fromPhone)
            putExtra("fromUid", fromUid)
            putExtra("playSiren", true)
        }
        val fullScreenPi = PendingIntent.getActivity(
            this, 2001, fullScreen,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val builder = NotificationCompat.Builder(this, SirenApplication.SOS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(fullScreenPi)
            .setFullScreenIntent(fullScreenPi, true)
            .setSound(sound)
            .setVibrate(longArrayOf(0, 800, 300, 800, 300, 800))
            .setOngoing(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(SirenApplication.SOS_CHANNEL_ID)
        }

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(SOS_NOTIFICATION_ID, builder.build())
    }

    companion object {
        const val SOS_NOTIFICATION_ID = 9001
    }
}
