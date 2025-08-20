package com.gigpulse.notifications
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.gigpulse.data.DI
import com.gigpulse.model.Hotspot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.round

class ExternalNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val hotWords = listOf("busy","very busy","dash now","surge","quest","peak pay")

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        val extras = sbn.notification.extras
        val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
        val body = (title + " " + text).lowercase()

        val hit = hotWords.any { body.contains(it) }
        if (!hit) return

        val platform = when {
            pkg.contains("dash", true) -> "DoorDash"
            pkg.contains("uber", true) -> "UberEats"
            else -> return
        }

        // Mark a generic hotspot for that platform as busy (simulate signal)
        scope.launch {
            val name = "$platform (Device alert)"
            val id = "${platform}-device"
            val dao = DI.db.hotspotDao()
            val h = Hotspot(id=id, name=name, lat=0.0, lng=0.0, intensity=95, platform=platform)
            dao.upsert(h)
        }
    }
}