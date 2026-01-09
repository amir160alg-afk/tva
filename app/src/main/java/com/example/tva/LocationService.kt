package com.example.tva

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class LocationService : Service() {
    private val db = FirebaseFirestore.getInstance()
    private var trackerId: String = ""
    private var client: FusedLocationProviderClient? = null
    private var callback: LocationCallback? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra("TRACKER_ID")
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        
        if (id != null) {
            trackerId = id
            prefs.edit().putString("t_id", id).apply()
        } else {
            trackerId = prefs.getString("t_id", "UNKNOWN") ?: "UNKNOWN"
        }
        
        showNotification()
        startUpdates()
        
        return START_STICKY
    }

    private fun showNotification() {
        val chId = "gps_svc"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(chId, "GPS System", NotificationManager.IMPORTANCE_HIGH)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }

        val n = NotificationCompat.Builder(this, chId)
            .setContentTitle("GPS System")
            .setContentText("ID: $trackerId | Active")
            .setSmallIcon(R.drawable.logo)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(101, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(101, n)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startUpdates() {
        if (client == null) client = LocationServices.getFusedLocationProviderClient(this)
        
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(5000)
            .setMinUpdateDistanceMeters(0f)
            .build()

        if (callback == null) {
            callback = object : LocationCallback() {
                override fun onLocationResult(res: LocationResult) {
                    val loc = res.locations.lastOrNull() ?: return
                    if (System.currentTimeMillis() - loc.time > 15000) return

                    val data = hashMapOf(
                        "lat" to loc.latitude,
                        "lng" to loc.longitude,
                        "timestamp" to System.currentTimeMillis(),
                        "status" to "online"
                    )
                    db.collection("trackers").document(trackerId).set(data, SetOptions.merge())
                }
            }
            client?.requestLocationUpdates(req, callback!!, Looper.getMainLooper())
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        callback?.let { client?.removeLocationUpdates(it) }
        if (trackerId != "UNKNOWN") {
            db.collection("trackers").document(trackerId).delete()
            getSharedPreferences("prefs", MODE_PRIVATE).edit().remove("t_id").apply()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}