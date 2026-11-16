package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.CraftRecipe
import com.example.model.CreatureKind
import com.example.model.GameMode
import com.example.model.GameWorldUiState
import com.example.model.ItemType
import com.example.model.ResourceKind
import com.example.model.SkinConfig
import com.example.model.SurvivalRecipes
import com.example.model.TimePhase
import com.example.network.LanSessionState
import com.example.network.VoiceChatState
import com.example.ui.components.drawSurvivorCharacter
import com.example.ui.theme.CampfireEmber
import com.example.ui.theme.ForestMoss
import com.example.ui.theme.HungerAmber
import com.example.ui.theme.LanternGold
import com.example.ui.theme.SanityViolet
import com.example.ui.theme.VitalityCrimson
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameWorldScreen(
    gameState: GameWorldUiState,
    activeSkin: SkinConfig,
    lanState: LanSessionState,
    voiceState: VoiceChatState,
    onSetGameActive: (Boolean) -> Unit,
    onJoystickMove: (dx: Float, dy: Float) -> Unit,
    onGatherOrAttack: () -> Unit,
    onFeedCampfire: () -> Unit,
    onUseItem: (ItemType) -> Unit,
    onCraftRecipe: (CraftRecipe) -> Unit,
    onRevive: () -> Unit,
    onToggleMic: (Boolean) -> Unit,
    onToggleSpeaker: (Boolean) -> Unit,
    onExitToHub: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val textMeasurer = rememberTextMeasurer()

    var showCraftSheet by remember { mutableStateOf(false) }
    var animTime by remember { mutableFloatStateOf(0f) }

    BackHandler {
        onSetGameActive(false)
        onExitToHub()
    }

    DisposableEffect(lifecycleOwner) {
        onSetGameActive(true)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> onSetGameActive(true)
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> onSetGameActive(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onSetGameActive(false)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(32L)
            animTime += 0.16f
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onToggleMic(true)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 60fps Survival World Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            val camX = gameState.playerX - size.width / 2f
            val camY = gameState.playerY - size.height / 2f

            // Ground Biome Tint
            val groundColor = when (gameState.timePhase) {
                TimePhase.DAY -> Color(0xFF283226)
                TimePhase.DUSK -> Color(0xFF272124)
                TimePhase.NIGHT -> Color(0xFF17161C)
            }
            drawRect(color = groundColor)

            // Procedural Grid & Turf Patches
            val step = 120f
            val startX = (-(camX % step))
            val startY = (-(camY % step))
            var gx = startX
            while (gx < size.width) {
                drawLine(
                    color = Color.White.copy(alpha = 0.03f),
                    start = Offset(gx, 0f),
                    end = Offset(gx, size.height),
                    strokeWidth = 1f
                )
                gx += step
            }
            var gy = startY
            while (gy < size.height) {
                drawLine(
                    color = Color.White.copy(alpha = 0.03f),
                    start = Offset(0f, gy),
                    end = Offset(size.width, gy),
                    strokeWidth = 1f
                )
                gy += step
            }

            // Campfire Stone Circle + Glowing Fire
            val campScreen = Offset(gameState.campfireX - camX, gameState.campfireY - camY)
            drawCampfire(campScreen, gameState.campfireFuel, animTime)

            // Resource Nodes
            gameState.resources.forEach { node ->
                if (node.hp <= 0) return@forEach
                val sx = node.x - camX
                val sy = node.y - camY
                if (sx !in -90f..(size.width + 90f) || sy !in -90f..(size.height + 90f)) return@forEach
                drawResourceNode(Offset(sx, sy), node.kindEnum(), animTime)
            }

            // World Creatures
            gameState.creatures.forEach { creature ->
                val sx = creature.x - camX
                val sy = creature.y - camY
                if (sx !in -90f..(size.width + 90f) || sy !in -90f..(size.height + 90f)) return@forEach
                drawWorldCreature(Offset(sx, sy), creature.kindEnum(), creature.hp, creature.maxHp, animTime)
            }

            // Remote LAN Players
            gameState.remotePlayers.values.forEach { remote ->
                val rx = remote.x - camX
                val ry = remote.y - camY
                if (rx in -120f..(size.width + 120f) && ry in -120f..(size.height + 120f)) {
                    drawSurvivorCharacter(
                        center = Offset(rx, ry),
                        scale = 1.05f,
                        skin = remote.skin,
                        facingRight = remote.facingRight,
                        walkBob = sin(animTime * 2f) * 2f,
                        isAttacking = remote.isAttacking,
                        hasTorch = false,
                        hasSpear = gameState.gameMode == GameMode.LAN_PVP
                    )
                    drawPlayerLabel(textMeasurer, remote.nickname, Offset(rx, ry - 60f))
                }
            }

            // Local Survivor Player (centered)
            val playerScreen = Offset(size.width / 2f, size.height / 2f)
            drawSurvivorCharacter(
                center = playerScreen,
                scale = 1.1f,
                skin = activeSkin,
                facingRight = gameState.facingRight,
                walkBob = if (gameState.isMoving) sin(animTime * 3.2f) * 3.2f else 0f,
                isAttacking = gameState.attackAnimTicks > 0,
                hasTorch = gameState.hasTorchEquipped,
                hasSpear = gameState.hasSpearEquipped
            )

            // Night & Dusk Darkness Vignette with Light Cutouts
            val darkAlpha = when (gameState.timePhase) {
                TimePhase.DAY -> 0f
                TimePhase.DUSK -> 0.34f
                TimePhase.NIGHT -> 0.80f
            }
            if (darkAlpha > 0f) {
                drawRect(color = Color(0xFF07060A).copy(alpha = darkAlpha))
                if (gameState.campfireFuel > 1f) {
                    val lightRadius = (150f + gameState.campfireFuel * 1.9f) + sin(animTime * 4f) * 6f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            center = campScreen,
                            radius = lightRadius
                        ),
                        radius = lightRadius,
                        center = campScreen,
                        blendMode = BlendMode.DstOut
                    )
                }
                if (gameState.hasTorchEquipped) {
                    val torchRadius = 210f + sin(animTime * 5f) * 8f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            center = playerScreen,
                            radius = torchRadius
                        ),
                        radius = torchRadius,
                        center = playerScreen,
                        blendMode = BlendMode.DstOut
                    )
                }
            }
        }

        // Top Survival HUD
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back + Day/Mode Pill
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = {
                            onSetGameActive(false)
                            onExitToHub()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                            .testTag("game_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回营地")
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text(
                                text = "第 ${gameState.dayCount} 天 · ${gameState.timePhase.label}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "${gameState.gameMode.label} · ${gameState.remotePlayers.size + 1}人",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Voice Chat + Auto-save Status Pill
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (gameState.lastAutoSaveAtMs > 0L) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, tint = ForestMoss, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("自动存档", style = MaterialTheme.typography.labelSmall, color = ForestMoss)
                            }
                        }
                    }

                    if (lanState.isConnected) {
                        IconButton(
                            onClick = {
                                if (voiceState.isMicEnabled) onToggleMic(false)
                                else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) onToggleMic(true)
                                    else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (voiceState.isMicEnabled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                                )
                                .testTag("hud_mic_button")
                        ) {
                            Icon(
                                imageVector = if (voiceState.isMicEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = "语音麦克风"
                            )
                        }

                        IconButton(
                            onClick = { onToggleSpeaker(!voiceState.isSpeakerEnabled) },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                                .testTag("hud_speaker_button")
                        ) {
                            Icon(
                                imageVector = if (voiceState.isSpeakerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = "语音扬声器"
                            )
                        }
                    }
                }
            }

            // 4 Survival Vital Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VitalChip("❤️ ${gameState.health}", VitalityCrimson, Modifier.weight(1f))
                VitalChip("🍖 ${gameState.hunger}", HungerAmber, Modifier.weight(1f))
                VitalChip("🧠 ${gameState.sanity}", SanityViolet, Modifier.weight(1f))
                VitalChip("🔥 ${gameState.campfireFuel.toInt()}%", CampfireEmber, Modifier.weight(1f))
            }

            // Status Hint Banner
            Surface(
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = gameState.statusBanner,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }

        // Bottom Controls Overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Quick Inventory Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ItemType.entries.forEach { item ->
                    val qty = gameState.inventory[item] ?: 0
                    if (qty > 0) {
                        val equipped = (item == ItemType.TORCH && gameState.hasTorchEquipped) ||
                            (item == ItemType.FLINT_SPEAR && gameState.hasSpearEquipped) ||
                            (item == ItemType.LOG_ARMOR && gameState.hasArmorEquipped)
                        Surface(
                            color = if (equipped) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .border(
                                    width = if (equipped) 1.5.dp else 1.dp,
                                    color = if (equipped) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onUseItem(item) }
                        ) {
                            Text(
                                text = "${item.label} x$qty",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Joystick (Left) + Action Buttons (Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                VirtualJoystick(
                    onVectorChanged = onJoystickMove,
                    modifier = Modifier.testTag("game_joystick")
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onFeedCampfire,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(54.dp)
                                .testTag("game_feed_fire_button")
                        ) {
                            Icon(Icons.Default.LocalFireDepartment, contentDescription = "篝火添柴", tint = CampfireEmber)
                        }
                        Button(
                            onClick = { showCraftSheet = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(54.dp)
                                .testTag("game_craft_button")
                        ) {
                            Icon(Icons.Default.Build, contentDescription = "打开合成台", tint = MaterialTheme.colorScheme.onSecondary)
                        }
                    }

                    Button(
                        onClick = onGatherOrAttack,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(74.dp)
                            .testTag("game_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsMartialArts,
                            contentDescription = "采集或攻击",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }

    if (showCraftSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCraftSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("荒原合成工坊", style = MaterialTheme.typography.headlineMedium)
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(340.dp)
                ) {
                    items(SurvivalRecipes.all, key = { it.id }) { recipe ->
                        val canCraft = recipe.costs.all { (item, qty) -> (gameState.inventory[item] ?: 0) >= qty } &&
                            (!recipe.requiresCampfire || gameState.isNearCampfire)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(recipe.title, style = MaterialTheme.typography.titleMedium)
                                    Text(recipe.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    val costStr = recipe.costs.entries.joinToString(" · ") { "${it.key.label} x${it.value}" }
                                    Text(
                                        text = costStr + if (recipe.requiresCampfire) " (需在篝火旁)" else "",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                Button(
                                    onClick = { onCraftRecipe(recipe) },
                                    enabled = canCraft
                                ) {
                                    Text("制作")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (gameState.isGameOver) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("篝火熄灭 · 第 ${gameState.dayCount} 天") },
            text = { Text("你在荒原的寒夜中耗尽了生命。可以立刻在营地篝火旁苏醒并继续这段自动存档！") },
            confirmButton = {
                Button(onClick = onRevive) {
                    Text("篝火重生")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        onSetGameActive(false)
                        onExitToHub()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text("返回主营地")
                }
            }
        )
    }
}

@Composable
private fun VitalChip(text: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun VirtualJoystick(
    onVectorChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    val maxDist = 52f

    Box(
        modifier = modifier
            .size(124.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.36f))
            .border(1.5.dp, Color(0xFFEDE3D2).copy(alpha = 0.35f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        knobOffset = Offset.Zero
                        onVectorChanged(0f, 0f)
                    },
                    onDragCancel = {
                        knobOffset = Offset.Zero
                        onVectorChanged(0f, 0f)
                    }
                ) { change, dragAmount ->
                    change.consume()
                    val candidate = knobOffset + dragAmount
                    val len = hypot(candidate.x, candidate.y)
                    val clamped = if (len > maxDist) {
                        Offset(candidate.x / len * maxDist, candidate.y / len * maxDist)
                    } else candidate
                    knobOffset = clamped
                    onVectorChanged(clamped.x / maxDist, clamped.y / maxDist)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = CampfireEmber.copy(alpha = 0.85f),
                radius = 22.dp.toPx(),
                center = center + knobOffset
            )
        }
    }
}

private fun DrawScope.drawCampfire(center: Offset, fuel: Float, animTime: Float) {
    // Stone ring
    for (i in 0 until 8) {
        val a = i * 0.785f
        drawCircle(
            color = Color(0xFF6F6863),
            radius = 8f,
            center = Offset(center.x + cos(a) * 28f, center.y + sin(a) * 20f)
        )
    }
    // Crossed logs
    drawLine(Color(0xFF5D3A1A), Offset(center.x - 16f, center.y + 6f), Offset(center.x + 16f, center.y - 4f), 7f)
    drawLine(Color(0xFF4A2E14), Offset(center.x - 15f, center.y - 4f), Offset(center.x + 15f, center.y + 6f), 7f)
    if (fuel > 1f) {
        val scale = (0.4f + fuel / 120f).coerceIn(0.4f, 1.3f)
        val flicker = sin(animTime * 6f) * 3f
        drawCircle(
            color = CampfireEmber.copy(alpha = 0.25f),
            radius = 65f * scale,
            center = center
        )
        val flame = Path().apply {
            moveTo(center.x - 14f * scale, center.y + 4f)
            lineTo(center.x + flicker, center.y - 34f * scale)
            lineTo(center.x + 14f * scale, center.y + 4f)
            close()
        }
        drawPath(flame, color = CampfireEmber)
        drawCircle(LanternGold, radius = 7f * scale, center = Offset(center.x + flicker * 0.5f, center.y - 6f * scale))
    }
}

private fun DrawScope.drawResourceNode(pos: Offset, kind: ResourceKind, animTime: Float) {
    when (kind) {
        ResourceKind.PINE_TREE -> {
            drawOval(Color.Black.copy(alpha = 0.3f), Offset(pos.x - 20f, pos.y + 12f), Size(40f, 12f))
            drawRect(Color(0xFF4F341D), Offset(pos.x - 5f, pos.y - 8f), Size(10f, 24f))
            val sway = sin(animTime + pos.x * 0.01f) * 2.5f
            val treePath = Path().apply {
                moveTo(pos.x + sway, pos.y - 62f)
                lineTo(pos.x + 26f, pos.y - 4f)
                lineTo(pos.x - 26f, pos.y - 4f)
                close()
            }
            drawPath(treePath, color = Color(0xFF28462C))
            drawPath(treePath, color = Color(0xFF152417), style = Stroke(2f))
        }
        ResourceKind.BOULDER -> {
            drawOval(Color.Black.copy(alpha = 0.3f), Offset(pos.x - 18f, pos.y + 8f), Size(36f, 10f))
            drawRoundRect(Color(0xFF6B6E75), Offset(pos.x - 18f, pos.y - 16f), Size(36f, 28f), CornerRadius(10f))
            drawRoundRect(Color(0xFF3A3C42), Offset(pos.x - 18f, pos.y - 16f), Size(36f, 28f), CornerRadius(10f), style = Stroke(2f))
        }
        ResourceKind.BERRY_BUSH -> {
            drawCircle(Color(0xFF2F5E34), radius = 18f, center = pos)
            drawCircle(VitalityCrimson, radius = 3.5f, center = Offset(pos.x - 6f, pos.y - 4f))
            drawCircle(VitalityCrimson, radius = 3.5f, center = Offset(pos.x + 7f, pos.y - 2f))
            drawCircle(VitalityCrimson, radius = 3.5f, center = Offset(pos.x, pos.y + 6f))
        }
        ResourceKind.FLOWER -> {
            drawCircle(Color(0xFF487344), radius = 6f, center = pos)
            drawCircle(LanternGold, radius = 5f, center = Offset(pos.x, pos.y - 6f))
            drawCircle(Color(0xFFEDE3D2), radius = 2.5f, center = Offset(pos.x, pos.y - 6f))
        }
        ResourceKind.GRASS_TUFT -> {
            drawLine(Color(0xFF939B50), Offset(pos.x - 6f, pos.y + 6f), Offset(pos.x - 10f, pos.y - 10f), 3f)
            drawLine(Color(0xFFA7AF5A), Offset(pos.x, pos.y + 6f), Offset(pos.x, pos.y - 13f), 3f)
            drawLine(Color(0xFF939B50), Offset(pos.x + 6f, pos.y + 6f), Offset(pos.x + 10f, pos.y - 10f), 3f)
        }
    }
}

private fun DrawScope.drawWorldCreature(
    pos: Offset,
    kind: CreatureKind,
    hp: Int,
    maxHp: Int,
    animTime: Float
) {
    if (kind == CreatureKind.BOAR) {
        drawOval(Color(0xFF5A3D31), Offset(pos.x - 18f, pos.y - 12f), Size(36f, 24f))
        drawOval(Color(0xFF231814), Offset(pos.x - 18f, pos.y - 12f), Size(36f, 24f), style = Stroke(2f))
        drawCircle(Color(0xFFEDE3D2), radius = 3f, center = Offset(pos.x + 14f, pos.y - 2f))
    } else {
        val hover = sin(animTime * 4f) * 4f
        drawCircle(Color(0xFF0E0B14).copy(alpha = 0.85f), radius = 17f, center = Offset(pos.x, pos.y + hover))
        drawCircle(Color(0xFFE5534B), radius = 3f, center = Offset(pos.x - 5f, pos.y + hover - 2f))
        drawCircle(Color(0xFFE5534B), radius = 3f, center = Offset(pos.x + 5f, pos.y + hover - 2f))
    }
    // Mini HP Bar
    val ratio = (hp.toFloat() / maxHp.coerceAtLeast(1)).coerceIn(0f, 1f)
    drawRect(Color.Black.copy(alpha = 0.6f), Offset(pos.x - 16f, pos.y - 24f), Size(32f, 4f))
    drawRect(VitalityCrimson, Offset(pos.x - 16f, pos.y - 24f), Size(32f * ratio, 4f))
}

private fun DrawScope.drawPlayerLabel(
    textMeasurer: TextMeasurer,
    label: String,
    topCenter: Offset
) {
    val result = textMeasurer.measure(
        text = label,
        style = TextStyle(color = Color(0xFFEDE3D2), fontSize = 11.sp)
    )
    drawText(
        textLayoutResult = result,
        topLeft = Offset(topCenter.x - result.size.width / 2f, topCenter.y)
    )
}
