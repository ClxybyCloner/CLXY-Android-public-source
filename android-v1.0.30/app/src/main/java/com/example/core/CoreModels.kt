package com.example.core

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class EnginePhase {
    STARTING,
    RUNNING,
    WAITING,
    ERROR,
    OFF,
    UNKNOWN;

    companion object {
        fun parse(raw: String): EnginePhase = when (raw) {
            "starting" -> STARTING
            "running" -> RUNNING
            "waiting" -> WAITING
            "error" -> ERROR
            "off" -> OFF
            else -> UNKNOWN
        }
    }
}

object StateCode {
    const val WAITING_FOR_NETWORK = "waiting_for_network"
    const val NO_ACCOUNTS = "no_accounts"
    const val NO_USABLE_ACCOUNT = "no_usable_account"
    const val PROXY_PORT_IN_USE = "proxy_port_in_use"
    const val START_FAILED = "start_failed"
}

object AccountStatus {
    const val READY = "ready"
    const val RESERVED = "reserved"
    const val REAUTH_REQUIRED = "reauth_required"
    const val UNAVAILABLE = "unavailable"
}

object FailureCode {
    const val NO_NETWORK = "no_network"
    const val UNREACHABLE = "unreachable"
    const val UPSTREAM_PROXY = "upstream_proxy"
    const val INSIDE_VPN = "inside_vpn"
    const val REFUSED = "refused"
    const val LOCATION_REFUSED = "location_refused"
    const val SIGN_IN = "sign_in"
    const val QUOTA = "quota"
    const val NOT_ENTITLED = "not_entitled"
    const val REGION = "region"
    const val SERVER = "server"
    const val NO_ACCOUNT = "no_account"
    const val TEST_SITE = "test_site"
    const val OTHER = "other"
}

object SignInStep {
    const val WORKING = "working"
    const val NEED_EMAIL_CODE = "need_email_code"
    const val NEED_TOTP = "need_totp"
    const val NEED_UNBLOCK_CODE = "need_unblock_code"
    const val NEED_CAPTCHA = "need_captcha"
    const val NEED_EMAIL_LINK = "need_email_link"
    const val DONE = "done"
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"
}

object SignInError {
    const val NO_ACCOUNT = "no_account"
    const val WRONG_PASSWORD = "wrong_password"
    const val NO_PASSWORD = "no_password"
    const val TOO_MANY_ATTEMPTS = "too_many_attempts"
    const val UNBLOCK_CODE_REJECTED = "unblock_code_rejected"
    const val WRONG_CODE = "wrong_code"
    const val WRONG_TOTP = "wrong_totp"
    const val TOO_MANY_CODES = "too_many_codes"
    const val TIMED_OUT = "timed_out"
    const val NETWORK = "network"
    const val BLOCKED = "blocked"
    const val TOKEN_BLOCKED = "token_blocked"
}

object JsonHelpers {
    fun str(obj: JSONObject?, key: String): String {
        if (obj == null || obj.isNull(key)) return ""
        val v = obj.opt(key)
        return v as? String ?: ""
    }

    fun int(obj: JSONObject?, key: String): Int {
        if (obj == null || obj.isNull(key)) return 0
        val v = obj.opt(key)
        return (v as? Number)?.toInt() ?: 0
    }

    fun long(obj: JSONObject?, key: String): Long {
        if (obj == null || obj.isNull(key)) return 0L
        val v = obj.opt(key)
        return (v as? Number)?.toLong() ?: 0L
    }

    fun double(obj: JSONObject?, key: String): Double {
        if (obj == null || obj.isNull(key)) return 0.0
        val v = obj.opt(key)
        return (v as? Number)?.toDouble() ?: 0.0
    }

    fun bool(obj: JSONObject?, key: String): Boolean {
        if (obj == null || obj.isNull(key)) return false
        return obj.opt(key) == true
    }

