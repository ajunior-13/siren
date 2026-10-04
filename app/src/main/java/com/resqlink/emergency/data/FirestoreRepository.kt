package com.Siren.Siren.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class EmergencyContact(val name: String, val phone: String)

/**
 * Firestore + callable Cloud Function for cross-device SOS.
 *
 * users/{uid}
 *   phone, fcmToken, updatedAt
 * users/{uid}/contacts/{otherUid}
 *   name, phone, otherUid
 * phoneIndex/{normalizedPhone} -> { uid }   // so we can find a user by phone number
 * sos/{uid}
 *   active, lat, lng, accuracy, fromPhone, fromUid, notifyUids[], updatedAt
 */
class FirestoreRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val functions = FirebaseFunctions.getInstance()

    private fun uid() = auth.currentUser?.uid
        ?: throw IllegalStateException("Not logged in")

    private fun normalize(phone: String) =
        phone.trim().replace(" ", "").replace("-", "")

    // ---------- Profile ----------

    suspend fun registerOrUpdateProfile() {
        val user = auth.currentUser ?: return
        val phone = user.phoneNumber ?: return
        val token = try {
            FirebaseMessaging.getInstance().token.await()
        } catch (_: Exception) {
            null
        }
        val data = hashMapOf<String, Any>(
            "phone" to phone,
            "updatedAt" to System.currentTimeMillis()
        )
        if (token != null) data["fcmToken"] = token

        db.collection("users").document(user.uid).set(data, SetOptions.merge()).await()
        // Index by phone so others can add this user by number
        db.collection("phoneIndex").document(normalize(phone))
            .set(mapOf("uid" to user.uid, "phone" to phone)).await()
    }

    suspend fun refreshFcmToken() {
        val user = auth.currentUser ?: return
        val token = FirebaseMessaging.getInstance().token.await()
        db.collection("users").document(user.uid)
            .set(mapOf("fcmToken" to token, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
            .await()
    }

    // ---------- Contacts ----------

    fun observeContacts(): Flow<List<EmergencyContact>> = callbackFlow {
        val myUid = auth.currentUser?.uid
        if (myUid == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val reg = db.collection("users").document(myUid).collection("contacts")
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { doc ->
                    val name = doc.getString("name") ?: return@mapNotNull null
                    val phone = doc.getString("phone") ?: return@mapNotNull null
                    EmergencyContact(name, phone)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    /**
     * Add contact by phone number. Looks up phoneIndex to get their uid.
     * The other person must have logged in at least once so their phone is indexed.
     */
    suspend fun addContactByPhone(name: String, phone: String) {
        val myUid = uid()
        val norm = normalize(phone)
        val index = db.collection("phoneIndex").document(norm).get().await()
        if (!index.exists()) {
            throw IllegalStateException(
                "No Siren user with that number yet. They must open the app and log in first."
            )
        }
        val otherUid = index.getString("uid")
            ?: throw IllegalStateException("Invalid phone index")
        val otherPhone = index.getString("phone") ?: phone

        db.collection("users").document(myUid)
            .collection("contacts").document(otherUid)
            .set(
                mapOf(
                    "name" to name.trim(),
                    "phone" to otherPhone,
                    "otherUid" to otherUid
                )
            ).await()
    }

    suspend fun removeContact(otherPhone: String) {
        val myUid = uid()
        val contacts = db.collection("users").document(myUid).collection("contacts").get().await()
        contacts.documents
            .filter { it.getString("phone") == otherPhone || normalize(it.getString("phone") ?: "") == normalize(otherPhone) }
            .forEach { it.reference.delete().await() }
    }

    private suspend fun contactUids(): List<String> {
        val myUid = uid()
        val snap = db.collection("users").document(myUid).collection("contacts").get().await()
        return snap.documents.mapNotNull { it.getString("otherUid") ?: it.id }
    }

    // ---------- SOS ----------

    data class SosState(
        val active: Boolean,
        val lat: Double,
        val lng: Double,
        val accuracy: Float,
        val fromPhone: String,
        val fromUid: String,
        val updatedAt: Long
    )

    /**
     * Publish live location. On first activation, also calls Cloud Function
     * to push FCM to emergency contacts (works even if their app is closed).
     */
    suspend fun publishSos(
        lat: Double,
        lng: Double,
        accuracy: Float,
        notifyPush: Boolean
    ) {
        val user = auth.currentUser ?: return
        val myUid = user.uid
        val phone = user.phoneNumber ?: ""
        val notify = contactUids()

        db.collection("sos").document(myUid).set(
            mapOf(
                "active" to true,
                "lat" to lat,
                "lng" to lng,
                "accuracy" to accuracy,
                "fromPhone" to phone,
                "fromUid" to myUid,
                "notifyUids" to notify,
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()

        if (notifyPush && notify.isNotEmpty()) {
            // Cloud Function sends high-priority FCM to each contact
            try {
                functions.getHttpsCallable("sendSosPush")
                    .call(
                        hashMapOf(
                            "fromUid" to myUid,
                            "fromPhone" to phone,
                            "lat" to lat,
                            "lng" to lng,
                            "notifyUids" to notify
                        )
                    ).await()
            } catch (_: Exception) {
                // Function may not be deployed yet; in-app listener still works
            }
        }
    }

    suspend fun stopSos() {
        val myUid = uid()
        db.collection("sos").document(myUid).set(
            mapOf(
                "active" to false,
                "fromUid" to myUid,
                "updatedAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).await()
    }

    fun observeIncomingSos(): Flow<SosState?> = callbackFlow {
        val myUid = auth.currentUser?.uid
        if (myUid == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }

        // Listen to SOS docs of people in my contact list
        var contactReg: ListenerRegistration? = null
        val sosRegs = mutableListOf<ListenerRegistration>()
        val latest = mutableMapOf<String, SosState>()

        fun emitBest() {
            trySend(latest.values.filter { it.active }.maxByOrNull { it.updatedAt })
        }

        fun attachSosListeners(uids: List<String>) {
            sosRegs.forEach { it.remove() }
            sosRegs.clear()
            latest.keys.retainAll(uids.toSet())
            uids.forEach { otherUid ->
                val reg = db.collection("sos").document(otherUid)
                    .addSnapshotListener { snap, _ ->
                        if (snap != null && snap.exists()) {
                            latest[otherUid] = SosState(
                                active = snap.getBoolean("active") ?: false,
                                lat = snap.getDouble("lat") ?: 0.0,
                                lng = snap.getDouble("lng") ?: 0.0,
                                accuracy = (snap.getDouble("accuracy") ?: 0.0).toFloat(),
                                fromPhone = snap.getString("fromPhone") ?: "",
                                fromUid = snap.getString("fromUid") ?: otherUid,
                                updatedAt = snap.getLong("updatedAt") ?: 0L
                            )
                        } else {
                            latest.remove(otherUid)
                        }
                        emitBest()
                    }
                sosRegs.add(reg)
            }
            emitBest()
        }

        contactReg = db.collection("users").document(myUid).collection("contacts")
            .addSnapshotListener { snap, _ ->
                val uids = snap?.documents?.mapNotNull { it.getString("otherUid") ?: it.id } ?: emptyList()
                attachSosListeners(uids)
            }

        awaitClose {
            contactReg?.remove()
            sosRegs.forEach { it.remove() }
        }
    }
}
