# Siren – Fully functional emergency app

**Real SMS OTP · Live location inside the app · Works when the other app is closed · Locked Firestore rules**

When Person A hits SOS (or says “call help”):
1. Live GPS is shared in Firestore.
2. A Cloud Function sends **high-priority FCM** to Person B.
3. Person B gets a **notification + full-screen SOS + siren** even if Siren was closed / phone locked.

No SMS location messages — everything is in-app + push.

---

## One-time Firebase setup (required)

### 1. Create project
1. [Firebase Console](https://console.firebase.google.com/) → Add project.
2. Add **Android** app, package name: `com.siren.emergency`
3. Download **google-services.json** → replace `app/google-services.json`

### 2. Enable Phone Authentication
1. Build → Authentication → Sign-in method → **Phone** → Enable.
2. Project settings → Your Android app → **Add fingerprint**
   - Debug SHA-1 (from Android Studio Gradle → signingReport, or):
     ```
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```
   - Add SHA-1 and SHA-256.

### 3. Firestore
1. Build → Firestore → Create database (start in production mode).
2. Deploy rules from this repo:
   ```
   npm install -g firebase-tools
   firebase login
   firebase use --add   # select your project
   firebase deploy --only firestore:rules
   ```
   Rules file: `firestore.rules` (already locked: only signed-in users, owner write).

### 4. Cloud Messaging
Enabled by default. No extra step.

### 5. Cloud Function (required for closed-app alert)
```
cd functions
npm install
cd ..
firebase deploy --only functions:sendSosPush
```
This sends the push when someone hits SOS so the other phone wakes up even if the app is closed.

### 6. Build the app
1. Open project in Android Studio.
2. Sync Gradle.
3. Run on **two real phones** (OTP + FCM need real devices).

---

## How to use

1. Both users open Siren and log in with **real SMS OTP** (+country code, e.g. `+9198…`).
2. Each adds the other as emergency contact (other person must have logged in once).
3. Grant Location, Microphone, Notifications.
4. A taps SOS or says “call help” → B gets push + full-screen siren + live location **even if B’s app was closed**.

---

## What is fully functional

| Feature | Status |
|---------|--------|
| Real SMS OTP (Firebase Phone Auth) | ✅ |
| Live location shared inside app only | ✅ |
| SOS when other app is **closed** (FCM + full-screen + siren) | ✅ (after Cloud Function deploy) |
| Firestore locked (not test mode) | ✅ `firestore.rules` |
| Voice “call help” | ✅ |
| Mutual contacts | ✅ |
| Open location in Maps | ✅ |

---

## Notes

- Phone numbers must include country code (`+91…`).
- First time a number is used, Firebase may show a reCAPTCHA / Play Integrity check.
- Some phone OEMs restrict “start activity from background”; the **heads-up notification** still appears with alarm sound. User taps it → full SOS screen + siren.
- For production, enable **App Check** and tighten rules further if needed.