    fun strList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val item = arr.opt(i)
                if (item is String) add(item)
            }
        }
    }

    fun parseRfc3339(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        // Reject Go zero time ("0001-01-01T00:00:00Z")
        if (trimmed.startsWith("0000") || trimmed.startsWith("0001")) return null
        // Normalize fractional seconds to milliseconds and Z to +0000
        val cleaned = buildString {
            var inFrac = false
            var fracCount = 0
            for (ch in trimmed) {
                if (ch == '.') {
                    inFrac = true
                    fracCount = 0
                    append('.')
                } else if (inFrac && ch in '0'..'9') {
                    if (fracCount < 3) {
                        append(ch)
                        fracCount++
                    }
                } else {
                    if (inFrac) {
                        while (fracCount < 3) {
                            append('0')
                            fracCount++
                        }
                        inFrac = false
                    }
                    append(ch)
                }
            }
        }
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
        )
        for (p in patterns) {
            try {
                val fmt = SimpleDateFormat(p, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed: Date? = fmt.parse(cleaned)
                if (parsed != null && parsed.time > 0) return parsed.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun time(obj: JSONObject?, key: String): Long? = parseRfc3339(str(obj, key))
}

data class EngineState(
    val phase: EnginePhase = EnginePhase.UNKNOWN,
    val message: String = "",
    val sinceMs: Long? = null,
    val retryAtMs: Long? = null,
    val code: String = "",
    val address: String = "",
) {
    val proxyPortInUse: Boolean get() = code == StateCode.PROXY_PORT_IN_USE
    val proxyPortBlocked: Boolean get() = proxyPortInUse || (code == StateCode.START_FAILED && address.isNotEmpty())

    companion object {
        fun fromJson(j: JSONObject?): EngineState = EngineState(
            phase = EnginePhase.parse(JsonHelpers.str(j, "phase")),
            message = JsonHelpers.str(j, "message"),
            sinceMs = JsonHelpers.time(j, "since"),
            retryAtMs = JsonHelpers.time(j, "retry_at"),
            code = JsonHelpers.str(j, "code"),
            address = JsonHelpers.str(j, "address"),
        )
    }
}

data class ProxyInfo(
    val listen: String = "",
    val upstream: String = "",
    val countryCode: String = "",
    val countryName: String = "",
    val cityName: String = "",
    val selectionSource: String = "",
    val transport: String = "",
    val startedAtMs: Long? = null,
    val sleepingSinceMs: Long? = null,
    val wakeError: String = "",
    val wakeErrorCode: String = "",
    val wakeFailedAtMs: Long? = null,
    val loginRequired: Boolean = false,
) {
    companion object {
        fun fromJson(j: JSONObject): ProxyInfo = ProxyInfo(
            listen = JsonHelpers.str(j, "listen"),
            upstream = JsonHelpers.str(j, "upstream"),
            countryCode = JsonHelpers.str(j, "country_code"),
            countryName = JsonHelpers.str(j, "country_name"),
            cityName = JsonHelpers.str(j, "city_name"),
            selectionSource = JsonHelpers.str(j, "selection_source"),
            transport = JsonHelpers.str(j, "transport"),
            startedAtMs = JsonHelpers.time(j, "started_at"),
            sleepingSinceMs = JsonHelpers.time(j, "sleeping_since"),
            wakeError = JsonHelpers.str(j, "wake_error"),
            wakeErrorCode = JsonHelpers.str(j, "wake_error_code"),
            wakeFailedAtMs = JsonHelpers.time(j, "wake_failed_at"),
            loginRequired = JsonHelpers.bool(j, "login_required"),
        )
    }
}

data class ExitInfo(
    val ipv4: String = "",
    val ipv6: String = "",
    val countryCode: String = "",
    val countryName: String = "",
    val city: String = "",
    val network: String = "",
) {
    companion object {
        fun fromJson(j: JSONObject): ExitInfo = ExitInfo(
            ipv4 = JsonHelpers.str(j, "ipv4"),
            ipv6 = JsonHelpers.str(j, "ipv6"),
            countryCode = JsonHelpers.str(j, "country_code"),
            countryName = JsonHelpers.str(j, "country_name"),
            city = JsonHelpers.str(j, "city"),
            network = JsonHelpers.str(j, "network"),
        )
    }
}

data class ExitMismatch(
    val chosenCountryCode: String = "",
    val chosenCountryName: String = "",
    val exitCountryCode: String = "",
    val exitCountryName: String = "",
) {
    companion object {
        fun fromJson(j: JSONObject): ExitMismatch = ExitMismatch(
            chosenCountryCode = JsonHelpers.str(j, "chosen_country_code"),
            chosenCountryName = JsonHelpers.str(j, "chosen_country_name"),
            exitCountryCode = JsonHelpers.str(j, "exit_country_code"),
            exitCountryName = JsonHelpers.str(j, "exit_country_name"),
        )
    }
}

data class Usage(
    val unlimited: Boolean = false,
    val limitBytes: Long = 0L,
    val remainingBytes: Long = 0L,
    val resetAtMs: Long? = null,
    val checkedAtMs: Long? = null,
) {
    val usedBytes: Long get() = if (unlimited) 0L else (limitBytes - remainingBytes).coerceAtLeast(0L)

    companion object {
        fun fromJson(j: JSONObject): Usage = Usage(
            unlimited = JsonHelpers.bool(j, "unlimited"),
            limitBytes = JsonHelpers.long(j, "limit_bytes"),
            remainingBytes = JsonHelpers.long(j, "remaining_bytes"),
            resetAtMs = JsonHelpers.time(j, "reset_at"),
            checkedAtMs = JsonHelpers.time(j, "checked_at"),
        )
    }
}

data class Account(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val position: Int = 0,
    val enabled: Boolean = false,
    val active: Boolean = false,
    val status: String = "",
    val reason: String = "",
    val reasonCode: String = "",
    val reasonDetail: String = "",
    val retryAtMs: Long? = null,
    val usage: Usage? = null,
    val usedPercent: Double = 0.0,
    val hasPassword: Boolean = false,
) {
    val needsSignIn: Boolean get() = status == AccountStatus.REAUTH_REQUIRED

    companion object {
        fun fromJson(j: JSONObject): Account = Account(
            id = JsonHelpers.str(j, "id"),
            name = JsonHelpers.str(j, "name"),
            email = JsonHelpers.str(j, "email"),
            position = JsonHelpers.int(j, "position"),
            enabled = JsonHelpers.bool(j, "enabled"),
            active = JsonHelpers.bool(j, "active"),
            status = JsonHelpers.str(j, "status"),
            reason = JsonHelpers.str(j, "reason"),
            reasonCode = JsonHelpers.str(j, "reason_code"),
            reasonDetail = JsonHelpers.str(j, "reason_detail"),
            retryAtMs = JsonHelpers.time(j, "retry_at"),
            usage = j.optJSONObject("usage")?.let { Usage.fromJson(it) },
            usedPercent = JsonHelpers.double(j, "used_percent"),
            hasPassword = JsonHelpers.bool(j, "has_password"),
        )
    }
}

data class AccountList(
    val accounts: List<Account> = emptyList(),
    val thresholdPercent: Double = 95.0,
) {
    companion object {
        fun fromJson(j: JSONObject): AccountList {
            val arr = j.optJSONArray("accounts")
            val list = buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { add(Account.fromJson(it)) }
                    }
                }
            }
            return AccountList(
                accounts = list,
                thresholdPercent = JsonHelpers.double(j, "switch_threshold_percent"),
            )
        }
    }
}

