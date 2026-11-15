package com.example.core

import org.json.JSONObject
import java.io.File

/**
 * Tracks persistent cumulative proxied bytes per account across disconnects and app restarts.
 * Used as the live + historical account data counter on unlimited plans (where the upstream
 * control-plane does not report a byte counter) and as an immediate progress bridge before
 * the remote quota check refreshes.
 */
class AccountTrafficStore(dataDir: String) {
    private val file = File(dataDir, "account-traffic.json")
    private val bytesByAccount = mutableMapOf<String, Long>()
    private val lastSavedByAccount = mutableMapOf<String, Long>()
    private val resetAtByAccount = mutableMapOf<String, Long>()

    private var lastAccountId: String = ""
    private var lastSessionTotal: Long = 0L
    private var lastSavedSessionTotal: Long = 0L

    init {
        load()
    }

    @Synchronized
    fun bytesFor(accountId: String): Long {
        if (accountId.isEmpty()) return 0L
        return bytesByAccount[accountId] ?: 0L
    }

    @Synchronized
    fun recordTraffic(
        accountId: String,
        traffic: Traffic?,
        resetAtMs: Long? = null,
    ): Long {
        if (accountId.isEmpty()) {
            if (lastAccountId.isNotEmpty() || lastSessionTotal != 0L) {
                lastAccountId = ""
                lastSessionTotal = 0L
                save()
            }
            return 0L
        }

        var dirty = false
        if (resetAtMs != null && resetAtMs > 0L) {
            val prevReset = resetAtByAccount[accountId]
            if (prevReset != null && prevReset > 0L && resetAtMs > prevReset) {
                bytesByAccount[accountId] = 0L
                lastSavedByAccount[accountId] = 0L
                dirty = true
            }
            if (prevReset != resetAtMs) {
                resetAtByAccount[accountId] = resetAtMs
                dirty = true
            }
        }

        val sessionTotal = if (traffic != null) (traffic.up + traffic.down).coerceAtLeast(0L) else 0L
        val delta = when {
            sessionTotal <= 0L -> 0L
            accountId == lastAccountId && sessionTotal >= lastSessionTotal -> sessionTotal - lastSessionTotal
            else -> sessionTotal
        }
        val sessionReset = sessionTotal < lastSessionTotal || accountId != lastAccountId
        lastAccountId = accountId
        lastSessionTotal = sessionTotal

        val current = bytesByAccount[accountId] ?: 0L
        val updated = current + delta
        if (delta > 0L) {
            bytesByAccount[accountId] = updated
            val lastSaved = lastSavedByAccount[accountId] ?: 0L
            if (lastSaved == 0L || updated - lastSaved >= PERSIST_THRESHOLD_BYTES) {
                dirty = true
            }
        } else if (sessionReset || (updated > (lastSavedByAccount[accountId] ?: 0L))) {
            dirty = true
        }

        if (dirty) {
            save()
        }
        return updated
    }

    @Synchronized
    fun flush() {
        val hasUnsaved = bytesByAccount.any { (id, v) -> v != (lastSavedByAccount[id] ?: 0L) } ||
            lastSessionTotal != lastSavedSessionTotal
        if (hasUnsaved) save()
    }

    private fun load() {
        runCatching {
            if (!file.exists()) return
            val root = JSONObject(file.readText())
            val totals = root.optJSONObject("totals")
            if (totals != null) {
                for (key in totals.keys()) {
                    val v = totals.optLong(key, 0L).coerceAtLeast(0L)
                    bytesByAccount[key] = v
                    lastSavedByAccount[key] = v
                }
            }
            val resets = root.optJSONObject("resets")
            if (resets != null) {
                for (key in resets.keys()) {
                    val v = resets.optLong(key, 0L)
                    if (v > 0L) resetAtByAccount[key] = v
                }
            }
            lastAccountId = root.optString("last_account_id", "")
            lastSessionTotal = root.optLong("last_session_total", 0L).coerceAtLeast(0L)
            lastSavedSessionTotal = lastSessionTotal
        }
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            val totals = JSONObject()
            for ((k, v) in bytesByAccount) {
                totals.put(k, v)
                lastSavedByAccount[k] = v
            }
            val resets = JSONObject()
            for ((k, v) in resetAtByAccount) {
                resets.put(k, v)
            }
            val root = JSONObject()
                .put("totals", totals)
                .put("resets", resets)
                .put("last_account_id", lastAccountId)
                .put("last_session_total", lastSessionTotal)
            lastSavedSessionTotal = lastSessionTotal
            file.writeText(root.toString())
        }
    }

    companion object {
        private const val PERSIST_THRESHOLD_BYTES = 16L * 1024L
    }
}
