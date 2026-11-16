package com.example.model

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

enum class TimePhase(val label: String) {
    DAY("白昼"),
    DUSK("黄昏"),
    NIGHT("暗夜")
}

enum class GameMode(val label: String) {
    OFFLINE_SOLO("离线求生"),
    LAN_COOP("局域网合作"),
    LAN_PVP("局域网对战")
}

enum class ResourceKind(val label: String, val maxHp: Int) {
    PINE_TREE("松树", 3),
    BOULDER("燧石岩", 3),
    BERRY_BUSH("浆果丛", 1),
    FLOWER("野花", 1),
    GRASS_TUFT("干草丛", 1)
}

enum class ItemType(val label: String, val isFood: Boolean = false, val isWeapon: Boolean = false) {
    WOOD("木材"),
    STONE("石块"),
    FLINT("燧石"),
    GRASS("干草"),
    BERRIES("浆果", isFood = true),
    PETALS("花瓣"),
    RAW_MEAT("生肉", isFood = true),
    COOKED_MEAT("烤肉", isFood = true),
    CAMP_STEW("荒原炖菜", isFood = true),
    TORCH("火把", isWeapon = true),
    FLINT_SPEAR("燧石长矛", isWeapon = true),
    LOG_ARMOR("木甲"),
    HEALING_SALVE("草药膏")
}

@JsonClass(generateAdapter = true)
data class ResourceNode(
    val id: String,
    val kind: String,
    val x: Float,
    val y: Float,
    val hp: Int,
    val respawnAtMs: Long = 0L
) {
    fun kindEnum(): ResourceKind =
        ResourceKind.entries.firstOrNull { it.name == kind } ?: ResourceKind.PINE_TREE
}

enum class CreatureKind(val label: String, val maxHp: Int, val damage: Int, val speed: Float) {
    BOAR("荒原野猪", 60, 12, 85f),
    SHADOW_STALKER("暗影潜行者", 45, 18, 125f)
}

@JsonClass(generateAdapter = true)
data class WorldCreature(
    val id: String,
    val kind: String,
    val x: Float,
    val y: Float,
    val hp: Int,
    val maxHp: Int,
    val vx: Float = 0f,
    val vy: Float = 0f
) {
    fun kindEnum(): CreatureKind =
        CreatureKind.entries.firstOrNull { it.name == kind } ?: CreatureKind.BOAR
}

data class CraftRecipe(
    val id: String,
    val resultItem: ItemType,
    val resultCount: Int = 1,
    val title: String,
    val description: String,
    val costs: Map<ItemType, Int>,
    val requiresCampfire: Boolean = false
)

object SurvivalRecipes {
    val all = listOf(
        CraftRecipe(
            id = "craft_torch",
            resultItem = ItemType.TORCH,
            title = "火把",
            description = "夜间照明驱散黑暗与理智流失",
            costs = mapOf(ItemType.WOOD to 2, ItemType.GRASS to 2)
        ),
        CraftRecipe(
            id = "craft_spear",
            resultItem = ItemType.FLINT_SPEAR,
            title = "燧石长矛",
            description = "攻击力提升至 28，适合狩猎与对战",
            costs = mapOf(ItemType.WOOD to 2, ItemType.FLINT to 2)
        ),
        CraftRecipe(
            id = "craft_armor",
            resultItem = ItemType.LOG_ARMOR,
            title = "木甲",
            description = "装备后减少 50% 所受伤害",
            costs = mapOf(ItemType.WOOD to 4, ItemType.GRASS to 2)
        ),
        CraftRecipe(
            id = "craft_cooked_meat",
            resultItem = ItemType.COOKED_MEAT,
            title = "炭烤肉排",
            description = "篝火旁烹制：饥饿 +35，生命 +15",
            costs = mapOf(ItemType.RAW_MEAT to 1),
            requiresCampfire = true
        ),
        CraftRecipe(
            id = "craft_stew",
            resultItem = ItemType.CAMP_STEW,
            title = "荒原炖菜",
            description = "饥饿 +55，生命 +25，理智 +15",
            costs = mapOf(ItemType.RAW_MEAT to 1, ItemType.BERRIES to 2),
            requiresCampfire = true
        ),
        CraftRecipe(
            id = "craft_salve",
            resultItem = ItemType.HEALING_SALVE,
            title = "草药膏",
            description = "使用后立即恢复 35 点生命",
            costs = mapOf(ItemType.PETALS to 2, ItemType.STONE to 1)
        )
    )
}