data class Traffic(
    val up: Long = 0L,
    val down: Long = 0L,
    val directUp: Long = 0L,
    val directDown: Long = 0L,
    val upRate: Long = 0L,
    val downRate: Long = 0L,
) {
    companion object {
        fun fromJson(j: JSONObject): Traffic = Traffic(
            up = JsonHelpers.long(j, "up"),
            down = JsonHelpers.long(j, "down"),
            directUp = JsonHelpers.long(j, "direct_up"),
            directDown = JsonHelpers.long(j, "direct_down"),
            upRate = JsonHelpers.long(j, "up_rate"),
            downRate = JsonHelpers.long(j, "down_rate"),
        )
    }
}

data class PassInfo(
    val expiresAtMs: Long? = null,
    val secondsUntilExpiry: Int = 0,
    val lastError: String = "",
    val quotaUnlimited: Boolean = false,
    val quotaLimitBytes: Long = 0L,
    val quotaRemainingBytes: Long = 0L,
    val quotaResetAtMs: Long? = null,
) {
    fun toFallbackUsage(): Usage? {
        if (!quotaUnlimited && quotaLimitBytes <= 0L) return null
        return Usage(
            unlimited = quotaUnlimited,
            limitBytes = quotaLimitBytes,
            remainingBytes = quotaRemainingBytes,
            resetAtMs = quotaResetAtMs,
            checkedAtMs = null,
        )
    }

    companion object {
        fun fromJson(j: JSONObject): PassInfo = PassInfo(
            expiresAtMs = JsonHelpers.time(j, "proxy_pass_expires_at"),
            secondsUntilExpiry = JsonHelpers.int(j, "seconds_until_expiry"),
            lastError = JsonHelpers.str(j, "last_error"),
            quotaUnlimited = JsonHelpers.bool(j, "quota_unlimited"),
            quotaLimitBytes = JsonHelpers.long(j, "quota_limit_bytes"),
            quotaRemainingBytes = JsonHelpers.long(j, "quota_remaining_bytes"),
            quotaResetAtMs = JsonHelpers.time(j, "quota_reset_at"),
        )
    }
}

