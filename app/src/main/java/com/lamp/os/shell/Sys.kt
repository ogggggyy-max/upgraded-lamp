package com.lamp.os.shell

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow

/** System toggles and readings used by the control panel. */
object Sys {

    fun start(ctx: Context, intent: Intent) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(ctx, "Not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    /** Tries each intent until one opens. */
    fun startFirst(ctx: Context, intents: List<Intent?>): Boolean {
        for (i in intents.filterNotNull()) {
            try {
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {
            }
        }
        Toast.makeText(ctx, "Couldn't open it automatically", Toast.LENGTH_SHORT).show()
        return false
    }

    fun wifiOn(ctx: Context): Boolean = try {
        (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
    } catch (_: Exception) { false }

    fun bluetoothOn(ctx: Context): Boolean = try {
        ctx.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
    } catch (_: Exception) { false }

    fun airplaneOn(ctx: Context): Boolean =
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    fun canWriteSettings(ctx: Context) = Settings.System.canWrite(ctx)

    fun writeSettingsIntent(ctx: Context) =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    fun autoRotate(ctx: Context): Boolean =
        Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    fun setAutoRotate(ctx: Context, on: Boolean): Boolean = try {
        Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
    } catch (_: Exception) { false }

    val brightnessMax: Int by lazy {
        val id = Resources.getSystem().getIdentifier("config_screenBrightnessSettingMaximum", "integer", "android")
        val v = if (id > 0) try { Resources.getSystem().getInteger(id) } catch (_: Exception) { 255 } else 255
        if (v > 0) v else 255
    }

    fun brightness(ctx: Context): Int =
        Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightnessMax / 2)

    fun setBrightness(ctx: Context, value: Int) {
        try {
            Settings.System.putInt(
                ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value.coerceIn(1, brightnessMax))
        } catch (_: Exception) {
        }
    }

    fun volume(ctx: Context): Float {
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    fun setVolume(ctx: Context, fraction: Float) {
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        try { am.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction * max).toInt(), 0) } catch (_: Exception) {}
    }

    fun battery(ctx: Context): Int {
        val bm = ctx.getSystemService(android.os.BatteryManager::class.java)
        return bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }

    // ---- Flashlight ----
    val torchOn = MutableStateFlow(false)
    private var torchId: String? = null
    private var torchRegistered = false

    private fun cameraManager(ctx: Context): CameraManager? {
        val cm = ctx.getSystemService(CameraManager::class.java) ?: return null
        if (!torchRegistered) {
            torchRegistered = true
            try {
                torchId = cm.cameraIdList.firstOrNull {
                    cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
                cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        if (cameraId == torchId) torchOn.value = enabled
                    }
                }, Handler(Looper.getMainLooper()))
            } catch (_: Exception) {
            }
        }
        return cm
    }

    fun initTorch(ctx: Context) { cameraManager(ctx) }

    fun toggleTorch(ctx: Context) {
        val cm = cameraManager(ctx) ?: return
        val id = torchId ?: run {
            Toast.makeText(ctx, "No flashlight", Toast.LENGTH_SHORT).show()
            return
        }
        try { cm.setTorchMode(id, !torchOn.value) } catch (_: Exception) {}
    }
}
