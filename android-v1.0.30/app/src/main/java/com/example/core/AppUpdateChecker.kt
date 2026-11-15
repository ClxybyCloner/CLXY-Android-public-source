package com.example.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val latestTag: String,
    val latestVersion: String,
    val title: String,
    val releaseNotes: String,
    val releasePageUrl: String,
    val apkAssetUrl: String?,
    val apkAssetName: String?,
    val apkAssetSizeBytes: Long,
    val publishedAt: String,
    val isUpdateAvailable: Boolean,
)

object VersionComparator {
    fun normalize(raw: String): List<Int> {
        val cleaned = raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
            .trim()
        if (cleaned.isEmpty()) return emptyList()
        val parts = cleaned.split('.')
            .mapNotNull { token ->
                val digits = token.takeWhile { it.isDigit() }
                digits.toIntOrNull()
            }
        if (parts.isNotEmpty()) return parts
        return Regex("""\d+""").findAll(cleaned).mapNotNull { it.value.toIntOrNull() }.toList()
    }

    fun compare(a: List<Int>, b: List<Int>): Int {
        val maxLen = maxOf(a.size, b.size)
        for (i in 0 until maxLen) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    fun isRemoteNewer(
        remoteTagOrName: String,
        currentVersionName: String,
        currentVersionCode: Int,
    ): Boolean {
        val remoteParts = normalize(remoteTagOrName)
        if (remoteParts.isEmpty()) return false
        val currentParts = normalize(currentVersionName)
        if (currentParts.isNotEmpty()) {
            return compare(remoteParts, currentParts) > 0
        }
        return compare(remoteParts, listOf(currentVersionCode)) > 0
    }
}

class AppUpdateChecker {
    private val directClient = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun checkLatest(
        currentVersionName: String,
        currentVersionCode: Int,
        socksProxyListen: String? = null,
        latestReleaseUrl: String = GITHUB_LATEST_RELEASE_API,
        releasesListUrl: String = GITHUB_RELEASES_API,
    ): AppUpdateInfo = withContext(Dispatchers.IO) {
        val clients = buildClientCandidates(socksProxyListen)
        var lastError: Exception? = null
        for (client in clients) {
            try {
                return@withContext fetchWithClient(
                    client = client,
                    currentVersionName = currentVersionName,
                    currentVersionCode = currentVersionCode,
                    latestReleaseUrl = latestReleaseUrl,
                    releasesListUrl = releasesListUrl,
                )
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Unable to reach GitHub releases")
    }

    private fun buildClientCandidates(socksProxyListen: String?): List<OkHttpClient> {
        val parsed = parseHostPort(socksProxyListen)
        if (parsed == null) return listOf(directClient)
        val proxied = directClient.newBuilder()
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(parsed.first, parsed.second)))
            .build()
        return listOf(proxied, directClient)
    }

    private fun fetchWithClient(
        client: OkHttpClient,
        currentVersionName: String,
        currentVersionCode: Int,
        latestReleaseUrl: String,
        releasesListUrl: String,
    ): AppUpdateInfo {
        val req = Request.Builder()
            .url(latestReleaseUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "CLXY-Android/$currentVersionName")
            .get()
            .build()

        client.newCall(req).execute().use { res ->
            if (res.code == 404) {
                return fetchFromReleasesList(
                    client = client,
                    currentVersionName = currentVersionName,
                    currentVersionCode = currentVersionCode,
                    releasesListUrl = releasesListUrl,
                )
            }
            if (!res.isSuccessful) {
                throw IOException("GitHub returned HTTP ${res.code}")
            }
            val body = res.body?.string().orEmpty()
            val obj = JSONObject(body)
            return parseReleaseJson(obj, currentVersionName, currentVersionCode)
                ?: upToDatePlaceholder(currentVersionName)
        }
    }

    private fun fetchFromReleasesList(
        client: OkHttpClient,
        currentVersionName: String,
        currentVersionCode: Int,
        releasesListUrl: String,
    ): AppUpdateInfo {
        val req = Request.Builder()
            .url(releasesListUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "CLXY-Android/$currentVersionName")
            .get()
            .build()
        client.newCall(req).execute().use { res ->
            if (res.code == 404) {
                return upToDatePlaceholder(currentVersionName)
            }
            if (!res.isSuccessful) {
                throw IOException("GitHub returned HTTP ${res.code}")
            }
            val arr = JSONArray(res.body?.string().orEmpty())
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val parsed = parseReleaseJson(item, currentVersionName, currentVersionCode)
                if (parsed != null) return parsed
            }
            return upToDatePlaceholder(currentVersionName)
        }
    }

    private fun upToDatePlaceholder(currentVersionName: String): AppUpdateInfo = AppUpdateInfo(
        latestTag = "v$currentVersionName",
        latestVersion = currentVersionName,
        title = "No GitHub releases published yet",
        releaseNotes = "",
        releasePageUrl = GITHUB_RELEASES_PAGE_URL,
        apkAssetUrl = null,
        apkAssetName = null,
        apkAssetSizeBytes = 0L,
        publishedAt = "",
        isUpdateAvailable = false,
    )

    companion object {
        const val GITHUB_REPO_URL = "https://github.com/ClxybyCloner/CLXY-Android"
        const val GITHUB_RELEASES_PAGE_URL = "$GITHUB_REPO_URL/releases"
        const val GITHUB_LATEST_RELEASE_API = "https://api.github.com/repos/ClxybyCloner/CLXY-Android/releases/latest"
        const val GITHUB_RELEASES_API = "https://api.github.com/repos/ClxybyCloner/CLXY-Android/releases?per_page=5"

        fun parseReleaseJson(
            obj: JSONObject,
            currentVersionName: String,
            currentVersionCode: Int,
        ): AppUpdateInfo? {
            if (obj.optBoolean("draft", false)) return null
            val tag = obj.optString("tag_name", "").trim()
            val title = obj.optString("name", "").trim().ifEmpty { tag }
            val versionSource = tag.ifEmpty { title }
            if (versionSource.isEmpty()) return null

            val cleanVersion = versionSource.removePrefix("v").removePrefix("V")
            val releasePage = obj.optString("html_url", "").trim().ifEmpty { GITHUB_RELEASES_PAGE_URL }
            val notes = obj.optString("body", "").trim()
            val publishedAt = obj.optString("published_at", "").trim()

            var apkUrl: String? = null
            var apkName: String? = null
            var apkSize = 0L
            val assets = obj.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "").trim()
                    val url = asset.optString("browser_download_url", "").trim()
                    if (name.endsWith(".apk", ignoreCase = true) && url.isNotEmpty()) {
                        apkName = name
                        apkUrl = url
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            return AppUpdateInfo(
                latestTag = tag.ifEmpty { cleanVersion },
                latestVersion = cleanVersion,
                title = title,
                releaseNotes = notes,
                releasePageUrl = releasePage,
                apkAssetUrl = apkUrl,
                apkAssetName = apkName,
                apkAssetSizeBytes = apkSize,
                publishedAt = publishedAt,
                isUpdateAvailable = VersionComparator.isRemoteNewer(
                    remoteTagOrName = versionSource,
                    currentVersionName = currentVersionName,
                    currentVersionCode = currentVersionCode,
                ),
            )
        }

        private fun parseHostPort(listen: String?): Pair<String, Int>? {
            val raw = listen?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val colon = raw.lastIndexOf(':')
            if (colon <= 0) return null
            val host = raw.substring(0, colon).trim('[', ']')
            val port = raw.substring(colon + 1).toIntOrNull() ?: return null
            if (host.isEmpty() || port !in 1..65535) return null
            return host to port
        }
    }
}
