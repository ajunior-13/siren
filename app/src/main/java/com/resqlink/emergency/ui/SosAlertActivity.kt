package com.Siren.Siren.ui

import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Siren.Siren.data.SirenPlayer
import com.Siren.Siren.ui.theme.SirenTheme

/**
 * Full-screen SOS screen that can appear over the lock screen when FCM arrives.
 * Plays siren until user mutes or dismisses.
 */
class SosAlertActivity : ComponentActivity() {

    private val siren = SirenPlayer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(KeyguardManager::class.java)
            km?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val lat = intent.getDoubleExtra("lat", 0.0)
        val lng = intent.getDoubleExtra("lng", 0.0)
        val fromPhone = intent.getStringExtra("fromPhone") ?: "Unknown"
        val play = intent.getBooleanExtra("playSiren", true)

        if (play) siren.startSiren()

        setContent {
            SirenTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF7F1D1D)) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("🚨", fontSize = 72.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("SOS ALERT", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Text(
                            "$fromPhone needs help!",
                            fontSize = 18.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF450A0A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Live location", color = Color.White, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Lat: ${"%.6f".format(lat)}", color = Color.LightGray)
                                Text("Lng: ${"%.6f".format(lng)}", color = Color.LightGray)
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(SOS)")
                                try {
                                    startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps"))
                                } catch (_: Exception) {
                                    startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$lat,$lng"))
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text("Open in Maps", color = Color(0xFF7F1D1D), fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { siren.stopSiren() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Mute alarm")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = {
                            siren.stopSiren()
                            finish()
                        }) {
                            Text("Dismiss", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        siren.stopSiren()
        super.onDestroy()
    }
}
