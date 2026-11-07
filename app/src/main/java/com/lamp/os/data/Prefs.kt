package com.lamp.os.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * All settings live here. [version] ticks on every change so Compose screens
 * and services can react without each setting needing its own flow.
 */
object Prefs {
    const val DOCK_SLOTS = 5

    private lateinit var sp: SharedPreferences
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("lamp", Context.MODE_PRIVATE)
    }

    private fun bump() {
        _version.value = _version.value + 1
    }

    private inline fun edit(block: SharedPreferences.Editor.() -> Unit) {
        sp.edit().apply(block).apply()
        bump()
    }

    // ---- Wallpaper ("system" shows the system/live wallpaper) ----
    var wallpaperType: String
        get() = sp.getString("wp_type", "system") ?: "system"
        set(v) = edit { putString("wp_type", v) }
    var wallpaperUri: String?
        get() = sp.getString("wp_uri", null)
        set(v) = edit { putString("wp_uri", v) }
    var videoSound: Boolean
        get() = sp.getBoolean("wp_sound", true)
        set(v) = edit { putBoolean("wp_sound", v) }
    var videoVolume: Float
        get() = sp.getFloat("wp_volume", 0.6f)
        set(v) = edit { putFloat("wp_volume", v) }
    var lockVideoSound: Boolean
        get() = sp.getBoolean("lock_sound", false)
        set(v) = edit { putBoolean("lock_sound", v) }

    // ---- Lock screen ----
    var lockEnabled: Boolean
        get() = sp.getBoolean("lock_on", true)
        set(v) = edit { putBoolean("lock_on", v) }
    var pinHash: String?
        get() = sp.getString("pin", null)
        set(v) = edit { putString("pin", v) }

    fun hashPin(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("lamp-os:$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun checkPin(pin: String): Boolean = pinHash != null && pinHash == hashPin(pin)

    // ---- Desktop look ----
    var iconSizeDp: Int
        get() = sp.getInt("icon_dp", 40)
        set(v) = edit { putInt("icon_dp", v) }
    var showLabels: Boolean
        get() = sp.getBoolean("labels", true)
        set(v) = edit { putBoolean("labels", v) }
    var framedIcons: Boolean
        get() = sp.getBoolean("framed", true)
        set(v) = edit { putBoolean("framed", v) }

    // ---- Control panel ----
    var stripEnabled: Boolean
        get() = sp.getBoolean("strip", true)
        set(v) = edit { putBoolean("strip", v) }

    // ---- Fake GPS ----
    var gpsLat: Double
        get() = sp.getString("gps_lat", "48.85837")?.toDoubleOrNull() ?: 48.85837
        set(v) = edit { putString("gps_lat", v.toString()) }
    var gpsLon: Double
        get() = sp.getString("gps_lon", "2.294481")?.toDoubleOrNull() ?: 2.294481
        set(v) = edit { putString("gps_lon", v.toString()) }
    var gpsRunning: Boolean
        get() = sp.getBoolean("gps_on", false)
        set(v) = edit { putBoolean("gps_on", v) }
    var gpsJitter: Boolean
        get() = sp.getBoolean("gps_jitter", false)
        set(v) = edit { putBoolean("gps_jitter", v) }

    fun setGps(lat: Double, lon: Double) = edit {
        putString("gps_lat", lat.toString())
        putString("gps_lon", lon.toString())
    }

    // ---- Dock: exactly 5 slots, "" means empty ----
    fun dock(): List<String> {
        val arr = JSONArray(sp.getString("dock", "[]"))
        return List(DOCK_SLOTS) { if (it < arr.length()) arr.optString(it, "") else "" }
    }

    fun setDockSlot(index: Int, key: String) {
        val d = dock().toMutableList()
        if (key.isNotEmpty()) for (i in d.indices) if (d[i] == key) d[i] = ""
        d[index] = key
        edit { putString("dock", JSONArray(d).toString()) }
    }

    // ---- Desktop grid: app key -> (column, row) ----
    fun desktop(): MutableMap<String, Pair<Int, Int>> {
        val o = JSONObject(sp.getString("desktop", "{}") ?: "{}")
        val m = mutableMapOf<String, Pair<Int, Int>>()
        o.keys().forEach { k ->
            val p = o.optString(k).split(",")
            if (p.size == 2) m[k] = (p[0].toIntOrNull() ?: 0) to (p[1].toIntOrNull() ?: 0)
        }
        return m
    }

    fun saveDesktop(m: Map<String, Pair<Int, Int>>, notify: Boolean = true) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, "${v.first},${v.second}") }
        sp.edit().putString("desktop", o.toString()).apply()
        if (notify) bump()
    }

    /** Moves an icon; if the target cell is taken, the two icons swap places. */
    fun moveDesktop(key: String, col: Int, row: Int) {
        val m = desktop()
        val old = m[key]
        val other = m.entries.firstOrNull { it.value == (col to row) && it.key != key }?.key
        if (other != null) {
            if (old != null) m[other] = old else m.remove(other)
        }
        m[key] = col to row
        saveDesktop(m)
    }

    fun resetDesktop() = edit { remove("desktop") }

    fun hidden(): Set<String> = sp.getStringSet("hidden", emptySet())?.toSet() ?: emptySet()

    fun hide(key: String) {
        val m = desktop().apply { remove(key) }
        saveDesktop(m, notify = false)
        edit { putStringSet("hidden", hidden() + key) }
    }

    fun unhide(key: String) = edit { putStringSet("hidden", hidden() - key) }
}