data class Notice(
    val kind: String = "",
    val detail: String = "",
    val hosts: List<String> = emptyList(),
    val count: Int = 0,
    val lastMs: Long? = null,
) {
    val key: String get() = "$kind|$detail|${lastMs ?: 0L}"

    companion object {
        fun fromJson(j: JSONObject): Notice = Notice(
            kind = JsonHelpers.str(j, "kind"),
            detail = JsonHelpers.str(j, "detail"),
            hosts = JsonHelpers.strList(j.optJSONArray("hosts")),
            count = JsonHelpers.int(j, "count"),
            lastMs = JsonHelpers.time(j, "last"),
        )
    }
}

data class ConnectionTest(
    val atMs: Long? = null,
    val ok: Boolean = false,
    val error: String = "",
    val errorCode: String = "",
    val target: String = "",
    val httpStatus: Int = 0,
    val tunnelMs: Int = 0,
    val tlsMs: Int = 0,
    val responseMs: Int = 0,
    val totalMs: Int = 0,
    val exitIpv4: String = "",
    val exitIpv6: String = "",
    val exitCountry: String = "",
    val exitCity: String = "",
    val exitNetwork: String = "",
    val exitError: String = "",
) {
    companion object {
        fun fromJson(j: JSONObject): ConnectionTest = ConnectionTest(
            atMs = JsonHelpers.time(j, "at"),
            ok = JsonHelpers.bool(j, "ok"),
            error = JsonHelpers.str(j, "error"),
            errorCode = JsonHelpers.str(j, "error_code"),
            target = JsonHelpers.str(j, "target"),
            httpStatus = JsonHelpers.int(j, "http_status"),
            tunnelMs = JsonHelpers.int(j, "tunnel_ms"),
            tlsMs = JsonHelpers.int(j, "tls_ms"),
            responseMs = JsonHelpers.int(j, "response_ms"),
            totalMs = JsonHelpers.int(j, "total_ms"),
            exitIpv4 = JsonHelpers.str(j, "exit_ipv4"),
            exitIpv6 = JsonHelpers.str(j, "exit_ipv6"),
            exitCountry = JsonHelpers.str(j, "exit_country"),
            exitCity = JsonHelpers.str(j, "exit_city"),
            exitNetwork = JsonHelpers.str(j, "exit_network"),
            exitError = JsonHelpers.str(j, "exit_error"),
        )
    }
}

