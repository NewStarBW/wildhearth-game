package com.example.data.cloud

import android.content.Context
import com.example.R
import com.example.data.local.WorldSaveEntity
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.SkinConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CloudBackupRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("请先绑定 Google 账号后再执行云端备份。")
    }

    fun observeCloudSaves(userId: String): Flow<List<CloudWorldSave>> = flow {
        val path = "users/$userId/saves"
        emitAll(
            db.collection("users").document(userId).collection("saves")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        CloudWorldSave::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCloudSkins(userId: String): Flow<List<CloudSkinPreset>> = flow {
        val path = "users/$userId/skins"
        emitAll(
            db.collection("users").document(userId).collection("skins")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        CloudSkinPreset::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertCloudProfile(nickname: String, email: String, activeSkinId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "nickname" to nickname.take(30).ifBlank { "幸存者" },
                        "email" to email.take(120).ifBlank { "survivor@local" },
                        "activeSkinId" to activeSkinId.take(128).ifBlank { "preset_scholar" },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "nickname" to nickname.take(30).ifBlank { "幸存者" },
                        "email" to email.take(120).ifBlank { "survivor@local" },
                        "activeSkinId" to activeSkinId.take(128).ifBlank { "preset_scholar" },
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun backupWorldSave(save: WorldSaveEntity) {
        val uid = requireUserId()
        val safeId = save.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120).ifBlank { "autosave_main" }
        val docRef = db.collection("users").document(uid).collection("saves").document(safeId)
        val safePhase = if (save.timePhase in listOf("DAY", "DUSK", "NIGHT")) save.timePhase else "DAY"
        try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "worldName" to save.worldName.take(50).ifBlank { "荒原营地" },
                        "dayCount" to save.dayCount.coerceIn(1, 99999),
                        "timePhase" to safePhase,
                        "health" to save.health.coerceIn(0, 100),
                        "hunger" to save.hunger.coerceIn(0, 100),
                        "sanity" to save.sanity.coerceIn(0, 100),
                        "payloadJson" to save.payloadJson.take(190000),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "worldName" to save.worldName.take(50).ifBlank { "荒原营地" },
                        "dayCount" to save.dayCount.coerceIn(1, 99999),
                        "timePhase" to safePhase,
                        "health" to save.health.coerceIn(0, 100),
                        "hunger" to save.hunger.coerceIn(0, 100),
                        "sanity" to save.sanity.coerceIn(0, 100),
                        "payloadJson" to save.payloadJson.take(190000),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun backupSkinPreset(skin: SkinConfig) {
        val uid = requireUserId()
        val safeId = skin.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120).ifBlank { "custom_skin" }
        val docRef = db.collection("users").document(uid).collection("skins").document(safeId)
        try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "name" to skin.name.take(40).ifBlank { "自定义皮肤" },
                        "archetype" to skin.archetype.take(30),
                        "hatStyle" to skin.hatStyle.take(30),
                        "coatColorHex" to skin.coatColorHex.take(12),
                        "hairColorHex" to skin.hairColorHex.take(12),
                        "skinToneHex" to skin.skinToneHex.take(12),
                        "accessory" to skin.accessory.take(30),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "name" to skin.name.take(40).ifBlank { "自定义皮肤" },
                        "archetype" to skin.archetype.take(30),
                        "hatStyle" to skin.hatStyle.take(30),
                        "coatColorHex" to skin.coatColorHex.take(12),
                        "hairColorHex" to skin.hairColorHex.take(12),
                        "skinToneHex" to skin.skinToneHex.take(12),
                        "accessory" to skin.accessory.take(30),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun getCloudSavesOnce(): List<CloudWorldSave> {
        val uid = requireUserId()
        val path = "users/$uid/saves"
        return try {
            db.collection("users").document(uid).collection("saves")
                .get()
                .await()
                .toObjects(
                    CloudWorldSave::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }
}
