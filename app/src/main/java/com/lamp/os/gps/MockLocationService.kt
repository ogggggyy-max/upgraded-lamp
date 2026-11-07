package com.lamp.os.gps

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.lamp.os.LampApp
import com.lamp.os.R
import com.lamp.os.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.random.Random

/**
 * Pushes the chosen location into the GPS, network and fused providers once a
 * second, around the clock. Lamp must be picked under Developer options >
 * "Select mock location app" for Android to accept it.
 */
class MockLocationService : Service() {

    companion object {
        private const val ACTION_STOP = "com.lamp.os.STOP_GPS"
        private const val NOTIF_ID = 42
        const val MOCK_APP_HINT =
            "Pick Lamp in Developer options → Select mock location app, then start again."

        val running = MutableStateFlow(false)
        val error = MutableStateFlow<String?>(null)

        fun start(ctx: Context) {
            Prefs.gpsRunning = true
            error.value = null
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, MockLocationService::class.java))
            } catch (e: Exception) {
                error.value = "Couldn't start fake GPS: ${e.message}"
            }
        }

        fun stop(ctx: Context) {
            Prefs.gpsRunning = false
            ctx.stopService(Intent(ctx, MockLocationService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val added = mutableListOf<String>()
    private lateinit var lm: LocationManager

    private val ticker = object : Runnable {
        override fun run() {
            push()
            if (running.value) handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lm = getSystemService(LocationManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs.gpsRunning = false
            stopSelf()
            return START_NOT_STICKY
        }
        goForeground()
        if (added.isEmpty() && !setupProviders()) {
            running.value = false
            stopSelf()
            return START_NOT_STICKY
        }
        running.value = true
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        return START_STICKY
    }

    private fun goForeground() {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, FakeGpsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MockLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n: Notification = NotificationCompat.Builder(this, LampApp.CHANNEL_GPS)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Fake GPS is on")
            .setContentText("%.5f, %.5f".format(Prefs.gpsLat, Prefs.gpsLon))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    @Suppress("DEPRECATION")
    private fun setupProviders(): Boolean {
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, "fused")) {
            try {
                try { lm.removeTestProvider(p) } catch (_: Exception) {}
                // powerRequirement = 1 (low), accuracy = 1 (fine)
                lm.addTestProvider(p, false, false, false, false, true, true, true, 1, 1)
                lm.setTestProviderEnabled(p, true)
                added += p
            } catch (e: SecurityException) {
                error.value = MOCK_APP_HINT
                return false
            } catch (_: Exception) {
                // This provider isn't supported on this device; skip it.
            }
        }
        if (added.isEmpty()) error.value = "This device didn't accept any mock location provider."
        return added.isNotEmpty()
    }

    private fun push() {
        var lat = Prefs.gpsLat
        var lon = Prefs.gpsLon
        if (Prefs.gpsJitter) {
            lat += Random.nextDouble(-0.00002, 0.00002)
            lon += Random.nextDouble(-0.00002, 0.00002)
        }
        for (p in added) {
            val loc = Location(p).apply {
                latitude = lat
                longitude = lon
                altitude = 35.0
                accuracy = 3f
                speed = 0f
                bearing = 0f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                verticalAccuracyMeters = 3f
                speedAccuracyMetersPerSecond = 0.1f
                bearingAccuracyDegrees = 1f
            }
            try {
                lm.setTestProviderLocation(p, loc)
            } catch (e: SecurityException) {
                error.value = MOCK_APP_HINT
                running.value = false
                stopSelf()
                return
            } catch (_: Exception) {
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        for (p in added) {
            try { lm.setTestProviderEnabled(p, false) } catch (_: Exception) {}
            try { lm.removeTestProvider(p) } catch (_: Exception) {}
        }
        added.clear()
        running.value = false
        super.onDestroy()
    }
}
