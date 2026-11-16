package com.example.data.cloud

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.local.WorldSaveEntity
import com.example.model.SkinConfig
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CloudBackupRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun backupAndReadSave_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = CloudBackupRepository(firestore)
        val slotId = "save_${UUID.randomUUID().toString().take(8)}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertCloudProfile("Lina", ALICE_EMAIL, "preset_scholar")
            repo.backupWorldSave(
                WorldSaveEntity(
                    id = slotId,
                    worldName = "Twilight Camp",
                    gameMode = "OFFLINE_SOLO",
                    dayCount = 4,
                    timePhase = "DUSK",
                    health = 90,
                    hunger = 80,
                    sanity = 85,
                    payloadJson = "{\"seed\":1042}"
                )
            )
            repo.backupSkinPreset(
                SkinConfig(
                    id = "skin_${UUID.randomUUID().toString().take(8)}",
                    name = "Ember Scout"
                )
            )
        }

        val saves = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeCloudSaves(uid).first { list -> list.any { it.id == slotId } }
        }
        assertTrue(saves.any { it.id == slotId && it.dayCount == 4L })
    }

    @Test
    fun observeCloudSaves_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CloudBackupRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.backupWorldSave(
                WorldSaveEntity(
                    id = "alice_slot_${UUID.randomUUID().toString().take(6)}",
                    worldName = "Alice Camp",
                    gameMode = "OFFLINE_SOLO",
                    dayCount = 2,
                    timePhase = "DAY",
                    health = 100,
                    hunger = 95,
                    sanity = 95,
                    payloadJson = "{\"seed\":9}"
                )
            )
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = CloudBackupRepository(firestore)
        var captured: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeCloudSaves(aliceUid).first()
            }
        } catch (t: Throwable) {
            captured = t
        }
        val fsEx = captured?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun observeCloudSaves_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = CloudBackupRepository(firestore)
        var captured: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeCloudSaves("some_uid").first()
            }
        } catch (t: Throwable) {
            captured = t
        }
        val fsEx = captured?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_wildhearth@test.com"
        const val BOB_EMAIL = "bob_wildhearth@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3500L
    }
}
