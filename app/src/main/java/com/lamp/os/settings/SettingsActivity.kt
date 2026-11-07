package com.lamp.os.settings

import android.Manifest
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.lamp.os.BuildConfig
import com.lamp.os.data.Apps
import com.lamp.os.data.Prefs
import com.lamp.os.gps.FakeGpsActivity
import com.lamp.os.shell.ShellAccessibilityService
import com.lamp.os.shell.Sys
import com.lamp.os.ui.LampTheme
import com.lamp.os.ui.lampFrame

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LampTheme { SettingsScreen() } }
    }
}

private class Step(val title: String, val desc: String, val done: Boolean?, val action: () -> Unit)

private fun a11yEnabled(ctx: Context): Boolean {
    val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return s.split(':').any { ComponentName.unflattenFromString(it)?.packageName == ctx.packageName }
}

@Composable
private fun SettingsScreen() {
    val ctx = LocalContext.current
    val v by Prefs.version.collectAsState()
    val apps by Apps.apps.collectAsState()
    // Re-check permission states whenever you come back to this screen.
    val life by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var pinDialog by remember { mutableStateOf(false) }

    val homeRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val rm = ctx.getSystemService(RoleManager::class.java)
        if (rm?.isRoleHeld(RoleManager.ROLE_HOME) != true) Sys.start(ctx, Intent(Settings.ACTION_HOME_SETTINGS))
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun persist(uri: Uri) {
        try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { persist(uri); Prefs.wallpaperUri = uri.toString(); Prefs.wallpaperType = "image" }
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { persist(uri); Prefs.wallpaperUri = uri.toString(); Prefs.wallpaperType = "video" }
    }

    val steps = remember(life, v) {
        val pm = ctx.getSystemService(PowerManager::class.java)
        val rm = ctx.getSystemService(RoleManager::class.java)
        buildList {
            add(Step("Make Lamp your home screen", "Replaces the HyperOS home screen.", rm?.isRoleHeld(RoleManager.ROLE_HOME) == true) {
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) homeRole.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                else Sys.start(ctx, Intent(Settings.ACTION_HOME_SETTINGS))
            })
            add(Step("Turn on the Lamp accessibility service", "Runs the pull-down control panel and the lock screen. Settings → Accessibility → Downloaded apps → Lamp.", a11yEnabled(ctx)) {
                Sys.start(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
            add(Step("Allow notification access", "Shows notifications, media controls and Do Not Disturb in the panel.", NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)) {
                Sys.start(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            })
            add(Step("Allow display over other apps", "Lets the panel open Settings pages from any app.", Settings.canDrawOverlays(ctx)) {
                Sys.start(ctx, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            })
            add(Step("Allow modify system settings", "Needed for the brightness slider and auto-rotate.", Settings.System.canWrite(ctx)) {
                Sys.start(ctx, Sys.writeSettingsIntent(ctx))
            })
            if (Build.VERSION.SDK_INT >= 33) {
                add(Step("Allow notifications", "So the fake GPS notification (with its Stop button) can show.", NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
                    notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                })
            }
            add(Step("Battery: no restrictions", "Stops Android from putting Lamp to sleep.", pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true) {
                Sys.start(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
            })
            add(Step("Xiaomi: turn on Autostart", "HyperOS kills apps without it. Also lock Lamp in Recents (long-press its card → lock).", null) {
                Sys.startFirst(ctx, listOf(
                    Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
                ))
            })
            add(Step("Pick Lamp as mock location app", "Developer options → Select mock location app → Lamp. (Turn on Developer options first: tap OS version 7 times in About tablet.)", null) {
                Sys.start(ctx, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            })
            add(Step("Add apps to Game Turbo", "Add Lamp and every app you use as a 'game' so HyperOS's own control panel stays out of the way. Security app → Game Turbo → add games.", null) {
                Sys.startFirst(ctx, listOf(
                    Intent().setClassName("com.miui.securitycenter", "com.miui.gamebooster.ui.GameBoosterMainActivity"),
                    Intent().setClassName("com.miui.securitycenter", "com.miui.gamebooster.GameBoosterMainActivity"),
                    ctx.packageManager.getLaunchIntentForPackage("com.miui.securitycenter"),
                ))
            })
            add(Step("Turn off the system lock screen", "Set Screen lock to None. Lamp's own lock screen takes over.", null) {
                Sys.start(ctx, Intent(Settings.ACTION_SECURITY_SETTINGS))
            })
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Lamp", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Light)
            Text("Version ${BuildConfig.VERSION_NAME}", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        }

        item { Section("Setup") }
        items(steps) { StepRow(it) }

        item { Section("Wallpaper") }
        item {
            val type = remember(v) { Prefs.wallpaperType }
            Card {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == "system", onClick = { Prefs.wallpaperType = "system" }, label = { Text("System / live") })
                    FilterChip(selected = type == "image", onClick = { pickImage.launch(arrayOf("image/*")) }, label = { Text("Picture") })
                    FilterChip(selected = type == "video", onClick = { pickVideo.launch(arrayOf("video/*")) }, label = { Text("Video") })
                }
                Text(
                    "System / live uses whatever wallpaper Android has, including live wallpaper apps. Picture and Video play your own file.",
                    color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
                )
                if (type == "system") {
                    OutlinedButton(onClick = { Sys.start(ctx, Intent(Intent.ACTION_SET_WALLPAPER)) }) { Text("Choose system or live wallpaper") }
                }
                if (type == "video") {
                    SwitchRow("Play video sound on home screen", remember(v) { Prefs.videoSound }) { Prefs.videoSound = it }
                    var vol by remember { mutableFloatStateOf(Prefs.videoVolume) }
                    Text("Video volume", color = Color.White, fontSize = 14.sp)
                    Slider(value = vol, onValueChange = { vol = it }, onValueChangeFinished = { Prefs.videoVolume = vol })
                    SwitchRow("Play video sound on lock screen", remember(v) { Prefs.lockVideoSound }) { Prefs.lockVideoSound = it }
                }
            }
        }

        item { Section("Lock screen") }
        item {
            Card {
                SwitchRow("Use Lamp lock screen", remember(v) { Prefs.lockEnabled }) { Prefs.lockEnabled = it }
                val hasPin = remember(v) { Prefs.pinHash != null }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pinDialog = true }) { Text(if (hasPin) "Change PIN" else "Set PIN") }
                    if (hasPin) OutlinedButton(onClick = { Prefs.pinHash = null }) { Text("Remove PIN") }
                }
                Text(
                    "Without a PIN it's swipe-to-unlock. Note: this lock is for looks and casual privacy. It isn't as secure as Android's own lock (the power menu and safe mode still work).",
                    color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
                )
                if (ShellAccessibilityService.instance != null) {
                    OutlinedButton(onClick = { ShellAccessibilityService.instance?.showLock() }) { Text("Preview lock screen") }
                }
            }
        }

        item { Section("Desktop") }
        item {
            Card {
                var size by remember { mutableFloatStateOf(Prefs.iconSizeDp.toFloat()) }
                Text("Icon size: ${size.toInt()} dp", color = Color.White, fontSize = 14.sp)
                Slider(value = size, onValueChange = { size = it }, valueRange = 28f..64f, onValueChangeFinished = { Prefs.iconSizeDp = size.toInt() })
                SwitchRow("Show app names", remember(v) { Prefs.showLabels }) { Prefs.showLabels = it }
                SwitchRow("Frame icons like the dock", remember(v) { Prefs.framedIcons }) { Prefs.framedIcons = it }
                OutlinedButton(onClick = { Prefs.resetDesktop() }) { Text("Tidy up (re-sort all icons A–Z)") }
                Text(
                    "Drag icons anywhere on the grid (long-press, then move). Long-press without moving for options. Double-tap empty space to lock.",
                    color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
                )
            }
        }
        val hidden = Prefs.hidden().let { h -> apps.filter { it.key in h } }
        if (hidden.isNotEmpty()) {
            item { Text("Hidden apps", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
            items(hidden, key = { "h_" + it.key }) { app ->
                Row(Modifier.fillMaxWidth().lampFrame(14.dp, 1.5.dp).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(app.icon, null, Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(app.label, color = Color.White, modifier = Modifier.weight(1f))
                    TextButton(onClick = { Prefs.unhide(app.key) }) { Text("Show") }
                }
            }
        }

        item { Section("Control panel") }
        item {
            Card {
                SwitchRow("Pull down from the top to open Lamp's panel", remember(v) { Prefs.stripEnabled }) { Prefs.stripEnabled = it }
                if (ShellAccessibilityService.instance != null) {
                    OutlinedButton(onClick = { ShellAccessibilityService.instance?.openPanel() }) { Text("Open panel now") }
                } else {
                    Text("Turn on the Lamp accessibility service (Setup above) first.", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
        }

        item { Section("Fake GPS") }
        item {
            OutlinedButton(onClick = { ctx.startActivity(Intent(ctx, FakeGpsActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) {
                Text("Open Fake GPS")
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }

    if (pinDialog) PinDialog(onDismiss = { pinDialog = false })
}

@Composable
private fun Section(title: String) {
    Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().lampFrame(18.dp, 1.5.dp).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StepRow(s: Step) {
    Row(
        Modifier.fillMaxWidth().lampFrame(16.dp, 1.5.dp).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when (s.done) { true -> "✓"; false -> "○"; null -> "•" },
            color = Color.White, fontSize = 20.sp, modifier = Modifier.width(28.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(s.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(s.desc, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = s.action) { Text(if (s.done == true) "Done" else "Open") }
    }
}

@Composable
private fun PinDialog(onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = pin.length in 4..8 && pin.all { it.isDigit() } && pin == confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set lock screen PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(8) }, label = { Text("PIN (4–8 digits)") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                OutlinedTextField(
                    value = confirm, onValueChange = { confirm = it.filter(Char::isDigit).take(8) }, label = { Text("Repeat PIN") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { Prefs.pinHash = Prefs.hashPin(pin); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
