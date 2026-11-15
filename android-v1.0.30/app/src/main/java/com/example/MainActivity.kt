package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.BackupContents
import com.example.core.ClxyEvent
import com.example.core.ClxyViewModel
import com.example.core.MainTab
import com.example.ui.AppUpdateDialog
import com.example.ui.BackupImportPreviewDialog
import com.example.ui.BrowserSignInDialog
import com.example.ui.ClxyBottomNavBar
import com.example.ui.ClxyCosmicBackground
import com.example.ui.ClxyToastBanner
import com.example.ui.DiagnosticsLogsDialog
import com.example.ui.HomeScreen
import com.example.ui.InAppUpdateBanner
import com.example.ui.LoginScreen
import com.example.ui.ProfileScreen
import com.example.ui.RelaySettingsDialog
import com.example.ui.RelayTestDialog
import com.example.ui.ServersScreen
import com.example.ui.SignInModalDialog
import com.example.ui.theme.ClxyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: ClxyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val darkBar = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = darkBar, navigationBarStyle = darkBar)

        when (intent?.getStringExtra("open")) {
            "home" -> {
                viewModel.completeOnboarding()
                viewModel.selectTab(MainTab.HOME)
            }
            "update" -> {
                viewModel.completeOnboarding()
                viewModel.openUpdateDialog()
            }
        }

        setContent {
            ClxyTheme {
                ClxyRoot(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        when (intent.getStringExtra("open")) {
            "home" -> {
                viewModel.completeOnboarding()
                viewModel.selectTab(MainTab.HOME)
            }
            "update" -> {
                viewModel.completeOnboarding()
                viewModel.openUpdateDialog()
            }
        }
    }
}

@Composable
private fun ClxyRoot(viewModel: ClxyViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var toastMessage by remember { mutableStateOf<String?>(null) }
    var pendingBackupExportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var backupPreview by remember { mutableStateOf<Pair<ByteArray, BackupContents>?>(null) }
    var showHomeDiagnostics by remember { mutableStateOf(false) }
    var showSignInRelaySettings by remember { mutableStateOf(false) }
    var signInRelayTestUrl by remember { mutableStateOf<String?>(null) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { res ->
        viewModel.onVpnPermissionResult(res.resultCode == Activity.RESULT_OK)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val data = pendingBackupExportBytes
        pendingBackupExportBytes = null
        if (uri != null && data != null) {
            scope.launch {
                val ok = runCatching {
                    viewModel.getApplication<android.app.Application>().contentResolver.openOutputStream(uri)?.use {
                        it.write(data)
                    }
                }.isSuccess
                viewModel.showToast(if (ok) "Backup saved" else "Could not write backup file")
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val bytes = viewModel.getApplication<android.app.Application>().contentResolver
                        .openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                    val contents = viewModel.inspectBackup(bytes)
                    backupPreview = bytes to contents
                } catch (e: Exception) {
                    viewModel.showToast(e.message ?: "Invalid CLXY backup archive")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val ctx = viewModel.getApplication<android.app.Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setOnScreen(true)
                Lifecycle.Event.ON_PAUSE -> viewModel.setOnScreen(false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ClxyEvent.Toast -> {
                    toastMessage = event.message
                }
                is ClxyEvent.RequestVpnPermission -> {
                    val intent = VpnService.prepare(viewModel.getApplication())
                    if (intent == null) {
                        viewModel.onVpnPermissionResult(true)
                    } else {
                        vpnPermissionLauncher.launch(intent)
                    }
                }
                is ClxyEvent.OpenBackupExport -> {
                    pendingBackupExportBytes = event.bytes
                    exportBackupLauncher.launch(event.suggestedName)
                }
                is ClxyEvent.OpenBackupImport -> {
                    importBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                }
            }
        }
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2500L)
            toastMessage = null
        }
    }

    if (!uiState.showOnboarding && uiState.activeTab != MainTab.HOME) {
        BackHandler {
            viewModel.selectTab(MainTab.HOME)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = uiState.showOnboarding,
            transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(240)) },
            label = "rootOnboardingTransition",
        ) { onboarding ->
            if (onboarding) {
                LoginScreen(
                    onStartSignIn = { email, password -> viewModel.startSignIn(email, password) },
                    onPollSignIn = { id -> viewModel.signInState(id) },
                    onAnswerSignIn = { id, value -> viewModel.answerSignIn(id, value) },
                    onCancelSignIn = { id -> viewModel.cancelSignIn(id) },
                    onSignedIn = {
                        viewModel.completeOnboarding()
                        viewModel.refresh(accountsToo = true)
                    },
                    onOpenRelaySettings = { showSignInRelaySettings = true },
                )
            } else {
                ClxyCosmicBackground {
                    Scaffold(
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets.statusBars,
                        bottomBar = {
                            ClxyBottomNavBar(
                                activeTab = uiState.activeTab,
                                onSelectTab = { viewModel.selectTab(it) },
                            )
                        },
                    ) { innerPadding ->
                        AnimatedContent(
                            targetState = uiState.activeTab,
                            transitionSpec = {
                                val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                                (fadeIn(tween(240)) + slideInHorizontally(tween(260)) { it / 12 * dir }) togetherWith
                                    (fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { -it / 12 * dir })
                            },
                            label = "mainTabTransition",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                        ) { tab ->
                            when (tab) {
                                MainTab.HOME -> HomeScreen(
                                    uiState = uiState,
                                    onToggleConnection = { viewModel.toggleConnection() },
                                    onSelectMode = { viewModel.setConnectionMode(it) },
                                    onOpenServers = { viewModel.selectTab(MainTab.SERVERS) },
                                    onOpenSignIn = { viewModel.openSignIn(forceForm = true) },
                                    onOpenDiagnostics = { showHomeDiagnostics = true },
                                )
                                MainTab.SERVERS -> ServersScreen(
                                    uiState = uiState,
                                    onSelectLocation = { code ->
                                        viewModel.selectServerLocation(code, connectNow = true)
                                        viewModel.selectTab(MainTab.HOME)
                                    },
                                    onDisconnect = {
                                        viewModel.turnOffVpn()
                                    },
                                    onPingAll = { viewModel.pingAllLocations() },
                                    onRefreshLocations = { viewModel.loadLocations() },
                                    onCloseToHome = { viewModel.selectTab(MainTab.HOME) },
                                    onEnableSharedRelay = { viewModel.enableSharedRelayAndRefresh() },
                                )
                                MainTab.PROFILE -> ProfileScreen(
                                    viewModel = viewModel,
                                    uiState = uiState,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (uiState.openSignInDialog) {
            SignInModalDialog(
                viewModel = viewModel,
                initialEmail = uiState.signInPrefillEmail,
                startedState = uiState.browserStartedState,
                onDismiss = { viewModel.closeSignInDialogs() },
                onSwitchToBrowser = { email -> viewModel.openSignIn(email = email, forceForm = false) },
            )
        }

        if (uiState.openBrowserSignIn) {
            BrowserSignInDialog(
                viewModel = viewModel,
                initialEmail = uiState.browserSignInEmail,
                onDismiss = { viewModel.closeSignInDialogs() },
                onFallbackToForm = { email -> viewModel.fallbackToPasswordSignIn(email) },
            )
        }

        if (uiState.showOnboarding && showSignInRelaySettings) {
            RelaySettingsDialog(
                viewModel = viewModel,
                onDismiss = { showSignInRelaySettings = false },
                onOpenRelayTest = { signInRelayTestUrl = it },
            )
        }
        if (uiState.showOnboarding && signInRelayTestUrl != null) {
            RelayTestDialog(
                viewModel = viewModel,
                relayUrl = signInRelayTestUrl!!,
                onDismiss = { signInRelayTestUrl = null },
            )
        }

        val preview = backupPreview
        if (preview != null) {
            BackupImportPreviewDialog(
                zipBytes = preview.first,
                contents = preview.second,
                onDismiss = { backupPreview = null },
                onConfirmImport = { bytes ->
                    backupPreview = null
                    scope.launch {
                        try {
                            val imported = viewModel.importBackup(bytes)
                            viewModel.showToast("Imported ${imported.added} new, ${imported.updated} updated")
                        } catch (e: Exception) {
                            viewModel.showToast(e.message ?: "Backup import failed")
                        }
                    }
                },
            )
        }

        if (showHomeDiagnostics) {
            DiagnosticsLogsDialog(
                viewModel = viewModel,
                uiState = uiState,
                onDismiss = { showHomeDiagnostics = false },
            )
        }

        val availableUpdate = uiState.availableUpdate
        if (!uiState.showOnboarding && availableUpdate != null && !uiState.updateBannerDismissed && !uiState.showUpdateDialog) {
            InAppUpdateBanner(
                updateInfo = availableUpdate,
                onOpenUpdate = { viewModel.openUpdateDialog() },
                onDismissBanner = { viewModel.dismissUpdateBanner() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (uiState.showUpdateDialog) {
            AppUpdateDialog(
                checking = uiState.checkingForUpdate,
                updateInfo = uiState.availableUpdate ?: uiState.lastCheckedUpdate,
                errorMessage = uiState.updateCheckError,
                onCheckAgain = { viewModel.checkForUpdates(userInitiated = true) },
                onOpenDownload = { url -> viewModel.openUpdateUrl(url) },
                onOpenReleasePage = { url -> viewModel.openUpdateUrl(url) },
                onDismiss = { viewModel.dismissUpdateDialog() },
            )
        }

        ClxyToastBanner(
            message = toastMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 86.dp),
        )
    }
}
