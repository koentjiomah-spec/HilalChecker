package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.domain.falak.HilalResult
import com.example.domain.falak.MarkazLocation
import com.example.domain.falak.RukyatCriteria
import com.example.domain.falak.VerdictStatus
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FalakCloudRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus masuk dengan Google sebelum mengakses Cloud Firestore.")
    }

    fun observeUserProfile(userId: String): Flow<UserFalakProfile?> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserFalakProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeObservations(userId: String): Flow<List<RukyatObservation>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/observations"
        emitAll(
            db.collection("users").document(userId).collection("observations")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        RukyatObservation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun syncUserProfile(
        displayName: String,
        email: String?,
        markaz: MarkazLocation,
        criteria: RukyatCriteria
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val existingSnap = docRef.get().await()

            val safeName = displayName.trim().ifBlank { "Pengamat Falak" }.take(100)
            val safeEmail = email?.trim()?.takeIf { it.isNotEmpty() }?.take(200)
            val safeMarkazId = markaz.id.trim().ifBlank { "custom" }.take(64)
            val safeMarkazName = markaz.name.trim().ifBlank { "Markaz" }.take(120)

            if (!existingSnap.exists()) {
                val createPayload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to safeName,
                    "preferredMarkazId" to safeMarkazId,
                    "preferredMarkazName" to safeMarkazName,
                    "preferredCriteria" to criteria.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (safeEmail != null) {
                    createPayload["email"] = safeEmail
                }
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mutableMapOf<String, Any>(
                    "displayName" to safeName,
                    "preferredMarkazId" to safeMarkazId,
                    "preferredMarkazName" to safeMarkazName,
                    "preferredCriteria" to criteria.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (safeEmail != null) {
                    updatePayload["email"] = safeEmail
                }
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveRukyatObservation(
        hilal: HilalResult,
        criteria: RukyatCriteria,
        hilalSighted: Boolean,
        notes: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/observations"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("observations").document()
            docPath = docRef.path

            val verdict = hilal.verdicts[criteria] ?: hilal.verdicts.values.first()
            val statusStr = when (verdict.status) {
                VerdictStatus.MEMENUHI, VerdictStatus.MARGINAL, VerdictStatus.TIDAK_MEMENUHI -> verdict.status.name
            }

            val payload = mapOf<String, Any>(
                "userId" to uid,
                "hijriYear" to hilal.hijriYear.coerceIn(1400, 1500),
                "hijriMonth" to hilal.hijriMonth.coerceIn(1, 12),
                "hijriMonthName" to hilal.hijriMonthName.ifBlank { "Hijriah" }.take(40),
                "observationDateLabel" to hilal.observationDateLabel.ifBlank { "-" }.take(80),
                "markazName" to hilal.markaz.name.ifBlank { "Markaz" }.take(120),
                "latitude" to hilal.markaz.latitude.coerceIn(-90.0, 90.0),
                "longitude" to hilal.markaz.longitude.coerceIn(-180.0, 180.0),
                "moonAltitudeMariDeg" to hilal.moonAltitudeMariDeg.coerceIn(-90.0, 90.0),
                "elongationTopoDeg" to hilal.elongationTopoDeg.coerceIn(0.0, 180.0),
                "criteriaCode" to criteria.shortCode.ifBlank { "MABIMS" }.take(40),
                "verdictSummary" to verdict.summary.ifBlank { statusStr }.take(120),
                "visibilityStatus" to statusStr,
                "hilalSighted" to hilalSighted,
                "notes" to notes.trim().take(500),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateRukyatObservation(
        observationId: String,
        hilalSighted: Boolean,
        notes: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/observations/$observationId"
        return try {
            val uid = requireUserId()
            require(observationId.isNotBlank()) { "Observation ID must not be blank" }
            val docRef = db.collection("users").document(uid)
                .collection("observations").document(observationId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "hilalSighted" to hilalSighted,
                    "notes" to notes.trim().take(500),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteRukyatObservation(observationId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/observations/$observationId"
        return try {
            val uid = requireUserId()
            require(observationId.isNotBlank()) { "Observation ID must not be blank" }
            val docRef = db.collection("users").document(uid)
                .collection("observations").document(observationId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