data class CoreStatus(
    val state: EngineState = EngineState(),
    val proxy: ProxyInfo? = null,
    val activeAccount: Account? = null,
    val exit: ExitInfo? = null,
    val exitMismatch: ExitMismatch? = null,
    val lastTest: ConnectionTest? = null,
    val traffic: Traffic? = null,
    val pass: PassInfo? = null,
    val notices: List<Notice> = emptyList(),
    val accountCounts: Map<String, Int> = emptyMap(),
    val thresholdPercent: Double = 95.0,
    val version: String = "",
    val canExit: Boolean = false,
    val systemProxy: String = "",
    val systemProxySupported: Boolean = false,
    val systemProxyTakenBy: String? = null,
    val tunSupported: Boolean = false,
    val autoConnecting: Boolean = false,
    val connectPending: Boolean = false,
    val connectFailure: String = "",
    val selection: String = "",
    val selectionName: String = "",
    val noNetwork: Boolean = false,
    val upstreamProxy: String = "",
    val networkChangedAtMs: Long? = null,
    val dataDir: String = "",
) {
    val totalAccounts: Int get() = accountCounts["total"] ?: 0
    val readyAccounts: Int get() = accountCounts[AccountStatus.READY] ?: 0
    val proxyOn: Boolean get() = connectPending || (connectFailure.isEmpty() && state.phase != EnginePhase.OFF)

    fun isCurrentTest(test: ConnectionTest?): Boolean {
        val at = test?.atMs ?: return false
        val started = proxy?.startedAtMs
        if (started != null && at < started) return false
        val changed = networkChangedAtMs
        return changed == null || at >= changed
    }

    fun confirmedBy(test: ConnectionTest?): Boolean {
        val p = proxy ?: return false
        val at = test?.atMs ?: return false
        if (connectPending || !proxyOn || state.phase != EnginePhase.RUNNING) return false
        if (test.ok != true || noNetwork || !isCurrentTest(test)) return false
        val failed = p.wakeFailedAtMs
        return !(p.wakeError.isNotEmpty() && failed != null && failed > at)
    }

    companion object {
        fun fromJson(j: JSONObject): CoreStatus {
            val counts = mutableMapOf<String, Int>()
            val accObj = j.optJSONObject("accounts")
            if (accObj != null) {
                for (k in accObj.keys()) {
                    counts[k] = JsonHelpers.int(accObj, k)
                }
            }
            val noticesArr = j.optJSONArray("notices")
            val notices = buildList {
                if (noticesArr != null) {
                    for (i in 0 until noticesArr.length()) {
                        noticesArr.optJSONObject(i)?.let { add(Notice.fromJson(it)) }
                    }
                }
            }
            return CoreStatus(
                state = EngineState.fromJson(j.optJSONObject("state")),
                proxy = j.optJSONObject("proxy")?.let { ProxyInfo.fromJson(it) },
                activeAccount = j.optJSONObject("active_account")?.let { Account.fromJson(it) },
                exit = j.optJSONObject("exit")?.let { ExitInfo.fromJson(it) },
                exitMismatch = j.optJSONObject("exit_mismatch")?.let { ExitMismatch.fromJson(it) },
                lastTest = j.optJSONObject("last_test")?.let { ConnectionTest.fromJson(it) },
                traffic = j.optJSONObject("traffic")?.let { Traffic.fromJson(it) },
                pass = j.optJSONObject("pass")?.let { PassInfo.fromJson(it) },
                notices = notices,
                accountCounts = counts,
                thresholdPercent = JsonHelpers.double(j, "switch_threshold_percent"),
                version = JsonHelpers.str(j, "version"),
                canExit = JsonHelpers.bool(j, "can_exit"),
                systemProxy = JsonHelpers.str(j, "system_proxy"),
                systemProxySupported = JsonHelpers.bool(j, "system_proxy_supported"),
                systemProxyTakenBy = j.optJSONObject("system_proxy_taken")?.let { JsonHelpers.str(it, "by") },
                tunSupported = JsonHelpers.bool(j, "tun_supported"),
                autoConnecting = JsonHelpers.bool(j, "auto_connecting"),
                connectPending = JsonHelpers.bool(j, "connect_pending"),
                connectFailure = JsonHelpers.str(j, "connect_failure"),
                selection = JsonHelpers.str(j, "selection"),
                selectionName = JsonHelpers.str(j, "selection_name"),
                noNetwork = JsonHelpers.bool(j, "no_network"),
                upstreamProxy = JsonHelpers.str(j, "upstream_proxy"),
                networkChangedAtMs = JsonHelpers.time(j, "network_changed_at"),
                dataDir = JsonHelpers.str(j, "data_dir"),
            )
        }
    }
}