@JsonClass(generateAdapter = true)
data class RemotePlayerState(
    val playerId: String,
    val nickname: String,
    val email: String = "",
    val x: Float,
    val y: Float,
    val facingRight: Boolean = true,
    val isAttacking: Boolean = false,
    val hp: Int = 100,
    val skin: SkinConfig = SkinConfig()
)

@JsonClass(generateAdapter = true)
data class WorldSavePayload(
    val seed: Long = 1042L,
    val worldName: String = "荒原营地",
    val gameMode: String = GameMode.OFFLINE_SOLO.name,
    val dayCount: Int = 1,
    val dayProgress: Float = 0.08f, // 0f..1f across Day -> Dusk -> Night
    val playerX: Float = 1200f,
    val playerY: Float = 1200f,
    val health: Int = 100,
    val hunger: Int = 100,
    val sanity: Int = 100,
    val campfireFuel: Float = 75f, // 0f..100f
    val hasTorchEquipped: Boolean = false,
    val hasSpearEquipped: Boolean = false,
    val hasArmorEquipped: Boolean = false,
    val inventory: Map<String, Int> = mapOf(
        ItemType.WOOD.name to 4,
        ItemType.FLINT.name to 2,
        ItemType.GRASS.name to 3,
        ItemType.BERRIES.name to 3
    ),
    val resources: List<ResourceNode> = emptyList(),
    val creatures: List<WorldCreature> = emptyList(),
    val kills: Int = 0
) {
    fun toJson(): String = WorldPayloadJson.adapter.toJson(this)

    companion object {
        fun fromJson(json: String): WorldSavePayload? = runCatching {
            WorldPayloadJson.adapter.fromJson(json)
        }.getOrNull()
    }
}

private object WorldPayloadJson {
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    val adapter = moshi.adapter(WorldSavePayload::class.java)
}

data class GameWorldUiState(
    val saveSlotId: String = "autosave_main",
    val worldName: String = "荒原营地",
    val seed: Long = 1042L,
    val gameMode: GameMode = GameMode.OFFLINE_SOLO,
    val dayCount: Int = 1,
    val dayProgress: Float = 0.08f,
    val timePhase: TimePhase = TimePhase.DAY,
    val playerX: Float = 1200f,
    val playerY: Float = 1200f,
    val facingRight: Boolean = true,
    val isMoving: Boolean = false,
    val attackAnimTicks: Int = 0,
    val health: Int = 100,
    val hunger: Int = 100,
    val sanity: Int = 100,
    val campfireFuel: Float = 75f,
    val campfireX: Float = 1200f,
    val campfireY: Float = 1200f,
    val hasTorchEquipped: Boolean = false,
    val hasSpearEquipped: Boolean = false,
    val hasArmorEquipped: Boolean = false,
    val inventory: Map<ItemType, Int> = mapOf(
        ItemType.WOOD to 4,
        ItemType.FLINT to 2,
        ItemType.GRASS to 3,
        ItemType.BERRIES to 3
    ),
    val resources: List<ResourceNode> = emptyList(),
    val creatures: List<WorldCreature> = emptyList(),
    val remotePlayers: Map<String, RemotePlayerState> = emptyMap(),
    val kills: Int = 0,
    val isNearCampfire: Boolean = true,
    val isGameOver: Boolean = false,
    val statusBanner: String = "采集木材维持篝火，黑夜降临前注意保持理智",
    val lastAutoSaveAtMs: Long = 0L
)
