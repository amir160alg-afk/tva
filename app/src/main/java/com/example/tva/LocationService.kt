package com.example.tva

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class LocationService : Service() {
    private val db = FirebaseFirestore.getInstance()
    private var tId: String = ""
    private var client: FusedLocationProviderClient? = null
    private var cb: LocationCallback? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra("TRACKER_ID")
        val prefs = getSharedPreferences("gps_prefs", MODE_PRIVATE)
        
        tId = id ?: prefs.getString("active_id", "UNKNOWN") ?: "UNKNOWN"
        if (id != null) prefs.edit().putString("active_id", id).apply()
        
        notifySvc()
        runGps()
        
        return START_STICKY
    }

    private fun notifySvc() {
        val cId = "gps_ch"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(cId, "GPS System", NotificationManager.IMPORTANCE_HIGH)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }

        val n = NotificationCompat.Builder(this, cId)
            .setContentTitle("GPS System")
            .setContentText("ID: $tId | Active")
            .setSmallIcon(R.drawable.logo)
            .setOngoing(true)
            .build()

        startForeground(101, n)
    }

    @SuppressLint("MissingPermission")
    private fun runGps() {
        client = LocationServices.getFusedLocationProviderClient(this)
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()

        cb = object : LocationCallback() {
            override fun onLocationResult(res: LocationResult) {
                val loc = res.locations.lastOrNull() ?: return
                val map = hashMapOf(
                    "lat" to loc.latitude,
                    "lng" to loc.longitude,
                    "time" to System.currentTimeMillis()
                )
                db.document("trackers/$tId").set(map, SetOptions.merge())
            }
        }
        client?.requestLocationUpdates(req, cb!!, Looper.getMainLooper())
    }

    override fun onDestroy() {
        cb?.let { client?.removeLocationUpdates(it) }
        if (tId != "UNKNOWN") db.document("trackers/$tId").delete()
        super.onDestroy()
    }

    override fun onBind(i: Intent?) = null
}