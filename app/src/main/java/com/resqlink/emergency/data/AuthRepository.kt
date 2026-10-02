package com.resqlink.emergency.data

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

/**
 * Real Firebase Phone Authentication (SMS OTP sent by Firebase).
 * Requires:
 * - Phone Auth enabled in Firebase Console
 * - SHA-1/SHA-256 of your signing key added to the Android app
 * - google-services.json from your project
 */
class AuthRepository {

    private val auth = FirebaseAuth.getInstance()
    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    fun currentPhone(): String? = auth.currentUser?.phoneNumber

    fun isLoggedIn(): Boolean = auth.currentUser != null

    fun signOut() = auth.signOut()

    fun sendOtp(
        activity: Activity,
        phoneE164: String, // e.g. +919876543210
        onCodeSent: () -> Unit,
        onAutoVerified: () -> Unit,
        onError: (String) -> Unit
    ) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Instant verification / auto-retrieval on some devices
                auth.signInWithCredential(credential)
                    .addOnSuccessListener { onAutoVerified() }
                    .addOnFailureListener { onError(it.message ?: "Sign-in failed") }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                onError(e.message ?: "Verification failed")
            }

            override fun onCodeSent(
                verId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                verificationId = verId
                resendToken = token
                onCodeSent()
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneE164)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyOtp(
        code: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val id = verificationId
        if (id.isNullOrBlank()) {
            onError("Send OTP first")
            return
        }
        val credential = PhoneAuthProvider.getCredential(id, code)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Invalid OTP") }
    }
}
