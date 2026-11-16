package com.example.model

import androidx.compose.ui.graphics.Color
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

enum class CharacterArchetype(val label: String, val bonusDesc: String) {
    SCHOLAR("学者·威尔", "理智恢复 +20%"),
    VALKYRIE("女武神·芙蕾", "近战伤害 +25%"),
    PYROMANCER("控火者·薇洛", "夜间抗暗影 +30%"),
    AUTOMATON("发条·维克", "饥饿消耗 -20%")
}

enum class HatStyle(val label: String) {
    NONE("凌乱短发"),
    EXPLORER_HAT("荒原草帽"),
    VIKING_HELM("牛角战盔"),
    FLOWER_CROWN("野花花环"),
    LANTERN_HOOD("守夜兜帽"),
    GOGGLES("探险护目镜")
}

enum class AccessoryStyle(val label: String) {
    NONE("轻装上阵"),
    BACKPACK("皮质行囊"),
    CAPE("破旧斗篷"),
    LANTERN_BELT("腰间提灯"),
    QUIVER("猎人箭袋")
}

@JsonClass(generateAdapter = true)
data class SkinConfig(
    val id: String = "preset_scholar",
    val name: String = "经典探险家",
    val archetype: String = CharacterArchetype.SCHOLAR.name,
    val hatStyle: String = HatStyle.EXPLORER_HAT.name,
    val coatColorHex: String = "#E87A3E",
    val hairColorHex: String = "#2A2421",
    val skinToneHex: String = "#F1C7A8",
    val accessory: String = AccessoryStyle.BACKPACK.name
) {
    fun archetypeEnum(): CharacterArchetype =
        CharacterArchetype.entries.firstOrNull { it.name == archetype } ?: CharacterArchetype.SCHOLAR

    fun hatEnum(): HatStyle =
        HatStyle.entries.firstOrNull { it.name == hatStyle } ?: HatStyle.EXPLORER_HAT

    fun accessoryEnum(): AccessoryStyle =
        AccessoryStyle.entries.firstOrNull { it.name == accessory } ?: AccessoryStyle.BACKPACK

    fun toJson(): String = SkinJson.adapter.toJson(this)

    companion object {
        fun fromJson(json: String): SkinConfig? = runCatching {
            SkinJson.adapter.fromJson(json)
        }.getOrNull()
    }
}

private object SkinJson {
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    val adapter = moshi.adapter(SkinConfig::class.java)
}

object SkinPalettes {
    val skinTones = listOf("#F5D6BA", "#F1C7A8", "#D9A077", "#A96E47", "#6B4229", "#C5D4C0")
    val coatColors = listOf("#E87A3E", "#8E3B2E", "#3D5A47", "#4D456E", "#C99A2E", "#2C2B30", "#3B6B8C", "#A84E79")
    val hairColors = listOf("#1F1B1E", "#5A3825", "#B5482A", "#D9B45A", "#D8D2C8", "#5E4F7A")

    val defaultPresets = listOf(
        SkinConfig(
            id = "preset_scholar",
            name = "余烬学者",
            archetype = CharacterArchetype.SCHOLAR.name,
            hatStyle = HatStyle.EXPLORER_HAT.name,
            coatColorHex = "#E87A3E",
            hairColorHex = "#1F1B1E",
            skinToneHex = "#F5D6BA",
            accessory = AccessoryStyle.BACKPACK.name
        ),
        SkinConfig(
            id = "preset_valkyrie",
            name = "霜原女武神",
            archetype = CharacterArchetype.VALKYRIE.name,
            hatStyle = HatStyle.VIKING_HELM.name,
            coatColorHex = "#8E3B2E",
            hairColorHex = "#B5482A",
            skinToneHex = "#F1C7A8",
            accessory = AccessoryStyle.CAPE.name
        ),
        SkinConfig(
            id = "preset_pyro",
            name = "守夜旅人",
            archetype = CharacterArchetype.PYROMANCER.name,
            hatStyle = HatStyle.LANTERN_HOOD.name,
            coatColorHex = "#4D456E",
            hairColorHex = "#5A3825",
            skinToneHex = "#D9A077",
            accessory = AccessoryStyle.LANTERN_BELT.name
        ),
        SkinConfig(
            id = "preset_meadow",
            name = "花环采集者",
            archetype = CharacterArchetype.AUTOMATON.name,
            hatStyle = HatStyle.FLOWER_CROWN.name,
            coatColorHex = "#3D5A47",
            hairColorHex = "#D9B45A",
            skinToneHex = "#F5D6BA",
            accessory = AccessoryStyle.QUIVER.name
        )
    )
}

fun String.toComposeColor(fallback: Color = Color(0xFFE87A3E)): Color =
    runCatching { Color(android.graphics.Color.parseColor(this)) }.getOrDefault(fallback)