data class SignInState(
    val id: String = "",
    val email: String = "",
    val step: String = "",
    val message: String = "",
    val error: String = "",
    val errorCode: String = "",
    val passwordAccepted: Boolean = false,
    val captcha: String = "",
    val accountId: String = "",
) {
    val finished: Boolean
        get() = step == SignInStep.DONE || step == SignInStep.FAILED || step == SignInStep.CANCELLED

    val needsAnswer: Boolean
        get() = step == SignInStep.NEED_EMAIL_CODE ||
            step == SignInStep.NEED_TOTP ||
            step == SignInStep.NEED_UNBLOCK_CODE ||
            step == SignInStep.NEED_CAPTCHA

    companion object {
        fun fromJson(j: JSONObject): SignInState = SignInState(
            id = JsonHelpers.str(j, "id"),
            email = JsonHelpers.str(j, "email"),
            step = JsonHelpers.str(j, "step"),
            message = JsonHelpers.str(j, "message"),
            error = JsonHelpers.str(j, "error"),
            errorCode = JsonHelpers.str(j, "error_code"),
            passwordAccepted = JsonHelpers.bool(j, "password_accepted"),
            captcha = JsonHelpers.str(j, "captcha"),
            accountId = JsonHelpers.str(j, "account_id"),
        )
    }
}

data class BackupAccount(
    val email: String,
    val hasPassword: Boolean,
    val exists: Boolean,
)

data class BackupContents(val accounts: List<BackupAccount>) {
    companion object {
        fun fromJson(j: JSONObject): BackupContents {
            val arr = j.optJSONArray("accounts")
            val list = buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val a = arr.optJSONObject(i) ?: continue
                        add(
                            BackupAccount(
                                email = JsonHelpers.str(a, "email"),
                                hasPassword = JsonHelpers.bool(a, "has_password"),
                                exists = JsonHelpers.bool(a, "exists"),
                            ),
                        )
                    }
                }
            }
            return BackupContents(list)
        }
    }
}

data class BackupImported(val added: Int, val updated: Int) {
    companion object {
        fun fromJson(j: JSONObject): BackupImported = BackupImported(
            added = JsonHelpers.int(j, "added"),
            updated = JsonHelpers.int(j, "updated"),
        )
    }
}

data class VpnLocation(
    val code: String,
    val name: String,
    val city: String,
    val server: String,
    val recommended: Boolean,
    val pingMs: Int,
    val pingError: String,
) {
    val answered: Boolean get() = pingMs > 0

    companion object {
        fun fromJson(j: JSONObject): VpnLocation = VpnLocation(
            code = JsonHelpers.str(j, "code"),
            name = JsonHelpers.str(j, "name"),
            city = JsonHelpers.str(j, "city"),
            server = JsonHelpers.str(j, "server"),
            recommended = JsonHelpers.bool(j, "recommended"),
            pingMs = JsonHelpers.int(j, "ping_ms"),
            pingError = JsonHelpers.str(j, "ping_error").ifEmpty { JsonHelpers.str(j, "error") },
        )
    }
}

