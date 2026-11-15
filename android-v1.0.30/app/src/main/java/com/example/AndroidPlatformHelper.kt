package com.example

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class InstalledAppItem(
    val packageName: String,
    val label: String,
    val isLauncher: Boolean,
)

data class AppRoutingConfig(
    val onlyChosen: Boolean,
    val bypass: Set<String>,
    val only: Set<String>,
    val suggested: Set<String>,
    val declined: Set<String>,
) {
    val chosen: Set<String>
        get() = if (onlyChosen) only else bypass

    fun isChecked(pkg: String): Boolean = chosen.contains(pkg)

    fun toggle(pkg: String, on: Boolean): AppRoutingConfig {
        val nextChosen = if (on) chosen + pkg else chosen - pkg
        val nextDeclined = if (!onlyChosen && suggested.contains(pkg)) {
            if (on) declined - pkg else declined + pkg
        } else {
            declined
        }
        return if (onlyChosen) {
            copy(only = nextChosen, declined = nextDeclined)
        } else {
            copy(bypass = nextChosen, declined = nextDeclined)
        }
    }

    fun checkAllSuggested(installed: Set<String>): Pair<AppRoutingConfig, Int> {
        val mine = installed.intersect(suggested)
        val added = mine - bypass
        return copy(
            bypass = bypass + added,
            declined = declined - mine,
        ) to added.size
    }
}

object AndroidPlatformHelper {
    val supportedRegions = listOf("", "ir", "cn", "ru", "af", "tr", "id", "br")

    fun firstRunRegion(context: Context): String {
        val countries = routingCountries(context)
        val systemCountry = Locale.getDefault().country
        val candidates = listOf(
            countries["network_country"],
            countries["sim_country"],
            systemCountry,
        )
        for (raw in candidates) {
            val cc = (raw ?: "").trim().lowercase(Locale.US)
            if (cc in supportedRegions && cc.isNotEmpty()) return cc
        }
        return ""
    }

    fun routingCountries(context: Context): Map<String, String> {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return emptyMap()
        }
        val service = context.getSystemService(TelephonyManager::class.java) ?: return emptyMap()
        val phone = runCatching {
            val subscription = SubscriptionManager.getDefaultDataSubscriptionId()
            if (subscription >= 0) service.createForSubscriptionId(subscription) else service
        }.getOrDefault(service)
        return mapOf(
            "network_country" to runCatching { phone.networkCountryIso ?: "" }.getOrDefault(""),
            "sim_country" to runCatching { phone.simCountryIso ?: "" }.getOrDefault(""),
        )
    }

    fun installedApps(context: Context): List<InstalledAppItem> {
        val pm = context.packageManager
        val launchActivities = listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER)
            .flatMap { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(it), 0) }
        val seen = linkedMapOf<String, InstalledAppItem>()
        for (info in launchActivities) {
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName || seen.containsKey(pkg)) continue
            val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
            seen[pkg] = InstalledAppItem(packageName = pkg, label = label, isLauncher = true)
        }
        return seen.values.sortedBy { it.label.lowercase(Locale.US) }
    }

    fun appIconBitmap(context: Context, pkg: String): Bitmap? {
        return runCatching {
            val drawable = context.packageManager.getApplicationIcon(pkg)
            val size = 96
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bitmap
        }.getOrNull()
    }

    fun loadAppRouting(context: Context): AppRoutingConfig {
        if (CoreService.appMode(context) == CoreService.MODE_EXCLUDE && CoreService.suggest(context, force = true)) {
            CoreService.reconnect(context)
        }
        return AppRoutingConfig(
            onlyChosen = CoreService.appMode(context) == CoreService.MODE_INCLUDE,
            bypass = CoreService.appSet(context, CoreService.BYPASS),
            only = CoreService.appSet(context, CoreService.ONLY),
            suggested = CoreService.regionAppList().toSet(),
            declined = CoreService.appSet(context, CoreService.DECLINED),
        )
    }

    fun saveAppRouting(context: Context, cfg: AppRoutingConfig, apply: Boolean) {
        CoreService.setAppRouting(
            context,
            if (cfg.onlyChosen) CoreService.MODE_INCLUDE else CoreService.MODE_EXCLUDE,
            mapOf(
                CoreService.BYPASS to cfg.bypass,
                CoreService.ONLY to cfg.only,
                CoreService.DECLINED to cfg.declined,
            ),
        )
        if (apply) CoreService.reconnect(context)
    }

    suspend fun setWebViewProxy(context: Context, address: String?): Boolean =
        withContext(Dispatchers.Main) {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                return@withContext false
            }
            suspendCancellableCoroutine { cont ->
                val proxies = ProxyController.getInstance()
                val executor = Executor { runnable -> context.mainExecutor.execute(runnable) }
                val done = Runnable {
                    if (cont.isActive) cont.resume(true)
                }
                if (address.isNullOrEmpty()) {
                    proxies.clearProxyOverride(executor, done)
                } else {
                    proxies.setProxyOverride(
                        ProxyConfig.Builder().addProxyRule(address).build(),
                        executor,
                        done,
                    )
                }
            }
        }

    fun openUrl(context: Context, url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    fun deviceAbi(): String = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
}
