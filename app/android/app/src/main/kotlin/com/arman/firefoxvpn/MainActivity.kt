package com.arman.firefoxvpn

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.OpenableColumns
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.Result

/**
 * The Flutter UI. Channel "app/android": dataDir, startCore (with the proxy
 * off), setOn, isRunning, openUrl. The UI talks to the core itself through
 * its HTTP API on loopback, as on the desktop.
 */
class MainActivity : FlutterActivity() {
    /** Waits for the user's answer to the VPN permission. */
    private var pendingOn: Result? = null
    /** A backup waiting for the place the user picks to save it. */
    private var pendingSave: Pair<ByteArray, Result>? = null
    /** A backup waiting for the user to pick it. */
    private var pendingOpen: Result? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_UPDATES, false)) {
            val messenger = flutterEngine?.dartExecutor?.binaryMessenger
            if (messenger != null) {
                MethodChannel(messenger, CHANNEL).invokeMethod("openUpdates", null)
                intent.removeExtra(EXTRA_OPEN_UPDATES)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        holdPhoneUpright(resources.configuration)
        super.onCreate(savedInstanceState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        holdPhoneUpright(newConfig)
    }

    /**
     * A phone stays upright (its layout is made for it); a tablet, whose
     * shorter side is 600 dp or more, turns freely. Asked again when the
     * screen changes, so a foldable follows its folding.
     */
    private fun holdPhoneUpright(config: Configuration) {
        val wanted = if (config.smallestScreenWidthDp < 600) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (requestedOrientation != wanted) requestedOrientation = wanted
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "consumeOpenUpdates" -> {
                    val open = intent?.getBooleanExtra(EXTRA_OPEN_UPDATES, false) == true
                    intent?.removeExtra(EXTRA_OPEN_UPDATES)
                    result.success(open)
                }
                "showUpdateNotification" -> {
                    val version = call.argument<String>("version")?.trim().orEmpty()
                    result.success(version.isNotEmpty() && showUpdateNotification(version))
                }
                "dataDir" -> result.success(CoreService.dataDir(this).absolutePath)
                "startCore" -> {
                    CoreService.startCore(this, on = false)
                    result.success(null)
                }
                "setOn" -> {
                    if (call.argument<Boolean>("on") != true) {
                        CoreService.turnOff(this)
                        result.success(null)
                    } else {
                        askForNotifications()
                        // The first time, Android asks the user to allow the
                        // VPN; proxy mode needs no VPN.
                        val consent = if (CoreService.usesVpn(this)) VpnService.prepare(this) else null
                        if (consent == null) {
                            turnOn(result)
                        } else {
                            pendingOn?.error("vpn_denied", "superseded", null)
                            pendingOn = result
                            startActivityForResult(consent, VPN_REQUEST)
                        }
                    }
                }
                "isRunning" -> result.success(mobile.Mobile.running())
                "lastCoreError" -> result.success(CoreService.lastCoreError())
                "connectionMode" -> result.success(CoreService.connectionMode(this))
                "showSpeed" -> result.success(CoreService.showSpeed(this))
                "setShowSpeed" -> {
                    CoreService.setShowSpeed(this, call.argument<Boolean>("on") == true)
                    result.success(null)
                }
                "setConnectionMode" -> {
                    val mode = call.argument<String>("mode") ?: CoreService.MODE_VPN
                    // A running proxy switched to the VPN needs the permission
                    // first; the app turns it off and on again to ask.
                    if (CoreService.on && mode == CoreService.MODE_VPN && VpnService.prepare(this) != null) {
                        result.error("vpn_denied", "the VPN is not allowed yet", null)
                    } else {
                        CoreService.setConnectionMode(this, mode)
                        result.success(null)
                    }
                }
                "installedApps" -> inBackground(result) { installedApps() }
                "appIcon" -> {
                    val pkg = call.argument<String>("package") ?: ""
                    inBackground(result) { appIcon(pkg) }
                }
                "appRouting" -> {
                    CoreService.suggestOnce(this)
                    result.success(
                    mapOf(
                        "mode" to CoreService.appMode(this),
                        "bypass" to CoreService.appSet(this, CoreService.BYPASS).toList(),
                        "only" to CoreService.appSet(this, CoreService.ONLY).toList(),
                        "region" to CoreService.regionAppList(),
                    ),
                    )
                }
                "setAppRouting" -> {
                    fun set(key: String) = (call.argument<List<String>>(key) ?: emptyList()).toSet()
                    CoreService.setAppRouting(
                        this,
                        call.argument<String>("mode") ?: CoreService.MODE_EXCLUDE,
                        mapOf(
                            CoreService.BYPASS to set("bypass"),
                            CoreService.ONLY to set("only"),
                        ),
                    )
                    if (call.argument<Boolean>("apply") == true) CoreService.reconnect(this)
                    result.success(null)
                }
                "setWebViewProxy" -> {
                    if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                        result.success(false)
                        return@setMethodCallHandler
                    }
                    val address = call.argument<String>("address")
                    val proxies = ProxyController.getInstance()
                    val done = Runnable { result.success(true) }
                    val onMain = Executor { main.post(it) }
                    if (address.isNullOrEmpty()) {
                        proxies.clearProxyOverride(onMain, done)
                    } else {
                        proxies.setProxyOverride(
                            ProxyConfig.Builder().addProxyRule(address).build(),
                            onMain,
                            done,
                        )
                    }
                }
                "saveFile" -> {
                    val bytes = call.argument<ByteArray>("bytes")
                    if (bytes == null) {
                        result.error("no_data", "nothing to save", null)
                        return@setMethodCallHandler
                    }
                    pendingSave?.second?.success(null)
                    pendingSave = bytes to result
                    val create = Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType(call.argument<String>("mime") ?: "application/octet-stream")
                        .putExtra(Intent.EXTRA_TITLE, call.argument<String>("name") ?: "CLXY-backup.zip")
                    try {
                        startActivityForResult(create, SAVE_REQUEST)
                    } catch (e: ActivityNotFoundException) {
                        pendingSave = null
                        result.error("no_picker", "this device has no file picker", null)
                    }
                }
                "openFile" -> {
                    pendingOpen?.success(null)
                    pendingOpen = result
                    val open = Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*")
                        .putExtra(Intent.EXTRA_MIME_TYPES, call.argument<List<String>>("mimes")?.toTypedArray())
                    try {
                        startActivityForResult(open, OPEN_REQUEST)
                    } catch (e: ActivityNotFoundException) {
                        pendingOpen = null
                        result.error("no_picker", "this device has no file picker", null)
                    }
                }
                // The phone's main processor type (arm64-v8a, armeabi-v7a, ...).
                "abi" -> result.success(Build.SUPPORTED_ABIS.firstOrNull() ?: "")
                "openUrl" -> {
                    val url = call.argument<String>("url") ?: ""
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        result.success(true)
                    } catch (e: ActivityNotFoundException) {
                        result.success(false)
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Runs [work] off the UI thread and answers with its value. */
    private fun inBackground(result: Result, work: () -> Any?) {
        worker.execute {
            val value = try {
                work()
            } catch (e: Exception) {
                null
            }
            main.post { result.success(value) }
        }
    }

    /** The apps with a launcher icon, but this one: package and name. */
    private fun installedApps(): List<Map<String, String>> {
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != packageName }
            .map { mapOf("package" to it.packageName, "label" to it.loadLabel(pm).toString()) }
    }

    /** An app's icon as a small PNG. */
    private fun appIcon(pkg: String): ByteArray {
        val drawable = packageManager.getApplicationIcon(pkg)
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun turnOn(result: Result) {
        try {
            CoreService.turnOn(this)
            result.success(null)
        } catch (e: Exception) {
            result.error("start_failed", ProviderText.error(e), null)
        }
    }

    /** Posts a separate, user-actionable update notice when Android permits it. */
    private fun showUpdateNotification(version: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = manager.getNotificationChannel(UPDATE_CHANNEL)
            if (existing?.importance == NotificationManager.IMPORTANCE_NONE) return false
            if (existing == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        UPDATE_CHANNEL,
                        getString(R.string.update_notification_channel),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ),
                )
            }
        }

        val openUpdates = Intent(this, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_UPDATES, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            UPDATE_REQUEST_CODE,
            openUpdates,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = ProviderText.neutralize(getString(R.string.update_notification_body, version))
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, UPDATE_CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder
            .setSmallIcon(R.drawable.ic_stat_clxy)
            .setContentTitle(getString(R.string.update_notification_title))
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_stat_clxy),
                    getString(R.string.update_notification_action),
                    openPendingIntent,
                ).build(),
            )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            builder.setPriority(Notification.PRIORITY_DEFAULT)
        }
        return try {
            manager.notify(UPDATE_NOTIFICATION_ID, builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    @Deprecated("The VPN permission is asked with startActivityForResult.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SAVE_REQUEST) {
            saved(resultCode, data?.data)
            return
        }
        if (requestCode == OPEN_REQUEST) {
            opened(resultCode, data?.data)
            return
        }
        if (requestCode != VPN_REQUEST) return
        val result = pendingOn ?: return
        pendingOn = null
        if (resultCode == RESULT_OK) turnOn(result) else result.error("vpn_denied", "the VPN was not allowed", null)
    }

    /** Writes the pending file to the place the user picked. */
    private fun saved(resultCode: Int, uri: Uri?) {
        val (bytes, result) = pendingSave ?: return
        pendingSave = null
        if (resultCode != RESULT_OK || uri == null) {
            result.success(null)
            return
        }
        worker.execute {
            val name = try {
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                    ?: throw IllegalStateException("the file could not be opened")
                displayName(uri)
            } catch (e: Exception) {
                main.post { result.error("write_failed", e.message, null) }
                return@execute
            }
            main.post { result.success(name) }
        }
    }

    /** Reads the picked file to the end, with a size bound independent of provider metadata. */
    private fun opened(resultCode: Int, uri: Uri?) {
        val result = pendingOpen ?: return
        pendingOpen = null
        if (resultCode != RESULT_OK || uri == null) {
            result.success(null)
            return
        }
        worker.execute {
            val bytes = try {
                contentResolver.openInputStream(uri)?.use { input ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > MAX_OPEN_BYTES) throw IllegalStateException("the file is too large")
                    }
                    out.toByteArray()
                } ?: throw IllegalStateException("the file could not be opened")
            } catch (e: Exception) {
                main.post { result.error("read_failed", e.message, null) }
                return@execute
            }
            main.post { result.success(bytes) }
        }
    }

    private fun displayName(uri: Uri): String =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment ?: ""

    override fun onDestroy() {
        // Off, nothing needs the core once the UI is gone.
        if (isFinishing && !CoreService.on) CoreService.stopCore()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "app/android"
        private const val UPDATE_CHANNEL = "clxy_updates"
        private const val UPDATE_NOTIFICATION_ID = 8173
        private const val UPDATE_REQUEST_CODE = 8173
        private const val EXTRA_OPEN_UPDATES = "com.clxy.android.OPEN_UPDATES"
        private const val VPN_REQUEST = 7
        private const val SAVE_REQUEST = 8
        private const val OPEN_REQUEST = 9
        private const val MAX_OPEN_BYTES = 16 * 1024 * 1024
    }

    /** The service's notification needs this on Android 13 and later. */
    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}
