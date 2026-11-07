package com.lamp.os.shell

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.lamp.os.data.Prefs
import com.lamp.os.gps.MockLocationService
import com.lamp.os.ui.LampTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The always-running part of Lamp:
 *  - an invisible strip over the status bar that opens Lamp's control panel when pulled down
 *  - the control panel itself
 *  - Lamp's lock screen, shown every time the screen turns off
 */
class ShellAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ShellAccessibilityService? = null
            private set
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private var strip: View? = null
    private var panel: OverlayWindow? = null
    private val panelVisible = mutableStateOf(false)
    private var lock: OverlayWindow? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    ShellState.screenOn.value = false
                    closePanel(immediate = true)
                    if (Prefs.lockEnabled) showLock()
                }
                Intent.ACTION_SCREEN_ON -> ShellState.screenOn.value = true
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        scope = MainScope().also { s -> s.launch { Prefs.version.collect { updateStrip() } } }
        if (Prefs.gpsRunning && !MockLocationService.running.value) MockLocationService.start(this)
        // Right after boot, start locked.
        if (Prefs.lockEnabled && SystemClock.elapsedRealtime() < 5 * 60_000L) showLock()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        scope?.cancel()
        removeStrip()
        panel?.dismiss(); panel = null
        lock?.dismiss(); lock = null
        ShellState.locked.value = false
        instance = null
        super.onDestroy()
    }

    // ---------------- Pull-down strip ----------------

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        val h = if (id > 0) resources.getDimensionPixelSize(id) else 0
        return if (h > 0) h else (24 * resources.displayMetrics.density).toInt()
    }

    private fun updateStrip() {
        if (Prefs.stripEnabled && strip == null) addStrip()
        else if (!Prefs.stripEnabled && strip != null) removeStrip()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addStrip() {
        val threshold = 16 * resources.displayMetrics.density
        var downY = 0f
        var fired = false
        val v = View(this)
        v.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downY = ev.rawY; fired = false }
                MotionEvent.ACTION_MOVE -> if (!fired && ev.rawY - downY > threshold) {
                    fired = true
                    openPanel()
                }
            }
            true
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            statusBarHeight(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = android.view.Gravity.TOP
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        if (Build.VERSION.SDK_INT >= 30) lp.fitInsetsTypes = 0
        try {
            wm.addView(v, lp)
            strip = v
        } catch (_: Exception) {
        }
    }

    private fun removeStrip() {
        strip?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        strip = null
    }

    // ---------------- Control panel ----------------

    fun openPanel() {
        if (lock != null || panel != null) return
        panelVisible.value = false
        val w = OverlayWindow(this, wm, focusable = true, onBack = { closePanel() }) {
            LampTheme { ControlPanel(visible = panelVisible.value, svc = this, onClose = { closePanel() }) }
        }
        w.show()
        panel = w
        handler.post { panelVisible.value = true }
    }

    fun closePanel(immediate: Boolean = false) {
        val w = panel ?: return
        panel = null
        panelVisible.value = false
        handler.postDelayed({ w.dismiss() }, if (immediate) 0L else 260L)
    }

    /** Closes the panel, then opens an activity. */
    fun launchFromPanel(intent: Intent) {
        closePanel()
        Sys.start(this, intent)
    }

    fun screenshot() {
        closePanel()
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) }, 500)
    }

    fun powerMenu() {
        closePanel()
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) }, 300)
    }

    /** Turns the screen off; Lamp's lock screen appears when it does. */
    fun lockNow() {
        closePanel(immediate = true)
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    // ---------------- Lock screen ----------------

    fun showLock() {
        if (lock != null) return
        closePanel(immediate = true)
        val w = OverlayWindow(
            this, wm,
            focusable = true,
            showWallpaper = true,
            onBack = {},
        ) {
            LampTheme { LockScreen(onUnlock = { handler.post { hideLock() } }) }
        }
        w.show()
        lock = w
        ShellState.locked.value = true
    }

    fun hideLock() {
        lock?.dismiss()
        lock = null
        ShellState.locked.value = false
    }
}
