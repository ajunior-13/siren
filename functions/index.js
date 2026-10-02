/**
 * Cloud Function: sendSosPush
 * Called from the Android app when user hits SOS.
 * Sends high-priority FCM to each emergency contact so they get
 * notification + full-screen siren even if Siren is closed.
 *
 * Deploy:
 *   cd functions
 *   npm install
 *   firebase deploy --only functions:sendSosPush
 */
const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

exports.sendSosPush = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Must be logged in");
  }

  const fromUid = data.fromUid || context.auth.uid;
  const fromPhone = data.fromPhone || "Unknown";
  const lat = data.lat || 0;
  const lng = data.lng || 0;
  const notifyUids = data.notifyUids || [];

  if (!Array.isArray(notifyUids) || notifyUids.length === 0) {
    return { sent: 0, message: "No contacts to notify" };
  }

  const db = admin.firestore();
  let sent = 0;

  for (const uid of notifyUids) {
    try {
      const userDoc = await db.collection("users").doc(uid).get();
      if (!userDoc.exists) continue;
      const token = userDoc.get("fcmToken");
      if (!token) continue;

      await admin.messaging().send({
        token: token,
        data: {
          title: "🚨 SOS ALERT",
          body: `${fromPhone} needs help – open Siren`,
          lat: String(lat),
          lng: String(lng),
          fromPhone: String(fromPhone),
          fromUid: String(fromUid),
        },
        android: {
          priority: "high",
          notification: {
            title: "🚨 SOS ALERT",
            body: `${fromPhone} needs help – open Siren`,
            channelId: "siren_sos_v2",
            priority: "max",
            defaultVibrateTimings: true,
            defaultSound: true,
          },
        },
      });
      sent++;
    } catch (e) {
      console.error("FCM failed for", uid, e);
    }
  }

  return { sent, message: `Push sent to ${sent} contact(s)` };
});
