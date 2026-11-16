package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SportsKabaddi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.local.SurvivorProfileEntity
import com.example.data.local.WorldSaveEntity
import com.example.firebase.GoogleSignInButton
import com.example.firebase.signOutGoogle
import com.example.model.GameMode
import com.example.model.SkinConfig
import com.example.ui.components.SurvivorCharacterPreview
import com.example.ui.theme.ForestMoss
import com.example.ui.theme.HungerAmber
import com.example.ui.theme.SanityViolet
import com.example.ui.theme.VitalityCrimson
import com.google.firebase.auth.FirebaseUser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CampHubScreen(
    profile: SurvivorProfileEntity?,
    activeSkin: SkinConfig,
    saves: List<WorldSaveEntity>,
    googleUser: FirebaseUser?,
    isCloudSyncing: Boolean,
    cloudStatusMessage: String?,
    onStartNewOffline: (worldName: String) -> Unit,
    onResumeSave: (WorldSaveEntity) -> Unit,
    onDeleteSave: (String) -> Unit,
    onOpenSkinStudio: () -> Unit,
    onOpenLanLobby: (GameMode) -> Unit,
    onUpdateProfile: (nickname: String, email: String) -> Unit,
    onSyncCloud: () -> Unit,
    onRestoreCloud: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showNewWorldDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var authErrorBanner by remember { mutableStateOf<String?>(null) }

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
            // Camp Hero Banner & Active Survivor Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_camp_banner),
                        contentDescription = "篝火营地背景",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.65f)
                                    )
                                )
                            )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                .clickable(onClick = onOpenSkinStudio)
                                .testTag("hub_skin_preview_card"),
                            contentAlignment = Alignment.Center
                        ) {
                            SurvivorCharacterPreview(
                                skin = activeSkin,
                                modifier = Modifier.fillMaxSize(),
                                hasTorch = true
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = profile?.nickname ?: "荒原旅人",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                IconButton(
                                    onClick = { showEditProfileDialog = true },
                                    modifier = Modifier.testTag("edit_profile_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "编辑邮箱名片",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = profile?.email.orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "当前装扮：${activeSkin.name} · ${activeSkin.archetypeEnum().bonusDesc}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Primary Launch Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val latestSave = saves.firstOrNull()
                    Button(
                        onClick = {
                            if (latestSave != null) onResumeSave(latestSave)
                            else showNewWorldDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .testTag("hub_primary_play_button")
                    ) {
                        Icon(
                            imageVector = if (latestSave != null) Icons.Default.PlayArrow else Icons.Default.LocalFireDepartment,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (latestSave != null) "继续第${latestSave.dayCount}天" else "开始离线求生",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    FilledTonalButton(
                        onClick = { showNewWorldDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(58.dp)
                            .testTag("hub_new_world_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("新世界")
                    }
                }
            }

            // LAN Multiplayer Quick Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenLanLobby(GameMode.LAN_COOP) }
                            .testTag("hub_lan_coop_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = null,
                                tint = ForestMoss
                            )
                            Text("同网扫码合作", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "同 Wi-Fi 共守篝火与实时语音",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenLanLobby(GameMode.LAN_PVP) }
                            .testTag("hub_lan_pvp_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsKabaddi,
                                contentDescription = null,
                                tint = VitalityCrimson
                            )
                            Text("同网联机对战", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "扫码入房 · 荒原长矛竞技",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Local Auto-Saves List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("本地自动存档 (${saves.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "每5秒与切换时自动保存",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (saves.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("尚无自动存档，点击上方“开始离线求生”创建你的第一座营地", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } else {
                items(saves, key = { it.id }) { save ->
                    SaveSlotCard(
                        save = save,
                        onResume = { onResumeSave(save) },
                        onDelete = { onDeleteSave(save.id) }
                    )
                }
            }

            // Optional Google Cloud Sync Section
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (googleUser != null) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("可选云端存档备份", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = if (googleUser != null) {
                                "已绑定 Google 账号 (${googleUser.email ?: googleUser.uid.take(8)})，可将离线存档与自定义皮肤备份到云端。"
                            } else {
                                "离线游玩与局域网联机完全无需登录。如需跨设备备份存档，可在此自愿绑定 Google 账号。"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (googleUser == null) {
                            GoogleSignInButton(
                                onAuthSuccess = {
                                    authErrorBanner = null
                                    onSyncCloud()
                                },
                                onAuthError = { authErrorBanner = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onSyncCloud,
                                    enabled = !isCloudSyncing,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("cloud_backup_now_button")
                                ) {
                                    if (isCloudSyncing) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text("立即备份")
                                    }
                                }
                                OutlinedButton(
                                    onClick = onRestoreCloud,
                                    enabled = !isCloudSyncing,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("cloud_restore_button")
                                ) {
                                    Text("从云端恢复")
                                }
                            }
                            TextButton(
                                onClick = {
                                    signOutGoogle(CredentialManager.create(context), scope)
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("解绑 Google 账号")
                            }
                        }

                        if (authErrorBanner != null) {
                            Text(authErrorBanner!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (cloudStatusMessage != null) {
                            Text(cloudStatusMessage, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    if (showNewWorldDialog) {
        var worldName by remember { mutableStateOf("余烬营地 #${saves.size + 1}") }
        AlertDialog(
            onDismissRequest = { showNewWorldDialog = false },
            title = { Text("创建新荒原世界") },
            text = {
                OutlinedTextField(
                    value = worldName,
                    onValueChange = { worldName = it.take(30) },
                    label = { Text("营地名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNewWorldDialog = false
                        onStartNewOffline(worldName.ifBlank { "荒原营地" })
                    },
                    modifier = Modifier.testTag("confirm_new_world_button")
                ) {
                    Text("出发探索")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewWorldDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showEditProfileDialog) {
        var nick by remember { mutableStateOf(profile?.nickname ?: "") }
        var email by remember { mutableStateOf(profile?.email ?: "") }
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("编辑邮箱名片") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nick,
                        onValueChange = { nick = it.take(24) },
                        label = { Text("昵称") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.take(80) },
                        label = { Text("邮箱") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nick.isNotBlank() && email.contains("@")) {
                            onUpdateProfile(nick, email)
                            showEditProfileDialog = false
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SaveSlotCard(
    save: WorldSaveEntity,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onResume)
            .testTag("save_slot_${save.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(save.worldName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "第 ${save.dayCount} 天 · ${save.timePhase} · ❤️${save.health} 🍖${save.hunger} 🧠${save.sanity}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "自动保存于 ${dateFmt.format(Date(save.updatedAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onResume) {
                Icon(Icons.Default.PlayArrow, contentDescription = "继续存档", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除存档", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