data class LocationList(
    val locations: List<VpnLocation> = emptyList(),
    val pingedAtMs: Long? = null,
) {
    companion object {
        fun fromJson(j: JSONObject): LocationList {
            val arr = j.optJSONArray("locations")
            val list = buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { add(VpnLocation.fromJson(it)) }
                    }
                }
            }
            return LocationList(
                locations = list,
                pingedAtMs = JsonHelpers.time(j, "pinged_at"),
            )
        }
    }
}

data class LogEntry(
    val id: Long,
    val timeMs: Long?,
    val level: String,
    val message: String,
) {
    companion object {
        fun fromJson(j: JSONObject): LogEntry = LogEntry(
            id = JsonHelpers.long(j, "id"),
            timeMs = JsonHelpers.time(j, "time"),
            level = JsonHelpers.str(j, "level"),
            message = JsonHelpers.str(j, "message"),
        )
    }
}

data class RoutingSource(
    val id: String,
    val name: String,
    val geositeUrl: String,
    val geoipUrl: String,
)

data class RoutingFileInfo(
    val name: String,
    val size: Long,
    val updatedAtMs: Long?,
)

data class RoutingInfo(
    val enabled: Boolean = false,
    val source: String = "",
    val sources: List<RoutingSource> = emptyList(),
    val regionRules: List<String> = emptyList(),
    val active: Boolean = false,
    val summary: String = "",
    val error: String = "",
    val updating: Boolean = false,
    val lastError: String = "",
    val lists: List<RoutingFileInfo> = emptyList(),
) {
    companion object {
        fun fromJson(j: JSONObject): RoutingInfo {
            val filesArr = j.optJSONArray("files")
            val files = buildList {
                if (filesArr != null) {
                    for (i in 0 until filesArr.length()) {
                        val f = filesArr.optJSONObject(i) ?: continue
                        add(
                            RoutingFileInfo(
                                name = JsonHelpers.str(f, "name"),
                                size = JsonHelpers.long(f, "size"),
                                updatedAtMs = JsonHelpers.time(f, "updated_at"),
                            ),
                        )
                    }
                }
            }
            val srcArr = j.optJSONArray("sources")
            val sources = buildList {
                if (srcArr != null) {
                    for (i in 0 until srcArr.length()) {
                        val s = srcArr.optJSONObject(i) ?: continue
                        add(
                            RoutingSource(
                                id = JsonHelpers.str(s, "id"),
                                name = JsonHelpers.str(s, "name"),
                                geositeUrl = JsonHelpers.str(s, "geosite_url"),
                                geoipUrl = JsonHelpers.str(s, "geoip_url"),
                            ),
                        )
                    }
                }
            }
            return RoutingInfo(
                enabled = JsonHelpers.bool(j, "enabled"),
                source = JsonHelpers.str(j, "source"),
                sources = sources,
                regionRules = JsonHelpers.strList(j.optJSONArray("region_rules")),
                active = JsonHelpers.bool(j, "active"),
                summary = JsonHelpers.str(j, "summary"),
                error = JsonHelpers.str(j, "error"),
                updating = JsonHelpers.bool(j, "updating"),
                lastError = JsonHelpers.str(j, "last_error"),
                lists = files,
            )
        }
    }
}

data class DestinationTest(
    val host: String,
    val route: String,
    val rule: String,
    val resolved: String,
    val portRefused: Boolean,
    val note: String,
) {
    companion object {
        fun fromJson(j: JSONObject): DestinationTest = DestinationTest(
            host = JsonHelpers.str(j, "host"),
            route = JsonHelpers.str(j, "route"),
            rule = JsonHelpers.str(j, "rule"),
            resolved = JsonHelpers.str(j, "resolved"),
            portRefused = JsonHelpers.bool(j, "port_refused"),
            note = JsonHelpers.str(j, "note"),
        )
    }
}

