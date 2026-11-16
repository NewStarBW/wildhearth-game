package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.firebase.authStateFlow
import com.example.firebase.configureAppCheck
import com.example.model.GameMode
import com.example.ui.screens.AiGuideScreen
import com.example.ui.screens.CampHubScreen
import com.example.ui.screens.GameWorldScreen
import com.example.ui.screens.LanLobbyScreen
import com.example.ui.screens.ProfileEntryScreen
import com.example.ui.screens.SkinStudioScreen
import com.example.ui.theme.WildHearthTheme
import com.example.viewmodel.AiGuideViewModel
import com.example.viewmodel.WildHearthViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

const val ROUTE_HUB = "camp_hub"
const val ROUTE_SKINS = "skin_studio"
const val ROUTE_LAN = "lan_lobby"
const val ROUTE_AI = "ai_guide"
const val ROUTE_GAME = "game_world"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory App Check initialization with launch intent BEFORE UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            WildHearthTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WildHearthApp()
                }
            }
        }
    }
}

@Composable
fun WildHearthApp() {
    val context = LocalContext.current
    val mainViewModel: WildHearthViewModel = viewModel(
        factory = WildHearthViewModel.provideFactory(context)
    )
    val aiViewModel: AiGuideViewModel = viewModel()

    val profile by mainViewModel.profile.collectAsStateWithLifecycle()
    val allSaves by mainViewModel.allSaves.collectAsStateWithLifecycle()
    val allSkins by mainViewModel.allSkins.collectAsStateWithLifecycle()
    val activeSkin by mainViewModel.activeSkin.collectAsStateWithLifecycle()
    val friends by mainViewModel.friends.collectAsStateWithLifecycle()
    val gameState by mainViewModel.gameState.collectAsStateWithLifecycle()
    val lanState by mainViewModel.lanState.collectAsStateWithLifecycle()
    val voiceState by mainViewModel.voiceState.collectAsStateWithLifecycle()
    val isCloudSyncing by mainViewModel.isCloudSyncing.collectAsStateWithLifecycle()
    val cloudStatusMessage by mainViewModel.cloudStatusMessage.collectAsStateWithLifecycle()
    val googleUser by Firebase.auth.authStateFlow().collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    var pendingLanMode by remember { mutableStateOf(GameMode.LAN_COOP) }

    if (profile == null) {
        ProfileEntryScreen(
            onEnterCamp = { nick, email ->
                mainViewModel.saveSurvivorProfile(nick, email)
            }
        )
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_HUB
    val isInGame = currentRoute == ROUTE_GAME

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp && !isInGame

        Scaffold(
            bottomBar = {
                if (!isInGame && !useRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        NavigationBarItem(
                            selected = currentRoute == ROUTE_HUB,
                            onClick = { navController.navigate(ROUTE_HUB) { launchSingleTop = true } },
                            icon = { Icon(Icons.Default.LocalFireDepartment, contentDescription = "营地") },
                            label = { Text("营地") },
                            modifier = Modifier.testTag("nav_hub")
                        )
                        NavigationBarItem(
                            selected = currentRoute == ROUTE_SKINS,
                            onClick = { navController.navigate(ROUTE_SKINS) { launchSingleTop = true } },
                            icon = { Icon(Icons.Default.Checkroom, contentDescription = "皮肤") },
                            label = { Text("皮肤") },
                            modifier = Modifier.testTag("nav_skins")
                        )
                        NavigationBarItem(
                            selected = currentRoute == ROUTE_LAN,
                            onClick = { navController.navigate(ROUTE_LAN) { launchSingleTop = true } },
                            icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = "联机") },
                            label = { Text("联机") },
                            modifier = Modifier.testTag("nav_lan")
                        )
                        NavigationBarItem(
                            selected = currentRoute == ROUTE_AI,
                            onClick = { navController.navigate(ROUTE_AI) { launchSingleTop = true } },
                            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "向导") },
                            label = { Text("向导") },
                            modifier = Modifier.testTag("nav_ai")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isInGame) androidx.compose.foundation.layout.PaddingValues(0.dp) else innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        NavigationRailItem(
                            selected = currentRoute == ROUTE_HUB,
                            onClick = { navController.navigate(ROUTE_HUB) { launchSingleTop = true } },
                            icon = { Icon(Icons.Default.LocalFireDepartment, contentDescription = "营地") },
                            label = { Text("营地") }
                        )
                        NavigationRailItem(
                            selected = currentRoute == ROUTE_SKINS,
                            onClick = { navController.navigate(ROUTE_SKINS) { launchSingleTop = true } },
                            icon = { Icon(Icons.Default.Checkroom, contentDescription = "皮肤") },
                            label = { Text("皮肤") }
                        )
                        NavigationRailItem(
                            selected = currentRoute == ROUTE_LAN,
                            onClick = { navController.navigate(ROUTE_LAN) { launchSingleTop = true } },
                            icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = "联机") },
                            label = { Text("联机") }
                        )
                        NavigationRailItem(
                            selected = currentRoute == ROUTE_AI,
                            onClick = { navController.navigate(ROUTE_AI) { launchSingleTop = true } },
                            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "向导") },
                            label = { Text("向导") }
                        )
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_HUB,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_HUB) {
                        CampHubScreen(
                            profile = profile,
                            activeSkin = activeSkin,
                            saves = allSaves,
                            googleUser = googleUser,
                            isCloudSyncing = isCloudSyncing,
                            cloudStatusMessage = cloudStatusMessage,
                            onStartNewOffline = { worldName ->
                                mainViewModel.startNewWorld(
                                    worldName = worldName,
                                    mode = GameMode.OFFLINE_SOLO,
                                    slotId = "save_${System.currentTimeMillis()}"
                                )
                                navController.navigate(ROUTE_GAME)
                            },
                            onResumeSave = { save ->
                                mainViewModel.loadWorldSave(save)
                                navController.navigate(ROUTE_GAME)
                            },
                            onDeleteSave = mainViewModel::deleteWorldSave,
                            onOpenSkinStudio = { navController.navigate(ROUTE_SKINS) },
                            onOpenLanLobby = { mode ->
                                pendingLanMode = mode
                                navController.navigate(ROUTE_LAN)
                            },
                            onUpdateProfile = mainViewModel::saveSurvivorProfile,
                            onSyncCloud = mainViewModel::syncToCloud,
                            onRestoreCloud = mainViewModel::restoreSavesFromCloud
                        )
                    }

                    composable(ROUTE_SKINS) {
                        SkinStudioScreen(
                            activeSkin = activeSkin,
                            savedSkins = allSkins,
                            onSelectSkin = mainViewModel::selectActiveSkin,
                            onSaveCustomSkin = { skin -> mainViewModel.saveCustomSkin(skin, true) },
                            onDeleteSkin = mainViewModel::deleteCustomSkin
                        )
                    }

                    composable(ROUTE_LAN) {
                        LanLobbyScreen(
                            profile = profile,
                            activeSkin = activeSkin,
                            lanState = lanState,
                            voiceState = voiceState,
                            friends = friends,
                            initialMode = pendingLanMode,
                            onStartDiscovery = { mainViewModel.lanManager.startDiscovery() },
                            onStopDiscovery = { mainViewModel.lanManager.stopDiscovery() },
                            onHostRoom = mainViewModel::hostLanRoom,
                            onJoinRoom = mainViewModel::joinLanRoom,
                            onLeaveRoom = mainViewModel::leaveLanRoom,
                            onScannedQr = mainViewModel::handleScannedQr,
                            onRemoveFriend = mainViewModel::removeFriend,
                            onToggleMic = { mainViewModel.voiceManager.setMicEnabled(it) },
                            onToggleSpeaker = { mainViewModel.voiceManager.setSpeakerEnabled(it) },
                            onEnterGameWorld = { navController.navigate(ROUTE_GAME) }
                        )
                    }

                    composable(ROUTE_AI) {
                        AiGuideScreen(
                            aiViewModel = aiViewModel,
                            gameState = gameState
                        )
                    }

                    composable(ROUTE_GAME) {
                        GameWorldScreen(
                            gameState = gameState,
                            activeSkin = activeSkin,
                            lanState = lanState,
                            voiceState = voiceState,
                            onSetGameActive = mainViewModel::setGameActive,
                            onJoystickMove = mainViewModel::setJoystickVector,
                            onGatherOrAttack = mainViewModel::performGatherOrAttack,
                            onFeedCampfire = mainViewModel::feedCampfire,
                            onUseItem = mainViewModel::useOrEatItem,
                            onCraftRecipe = mainViewModel::craftRecipe,
                            onRevive = mainViewModel::reviveAtCampfire,
                            onToggleMic = { mainViewModel.voiceManager.setMicEnabled(it) },
                            onToggleSpeaker = { mainViewModel.voiceManager.setSpeakerEnabled(it) },
                            onExitToHub = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
