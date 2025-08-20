package com.gigpulse.notifications
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gigpulse.data.DI
import com.gigpulse.model.Hotspot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

class HotspotWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    private val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        ensureChannel()
        val dao = DI.db.hotspotDao()
        val list = dao.all().ifEmpty {
            // seed some defaults
            val seeds = listOf(
                Hotspot("dd-downtown","Downtown",36.206,-119.34, Random.nextInt(20,60), "DoorDash"),
                Hotspot("ue-mall","Mall",36.21,-119.36, Random.nextInt(20,60), "UberEats"),
                Hotspot("dd-campus","Campus",36.19,-119.33, Random.nextInt(20,60), "DoorDash"),
            )
            seeds.forEach { dao.upsert(it) }
            seeds
        }
        // Update intensities
        val updated = list.map { it.copy(intensity = (it.intensity + Random.nextInt(-15, 25)).coerceIn(0,100)) }
        updated.forEach { dao.upsert(it) }
        // Notify on "red"
        updated.filter { it.intensity >= 80 }.forEachIndexed { i, h ->
            nm.notify(2000 + i, NotificationCompat.Builder(applicationContext, "hotspots")
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle("🔥 Hotspot is red: ${h.name}")
                .setContentText("${h.platform} • intensity ${h.intensity}")
                .setAutoCancel(true)
                .build())
        }
        Result.success()
    }
    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("hotspots","Hotspot Alerts", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
}