package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.AccessoryStyle
import com.example.model.CharacterArchetype
import com.example.model.HatStyle
import com.example.model.SkinConfig
import com.example.model.SkinPalettes
import com.example.model.toComposeColor
import com.example.ui.components.SurvivorCharacterPreview
import kotlinx.coroutines.delay
import java.util.UUID

@Composable
fun SkinStudioScreen(
    activeSkin: SkinConfig,
    savedSkins: List<SkinConfig>,
    onSelectSkin: (String) -> Unit,
    onSaveCustomSkin: (SkinConfig) -> Unit,
    onDeleteSkin: (String) -> Unit
) {
    var draftSkin by remember(activeSkin.id) { mutableStateOf(activeSkin) }
    var animateWalk by remember { mutableStateOf(true) }
    var walkPhase by remember { mutableFloatStateOf(0f) }
    var previewSpear by remember { mutableStateOf(false) }
    var savedBanner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(animateWalk) {
        while (animateWalk) {
            delay(40L)
            walkPhase += 0.35f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Canvas Preview Stage
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("自定义角色皮肤工坊", style = MaterialTheme.typography.headlineMedium)
                                Text(
                                    text = "所有联机玩家将实时看到你的装扮",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            SurvivorCharacterPreview(
                                skin = draftSkin,
                                modifier = Modifier.size(160.dp),
                                isMoving = animateWalk,
                                walkPhase = walkPhase,
                                hasTorch = !previewSpear,
                                hasSpear = previewSpear
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("行走动画", style = MaterialTheme.typography.labelLarge)
                                Spacer(modifier = Modifier.width(6.dp))
                                Switch(checked = animateWalk, onCheckedChange = { animateWalk = it })
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("预览长矛", style = MaterialTheme.typography.labelLarge)
                                Spacer(modifier = Modifier.width(6.dp))
                                Switch(checked = previewSpear, onCheckedChange = { previewSpear = it })
                            }
                        }
                    }
                }
            }

            // Saved Skin Presets Carousel
            item {
                Text("已保存皮肤方案 (${savedSkins.size})", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(savedSkins, key = { it.id }) { preset ->
                        val isSelected = preset.id == activeSkin.id
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .width(130.dp)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    draftSkin = preset
                                    onSelectSkin(preset.id)
                                    savedBanner = "已穿戴「${preset.name}」"
                                }
                                .testTag("skin_preset_${preset.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                SurvivorCharacterPreview(skin = preset, modifier = Modifier.size(64.dp))
                                Text(preset.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                if (!preset.id.startsWith("preset_")) {
                                    IconButton(
                                        onClick = { onDeleteSkin(preset.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除皮肤", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Editor Controls
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OutlinedTextField(
                            value = draftSkin.name,
                            onValueChange = { draftSkin = draftSkin.copy(name = it.take(20)) },
                            label = { Text("方案名称") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("skin_name_input")
                        )

                        Text("角色模板（含被动加成）", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CharacterArchetype.entries.forEach { arch ->
                                FilterChip(
                                    selected = draftSkin.archetype == arch.name,
                                    onClick = { draftSkin = draftSkin.copy(archetype = arch.name) },
                                    label = { Text("${arch.label} · ${arch.bonusDesc}") }
                                )
                            }
                        }

                        Text("头饰与发型", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HatStyle.entries.forEach { hat ->
                                FilterChip(
                                    selected = draftSkin.hatStyle == hat.name,
                                    onClick = { draftSkin = draftSkin.copy(hatStyle = hat.name) },
                                    label = { Text(hat.label) }
                                )
                            }
                        }

                        Text("随身配饰", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AccessoryStyle.entries.forEach { acc ->
                                FilterChip(
                                    selected = draftSkin.accessory == acc.name,
                                    onClick = { draftSkin = draftSkin.copy(accessory = acc.name) },
                                    label = { Text(acc.label) }
                                )
                            }
                        }

                        ColorSwatchRow(
                            title = "外套色彩",
                            colors = SkinPalettes.coatColors,
                            selectedHex = draftSkin.coatColorHex,
                            onSelect = { draftSkin = draftSkin.copy(coatColorHex = it) }
                        )
                        ColorSwatchRow(
                            title = "头发色彩",
                            colors = SkinPalettes.hairColors,
                            selectedHex = draftSkin.hairColorHex,
                            onSelect = { draftSkin = draftSkin.copy(hairColorHex = it) }
                        )
                        ColorSwatchRow(
                            title = "角色肤色",
                            colors = SkinPalettes.skinTones,
                            selectedHex = draftSkin.skinToneHex,
                            onSelect = { draftSkin = draftSkin.copy(skinToneHex = it) }
                        )

                        if (savedBanner != null) {
                            Text(savedBanner!!, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val newId = if (draftSkin.id.startsWith("preset_")) {
                                        "custom_${UUID.randomUUID().toString().take(8)}"
                                    } else {
                                        draftSkin.id
                                    }
                                    val finalSkin = draftSkin.copy(
                                        id = newId,
                                        name = draftSkin.name.ifBlank { "荒原新装" }
                                    )
                                    draftSkin = finalSkin
                                    onSaveCustomSkin(finalSkin)
                                    savedBanner = "已保存并穿戴「${finalSkin.name}」"
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("save_skin_button")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("保存并穿戴")
                            }

                            OutlinedButton(
                                onClick = {
                                    val clone = draftSkin.copy(
                                        id = "custom_${UUID.randomUUID().toString().take(8)}",
                                        name = "${draftSkin.name.take(12)}·改"
                                    )
                                    draftSkin = clone
                                    onSaveCustomSkin(clone)
                                    savedBanner = "已另存为新皮肤「${clone.name}」"
                                },
                                modifier = Modifier.height(50.dp)
                            ) {
                                Text("另存新套")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatchRow(
    title: String,
    colors: List<String>,
    selectedHex: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            colors.forEach { hex ->
                val selected = hex.equals(selectedHex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(hex.toComposeColor())
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable { onSelect(hex) },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "已选颜色",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
