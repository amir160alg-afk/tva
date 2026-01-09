package com.example.tva

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
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
            var trackerId by remember { mutableStateOf("") }
            var isRunning by remember { mutableStateOf(false) }
            val context = LocalContext.current

            LaunchedEffect(isRunning) {
                if (!isRunning) {
                    generateUniqueId { trackerId = it }
                }
            }

            val settingsLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    startService(context, trackerId)
                    isRunning = true
                }
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { perms ->
                if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                    checkSettings(context, 
                        {
                            startService(context, trackerId)
                            isRunning = true
                        },
                        { settingsLauncher.launch(it) }
                    )
                }
            }

            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(CircleShape)
                            .border(3.dp, Color(0xFF20B2AA), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
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
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(trackerId, fontSize = 48.sp, fontWeight = FontWeight.Black, color = Color(0xFF20B2AA))
                    }

                    Button(
                        onClick = {
                            if (!isRunning) {
                                if (trackerId.isEmpty()) return@Button
                                val p = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) p.add(Manifest.permission.POST_NOTIFICATIONS)
                                permissionLauncher.launch(p.toTypedArray())
                            } else {
                                context.stopService(Intent(context, LocationService::class.java))
                                isRunning = false
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

    private fun generateUniqueId(onDone: (String) -> Unit) {
        val id = (10000000..99999999).random().toString()
        db.collection("trackers").document(id).get()
            .addOnSuccessListener { if (it.exists()) generateUniqueId(onDone) else onDone(id) }
            .addOnFailureListener { onDone(id) }
    }

    private fun checkSettings(context: Context, onOk: () -> Unit, onFail: (IntentSenderRequest) -> Unit) {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
        val builder = LocationSettingsRequest.Builder().addLocationRequest(req)
        LocationServices.getSettingsClient(context).checkLocationSettings(builder.build())
            .addOnSuccessListener { onOk() }
            .addOnFailureListener { e ->
                if (e is ResolvableApiException) onFail(IntentSenderRequest.Builder(e.resolution).build())
                else context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
    }

    private fun startService(context: Context, id: String) {
        val intent = Intent(context, LocationService::class.java).apply { putExtra("TRACKER_ID", id) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
        else context.startService(intent)
    }
}