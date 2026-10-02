package com.resqlink.emergency

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.google.firebase.FirebaseApp

class SirenApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        createSosChannel()
    }

    private fun createSosChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel(
                SOS_CHANNEL_ID,
                "SOS Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical SOS from emergency contacts – plays even when app is closed"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 300, 800, 300, 800)
                setSound(sound, audioAttrs)
                setBypassDnd(true)
                setShowBadge(true)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val SOS_CHANNEL_ID = "siren_sos_v2"
    }
}
