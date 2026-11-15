package com.example.core

import android.app.Application
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AndroidPlatformHelper
import com.example.AppRoutingConfig
import com.example.BuildConfig
import com.example.CoreService
import com.example.UpdateNotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal object RelayFallbackDetector {
    fun isDirectControlPlaneFailure(message: String): Boolean {
        val m = message.lowercase(Locale.US)
        if (m.isEmpty()) return false
        val isTimeoutOrBlock = m.contains("context deadline exceeded") ||
            m.contains("http 451") ||
            m.contains("timed out") ||
            m.contains("connection reset") ||
            m.contains("unexpected eof") ||
            m.contains("eof") ||
            m.contains("connection refused")
        if (!isTimeoutOrBlock) return false
        return m.contains("fetching server list") ||
            m.contains("firefox versions") ||
            m.contains("firefox_versions.json") ||
            m.contains("vpn-serverlist") ||
            m.contains("vpn.mozilla.org") ||
            m.contains("accounts.firefox.com") ||
            m.contains("acquire pass") ||
            m.contains("guardian")
    }
}

enum class MainTab {
    HOME,
    SERVERS,
    PROFILE,
}

enum class ConnectionModeUi(val wire: String, val label: String, val subtitle: String) {
    PROXY(CoreService.MODE_PROXY, "Proxy", "SOCKS5"),
    VPN(CoreService.MODE_VPN, "VPN", "All Device");

    companion object {
        fun fromWire(wire: String): ConnectionModeUi = when (wire) {
            CoreService.MODE_PROXY -> PROXY
            else -> VPN
        }
    }
}

sealed interface ClxyEvent {
    data class Toast(val message: String) : ClxyEvent
    data object RequestVpnPermission : ClxyEvent
    data class OpenBackupExport(val suggestedName: String, val bytes: ByteArray) : ClxyEvent
    data object OpenBackupImport : ClxyEvent
}

data class ClxyUiState(
    val showOnboarding: Boolean = true,
    val activeTab: MainTab = MainTab.HOME,
    val connectionMode: ConnectionModeUi = ConnectionModeUi.VPN,
    val status: CoreStatus? = null,
    val accounts: AccountList? = null,
    val locations: LocationList? = null,
    val locationsLoading: Boolean = false,
    val locationsPinging: Boolean = false,
    val locationsError: String? = null,
    val lastTest: ConnectionTest? = null,
    val testingConnection: Boolean = false,
    val switchingProxy: Boolean = false,
    val switchingLocation: Boolean = false,
    val busyAccountIds: Set<String> = emptySet(),
    val coreStarting: Boolean = true,
    val unavailable: CoreUnavailable? = null,
    val elapsedSeconds: Long = 0L,
    val deviceOriginCountryCode: String = "",
    val deviceOriginLabel: String = "Local Device",
    val deviceLocalIp: String = "Direct",
    val appSettings: AppSettingsState = AppSettingsState(),
    val sharedRelayEnabled: Boolean = false,
    val showSpeedInNotification: Boolean = true,
    val activeAccountTrackedBytes: Long = 0L,
    val openSignInDialog: Boolean = false,
    val signInPrefillEmail: String = "",
    val openBrowserSignIn: Boolean = false,
    val browserSignInEmail: String = "",
    val browserStartedState: SignInState? = null,
    val checkingForUpdate: Boolean = false,
    val availableUpdate: AppUpdateInfo? = null,
    val lastCheckedUpdate: AppUpdateInfo? = null,
    val updateCheckError: String? = null,
    val showUpdateDialog: Boolean = false,
    val updateBannerDismissed: Boolean = false,
)

class ClxyViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val dataDir = CoreService.dataDir(appContext).absolutePath
    private val settingsStore = AppSettingsStore(dataDir)
    private val accountTrafficStore = AccountTrafficStore(dataDir)

    private val _uiState = MutableStateFlow(
        ClxyUiState(
            showOnboarding = !settingsStore.current.onboardingCompleted,
            connectionMode = ConnectionModeUi.fromWire(CoreService.connectionMode(appContext)),
            appSettings = settingsStore.current,
            showSpeedInNotification = CoreService.showSpeed(appContext),
            deviceOriginCountryCode = detectDeviceCountryCode(),
            deviceOriginLabel = detectDeviceCountryName(),
        ),
    )
    val uiState: StateFlow<ClxyUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ClxyEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<ClxyEvent> = _events.asSharedFlow()

    private var api: CoreApi? = null
    private var pollJob: Job? = null
    private var timerJob: Job? = null
    private var onScreen = true
    private var lastAccountPollMs = 0L
    private var firstRunApplied = false
    private var pendingVpnStartAction: (() -> Unit)? = null
    private var retestAtMs: Long = 0L
    private var retestDelayMs: Long = 5_000L
    private var seenConnectionKey: String? = null
    private var measuringDelay = false
    private var lastRetriedAtMs = 0L
    private var cameToFront = false
    private var lastAutoQuotaRefreshMs = 0L
    private var quotaRefreshJob: Job? = null
    private var locationsJob: Job? = null
    private var pingLocationsJob: Job? = null
    private var lastCheckedLogId = 0L
    private var autoRelayFallbackAttempted = false
    private val relayFallbackMutex = Mutex()
    private val updateChecker = AppUpdateChecker()
    private var updateCheckJob: Job? = null
    private var checkedUpdateSessionKey: String? = null
    private var notifiedUpdateTag: String? = null

    private var lastTestKey: String? = null

    init {
        startCoreAndPolling()
        startSessionTimer()
    }

    private fun startCoreAndPolling() {
        viewModelScope.launch(Dispatchers.IO) {
            CoreService.startCore(appContext, on = CoreService.on)
            repeat(20) {
                try {
                    val endpoint = CoreLocator.locate(dataDir)
                    api = CoreApi(endpoint)
                    _uiState.update { it.copy(coreStarting = false, unavailable = null) }
                    return@launch
                } catch (e: CoreUnavailable) {
                    _uiState.update { it.copy(unavailable = e) }
                    delay(250)
                }
            }
            _uiState.update { it.copy(coreStarting = false) }
        }
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                if (api == null) {
                    tryConnectApi()
                }
                if (api != null) {
                    refreshInternal(accountsToo = false)
                }
                val interval = computePollInterval()
                delay(interval)
            }
        }
    }

    private fun startSessionTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                val st = _uiState.value.status
                val running = st != null && st.proxyOn && st.state.phase == EnginePhase.RUNNING
                if (running) {
                    val startedAt = st?.proxy?.startedAtMs
                    val elapsed = if (startedAt != null && startedAt > 0L) {
                        ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0L)
                    } else {
                        _uiState.value.elapsedSeconds + 1L
                    }
                    _uiState.update { it.copy(elapsedSeconds = elapsed) }
                } else if (_uiState.value.elapsedSeconds != 0L) {
                    _uiState.update { it.copy(elapsedSeconds = 0L) }
                }
                delay(1_000L)
            }
        }
    }

    private suspend fun tryConnectApi() {
        withContext(Dispatchers.IO) {
            try {
                if (!mobile.Mobile.running()) {
                    CoreService.startCore(appContext, on = CoreService.on)
                }
                val endpoint = CoreLocator.locate(dataDir)
                api = CoreApi(endpoint)
                _uiState.update { it.copy(coreStarting = false, unavailable = null) }
            } catch (e: CoreUnavailable) {
                _uiState.update { it.copy(unavailable = e) }
            }
        }
    }

    private fun computePollInterval(): Long {
        val s = _uiState.value.status
        val fast = _uiState.value.switchingProxy ||
            _uiState.value.switchingLocation ||
            (s?.connectPending == true) ||
            (s?.autoConnecting == true) ||
            (s?.state?.phase == EnginePhase.STARTING)
        return when {
            fast -> 1_000L
            onScreen -> 2_000L
            else -> 10_000L
        }
    }

    fun setOnScreen(visible: Boolean) {
        val wasOff = !onScreen
        onScreen = visible
        if (!visible) {
            accountTrafficStore.flush()
        } else if (wasOff) {
            cameToFront = true
            viewModelScope.launch {
                val now = System.currentTimeMillis()
                if (now - lastRetriedAtMs > 5_000L) {
                    lastRetriedAtMs = now
                    runCatching { api?.retry() }
                }
                refresh(accountsToo = true)
            }
        }
    }

    fun refresh(accountsToo: Boolean = true) {
        viewModelScope.launch {
            refreshInternal(accountsToo)
        }
    }

    private suspend fun refreshInternal(accountsToo: Boolean) {
        val c = api ?: return
        try {
            val status = c.status()
            if (status.totalAccounts > 0 && _uiState.value.showOnboarding) {
                settingsStore.markOnboardingCompleted()
                _uiState.update {
                    it.copy(
                        showOnboarding = false,
                        appSettings = settingsStore.current,
                    )
                }
            }
            if (!firstRunApplied) {
                if (applyInitialDefaultsIfNeeded(c)) {
                    firstRunApplied = true
                }
            }

            val test = status.lastTest
            val newTest = _uiState.value.lastTest
            val mergedTest = if (test != null && status.isCurrentTest(test)) {
                test
            } else if (newTest != null && status.isCurrentTest(newTest)) {
                newTest
            } else {
                null
            }

            val now = System.currentTimeMillis()
            val shouldFetchAccounts = accountsToo || _uiState.value.accounts == null || (now - lastAccountPollMs >= 15_000L)
            val accList = if (shouldFetchAccounts) {
                lastAccountPollMs = now
                runCatching { c.accounts() }.getOrNull() ?: _uiState.value.accounts
            } else {
                _uiState.value.accounts
            }

            val listedActive = accList?.accounts?.firstOrNull { it.active }
                ?: accList?.accounts?.firstOrNull { it.enabled && !it.needsSignIn }
                ?: accList?.accounts?.firstOrNull { !it.needsSignIn }
                ?: accList?.accounts?.firstOrNull()
            val resolvedAcc = status.activeAccount ?: listedActive
            val effectiveResetMs = (resolvedAcc?.usage ?: status.pass?.toFallbackUsage())?.resetAtMs
            val trackedTotal = accountTrafficStore.recordTraffic(
                accountId = resolvedAcc?.id.orEmpty(),
                traffic = status.traffic,
                resetAtMs = effectiveResetMs,
            )

            _uiState.update {
                it.copy(
                    status = status,
                    accounts = accList,
                    lastTest = mergedTest,
                    activeAccountTrackedBytes = trackedTotal,
                    unavailable = null,
                    coreStarting = false,
                )
            }

            maybeRetestUnconfirmed(status, mergedTest)
            maybeRefreshDelay(status, mergedTest)
            maybeRefreshActiveQuota(status, accList)
            maybeCheckControlPlaneLogs(c, status)
            maybeCheckForUpdatesAfterConnect(status, mergedTest)
        } catch (e: CoreUnavailable) {
            api?.close()
            api = null
            _uiState.update { it.copy(unavailable = e) }
        } catch (_: Exception) {
        }
    }

    private suspend fun applyInitialDefaultsIfNeeded(c: CoreApi): Boolean {
        val current = settingsStore.current
        return runCatching {
            val cfg = c.settings()
            var changed = false

            if (!current.initialRelayDefaultsApplied) {
                val net = cfg.optJSONObject("network") ?: JSONObject().also { cfg.put("network", it) }
                net.put("shared_relay_enabled", false)
                net.put("api_relay_enabled", false)
                net.put("api_relay", "")
                net.put("api_relay_ech", "off")
                net.put("api_relay_ech_config", "")
                changed = true
            }

            var detectedRegion = ""
            if (current.firstRun) {
                detectedRegion = AndroidPlatformHelper.firstRunRegion(appContext)
                if (detectedRegion.isNotEmpty()) {
                    val routing = cfg.optJSONObject("routing") ?: JSONObject().also { cfg.put("routing", it) }
                    if (!routing.optBoolean("enabled", false) && routing.optString("region", "").isEmpty()) {
                        routing.put("enabled", true)
                        routing.put("region", detectedRegion)
                        changed = true
                    }
                }
            }

            if (changed) {
                c.saveSettings(cfg)
            }
            if (!current.initialRelayDefaultsApplied) {
                settingsStore.markInitialRelayDefaultsApplied()
            }
            if (current.firstRun) {
                settingsStore.markFirstRunRegionDone(detectedRegion)
            }
            val net = cfg.optJSONObject("network")
            val sharedEnabled = net?.optBoolean("shared_relay_enabled", false) == true
            _uiState.update {
                it.copy(
                    appSettings = settingsStore.current,
                    sharedRelayEnabled = sharedEnabled,
                )
            }
            true
        }.getOrDefault(false)
    }

    private suspend fun maybeCheckControlPlaneLogs(c: CoreApi, status: CoreStatus) {
        if (autoRelayFallbackAttempted || settingsStore.current.relayUserConfigured || status.noNetwork) {
            return
        }
        val shouldInspectLogs = status.proxyOn ||
            status.connectPending ||
            status.autoConnecting ||
            status.connectFailure.isNotEmpty() ||
            _uiState.value.locationsLoading ||
            _uiState.value.locations?.locations.isNullOrEmpty() ||
            _uiState.value.testingConnection
        if (!shouldInspectLogs) return

        val entries = runCatching { c.logs(lastCheckedLogId) }.getOrNull().orEmpty()
        if (entries.isNotEmpty()) {
            lastCheckedLogId = entries.maxOf { it.id }
        }
        val failedInLogs = entries.any { RelayFallbackDetector.isDirectControlPlaneFailure(it.message) }
        val failedInStatus = RelayFallbackDetector.isDirectControlPlaneFailure(status.connectFailure) ||
            RelayFallbackDetector.isDirectControlPlaneFailure(status.proxy?.wakeError.orEmpty())
        if (!failedInLogs && !failedInStatus) return

        if (tryAutoEnableSharedRelay(c)) {
            if (_uiState.value.activeTab == MainTab.SERVERS && _uiState.value.locations?.locations.isNullOrEmpty()) {
                loadLocations()
            }
        }
    }

    private suspend fun tryAutoEnableSharedRelay(c: CoreApi, forceEvenIfAttempted: Boolean = false): Boolean =
        relayFallbackMutex.withLock {
            if (!forceEvenIfAttempted && (autoRelayFallbackAttempted || settingsStore.current.relayUserConfigured)) {
                return@withLock false
            }
            if (!forceEvenIfAttempted) {
                autoRelayFallbackAttempted = true
            }
            runCatching {
                val cfg = c.settings()
                val net = cfg.optJSONObject("network") ?: JSONObject().also { cfg.put("network", it) }
                if (net.optBoolean("shared_relay_enabled", false) || net.optBoolean("api_relay_enabled", false)) {
                    _uiState.update { it.copy(sharedRelayEnabled = net.optBoolean("shared_relay_enabled", false)) }
                    return@withLock false
                }
                net.put("shared_relay_enabled", true)
                if (net.optString("api_relay_ech", "off") == "off") {
                    net.put("api_relay_ech", "on")
                }
                c.saveSettings(cfg)
                _uiState.update { it.copy(sharedRelayEnabled = true) }
                val st = _uiState.value.status
                if (st?.proxyOn == true || st?.connectPending == true || st?.connectFailure?.isNotEmpty() == true) {
                    runCatching { c.retry() }
                }
                showToast("Direct server API timed out — enabled Shared Relay Pool fallback")
                true
            }.getOrDefault(false)
        }

    private fun maybeRetestUnconfirmed(s: CoreStatus, currentTest: ConnectionTest?) {
        val key = if (s.state.phase == EnginePhase.RUNNING) "${s.proxy?.startedAtMs}|${s.networkChangedAtMs}" else null
        if (key != seenConnectionKey) {
            seenConnectionKey = key
            retestAtMs = 0L
            retestDelayMs = 5_000L
        }
        if (!s.proxyOn || s.state.phase != EnginePhase.RUNNING || _uiState.value.testingConnection || _uiState.value.switchingProxy) {
            return
        }
        if (s.noNetwork || s.autoConnecting || s.connectPending) return
        if (s.confirmedBy(currentTest)) {
            retestAtMs = 0L
            retestDelayMs = 5_000L
            return
        }
        val now = System.currentTimeMillis()
        if (retestAtMs != 0L && now < retestAtMs) return
        retestAtMs = now + retestDelayMs
        retestDelayMs = (retestDelayMs * 2L).coerceAtMost(60_000L)
        viewModelScope.launch {
            runCatching { runConnectionTestInternal(silent = true) }
        }
    }

    private fun maybeRefreshDelay(s: CoreStatus, currentTest: ConnectionTest?) {
        val looked = cameToFront
        cameToFront = false
        val at = currentTest?.atMs ?: return
        if (!onScreen || measuringDelay || _uiState.value.testingConnection || _uiState.value.switchingProxy) return
        if (!s.confirmedBy(currentTest)) return
        val age = System.currentTimeMillis() - at
        val due = if (currentTest.responseMs >= 400) age >= 10_000L else (looked && age >= 60_000L)
        if (!due) return
        measuringDelay = true
        viewModelScope.launch {
            try {
                api?.delay()
            } catch (_: Exception) {
            } finally {
                measuringDelay = false
            }
        }
    }

    private fun maybeRefreshActiveQuota(
        s: CoreStatus,
        accList: AccountList?,
        force: Boolean = false,
    ) {
        if (!onScreen || s.noNetwork) return
        if (quotaRefreshJob?.isActive == true) return
        val listedActive = accList?.accounts?.firstOrNull { it.active }
            ?: accList?.accounts?.firstOrNull { it.enabled && !it.needsSignIn }
            ?: accList?.accounts?.firstOrNull { !it.needsSignIn }
        val activeAcc = s.activeAccount ?: listedActive ?: return
        if (activeAcc.id.isEmpty() || activeAcc.needsSignIn) return

        val now = System.currentTimeMillis()
        val usage = activeAcc.usage ?: listedActive?.usage
        val checkedAt = usage?.checkedAtMs ?: 0L
        val due = when {
            force -> now - lastAutoQuotaRefreshMs >= 5_000L
            usage == null -> now - lastAutoQuotaRefreshMs >= 15_000L
            s.state.phase == EnginePhase.RUNNING ->
                (checkedAt == 0L || now - checkedAt >= 90_000L) && (now - lastAutoQuotaRefreshMs >= 90_000L)
            checkedAt > 0L && now - checkedAt >= 120_000L ->
                now - lastAutoQuotaRefreshMs >= 60_000L
            else -> false
        }
        if (!due) return

        lastAutoQuotaRefreshMs = now
        val targetId = activeAcc.id
        quotaRefreshJob = viewModelScope.launch {
            try {
                api?.refreshAccount(targetId)
                refreshInternal(accountsToo = true)
            } catch (_: Exception) {
            }
        }
    }

    private fun maybeCheckForUpdatesAfterConnect(s: CoreStatus, currentTest: ConnectionTest?) {
        if (!s.proxyOn) {
            checkedUpdateSessionKey = null
            return
        }
        if (s.noNetwork || s.state.phase != EnginePhase.RUNNING || !s.confirmedBy(currentTest)) {
            return
        }
        val sessionKey = "${s.proxy?.startedAtMs ?: 0L}|${s.networkChangedAtMs}"
        if (sessionKey == checkedUpdateSessionKey) return
        checkedUpdateSessionKey = sessionKey
        checkForUpdates(userInitiated = false, socksProxyListen = s.proxy?.listen)
    }

    fun openUpdateDialog() {
        _uiState.update { it.copy(showUpdateDialog = true) }
        if (!_uiState.value.checkingForUpdate) {
            checkForUpdates(userInitiated = true)
        }
    }

    fun dismissUpdateDialog() {
        _uiState.update { it.copy(showUpdateDialog = false) }
    }

    fun dismissUpdateBanner() {
        _uiState.update { it.copy(updateBannerDismissed = true) }
    }

    fun openUpdateUrl(url: String) {
        if (!AndroidPlatformHelper.openUrl(appContext, url)) {
            showToast("No browser found to open $url")
        }
    }

    fun checkForUpdates(
        userInitiated: Boolean = true,
        socksProxyListen: String? = _uiState.value.status?.proxy?.listen,
    ) {
        if (updateCheckJob?.isActive == true) return
        val statusSnapshot = _uiState.value.status
        val activeProxy = socksProxyListen?.takeIf {
            statusSnapshot?.proxyOn == true && statusSnapshot.state.phase == EnginePhase.RUNNING
        }
        updateCheckJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    checkingForUpdate = true,
                    updateCheckError = if (userInitiated) null else it.updateCheckError,
                )
            }
            try {
                val info = updateChecker.checkLatest(
                    currentVersionName = BuildConfig.VERSION_NAME,
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    socksProxyListen = activeProxy,
                )
                if (info.isUpdateAvailable) {
                    val isNewTag = notifiedUpdateTag != info.latestTag
                    _uiState.update {
                        it.copy(
                            checkingForUpdate = false,
                            availableUpdate = info,
                            lastCheckedUpdate = info,
                            updateCheckError = null,
                            updateBannerDismissed = if (!userInitiated && isNewTag) false else it.updateBannerDismissed,
                        )
                    }
                    if (!userInitiated && isNewTag) {
                        notifiedUpdateTag = info.latestTag
                        UpdateNotificationHelper.showUpdateAvailable(appContext, info)
                        showToast("Update available: ${info.latestTag}")
                    } else if (userInitiated && isNewTag) {
                        notifiedUpdateTag = info.latestTag
                        UpdateNotificationHelper.showUpdateAvailable(appContext, info)
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            checkingForUpdate = false,
                            availableUpdate = null,
                            lastCheckedUpdate = info,
                            updateCheckError = null,
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        checkingForUpdate = false,
                        updateCheckError = if (userInitiated) (e.message ?: "Unable to check GitHub for updates") else it.updateCheckError,
                    )
                }
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _uiState.update { it.copy(activeTab = tab) }
        if (tab == MainTab.SERVERS && _uiState.value.locations?.locations.isNullOrEmpty()) {
            loadLocations()
        }
    }

    fun completeOnboarding() {
        settingsStore.markOnboardingCompleted()
        _uiState.update {
            it.copy(
                showOnboarding = false,
                appSettings = settingsStore.current,
            )
        }
    }

    fun showToast(message: String) {
        _events.tryEmit(ClxyEvent.Toast(CountryNames.brandAsClxy(message)))
    }

    fun setConnectionMode(mode: ConnectionModeUi) {
        val previous = _uiState.value.connectionMode
        if (previous == mode) return
        if (mode == ConnectionModeUi.VPN && CoreService.on && VpnService.prepare(appContext) != null) {
            pendingVpnStartAction = {
                CoreService.setConnectionMode(appContext, mode.wire)
                _uiState.update { it.copy(connectionMode = mode) }
                showToast("Mode switched to ${mode.label}")
            }
            _events.tryEmit(ClxyEvent.RequestVpnPermission)
            return
        }
        CoreService.setConnectionMode(appContext, mode.wire)
        _uiState.update { it.copy(connectionMode = mode) }
        val modeNote = when (mode) {
            ConnectionModeUi.VPN -> "Mode: VPN (Full device tunnel)"
            ConnectionModeUi.PROXY -> {
                val port = _uiState.value.status?.proxy?.listen ?: "127.0.0.1:10810"
                "Mode: Proxy (SOCKS5 $port)"
            }
        }
        showToast(modeNote)
    }

    fun toggleConnection() {
        val s = _uiState.value.status
        val currentlyOn = s?.proxyOn == true || CoreService.on
        if (currentlyOn) {
            turnOffVpn()
        } else {
            if ((s?.totalAccounts ?: 0) == 0) {
                showToast("Add a CLXY Account before connecting")
                openSignIn(forceForm = true)
                return
            }
            turnOnVpn()
        }
    }

    fun turnOnVpn() {
        if (CoreService.usesVpn(appContext) && VpnService.prepare(appContext) != null) {
            pendingVpnStartAction = { executeTurnOn() }
            _events.tryEmit(ClxyEvent.RequestVpnPermission)
            return
        }
        executeTurnOn()
    }

    fun onVpnPermissionResult(granted: Boolean) {
        val pending = pendingVpnStartAction
        pendingVpnStartAction = null
        if (granted) {
            pending?.invoke()
        } else {
            showToast("VPN permission is required for VPN mode")
        }
    }

    private fun executeTurnOn() {
        viewModelScope.launch {
            _uiState.update { it.copy(switchingProxy = true) }
            try {
                withContext(Dispatchers.IO) {
                    CoreService.turnOn(appContext)
                }
                val c = api
                if (c != null) {
                    repeat(15) {
                        val next = runCatching { c.status() }.getOrNull() ?: return@repeat
                        _uiState.update { it.copy(status = next) }
                        if (next.proxyOn || next.connectFailure.isNotEmpty()) return@repeat
                        delay(150)
                    }
                }
                refreshInternal(accountsToo = true)
                _uiState.value.status?.let { st ->
                    maybeRefreshActiveQuota(st, _uiState.value.accounts, force = true)
                }
            } finally {
                _uiState.update { it.copy(switchingProxy = false) }
            }
        }
    }

    fun turnOffVpn() {
        viewModelScope.launch {
            _uiState.update { it.copy(switchingProxy = true) }
            try {
                withContext(Dispatchers.IO) {
                    CoreService.turnOff(appContext)
                }
                refreshInternal(accountsToo = true)
                _uiState.value.status?.let { st ->
                    maybeRefreshActiveQuota(st, _uiState.value.accounts, force = true)
                }
                showToast("CLXY Disconnected")
            } finally {
                _uiState.update { it.copy(switchingProxy = false) }
            }
        }
    }

    fun selectServerLocation(countryCode: String, connectNow: Boolean = true) {
        viewModelScope.launch {
            val c = api ?: return@launch
            _uiState.update { it.copy(switchingLocation = true) }
            try {
                c.setLocation(countryCode)
                refreshInternal(accountsToo = false)
                val name = CountryNames.locationName(countryCode.ifEmpty { "REC" })
                if (connectNow && _uiState.value.status?.proxyOn != true && !CoreService.on) {
                    if ((_uiState.value.status?.totalAccounts ?: 0) > 0) {
                        turnOnVpn()
                    }
                }
                showToast("Selected $name")
            } catch (e: Exception) {
                showToast(e.message ?: "Could not switch server")
            } finally {
                _uiState.update { it.copy(switchingLocation = false) }
            }
        }
    }

    fun loadLocations() {
        if (locationsJob?.isActive == true) return
        val c = api ?: return
        locationsJob = viewModelScope.launch {
            _uiState.update { it.copy(locationsLoading = true, locationsError = null) }
            try {
                var list = c.locations()
                if (list.locations.isEmpty() && _uiState.value.status?.noNetwork != true) {
                    if (tryAutoEnableSharedRelay(c)) {
                        list = c.locations()
                    }
                }
                _uiState.update { it.copy(locations = list, locationsLoading = false) }
            } catch (e: Exception) {
                if (_uiState.value.status?.noNetwork != true && tryAutoEnableSharedRelay(c)) {
                    try {
                        val retried = c.locations()
                        _uiState.update { it.copy(locations = retried, locationsLoading = false, locationsError = null) }
                        return@launch
                    } catch (retryErr: Exception) {
                        val msg = retryErr.message?.let { CountryNames.brandAsClxy(it) }
                        _uiState.update { it.copy(locationsLoading = false, locationsError = msg) }
                        return@launch
                    }
                }
                val msg = e.message?.let { CountryNames.brandAsClxy(it) }
                _uiState.update { it.copy(locationsLoading = false, locationsError = msg) }
            }
        }
    }

    fun enableSharedRelayAndRefresh() {
        val c = api ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(locationsLoading = true, locationsError = null) }
            try {
                val cfg = c.settings()
                val net = cfg.optJSONObject("network") ?: JSONObject().also { cfg.put("network", it) }
                net.put("shared_relay_enabled", true)
                if (net.optString("api_relay_ech", "off") == "off") {
                    net.put("api_relay_ech", "on")
                }
                c.saveSettings(cfg)
                autoRelayFallbackAttempted = true
                _uiState.update { it.copy(sharedRelayEnabled = true) }
                if (_uiState.value.status?.proxyOn == true) {
                    runCatching { c.retry() }
                }
                val list = c.locations()
                _uiState.update { it.copy(locations = list, locationsLoading = false, locationsError = null) }
                showToast("Shared Relay Pool enabled")
            } catch (e: Exception) {
                val msg = e.message?.let { CountryNames.brandAsClxy(it) }
                _uiState.update { it.copy(locationsLoading = false, locationsError = msg) }
                showToast(msg ?: "Could not enable Shared Relay Pool")
            }
        }
    }

    fun pingAllLocations() {
        if (pingLocationsJob?.isActive == true) return
        val c = api ?: return
        pingLocationsJob = viewModelScope.launch {
            _uiState.update { it.copy(locationsPinging = true, locationsError = null) }
            try {
                var list = c.pingLocations()
                if (list.locations.isEmpty() && _uiState.value.status?.noNetwork != true) {
                    if (tryAutoEnableSharedRelay(c)) {
                        list = c.pingLocations()
                    }
                }
                _uiState.update { it.copy(locations = list, locationsPinging = false) }
                showToast("Updated server latencies")
            } catch (e: Exception) {
                if (_uiState.value.status?.noNetwork != true && tryAutoEnableSharedRelay(c)) {
                    try {
                        val retried = c.pingLocations()
                        _uiState.update { it.copy(locations = retried, locationsPinging = false, locationsError = null) }
                        showToast("Updated server latencies")
                        return@launch
                    } catch (retryErr: Exception) {
                        val msg = retryErr.message?.let { CountryNames.brandAsClxy(it) }
                        _uiState.update { it.copy(locationsPinging = false, locationsError = msg) }
                        showToast(msg ?: "Latency check failed")
                        return@launch
                    }
                }
                val msg = e.message?.let { CountryNames.brandAsClxy(it) }
                _uiState.update { it.copy(locationsPinging = false, locationsError = msg) }
                showToast(msg ?: "Latency check failed")
            }
        }
    }

    fun runConnectionTest() {
        viewModelScope.launch {
            runConnectionTestInternal(silent = false)
        }
    }

    private suspend fun runConnectionTestInternal(silent: Boolean) {
        val c = api ?: return
        _uiState.update { it.copy(testingConnection = true) }
        try {
            val result = c.test()
            _uiState.update { it.copy(lastTest = result, testingConnection = false) }
            refreshInternal(accountsToo = false)
            if (!silent) {
                if (result.ok) {
                    showToast("Connection verified (${result.totalMs} ms)")
                } else {
                    showToast(CountryNames.describeFailure(result.errorCode, result.error))
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(testingConnection = false) }
            if (!silent) showToast(e.message ?: "Connection test failed")
        }
    }

    fun restartProxy() {
        viewModelScope.launch {
            try {
                api?.restartProxy()
                refreshInternal(accountsToo = false)
                showToast("Proxy restarted")
            } catch (e: Exception) {
                showToast(e.message ?: "Could not restart proxy")
            }
        }
    }

    fun openSignIn(email: String = "", forceForm: Boolean = true, formFallbackIfUnsupported: Boolean = false) {
        val method = if (forceForm || formFallbackIfUnsupported) SignInMethod.FORM else _uiState.value.appSettings.signInMethod
        if (method == SignInMethod.BROWSER) {
            _uiState.update {
                it.copy(
                    openBrowserSignIn = true,
                    browserSignInEmail = email,
                    openSignInDialog = false,
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    openSignInDialog = true,
                    signInPrefillEmail = email,
                    browserStartedState = null,
                    openBrowserSignIn = false,
                )
            }
        }
    }

    fun closeSignInDialogs() {
        _uiState.update {
            it.copy(
                openSignInDialog = false,
                openBrowserSignIn = false,
                browserStartedState = null,
            )
        }
    }

    fun onBrowserSignInFinished(state: SignInState) {
        _uiState.update {
            it.copy(
                openBrowserSignIn = false,
                openSignInDialog = true,
                signInPrefillEmail = state.email,
                browserStartedState = state,
            )
        }
        if (state.step == SignInStep.DONE) {
            settingsStore.markOnboardingCompleted()
            _uiState.update {
                it.copy(
                    showOnboarding = false,
                    appSettings = settingsStore.current,
                )
            }
            refresh(accountsToo = true)
            _uiState.value.status?.let { st ->
                maybeRefreshActiveQuota(st, _uiState.value.accounts, force = true)
            }
        }
    }

    fun fallbackToPasswordSignIn(email: String = "") {
        _uiState.update {
            it.copy(
                openBrowserSignIn = false,
                openSignInDialog = true,
                signInPrefillEmail = email,
                browserStartedState = null,
            )
        }
    }

    fun activateAccount(a: Account) = performAccountAction(a.id) {
        api?.activateAccount(a.id)
        runCatching { api?.refreshAccount(a.id) }
    }
    fun setAccountEnabled(a: Account, enabled: Boolean) = performAccountAction(a.id) { api?.setAccountEnabled(a.id, enabled) }
    fun refreshAccountQuota(a: Account) = performAccountAction(a.id) { api?.refreshAccount(a.id) }
    fun removeAccount(a: Account) = performAccountAction(a.id) { api?.removeAccount(a.id) }

    fun moveAccount(a: Account, delta: Int) {
        val list = _uiState.value.accounts?.accounts?.toMutableList() ?: return
        val from = list.indexOfFirst { it.id == a.id }
        val to = from + delta
        if (from < 0 || to < 0 || to >= list.size) return
        val moved = list.removeAt(from)
        list.add(to, moved)
        viewModelScope.launch {
            try {
                api?.setAccountOrder(list.map { it.id })
                refreshInternal(accountsToo = true)
            } catch (e: Exception) {
                showToast(e.message ?: "Failed to reorder accounts")
            }
        }
    }

    private fun performAccountAction(id: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(busyAccountIds = it.busyAccountIds + id) }
            try {
                block()
                refreshInternal(accountsToo = true)
            } catch (e: Exception) {
                showToast(e.message ?: "Account action failed")
            } finally {
                _uiState.update { it.copy(busyAccountIds = it.busyAccountIds - id) }
            }
        }
    }

    fun setSignInMethod(method: SignInMethod) {
        settingsStore.setSignInMethod(method)
        _uiState.update { it.copy(appSettings = settingsStore.current) }
    }

    fun setShowPublicIp(show: Boolean) {
        settingsStore.setShowPublicIp(show)
        _uiState.update { it.copy(appSettings = settingsStore.current) }
    }

    fun setUdpNotice(show: Boolean) {
        settingsStore.setUdpNotice(show)
        _uiState.update { it.copy(appSettings = settingsStore.current) }
    }

    fun setShowSpeedInNotification(show: Boolean) {
        CoreService.setShowSpeed(appContext, show)
        _uiState.update { it.copy(showSpeedInNotification = show) }
    }

    fun retryConnection() {
        viewModelScope.launch {
            try {
                api?.retry()
                refreshInternal(accountsToo = false)
                showToast("Retrying connection…")
            } catch (e: Exception) {
                showToast(e.message ?: "Could not retry connection")
            }
        }
    }

    fun triggerBackupExport() {
        viewModelScope.launch {
            val c = api ?: return@launch
            try {
                val (name, bytes) = c.exportBackup()
                _events.emit(ClxyEvent.OpenBackupExport(name, bytes))
            } catch (e: Exception) {
                showToast(e.message ?: "Backup export failed")
            }
        }
    }

    fun triggerBackupImport() {
        _events.tryEmit(ClxyEvent.OpenBackupImport)
    }

    suspend fun inspectBackup(bytes: ByteArray): BackupContents = requireApi().inspectBackup(bytes)

    suspend fun importBackup(bytes: ByteArray): BackupImported {
        val result = requireApi().importBackup(bytes)
        settingsStore.markOnboardingCompleted()
        _uiState.update {
            it.copy(
                showOnboarding = false,
                appSettings = settingsStore.current,
            )
        }
        refreshInternal(accountsToo = true)
        return result
    }

    fun markRelayUserConfigured() {
        settingsStore.markRelayUserConfigured()
        _uiState.update { it.copy(appSettings = settingsStore.current) }
    }

    suspend fun loadSettings(): JSONObject = requireApi().settings()

    suspend fun updateSettings(edit: (JSONObject) -> Unit) {
        val c = requireApi()
        val cfg = c.settings()
        edit(cfg)
        c.saveSettings(cfg)
        val sharedEnabled = cfg.optJSONObject("network")?.optBoolean("shared_relay_enabled", false) == true
        _uiState.update { it.copy(sharedRelayEnabled = sharedEnabled) }
        refreshInternal(accountsToo = true)
    }

    suspend fun loadRouting(): RoutingInfo = requireApi().routing()
    suspend fun updateRoutingLists(): RoutingInfo = requireApi().updateRoutingLists()
    suspend fun testDestination(target: String): DestinationTest = requireApi().testDestination(target)
    suspend fun testDns(servers: List<String>): List<DnsTestHost> = requireApi().testDns(servers)
    suspend fun loadLogs(after: Long): List<LogEntry> = requireApi().logs(after)
    suspend fun loadConnections(): List<ProxyConnection> = requireApi().connections()
    suspend fun clearConnections(): List<ProxyConnection> = requireApi().clearConnections()
    fun testRelay(url: String) = requireApi().testRelay(url)

    suspend fun startSignIn(email: String, password: String): SignInState = requireApi().startSignIn(email, password)
    suspend fun startSignInAgain(accountId: String): SignInState = requireApi().startSignInAgain(accountId)
    suspend fun startBrowserSignIn(email: String, sessionToken: String): SignInState =
        requireApi().startBrowserSignIn(email, sessionToken)
    suspend fun signInState(id: String): SignInState = requireApi().signInState(id)
    suspend fun answerSignIn(id: String, value: String): SignInState = requireApi().answerSignIn(id, value)
    suspend fun cancelSignIn(id: String) = requireApi().cancelSignIn(id)
    suspend fun openSignInProxy(): String = requireApi().openSignInProxy()
    suspend fun closeSignInProxy() = runCatching { requireApi().closeSignInProxy() }.let {}

    fun loadAppRouting(): AppRoutingConfig = AndroidPlatformHelper.loadAppRouting(appContext)
    fun saveAppRouting(cfg: AppRoutingConfig, apply: Boolean) = AndroidPlatformHelper.saveAppRouting(appContext, cfg, apply)
    fun installedApps() = AndroidPlatformHelper.installedApps(appContext)
    fun appIconBitmap(pkg: String) = AndroidPlatformHelper.appIconBitmap(appContext, pkg)

    fun isPrivateDnsActive(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val mode = runCatching {
            Settings.Global.getString(appContext.contentResolver, "private_dns_mode")
        }.getOrNull()
        return mode == "hostname"
    }

    private fun requireApi(): CoreApi = api ?: throw CoreUnavailable(CoreProblem.NOT_ANSWERING)

    private fun detectDeviceCountryCode(): String {
        val simOrNet = AndroidPlatformHelper.routingCountries(appContext)["network_country"]
            ?.takeIf { it.length == 2 }
            ?: AndroidPlatformHelper.routingCountries(appContext)["sim_country"]?.takeIf { it.length == 2 }
            ?: Locale.getDefault().country.takeIf { it.length == 2 }
            ?: "US"
        return simOrNet.uppercase(Locale.US)
    }

    private fun detectDeviceCountryName(): String {
        val cc = detectDeviceCountryCode()
        return CountryNames.locationName(cc, cc)
    }

    override fun onCleared() {
        accountTrafficStore.flush()
        pollJob?.cancel()
        timerJob?.cancel()
        quotaRefreshJob?.cancel()
        updateCheckJob?.cancel()
        locationsJob?.cancel()
        pingLocationsJob?.cancel()
        api?.close()
        super.onCleared()
    }
}
