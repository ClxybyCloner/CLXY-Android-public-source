package com.example.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

enum class SignInMethod {
    BROWSER,
    FORM;

    companion object {
        fun parse(raw: String): SignInMethod = if (raw == "browser") BROWSER else FORM
    }

    val raw: String get() = if (this == FORM) "form" else "browser"
}

data class AppSettingsState(
    val firstRun: Boolean = true,
    val initialRelayDefaultsApplied: Boolean = false,
    val relayUserConfigured: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val signInMethod: SignInMethod = SignInMethod.FORM,
    val firstRunRegion: String = "",
    val udpNotice: Boolean = true,
    val showPublicIp: Boolean = true,
    val localeCode: String = "",
)

class AppSettingsStore(private val dataDir: String) {
    private val file = File(dataDir, "app-settings.json")
    private val _state = MutableStateFlow(readInitial())
    val state: StateFlow<AppSettingsState> = _state.asStateFlow()

    val current: AppSettingsState get() = _state.value

    private fun readInitial(): AppSettingsState {
        return try {
            if (!file.exists()) return AppSettingsState()
            val j = JSONObject(file.readText())
            AppSettingsState(
                firstRun = j.optBoolean("first_run", false),
                initialRelayDefaultsApplied = j.optBoolean("initial_relay_defaults_applied", false),
                relayUserConfigured = j.optBoolean("relay_user_configured", false),
                onboardingCompleted = j.optBoolean("onboarding_completed", false),
                signInMethod = SignInMethod.parse(j.optString("sign_in_method", "form")),
                firstRunRegion = j.optString("first_run_region", ""),
                udpNotice = j.optBoolean("udp_notice", true),
                showPublicIp = j.optBoolean("show_public_ip", true),
                localeCode = j.optString("locale", ""),
            )
        } catch (_: Exception) {
            AppSettingsState()
        }
    }

    fun update(transform: (AppSettingsState) -> AppSettingsState) {
        val next = transform(_state.value)
        _state.value = next
        save(next)
    }

    fun markFirstRunRegionDone(region: String) {
        update { it.copy(firstRun = false, firstRunRegion = region) }
    }

    fun markInitialRelayDefaultsApplied() {
        update { it.copy(initialRelayDefaultsApplied = true) }
    }

    fun markRelayUserConfigured() {
        update { it.copy(relayUserConfigured = true) }
    }

    fun markOnboardingCompleted() {
        update { it.copy(onboardingCompleted = true, firstRun = false) }
    }

    fun setSignInMethod(method: SignInMethod) {
        update { it.copy(signInMethod = method) }
    }

    fun setUdpNotice(show: Boolean) {
        update { it.copy(udpNotice = show) }
    }

    fun setShowPublicIp(show: Boolean) {
        update { it.copy(showPublicIp = show) }
    }

    private fun save(s: AppSettingsState) {
        runCatching {
            file.parentFile?.mkdirs()
            val j = JSONObject()
                .put("first_run", s.firstRun)
                .put("initial_relay_defaults_applied", s.initialRelayDefaultsApplied)
                .put("relay_user_configured", s.relayUserConfigured)
                .put("onboarding_completed", s.onboardingCompleted)
                .put("sign_in_method", s.signInMethod.raw)
                .put("first_run_region", s.firstRunRegion)
                .put("udp_notice", s.udpNotice)
                .put("show_public_ip", s.showPublicIp)
                .put("locale", s.localeCode)
            file.writeText(j.toString(2))
        }
    }
}