data class DnsTestHost(
    val host: String,
    val addrs: List<String>,
    val ms: Int,
    val error: String,
    val role: String,
    val source: String,
    val systemAddrs: List<String>,
    val systemMs: Int,
    val systemError: String,
    val differs: Boolean,
    val filtered: Boolean,
) {
    companion object {
        fun fromJson(j: JSONObject): DnsTestHost = DnsTestHost(
            host = JsonHelpers.str(j, "host"),
            addrs = JsonHelpers.strList(j.optJSONArray("addrs")),
            ms = JsonHelpers.int(j, "ms"),
            error = JsonHelpers.str(j, "error"),
            role = JsonHelpers.str(j, "role"),
            source = JsonHelpers.str(j, "source"),
            systemAddrs = JsonHelpers.strList(j.optJSONArray("system_addrs")),
            systemMs = JsonHelpers.int(j, "system_ms"),
            systemError = JsonHelpers.str(j, "system_error"),
            differs = JsonHelpers.bool(j, "differs"),
            filtered = JsonHelpers.bool(j, "filtered"),
        )
    }
}

data class ProxyConnection(
    val id: Int,
    val startMs: Long?,
    val endMs: Long?,
    val inbound: String,
    val client: String,
    val target: String,
    val network: String,
    val route: String,
    val transport: String,
    val up: Long,
    val down: Long,
) {
    val open: Boolean get() = endMs == null

    companion object {
        fun fromJson(j: JSONObject): ProxyConnection = ProxyConnection(
            id = JsonHelpers.int(j, "id"),
            startMs = JsonHelpers.time(j, "start"),
            endMs = JsonHelpers.time(j, "end"),
            inbound = JsonHelpers.str(j, "inbound"),
            client = JsonHelpers.str(j, "client"),
            target = JsonHelpers.str(j, "target"),
            network = JsonHelpers.str(j, "network"),
            route = JsonHelpers.str(j, "route"),
            transport = JsonHelpers.str(j, "transport"),
            up = JsonHelpers.long(j, "up"),
            down = JsonHelpers.long(j, "down"),
        )
    }
}

data class RelayTestStep(
    val stage: String,
    val status: String,
    val code: String,
    val detail: String,
    val elapsedMs: Int,
) {
    companion object {
        fun fromJson(j: JSONObject): RelayTestStep = RelayTestStep(
            stage = JsonHelpers.str(j, "stage"),
            status = JsonHelpers.str(j, "status"),
            code = JsonHelpers.str(j, "code"),
            detail = JsonHelpers.str(j, "detail"),
            elapsedMs = JsonHelpers.int(j, "elapsed_ms"),
        )
    }
}

data class RelayTestResult(
    val ok: Boolean,
    val host: String,
    val totalMs: Int,
    val steps: List<RelayTestStep>,
) {
    companion object {
        fun fromJson(j: JSONObject): RelayTestResult {
            val arr = j.optJSONArray("steps")
            val steps = buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { add(RelayTestStep.fromJson(it)) }
                    }
                }
            }
            return RelayTestResult(
                ok = JsonHelpers.bool(j, "ok"),
                host = JsonHelpers.str(j, "host"),
                totalMs = JsonHelpers.int(j, "total_ms"),
                steps = steps,
            )
        }
    }
}

sealed interface RelayTestEvent {
    data class Step(val step: RelayTestStep) : RelayTestEvent
    data class Result(val result: RelayTestResult) : RelayTestEvent

    companion object {
        fun fromJson(j: JSONObject): RelayTestEvent? = when (JsonHelpers.str(j, "type")) {
            "step" -> j.optJSONObject("step")?.let { Step(RelayTestStep.fromJson(it)) }
            "result" -> j.optJSONObject("result")?.let { Result(RelayTestResult.fromJson(it)) }
            else -> null
        }
    }
}
