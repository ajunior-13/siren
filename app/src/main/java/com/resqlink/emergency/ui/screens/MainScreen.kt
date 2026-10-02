package com.resqlink.emergency.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqlink.emergency.data.AuthRepository
import com.resqlink.emergency.data.EmergencyContact
import com.resqlink.emergency.data.FirestoreRepository
import com.resqlink.emergency.data.LocationService
import com.resqlink.emergency.data.SirenPlayer
import com.resqlink.emergency.data.VoiceCommandService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun MainScreen(activity: Activity) {
    val authRepo = remember { AuthRepository() }
    val repo = remember { FirestoreRepository() }
    val scope = rememberCoroutineScope()

    var isLoggedIn by remember { mutableStateOf(authRepo.isLoggedIn()) }
    var currentTab by remember { mutableStateOf(0) }

    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var authBusy by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf("") }

    var latitude by remember { mutableStateOf(0.0) }
    var longitude by remember { mutableStateOf(0.0) }
    var accuracy by remember { mutableStateOf(0f) }
    var isSosActive by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }
    var firstPublishDone by remember { mutableStateOf(false) }

    var contacts by remember { mutableStateOf<List<EmergencyContact>>(emptyList()) }
    var newContactName by remember { mutableStateOf("") }
    var newContactPhone by remember { mutableStateOf("") }

    var incomingSos by remember { mutableStateOf<FirestoreRepository.SosState?>(null) }

    val locationService = remember { LocationService(activity) }
    val sirenPlayer = remember { SirenPlayer() }
    val myPhone = authRepo.currentPhone()

    fun startMySos() {
        if (latitude == 0.0 && longitude == 0.0) {
            Toast.makeText(activity, "Waiting for GPS…", Toast.LENGTH_SHORT).show()
            return
        }
        isSosActive = true
        firstPublishDone = false
        sirenPlayer.startSiren()
        statusMsg = "SOS active – notifying contacts"
        scope.launch {
            try {
                repo.publishSos(latitude, longitude, accuracy, notifyPush = true)
                firstPublishDone = true
                statusMsg = "SOS live – location sharing + push sent"
            } catch (e: Exception) {
                statusMsg = "Error: ${e.message}"
                Toast.makeText(activity, e.message ?: "SOS failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun stopMySos() {
        isSosActive = false
        firstPublishDone = false
        sirenPlayer.stopSiren()
        statusMsg = "SOS stopped"
        scope.launch {
            try { repo.stopSos() } catch (_: Exception) {}
        }
    }

    fun toggleSos() {
        if (isSosActive) stopMySos() else startMySos()
    }

    val voiceCommandService = remember {
        VoiceCommandService(activity) {
            if (!isSosActive) startMySos()
        }
    }

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) return@LaunchedEffect
        scope.launch {
            try {
                repo.registerOrUpdateProfile()
                repo.refreshFcmToken()
            } catch (_: Exception) {}
        }
        try {
            locationService.requestRealtimeLocation().collectLatest { loc ->
                latitude = loc.latitude
                longitude = loc.longitude
                accuracy = loc.accuracy
                if (isSosActive) {
                    try {
                        // Subsequent updates: location only, push already sent on first publish
                        repo.publishSos(loc.latitude, loc.longitude, loc.accuracy, notifyPush = false)
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) voiceCommandService.startListening()
        else voiceCommandService.stopListening()
    }

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) return@LaunchedEffect
        repo.observeContacts().collectLatest { contacts = it }
    }

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) return@LaunchedEffect
        repo.observeIncomingSos().collectLatest { sos ->
            val prev = incomingSos
            incomingSos = sos
            if (sos != null && sos.active && (prev == null || !prev.active) && !isSosActive) {
                sirenPlayer.startSiren()
            }
            if ((sos == null || !sos.active) && prev != null && prev.active && !isSosActive) {
                sirenPlayer.stopSiren()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceCommandService.stopListening()
            sirenPlayer.stopSiren()
        }
    }

    // Incoming SOS overlay (app open)
    if (incomingSos != null && incomingSos!!.active && !isSosActive) {
        val sos = incomingSos!!
        val name = contacts.find { it.phone == sos.fromPhone }?.name ?: sos.fromPhone
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF7F1D1D)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("🚨", fontSize = 64.sp)
                Text("SOS ALERT", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text("$name needs help!", fontSize = 18.sp, color = Color.White, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(20.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF450A0A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Live location", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Lat: ${"%.6f".format(sos.lat)}", color = Color.LightGray)
                        Text("Lng: ${"%.6f".format(sos.lng)}", color = Color.LightGray)
                        Text("Accuracy: ${"%.0f".format(sos.accuracy)} m", color = Color(0xFF4ADE80))
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        val uri = Uri.parse("geo:${sos.lat},${sos.lng}?q=${sos.lat},${sos.lng}(SOS)")
                        try {
                            activity.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps"))
                        } catch (_: Exception) {
                            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${sos.lat},${sos.lng}")))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Open in Maps", color = Color(0xFF7F1D1D), fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { sirenPlayer.stopSiren() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Mute alarm") }
            }
        }
        return
    }

    // ========== REAL PHONE OTP LOGIN ==========
    if (!isLoggedIn) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Siren", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Real SMS OTP login (Firebase)", fontSize = 14.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(28.dp))

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Mobile number with country code") },
                    placeholder = { Text("+919876543210") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                if (otpSent) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { otpCode = it },
                        label = { Text("OTP from SMS") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                if (authError.isNotBlank()) {
                    Text(authError, color = Color(0xFFF87171), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        authError = ""
                        if (!otpSent) {
                            val phone = phoneNumber.trim()
                            if (!phone.startsWith("+") || phone.length < 11) {
                                authError = "Use international format, e.g. +919876543210"
                                return@Button
                            }
                            authBusy = true
                            authRepo.sendOtp(
                                activity = activity,
                                phoneE164 = phone,
                                onCodeSent = {
                                    authBusy = false
                                    otpSent = true
                                    Toast.makeText(activity, "OTP SMS sent", Toast.LENGTH_SHORT).show()
                                },
                                onAutoVerified = {
                                    authBusy = false
                                    scope.launch {
                                        try { repo.registerOrUpdateProfile() } catch (_: Exception) {}
                                        isLoggedIn = true
                                    }
                                },
                                onError = { msg ->
                                    authBusy = false
                                    authError = msg
                                }
                            )
                        } else {
                            if (otpCode.length < 4) {
                                authError = "Enter the OTP from SMS"
                                return@Button
                            }
                            authBusy = true
                            authRepo.verifyOtp(
                                code = otpCode.trim(),
                                onSuccess = {
                                    authBusy = false
                                    scope.launch {
                                        try { repo.registerOrUpdateProfile() } catch (_: Exception) {}
                                        isLoggedIn = true
                                        Toast.makeText(activity, "Logged in", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onError = { msg ->
                                    authBusy = false
                                    authError = msg
                                }
                            )
                        }
                    },
                    enabled = !authBusy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    if (authBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(if (!otpSent) "Send OTP SMS" else "Verify OTP", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    // ========== MAIN ==========
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Text("🚨", fontSize = 20.sp) },
                    label = { Text("SOS") }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Text("👥", fontSize = 20.sp) },
                    label = { Text("Contacts (${contacts.size})") }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (currentTab) {
                0 -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Siren", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 22.sp)
                        Text("Logged in as $myPhone", color = Color.Gray, fontSize = 12.sp)
                        Text("Say \"call help\" or tap SOS", color = Color(0xFF4ADE80), fontSize = 14.sp)
                    }
                    Button(
                        onClick = { toggleSos() },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSosActive) Color(0xFF991B1B) else Color(0xFFDC2626)
                        ),
                        modifier = Modifier.size(200.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (isSosActive) "STOP" else "SOS", fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color.White)
                            Text(
                                if (isSosActive) "Stop sharing" else "Alert contacts",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier = Modifier.padding(16.dp)) {
                            Text("Your live location", fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Lat: ${"%.5f".format(latitude)}", color = Color.LightGray, fontSize = 13.sp)
                            Text("Lng: ${"%.5f".format(longitude)}", color = Color.LightGray, fontSize = 13.sp)
                            Text("Accuracy: ${"%.0f".format(accuracy)} m", color = Color(0xFF4ADE80), fontSize = 13.sp)
                            if (statusMsg.isNotBlank()) {
                                Text(statusMsg, color = Color.Yellow, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                }

                1 -> Column(Modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text("Emergency Contacts", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        "They must log in to Siren once first. Then add their number here.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            OutlinedTextField(
                                value = newContactName,
                                onValueChange = { newContactName = it },
                                label = { Text("Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = newContactPhone,
                                onValueChange = { newContactPhone = it },
                                label = { Text("Phone with country code") },
                                placeholder = { Text("+91…") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (newContactName.isBlank() || newContactPhone.length < 10) {
                                        Toast.makeText(activity, "Enter name and full phone", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    scope.launch {
                                        try {
                                            repo.addContactByPhone(newContactName.trim(), newContactPhone.trim())
                                            newContactName = ""
                                            newContactPhone = ""
                                            Toast.makeText(activity, "Contact added", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(activity, e.message ?: "Failed", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Add emergency contact") }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(contacts, key = { it.phone }) { c ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(c.name, color = Color.White, fontWeight = FontWeight.Bold)
                                        Text(c.phone, color = Color.Gray, fontSize = 14.sp)
                                    }
                                    TextButton(onClick = {
                                        scope.launch {
                                            try { repo.removeContact(c.phone) } catch (_: Exception) {}
                                        }
                                    }) { Text("Remove", color = Color(0xFFEF4444)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
