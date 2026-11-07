package com.lamp.os.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AppEntry(
    val key: String,
    val label: String,
    val pkg: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
)

/** Installed launchable apps, kept up to date as apps are installed or removed. */
object Apps {
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    fun init(ctx: Context) {
        app = ctx.applicationContext
        val la = app.getSystemService(LauncherApps::class.java)
        la.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) = reload()
            override fun onPackageAdded(packageName: String?, user: UserHandle?) = reload()
            override fun onPackageChanged(packageName: String?, user: UserHandle?) = reload()
            override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = reload()
            override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = reload()
        }, Handler(Looper.getMainLooper()))
        reload()
    }

    fun reload() {
        job?.cancel()
        job = scope.launch {
            val list = try { load() } catch (e: Exception) { null }
            if (list != null) _apps.value = list
        }
    }

    private fun load(): List<AppEntry> {
        val la = app.getSystemService(LauncherApps::class.java)
        val um = app.getSystemService(UserManager::class.java)
        val px = (72 * app.resources.displayMetrics.density).toInt().coerceIn(96, 192)
        val me = Process.myUserHandle()
        return um.userProfiles.flatMap { user ->
            la.getActivityList(null, user).map { ai ->
                val cn = ai.componentName
                AppEntry(
                    key = cn.flattenToShortString() + if (user != me) "#${user.hashCode()}" else "",
                    label = ai.label?.toString() ?: cn.packageName,
                    pkg = cn.packageName,
                    component = cn,
                    user = user,
                    icon = ai.getBadgedIcon(0).toBitmap(px, px).asImageBitmap(),
                )
            }
        }.sortedBy { it.label.lowercase() }
    }

    fun byPackage(pkg: String): AppEntry? = _apps.value.firstOrNull { it.pkg == pkg }

    fun label(ctx: Context, pkg: String): String = byPackage(pkg)?.label ?: try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    fun launch(ctx: Context, e: AppEntry) {
        try {
            ctx.getSystemService(LauncherApps::class.java).startMainActivity(e.component, e.user, null, null)
        } catch (ex: Exception) {
            Toast.makeText(ctx, "Can't open ${e.label}", Toast.LENGTH_SHORT).show()
        }
    }

    fun appInfo(ctx: Context, e: AppEntry) {
        try {
            ctx.getSystemService(LauncherApps::class.java).startAppDetailsActivity(e.component, e.user, null, null)
        } catch (_: Exception) {
        }
    }

    fun uninstall(ctx: Context, e: AppEntry) {
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:${e.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }
}
