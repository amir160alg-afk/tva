package com.example.tva

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val ctx = LocalContext.current
            val prefs = remember { ctx.getSharedPreferences("gps_prefs", MODE_PRIVATE) }
            
            var trackerId by remember { mutableStateOf(prefs.getString("active_id", "") ?: "") }
            var isRunning by remember { mutableStateOf(isServiceRunning(ctx)) }

            LaunchedEffect(Unit) {
                if (!isRunning && trackerId.isEmpty()) {
                    genId { id ->
                        trackerId = id
                        prefs.edit().putString("active_id", id).apply()
                    }
                }
            }

            val setLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
                if (res.resultCode == RESULT_OK) {
                    start(ctx, trackerId)
                    isRunning = true
                }
            }

            val pLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
                if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                    checkGps(ctx, {
                        start(ctx, trackerId)
                        isRunning = true
                    }, { setLauncher.launch(it) })
                }
            }

            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.size(180.dp).clip(CircleShape).border(3.dp, Color(0xFF20B2AA), CircleShape)) {
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("GPS System", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Online Tracker", fontSize = 14.sp, color = Color.Gray)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ВАШ ПЕРСОНАЛЬНЫЙ КОД:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Text(trackerId, fontSize = 48.sp, fontWeight = FontWeight.Black, color = Color(0xFF20B2AA))
                    }

                    Button(
                        onClick = {
                            if (!isRunning) {
                                if (trackerId.isEmpty()) return@Button
                                val p = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) p.add(Manifest.permission.POST_NOTIFICATIONS)
                                pLauncher.launch(p.toTypedArray())
                            } else {
                                db.document("trackers/$trackerId").delete()
                                ctx.stopService(Intent(ctx, LocationService::class.java))
                                isRunning = false
                                
                                genId { id ->
                                    trackerId = id
                                    prefs.edit().putString("active_id", id).apply()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(65.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) Color(0xFFE57373) else Color(0xFF20B2AA))
                    ) {
                        Text(if (isRunning) "ОТКЛЮЧИТЬ" else "ЗАПУСТИТЬ GPS", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    private fun isServiceRunning(ctx: Context): Boolean {
        val am = ctx.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        return am.getRunningServices(Int.MAX_VALUE).any { it.service.className == LocationService::class.java.name }
    }

    private fun genId(done: (String) -> Unit) {
        val id = (10000000..99999999).random().toString()
        db.document("trackers/$id").get().addOnSuccessListener { 
            if (it.exists()) genId(done) else done(id) 
        }.addOnFailureListener { done(id) }
    }

    private fun checkGps(ctx: Context, ok: () -> Unit, fail: (IntentSenderRequest) -> Unit) {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
        LocationServices.getSettingsClient(ctx).checkLocationSettings(LocationSettingsRequest.Builder().addLocationRequest(req).build())
            .addOnSuccessListener { ok() }
            .addOnFailureListener { e ->
                if (e is ResolvableApiException) fail(IntentSenderRequest.Builder(e.resolution).build())
                else ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
    }

    private fun start(ctx: Context, id: String) {
        val i = Intent(ctx, LocationService::class.java).apply { putExtra("TRACKER_ID", id) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
        else ctx.startService(i)
    }
}