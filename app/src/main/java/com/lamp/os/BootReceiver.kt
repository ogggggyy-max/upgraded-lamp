package com.lamp.os

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lamp.os.data.Prefs
import com.lamp.os.gps.MockLocationService

/** Restarts fake GPS after a reboot or an app update if it was on before. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.gpsRunning) MockLocationService.start(context)
    }
}
