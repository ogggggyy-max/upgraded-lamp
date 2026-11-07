package com.lamp.os.shell

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow

data class NotifItem(
    val key: String,
    val pkg: String,
    val title: String,
    val text: String,
    val time: Long,
    val clearable: Boolean,
    val intent: PendingIntent?,
    val autoCancel: Boolean,
)

/** Feeds notifications, Do Not Disturb state and media sessions to Lamp's panel. */
class NotifService : NotificationListenerService() {

    companion object {
        @Volatile
        var instance: NotifService? = null
            private set
        val items = MutableStateFlow<List<NotifItem>>(emptyList())
        val dnd = MutableStateFlow(false)

        fun open(ctx: Context, n: NotifItem) {
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    val opts = ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    n.intent?.send(ctx, 0, null, null, null, null, opts.toBundle())
                } else {
                    n.intent?.send()
                }
            } catch (_: Exception) {
            }
            if (n.autoCancel) dismiss(n)
        }

        fun dismiss(n: NotifItem) {
            try { instance?.cancelNotification(n.key) } catch (_: Exception) {}
        }

        fun clearAll() {
            try { instance?.cancelAllNotifications() } catch (_: Exception) {}
        }

        fun toggleDnd() {
            val s = instance ?: return
            try {
                s.requestInterruptionFilter(if (dnd.value) NotificationListenerService.INTERRUPTION_FILTER_ALL else NotificationListenerService.INTERRUPTION_FILTER_PRIORITY)
            } catch (_: Exception) {
            }
        }
    }

    override fun onListenerConnected() {
        instance = this
        refresh()
        dnd.value = currentInterruptionFilter > INTERRUPTION_FILTER_ALL
        Media.start(this)
    }

    override fun onListenerDisconnected() {
        instance = null
        Media.stop()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    override fun onInterruptionFilterChanged(interruptionFilter: Int) {
        dnd.value = interruptionFilter > INTERRUPTION_FILTER_ALL
    }

    private fun refresh() {
        val list = try { activeNotifications?.toList() ?: emptyList() } catch (_: Exception) { emptyList() }
        items.value = list
            .filter { (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 && it.packageName != packageName }
            .mapNotNull { sbn ->
                val ex = sbn.notification.extras
                val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
                    ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
                if (title.isBlank() && text.isBlank()) null
                else NotifItem(
                    key = sbn.key,
                    pkg = sbn.packageName,
                    title = title,
                    text = text,
                    time = sbn.postTime,
                    clearable = sbn.isClearable,
                    intent = sbn.notification.contentIntent,
                    autoCancel = (sbn.notification.flags and Notification.FLAG_AUTO_CANCEL) != 0,
                )
            }
            .sortedByDescending { it.time }
    }
}

/** Tracks the active media session (music, video) for the panel's media controls. */
object Media {
    val controller = MutableStateFlow<MediaController?>(null)
    val tick = MutableStateFlow(0)
    private var msm: MediaSessionManager? = null
    private val main = Handler(Looper.getMainLooper())

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) { tick.value++ }
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) { tick.value++ }
        override fun onSessionDestroyed() { controller.value = null; tick.value++ }
    }

    private val listener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list) }

    fun start(ctx: Context) {
        try {
            val m = ctx.getSystemService(MediaSessionManager::class.java)
            val comp = ComponentName(ctx, NotifService::class.java)
            m.addOnActiveSessionsChangedListener(listener, comp, main)
            msm = m
            pick(m.getActiveSessions(comp))
        } catch (_: Exception) {
        }
    }

    fun stop() {
        try { msm?.removeOnActiveSessionsChangedListener(listener) } catch (_: Exception) {}
        controller.value?.unregisterCallback(callback)
        controller.value = null
    }

    private fun pick(list: List<MediaController>?) {
        val c = list?.firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING }
            ?: list?.firstOrNull()
        controller.value?.unregisterCallback(callback)
        c?.registerCallback(callback, main)
        controller.value = c
        tick.value++
    }
}
