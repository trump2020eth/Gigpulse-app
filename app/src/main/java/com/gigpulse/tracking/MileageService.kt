package com.gigpulse.tracking
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.gigpulse.data.DI
import com.gigpulse.model.MileageEvent
class MileageService : Service() {
    private lateinit var client: FusedLocationProviderClient
    private var last: Location? = null
    private var miles = 0.0
    private var start = System.currentTimeMillis()
    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(1001, notif("Tracking miles… 0.0 mi"))
        client = LocationServices.getFusedLocationProviderClient(this)
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000)
            .setMinUpdateDistanceMeters(20f)
            .build()
        client.requestLocationUpdates(req, callback, mainLooper)
    }
    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            last?.let {
                miles += it.distanceTo(loc) / 1609.344
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(1001, notif("Tracking miles… %,.1f mi".format(miles)))
            }
            last = loc
        }
    }
    private fun notif(text: String) = NotificationCompat.Builder(this, "mileage")
        .setContentTitle("GigPulse").setContentText(text).setSmallIcon(android.R.drawable.ic_menu_mylocation).setOngoing(true).build()
    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel("mileage","Mileage", NotificationManager.IMPORTANCE_LOW))
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        val end = System.currentTimeMillis()
        Thread { DI.db.mileageDao().insert(MileageEvent(start, end, miles)) }.start()
        super.onDestroy()
    }
}