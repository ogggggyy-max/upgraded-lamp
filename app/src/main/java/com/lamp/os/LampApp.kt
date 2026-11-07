package com.lamp.os

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.lamp.os.data.Apps
import com.lamp.os.data.Prefs

class LampApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Apps.init(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_GPS, "Fake GPS", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        const val CHANNEL_GPS = "gps"
    }
}
