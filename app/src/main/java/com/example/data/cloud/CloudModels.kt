package com.example.data.cloud

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserCloudProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val nickname: String = "",
    val email: String = "",
    val activeSkinId: String = "preset_scholar",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudWorldSave(
    @DocumentId val id: String = "",
    val userId: String = "",
    val worldName: String = "",
    val dayCount: Long = 1L,
    val timePhase: String = "DAY",
    val health: Long = 100L,
    val hunger: Long = 100L,
    val sanity: Long = 100L,
    val payloadJson: String = "{}",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudSkinPreset(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val archetype: String = "SCHOLAR",
    val hatStyle: String = "EXPLORER_HAT",
    val coatColorHex: String = "#E87A3E",
    val hairColorHex: String = "#1F1B1E",
    val skinToneHex: String = "#F5D6BA",
    val accessory: String = "BACKPACK",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
