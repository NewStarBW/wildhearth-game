package com.example.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.cloud.CloudBackupRepository
import com.example.data.cloud.CloudWorldSave
import com.example.data.local.FriendEntity
import com.example.data.local.LocalRepository
import com.example.data.local.SurvivorProfileEntity
import com.example.data.local.WildHearthDatabase
import com.example.data.local.WorldSaveEntity
import com.example.model.CharacterArchetype
import com.example.model.CraftRecipe
import com.example.model.CreatureKind
import com.example.model.GameMode
import com.example.model.GameWorldUiState
import com.example.model.ItemType
import com.example.model.ResourceKind
import com.example.model.ResourceNode
import com.example.model.SkinConfig
import com.example.model.SkinPalettes
import com.example.model.TimePhase
import com.example.model.WorldCreature
import com.example.model.WorldSavePayload
import com.example.network.LanGameEvent
import com.example.network.LanMultiplayerManager
import com.example.network.LanSessionState
import com.example.network.LanVoiceChatManager
import com.example.network.ScannedQrPayload
import com.example.network.VoiceChatState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.hypot
import kotlin.random.Random

class WildHearthViewModel(
    private val localRepo: LocalRepository,
    private val cloudRepo: CloudBackupRepository,
    val lanManager: LanMultiplayerManager,
    val voiceManager: LanVoiceChatManager
) : ViewModel() {

    val profile: StateFlow<SurvivorProfileEntity?> = localRepo.observeProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allSaves: StateFlow<List<WorldSaveEntity>> = localRepo.observeAllSaves
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSkins: StateFlow<List<SkinConfig>> = localRepo.observeAllSkins
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SkinPalettes.defaultPresets)

    val friends: StateFlow<List<FriendEntity>> = localRepo.observeFriends
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSkin: StateFlow<SkinConfig> = combine(profile, allSkins) { prof, skins ->
        val id = prof?.activeSkinId
        skins.firstOrNull { it.id == id } ?: skins.firstOrNull() ?: SkinPalettes.defaultPresets.first()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SkinPalettes.defaultPresets.first())

    val lanState: StateFlow<LanSessionState> = lanManager.sessionState
    val voiceState: StateFlow<VoiceChatState> = voiceManager.voiceState

    private val _gameState = MutableStateFlow(createFreshWorldState())
    val gameState: StateFlow<GameWorldUiState> = _gameState.asStateFlow()

    private val _cloudStatusMessage = MutableStateFlow<String?>(null)
    val cloudStatusMessage: StateFlow<String?> = _cloudStatusMessage.asStateFlow()

    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing: StateFlow<Boolean> = _isCloudSyncing.asStateFlow()

    @Volatile
    private var joystickInputX = 0f
    @Volatile
    private var joystickInputY = 0f
    @Volatile
    private var isGameLoopRunning = false
    private var gameLoopJob: Job? = null

    init {
        viewModelScope.launch {
            localRepo.ensureDefaultSkins()
        }
        viewModelScope.launch {
            lanManager.sessionState.collect { session ->
                voiceManager.updatePeers(session.peerVoiceEndpoints.values)
            }
        }
        viewModelScope.launch {
            lanManager.events.collect { event ->
                when (event) {
                    is LanGameEvent.PvpDamageReceived -> {
                        applyDamage(event.damage, "被 ${event.attackerName} 击中 (-${event.damage} 生命)")
                    }
                    is LanGameEvent.CampfireFuelSynced -> {
                        _gameState.update { it.copy(campfireFuel = event.fuel) }
                    }
                    is LanGameEvent.PeerJoinedFriend -> {
                        localRepo.addOrUpdateFriend(
                            friendId = event.player.playerId,
                            nickname = event.player.nickname,
                            email = event.player.email,
                            lastIp = event.ip,
                            lastPort = event.port,
                            skin = event.player.skin
                        )
                    }
                }
            }
        }
    }

    fun saveSurvivorProfile(nickname: String, email: String) {
        viewModelScope.launch {
            localRepo.saveLocalProfile(nickname = nickname, email = email)
        }
    }

    fun selectActiveSkin(skinId: String) {
        viewModelScope.launch {
            localRepo.setActiveSkin(skinId)
        }
    }

    fun saveCustomSkin(skin: SkinConfig, equipImmediately: Boolean = true) {
        viewModelScope.launch {
            localRepo.saveSkinPreset(skin)
            if (equipImmediately) {
                localRepo.setActiveSkin(skin.id)
            }
        }
    }

    fun deleteCustomSkin(skinId: String) {
        viewModelScope.launch {
            if (!skinId.startsWith("preset_")) {
                localRepo.deleteSkinPreset(skinId)
            }
        }
    }

    fun startNewWorld(
        worldName: String = "荒原营地",
        mode: GameMode = GameMode.OFFLINE_SOLO,
        slotId: String = "autosave_main"
    ) {
        val fresh = createFreshWorldState(
            slotId = slotId,
            worldName = worldName,
            mode = mode,
            seed = System.currentTimeMillis()
        )
        _gameState.value = fresh
        persistCurrentWorldNow()
    }

    fun loadWorldSave(save: WorldSaveEntity) {
        val payload = WorldSavePayload.fromJson(save.payloadJson) ?: return
        val mode = GameMode.entries.firstOrNull { it.name == payload.gameMode } ?: GameMode.OFFLINE_SOLO
        val inv = payload.inventory.mapNotNull { (k, v) ->
            ItemType.entries.firstOrNull { it.name == k }?.let { it to v }
        }.toMap()
        _gameState.value = GameWorldUiState(
            saveSlotId = save.id,
            worldName = save.worldName,
            seed = payload.seed,
            gameMode = mode,
            dayCount = payload.dayCount,
            dayProgress = payload.dayProgress,
            timePhase = phaseForProgress(payload.dayProgress),
            playerX = payload.playerX,
            playerY = payload.playerY,
            health = payload.health,
            hunger = payload.hunger,
            sanity = payload.sanity,
            campfireFuel = payload.campfireFuel,
            hasTorchEquipped = payload.hasTorchEquipped,
            hasSpearEquipped = payload.hasSpearEquipped,
            hasArmorEquipped = payload.hasArmorEquipped,
            inventory = inv,
            resources = payload.resources.ifEmpty { generateInitialResources(payload.seed) },
            creatures = payload.creatures.ifEmpty { generateInitialCreatures(payload.seed) },
            kills = payload.kills,
            isGameOver = payload.health <= 0,
            lastAutoSaveAtMs = save.updatedAt,
            statusBanner = "已读取自动存档 · 第 ${payload.dayCount} 天"
        )
    }

    fun deleteWorldSave(slotId: String) {
        viewModelScope.launch {
            localRepo.deleteWorldSave(slotId)
        }
    }

    fun setGameActive(active: Boolean) {
        if (active && !isGameLoopRunning) {
            isGameLoopRunning = true
            startGameLoop()
        } else if (!active && isGameLoopRunning) {
            isGameLoopRunning = false
            gameLoopJob?.cancel()
            gameLoopJob = null
            persistCurrentWorldNow()
        }
    }

    fun setJoystickVector(dx: Float, dy: Float) {
        joystickInputX = dx.coerceIn(-1f, 1f)
        joystickInputY = dy.coerceIn(-1f, 1f)
    }

    private fun startGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = viewModelScope.launch {
            var tick = 0
            while (isActive && isGameLoopRunning) {
                delay(50L) // 20Hz simulation step
                tick++
                stepSimulation(tick)
                if (tick % 100 == 0) { // Every 5 seconds auto-save
                    persistCurrentWorldNow()
                }
            }
        }
    }

    private fun stepSimulation(tick: Int) {
        val state = _gameState.value
        if (state.isGameOver) return

        val dt = 0.05f
        val moveSpeed = 195f
        val jx = joystickInputX
        val jy = joystickInputY
        val moving = hypot(jx, jy) > 0.08f
        val nextX = (state.playerX + jx * moveSpeed * dt).coerceIn(80f, WORLD_SIZE - 80f)
        val nextY = (state.playerY + jy * moveSpeed * dt).coerceIn(80f, WORLD_SIZE - 80f)
        val facingRight = if (jx > 0.08f) true else if (jx < -0.08f) false else state.facingRight

        // Day cycle: ~90 seconds per full day
        var progress = state.dayProgress + dt / 90f
        var day = state.dayCount
        if (progress >= 1f) {
            progress -= 1f
            day += 1
        }
        val phase = phaseForProgress(progress)

        // Campfire fuel drains slowly
        val fuelDrain = if (phase == TimePhase.NIGHT) 0.55f else 0.28f
        val nextFuel = (state.campfireFuel - fuelDrain * dt).coerceAtLeast(0f)

        val distCamp = hypot(nextX - state.campfireX, nextY - state.campfireY)
        val nearCampfire = distCamp <= (130f + nextFuel * 1.4f) && nextFuel > 1f
        val hasLight = nearCampfire || state.hasTorchEquipped

        // Hunger & Sanity ticks once per second (every 20 ticks)
        var nextHunger = state.hunger
        var nextSanity = state.sanity
        var nextHealth = state.health
        var banner = state.statusBanner
        val arch = activeSkin.value.archetypeEnum()

        if (tick % 20 == 0) {
            val hungerTick = if (arch == CharacterArchetype.AUTOMATON && tick % 40 == 0) 0 else 1
            nextHunger = (nextHunger - hungerTick).coerceAtLeast(0)
            if (nextHunger == 0) {
                nextHealth = (nextHealth - 2).coerceAtLeast(0)
                banner = "极度饥饿！快吃浆果或烤肉"
            }

            if (phase == TimePhase.NIGHT && !hasLight) {
                val sanityLoss = if (arch == CharacterArchetype.PYROMANCER) 2 else 4
                nextSanity = (nextSanity - sanityLoss).coerceAtLeast(0)
                nextHealth = (nextHealth - 2).coerceAtLeast(0)
                banner = "警告：处于完全黑暗中！点亮火把或靠近篝火"
            } else if (nearCampfire && nextFuel > 15f) {
                val sanityGain = if (arch == CharacterArchetype.SCHOLAR) 2 else 1
                nextSanity = (nextSanity + sanityGain).coerceAtMost(100)
            } else if (phase == TimePhase.DUSK && !hasLight) {
                if (tick % 40 == 0) nextSanity = (nextSanity - 1).coerceAtLeast(0)
            }
        }

        // Respawn depleted resources over time
        val now = System.currentTimeMillis()
        val updatedResources = state.resources.map { node ->
            if (node.hp <= 0 && node.respawnAtMs in 1..now) {
                node.copy(hp = node.kindEnum().maxHp, respawnAtMs = 0L)
            } else {
                node
            }
        }

        // Move creatures & spawn night shadow stalker if low sanity or night
        var creatures = state.creatures.toMutableList()
        if (phase == TimePhase.NIGHT && creatures.count { it.kind == CreatureKind.SHADOW_STALKER.name } < 2 && tick % 120 == 0) {
            val angle = Random.nextFloat() * 6.28f
            val sx = (nextX + kotlin.math.cos(angle) * 320f).coerceIn(100f, WORLD_SIZE - 100f)
            val sy = (nextY + kotlin.math.sin(angle) * 320f).coerceIn(100f, WORLD_SIZE - 100f)
            creatures.add(
                WorldCreature(
                    id = "shadow_${UUID.randomUUID().toString().take(6)}",
                    kind = CreatureKind.SHADOW_STALKER.name,
                    x = sx,
                    y = sy,
                    hp = CreatureKind.SHADOW_STALKER.maxHp,
                    maxHp = CreatureKind.SHADOW_STALKER.maxHp
                )
            )
        }

        creatures = creatures.mapNotNull { c ->
            if (c.hp <= 0) return@mapNotNull null
            val kind = c.kindEnum()
            val dPlayer = hypot(nextX - c.x, nextY - c.y)
            val aggroRange = if (kind == CreatureKind.SHADOW_STALKER) 420f else 220f
            var cx = c.x
            var cy = c.y
            if (dPlayer in 36f..aggroRange) {
                val step = kind.speed * dt
                cx += ((nextX - c.x) / dPlayer) * step
                cy += ((nextY - c.y) / dPlayer) * step
            } else if (tick % 30 == 0 && dPlayer >= aggroRange) {
                cx = (cx + Random.nextInt(-25, 26)).coerceIn(90f, WORLD_SIZE - 90f)
                cy = (cy + Random.nextInt(-25, 26)).coerceIn(90f, WORLD_SIZE - 90f)
            }
            if (dPlayer <= 42f && tick % 25 == 0) {
                val rawDmg = kind.damage
                val finalDmg = if (state.hasArmorEquipped) (rawDmg * 0.5f).toInt().coerceAtLeast(1) else rawDmg
                nextHealth = (nextHealth - finalDmg).coerceAtLeast(0)
                banner = "遭到 ${kind.label} 袭击 (-$finalDmg 生命)"
            }
            c.copy(x = cx, y = cy)
        }.toMutableList()

        // Ensure boars replenish slowly
        if (creatures.count { it.kind == CreatureKind.BOAR.name } < 5 && tick % 200 == 0) {
            creatures.add(
                WorldCreature(
                    id = "boar_${UUID.randomUUID().toString().take(6)}",
                    kind = CreatureKind.BOAR.name,
                    x = Random.nextInt(250, 2150).toFloat(),
                    y = Random.nextInt(250, 2150).toFloat(),
                    hp = CreatureKind.BOAR.maxHp,
                    maxHp = CreatureKind.BOAR.maxHp
                )
            )
        }

        val session = lanManager.sessionState.value
        val activeMode = if (session.isConnected) session.gameMode else state.gameMode
        val gameOver = nextHealth <= 0

        _gameState.update {
            it.copy(
                gameMode = activeMode,
                dayCount = day,
                dayProgress = progress,
                timePhase = phase,
                playerX = nextX,
                playerY = nextY,
                facingRight = facingRight,
                isMoving = moving,
                attackAnimTicks = (it.attackAnimTicks - 1).coerceAtLeast(0),
                health = nextHealth,
                hunger = nextHunger,
                sanity = nextSanity,
                campfireFuel = nextFuel,
                isNearCampfire = nearCampfire,
                resources = updatedResources,
                creatures = creatures,
                remotePlayers = session.peers,
                isGameOver = gameOver,
                statusBanner = if (gameOver) "你在荒原的第 $day 天倒下了…" else banner
            )
        }

        if (tick % 2 == 0 && session.isConnected) {
            val prof = profile.value
            lanManager.broadcastPlayerState(
                playerId = prof?.localPlayerId ?: "local",
                nickname = prof?.nickname ?: "幸存者",
                email = prof?.email.orEmpty(),
                x = nextX,
                y = nextY,
                facingRight = facingRight,
                isAttacking = _gameState.value.attackAnimTicks > 0,
                hp = nextHealth,
                skin = activeSkin.value,
                campfireFuel = nextFuel
            )
        }
    }

    fun performGatherOrAttack() {
        val state = _gameState.value
        if (state.isGameOver) return

        val arch = activeSkin.value.archetypeEnum()
        val baseDmg = if (state.hasSpearEquipped) 28 else 14
        val finalDmg = if (arch == CharacterArchetype.VALKYRIE) (baseDmg * 1.25f).toInt() else baseDmg

        // 1. Check PvP remote player hit if in LAN_PVP mode
        if (state.gameMode == GameMode.LAN_PVP) {
            val targetPeer = state.remotePlayers.values.firstOrNull { peer ->
                hypot(peer.x - state.playerX, peer.y - state.playerY) <= 95f
            }
            if (targetPeer != null) {
                val prof = profile.value
                lanManager.sendPvpHit(
                    attackerId = prof?.localPlayerId ?: "local",
                    attackerName = prof?.nickname ?: "对手",
                    targetId = targetPeer.playerId,
                    damage = finalDmg
                )
                _gameState.update {
                    it.copy(attackAnimTicks = 6, statusBanner = "命中玩家 ${targetPeer.nickname}！造成 $finalDmg 点伤害")
                }
                return
            }
        }

        // 2. Check nearby hostile creature
        val creatureIdx = state.creatures.indexOfFirst { c ->
            hypot(c.x - state.playerX, c.y - state.playerY) <= 95f
        }
        if (creatureIdx >= 0) {
            val target = state.creatures[creatureIdx]
            val newHp = target.hp - finalDmg
            val updatedCreatures = state.creatures.toMutableList()
            val updatedInv = state.inventory.toMutableMap()
            var kills = state.kills
            var sanity = state.sanity
            val msg: String
            if (newHp <= 0) {
                updatedCreatures.removeAt(creatureIdx)
                kills++
                if (target.kind == CreatureKind.BOAR.name) {
                    updatedInv[ItemType.RAW_MEAT] = (updatedInv[ItemType.RAW_MEAT] ?: 0) + 2
                    msg = "击倒荒原野猪！获得 生肉 x2"
                } else {
                    sanity = (sanity + 15).coerceAtMost(100)
                    updatedInv[ItemType.FLINT] = (updatedInv[ItemType.FLINT] ?: 0) + 1
                    msg = "驱散暗影潜行者！理智 +15"
                }
            } else {
                updatedCreatures[creatureIdx] = target.copy(hp = newHp)
                msg = "攻击 ${target.kindEnum().label} (-$finalDmg)"
            }
            _gameState.update {
                it.copy(
                    attackAnimTicks = 6,
                    creatures = updatedCreatures,
                    inventory = updatedInv,
                    kills = kills,
                    sanity = sanity,
                    statusBanner = msg
                )
            }
            return
        }

        // 3. Check nearby resource node
        val resIdx = state.resources.indexOfFirst { r ->
            r.hp > 0 && hypot(r.x - state.playerX, r.y - state.playerY) <= 92f
        }
        if (resIdx >= 0) {
            val node = state.resources[resIdx]
            val kind = node.kindEnum()
            val nextHp = node.hp - 1
            val updatedResources = state.resources.toMutableList()
            val updatedInv = state.inventory.toMutableMap()
            var sanity = state.sanity
            val msg: String

            if (nextHp <= 0) {
                updatedResources[resIdx] = node.copy(hp = 0, respawnAtMs = System.currentTimeMillis() + 35_000L)
                when (kind) {
                    ResourceKind.PINE_TREE -> {
                        updatedInv[ItemType.WOOD] = (updatedInv[ItemType.WOOD] ?: 0) + 3
                        msg = "砍倒松树！获得 木材 x3"
                    }
                    ResourceKind.BOULDER -> {
                        updatedInv[ItemType.STONE] = (updatedInv[ItemType.STONE] ?: 0) + 2
                        updatedInv[ItemType.FLINT] = (updatedInv[ItemType.FLINT] ?: 0) + 1
                        msg = "凿碎岩石！获得 石块 x2、燧石 x1"
                    }
                    ResourceKind.BERRY_BUSH -> {
                        updatedInv[ItemType.BERRIES] = (updatedInv[ItemType.BERRIES] ?: 0) + 2
                        msg = "采摘浆果！获得 浆果 x2"
                    }
                    ResourceKind.FLOWER -> {
                        updatedInv[ItemType.PETALS] = (updatedInv[ItemType.PETALS] ?: 0) + 2
                        sanity = (sanity + 8).coerceAtMost(100)
                        msg = "拾取野花：花瓣 x2，理智 +8"
                    }
                    ResourceKind.GRASS_TUFT -> {
                        updatedInv[ItemType.GRASS] = (updatedInv[ItemType.GRASS] ?: 0) + 2
                        msg = "割取干草！获得 干草 x2"
                    }
                }
            } else {
                updatedResources[resIdx] = node.copy(hp = nextHp)
                msg = "正在采集 ${kind.label} ($nextHp/${kind.maxHp})"
            }
            _gameState.update {
                it.copy(
                    attackAnimTicks = 5,
                    resources = updatedResources,
                    inventory = updatedInv,
                    sanity = sanity,
                    statusBanner = msg
                )
            }
        } else {
            _gameState.update {
                it.copy(attackAnimTicks = 4, statusBanner = "附近没有可采集的树木、矿石或目标")
            }
        }
    }

    fun feedCampfire() {
        val state = _gameState.value
        if (!state.isNearCampfire && hypot(state.playerX - state.campfireX, state.playerY - state.campfireY) > 220f) {
            _gameState.update { it.copy(statusBanner = "请回到中心篝火附近再添柴") }
            return
        }
        val wood = state.inventory[ItemType.WOOD] ?: 0
        if (wood < 1) {
            _gameState.update { it.copy(statusBanner = "木材不足！砍伐松树可获得木材") }
            return
        }
        val inv = state.inventory.toMutableMap()
        inv[ItemType.WOOD] = wood - 1
        val nextFuel = (state.campfireFuel + 28f).coerceAtMost(100f)
        _gameState.update {
            it.copy(
                inventory = inv,
                campfireFuel = nextFuel,
                statusBanner = "添入木材，篝火重新燃起！燃料 ${nextFuel.toInt()}%"
            )
        }
    }

    fun useOrEatItem(item: ItemType) {
        val state = _gameState.value
        val count = state.inventory[item] ?: 0
        if (count <= 0) return
        val inv = state.inventory.toMutableMap()
        when (item) {
            ItemType.BERRIES -> {
                inv[item] = count - 1
                _gameState.update {
                    it.copy(
                        inventory = inv,
                        hunger = (it.hunger + 16).coerceAtMost(100),
                        health = (it.health + 3).coerceAtMost(100),
                        statusBanner = "食用浆果：饥饿 +16，生命 +3"
                    )
                }
            }
            ItemType.RAW_MEAT -> {
                inv[item] = count - 1
                _gameState.update {
                    it.copy(
                        inventory = inv,
                        hunger = (it.hunger + 15).coerceAtMost(100),
                        sanity = (it.sanity - 5).coerceAtLeast(0),
                        statusBanner = "生吃肉块：饥饿 +15，理智 -5（建议篝火烤熟）"
                    )
                }
            }
            ItemType.COOKED_MEAT -> {
                inv[item] = count - 1
                _gameState.update {
                    it.copy(
                        inventory = inv,
                        hunger = (it.hunger + 35).coerceAtMost(100),
                        health = (it.health + 15).coerceAtMost(100),
                        statusBanner = "享用炭烤肉排：饥饿 +35，生命 +15"
                    )
                }
            }
            ItemType.CAMP_STEW -> {
                inv[item] = count - 1
                _gameState.update {
                    it.copy(
                        inventory = inv,
                        hunger = (it.hunger + 55).coerceAtMost(100),
                        health = (it.health + 25).coerceAtMost(100),
                        sanity = (it.sanity + 15).coerceAtMost(100),
                        statusBanner = "享用荒原炖菜：状态全面回暖！"
                    )
                }
            }
            ItemType.HEALING_SALVE -> {
                inv[item] = count - 1
                _gameState.update {
                    it.copy(
                        inventory = inv,
                        health = (it.health + 35).coerceAtMost(100),
                        statusBanner = "敷上草药膏：生命 +35"
                    )
                }
            }
            ItemType.TORCH -> {
                _gameState.update {
                    it.copy(
                        hasTorchEquipped = !it.hasTorchEquipped,
                        statusBanner = if (!it.hasTorchEquipped) "已举起火把照明" else "已收起火把"
                    )
                }
            }
            ItemType.FLINT_SPEAR -> {
                _gameState.update {
                    it.copy(
                        hasSpearEquipped = !it.hasSpearEquipped,
                        statusBanner = if (!it.hasSpearEquipped) "已装备燧石长矛" else "已切换徒手"
                    )
                }
            }
            ItemType.LOG_ARMOR -> {
                _gameState.update {
                    it.copy(
                        hasArmorEquipped = !it.hasArmorEquipped,
                        statusBanner = if (!it.hasArmorEquipped) "已穿戴木甲（减伤50%）" else "已卸下木甲"
                    )
                }
            }
            else -> Unit
        }
    }

    fun craftRecipe(recipe: CraftRecipe) {
        val state = _gameState.value
        if (recipe.requiresCampfire && !state.isNearCampfire) {
            _gameState.update { it.copy(statusBanner = "${recipe.title} 需要靠近篝火才能烹制！") }
            return
        }
        val missing = recipe.costs.entries.firstOrNull { (item, qty) ->
            (state.inventory[item] ?: 0) < qty
        }
        if (missing != null) {
            _gameState.update {
                it.copy(statusBanner = "材料不足：需要 ${missing.key.label} x${missing.value}")
            }
            return
        }
        val inv = state.inventory.toMutableMap()
        recipe.costs.forEach { (item, qty) ->
            inv[item] = ((inv[item] ?: 0) - qty).coerceAtLeast(0)
        }
        inv[recipe.resultItem] = (inv[recipe.resultItem] ?: 0) + recipe.resultCount
        _gameState.update {
            it.copy(
                inventory = inv,
                hasTorchEquipped = it.hasTorchEquipped || recipe.resultItem == ItemType.TORCH,
                hasSpearEquipped = it.hasSpearEquipped || recipe.resultItem == ItemType.FLINT_SPEAR,
                hasArmorEquipped = it.hasArmorEquipped || recipe.resultItem == ItemType.LOG_ARMOR,
                statusBanner = "成功制作 ${recipe.title}！"
            )
        }
        persistCurrentWorldNow()
    }

    private fun applyDamage(dmg: Int, reason: String) {
        _gameState.update { state ->
            val reduced = if (state.hasArmorEquipped) (dmg * 0.5f).toInt().coerceAtLeast(1) else dmg
            val nextHp = (state.health - reduced).coerceAtLeast(0)
            state.copy(
                health = nextHp,
                isGameOver = nextHp <= 0,
                statusBanner = reason
            )
        }
    }

    fun reviveAtCampfire() {
        _gameState.update {
            it.copy(
                playerX = it.campfireX,
                playerY = it.campfireY + 45f,
                health = 75,
                hunger = 70,
                sanity = 75,
                campfireFuel = maxOf(it.campfireFuel, 60f),
                isGameOver = false,
                statusBanner = "在篝火旁苏醒，重新振作继续生存！"
            )
        }
        persistCurrentWorldNow()
    }

    fun persistCurrentWorldNow() {
        val s = _gameState.value
        val payload = WorldSavePayload(
            seed = s.seed,
            worldName = s.worldName,
            gameMode = s.gameMode.name,
            dayCount = s.dayCount,
            dayProgress = s.dayProgress,
            playerX = s.playerX,
            playerY = s.playerY,
            health = s.health,
            hunger = s.hunger,
            sanity = s.sanity,
            campfireFuel = s.campfireFuel,
            hasTorchEquipped = s.hasTorchEquipped,
            hasSpearEquipped = s.hasSpearEquipped,
            hasArmorEquipped = s.hasArmorEquipped,
            inventory = s.inventory.mapKeys { it.key.name },
            resources = s.resources,
            creatures = s.creatures,
            kills = s.kills
        )
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            localRepo.autoSaveWorld(
                slotId = s.saveSlotId,
                worldName = s.worldName,
                gameMode = s.gameMode.name,
                dayCount = s.dayCount,
                timePhase = s.timePhase.name,
                health = s.health,
                hunger = s.hunger,
                sanity = s.sanity,
                payload = payload
            )
            _gameState.update { it.copy(lastAutoSaveAtMs = now) }
        }
    }

    // LAN & QR Helpers
    fun hostLanRoom(roomName: String, mode: GameMode) {
        val prof = profile.value ?: return
        val voicePort = voiceManager.startSocketAndPlayback()
        _gameState.update { it.copy(gameMode = mode, worldName = roomName) }
        lanManager.startHosting(
            roomName = roomName,
            gameMode = mode,
            localPlayerId = prof.localPlayerId,
            nickname = prof.nickname,
            email = prof.email,
            skin = activeSkin.value,
            voicePort = voicePort
        )
    }

    fun joinLanRoom(ip: String, port: Int, mode: GameMode = GameMode.LAN_COOP) {
        val prof = profile.value ?: return
        val voicePort = voiceManager.startSocketAndPlayback()
        _gameState.update { it.copy(gameMode = mode) }
        lanManager.joinRoom(
            hostIp = ip,
            hostPort = port,
            localPlayerId = prof.localPlayerId,
            nickname = prof.nickname,
            email = prof.email,
            skin = activeSkin.value,
            voicePort = voicePort
        )
    }

    fun handleScannedQr(payload: ScannedQrPayload) {
        viewModelScope.launch {
            when (payload) {
                is ScannedQrPayload.RoomInvite -> {
                    localRepo.addOrUpdateFriend(
                        friendId = payload.hostId,
                        nickname = payload.hostNickname,
                        email = payload.hostEmail,
                        lastIp = payload.ip,
                        lastPort = payload.port,
                        skin = payload.hostSkin
                    )
                    joinLanRoom(payload.ip, payload.port, payload.gameMode)
                }
                is ScannedQrPayload.FriendCard -> {
                    localRepo.addOrUpdateFriend(
                        friendId = payload.friendId,
                        nickname = payload.nickname,
                        email = payload.email,
                        skin = payload.skin
                    )
                }
            }
        }
    }

    fun leaveLanRoom() {
        lanManager.stopSession()
        voiceManager.stopAll()
        _gameState.update { it.copy(gameMode = GameMode.OFFLINE_SOLO, remotePlayers = emptyMap()) }
    }

    fun removeFriend(friendId: String) {
        viewModelScope.launch {
            localRepo.deleteFriend(friendId)
        }
    }

    // Optional Google Cloud Backup (only executes when Google-signed-in)
    fun syncToCloud() {
        if (Firebase.auth.currentUser == null) {
            _cloudStatusMessage.value = "请先绑定 Google 账号以开启云端备份"
            return
        }
        viewModelScope.launch {
            _isCloudSyncing.value = true
            _cloudStatusMessage.value = null
            try {
                val prof = profile.value
                if (prof != null) {
                    cloudRepo.upsertCloudProfile(prof.nickname, prof.email, prof.activeSkinId)
                }
                allSaves.value.forEach { save ->
                    cloudRepo.backupWorldSave(save)
                }
                allSkins.value.forEach { skin ->
                    cloudRepo.backupSkinPreset(skin)
                }
                _cloudStatusMessage.value = "已将 ${allSaves.value.size} 份存档与 ${allSkins.value.size} 套皮肤同步至云端"
            } catch (e: Exception) {
                Log.e("CloudSync", "Cloud backup failed", e)
                _cloudStatusMessage.value = "云端同步失败：${e.localizedMessage}"
            } finally {
                _isCloudSyncing.value = false
            }
        }
    }

    fun restoreSavesFromCloud() {
        if (Firebase.auth.currentUser == null) {
            _cloudStatusMessage.value = "请先绑定 Google 账号"
            return
        }
        viewModelScope.launch {
            _isCloudSyncing.value = true
            try {
                val cloudSaves: List<CloudWorldSave> = cloudRepo.getCloudSavesOnce()
                var count = 0
                cloudSaves.forEach { cs ->
                    val payload = WorldSavePayload.fromJson(cs.payloadJson)
                    if (payload != null) {
                        localRepo.autoSaveWorld(
                            slotId = cs.id,
                            worldName = cs.worldName,
                            gameMode = payload.gameMode,
                            dayCount = cs.dayCount.toInt(),
                            timePhase = cs.timePhase,
                            health = cs.health.toInt(),
                            hunger = cs.hunger.toInt(),
                            sanity = cs.sanity.toInt(),
                            payload = payload
                        )
                        count++
                    }
                }
                _cloudStatusMessage.value = "已从云端恢复 $count 份生存存档"
            } catch (e: Exception) {
                Log.e("CloudSync", "Restore failed", e)
                _cloudStatusMessage.value = "云端恢复失败：${e.localizedMessage}"
            } finally {
                _isCloudSyncing.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        lanManager.stopDiscovery()
        lanManager.stopSession()
        voiceManager.stopAll()
    }

    private fun phaseForProgress(progress: Float): TimePhase = when {
        progress < 0.55f -> TimePhase.DAY
        progress < 0.78f -> TimePhase.DUSK
        else -> TimePhase.NIGHT
    }

    private fun createFreshWorldState(
        slotId: String = "autosave_main",
        worldName: String = "荒原营地",
        mode: GameMode = GameMode.OFFLINE_SOLO,
        seed: Long = 1042L
    ): GameWorldUiState {
        return GameWorldUiState(
            saveSlotId = slotId,
            worldName = worldName,
            seed = seed,
            gameMode = mode,
            resources = generateInitialResources(seed),
            creatures = generateInitialCreatures(seed)
        )
    }

    private fun generateInitialResources(seed: Long): List<ResourceNode> {
        val rng = Random(seed)
        val nodes = mutableListOf<ResourceNode>()
        val kinds = listOf(
            ResourceKind.PINE_TREE to 32,
            ResourceKind.BOULDER to 18,
            ResourceKind.BERRY_BUSH to 18,
            ResourceKind.FLOWER to 14,
            ResourceKind.GRASS_TUFT to 20
        )
        var idx = 0
        for ((kind, count) in kinds) {
            repeat(count) {
                var x: Float
                var y: Float
                do {
                    x = rng.nextInt(140, 2260).toFloat()
                    y = rng.nextInt(140, 2260).toFloat()
                } while (hypot(x - 1200f, y - 1200f) < 130f)
                nodes.add(
                    ResourceNode(
                        id = "res_${idx++}",
                        kind = kind.name,
                        x = x,
                        y = y,
                        hp = kind.maxHp
                    )
                )
            }
        }
        return nodes
    }

    private fun generateInitialCreatures(seed: Long): List<WorldCreature> {
        val rng = Random(seed + 7)
        return (0 until 6).map { i ->
            WorldCreature(
                id = "boar_$i",
                kind = CreatureKind.BOAR.name,
                x = rng.nextInt(320, 2080).toFloat(),
                y = rng.nextInt(320, 2080).toFloat(),
                hp = CreatureKind.BOAR.maxHp,
                maxHp = CreatureKind.BOAR.maxHp
            )
        }
    }

    companion object {
        const val WORLD_SIZE = 2400f

        fun provideFactory(context: Context): ViewModelProvider.Factory {
            val appCtx = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = WildHearthDatabase.getInstance(appCtx)
                    return WildHearthViewModel(
                        localRepo = LocalRepository(db),
                        cloudRepo = CloudBackupRepository(appCtx),
                        lanManager = LanMultiplayerManager(appCtx),
                        voiceManager = LanVoiceChatManager()
                    ) as T
                }
            }
        }
    }
}
