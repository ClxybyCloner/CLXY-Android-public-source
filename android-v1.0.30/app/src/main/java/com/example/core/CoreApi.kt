package com.example.core

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class CoreEndpoint(
    val baseUrl: String,
    val token: String,
)

enum class CoreProblem {
    STARTING,
    NOT_ANSWERING,
    REFUSED,
    PORT_IN_USE,
    NO_CORE,
    EXITED,
}

class CoreUnavailable(
    val problem: CoreProblem,
    val detail: String = "",
) : Exception(if (detail.isEmpty()) problem.name else "${problem.name}: $detail")

class CoreApiException(
    val statusCode: Int,
    override val message: String,
) : Exception(message)

object CoreLocator {
    const val TOKEN_FILE = "local-api.token"
    const val CONFIG_FILE = "config.json"
    const val DEFAULT_LISTEN = "127.0.0.1:10820"
    private const val DEFAULT_PORT = 10820

    fun locate(dataDir: String): CoreEndpoint {
        val tokenFile = File(dataDir, TOKEN_FILE)
        val token = try {
            tokenFile.readText().trim()
        } catch (_: Exception) {
            throw CoreUnavailable(CoreProblem.NOT_ANSWERING, tokenFile.absolutePath)
        }
        if (token.isEmpty()) {
            throw CoreUnavailable(CoreProblem.NOT_ANSWERING, tokenFile.absolutePath)
        }
        val listen = dashboardListen(dataDir)
        return CoreEndpoint(listenBaseUrl(listen), token)
    }

    fun dashboardListen(dataDir: String): String {
        val cfgFile = File(dataDir, CONFIG_FILE)
        return try {
            val text = cfgFile.readText()
            val obj = JSONObject(text)
            val dash = obj.optJSONObject("dashboard")
            val listen = dash?.optString("listen", "")?.trim().orEmpty()
            listen.ifEmpty { DEFAULT_LISTEN }
        } catch (_: Exception) {
            DEFAULT_LISTEN
        }
    }

    fun listenBaseUrl(listen: String): String {
        val trimmed = listen.trim().ifEmpty { DEFAULT_LISTEN }
        val colon = trimmed.lastIndexOf(':')
        var host = if (colon < 0) trimmed else trimmed.substring(0, colon)
        val port = if (colon < 0) DEFAULT_PORT else trimmed.substring(colon + 1).toIntOrNull() ?: DEFAULT_PORT
        if (host.startsWith("[") && host.endsWith("]") && host.length >= 2) {
            host = host.substring(1, host.length - 1)
        }
        if (host.isEmpty() || host == "0.0.0.0" || host == "::") {
            host = "127.0.0.1"
        }
        val formattedHost = if (host.contains(":")) "[$host]" else host
        return "http://$formattedHost:$port"
    }

    fun withPort(listen: String, port: Int): String {
        val trimmed = listen.trim()
        val colon = trimmed.lastIndexOf(':')
        val host = if (colon < 0) {
            trimmed.ifEmpty { "127.0.0.1" }
        } else {
            trimmed.substring(0, colon).ifEmpty { "127.0.0.1" }
        }
        return "$host:$port"
    }

    fun portOf(listen: String): Int? {
        val colon = listen.lastIndexOf(':')
        if (colon < 0) return null
        return listen.substring(colon + 1).trim().toIntOrNull()
    }
}

class CoreApi(val endpoint: CoreEndpoint) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val slowClient = client.newBuilder()
        .readTimeout(65, TimeUnit.SECONDS)
        .writeTimeout(65, TimeUnit.SECONDS)
        .build()

    private val slowestClient = client.newBuilder()
        .readTimeout(160, TimeUnit.SECONDS)
        .writeTimeout(160, TimeUnit.SECONDS)
        .build()

    suspend fun status(): CoreStatus = CoreStatus.fromJson(requestJson("GET", "/api/status"))

    suspend fun accounts(): AccountList = AccountList.fromJson(requestJson("GET", "/api/accounts"))

    suspend fun setAccountOrder(ids: List<String>) {
        val obj = JSONObject().put("ids", JSONArray(ids))
        requestJson("POST", "/api/accounts/order", obj)
    }

    suspend fun activateAccount(id: String) {
        requestJson("POST", "/api/accounts/${seg(id)}/activate")
    }

    suspend fun setAccountEnabled(id: String, enabled: Boolean) {
        val obj = JSONObject().put("enabled", enabled)
        requestJson("POST", "/api/accounts/${seg(id)}/enabled", obj)
    }

    suspend fun refreshAccount(id: String) {
        requestJson("POST", "/api/accounts/${seg(id)}/refresh", null, slowClient)
    }

    suspend fun removeAccount(id: String) {
        requestJson("DELETE", "/api/accounts/${seg(id)}")
    }

    suspend fun test(): ConnectionTest =
        ConnectionTest.fromJson(requestJson("POST", "/api/test", null, slowClient))

    suspend fun delay(): ConnectionTest =
        ConnectionTest.fromJson(requestJson("POST", "/api/delay", null, slowClient))

    suspend fun restartProxy() {
        requestJson("POST", "/api/proxy/restart", null, slowClient)
    }

    suspend fun startSignIn(email: String, password: String): SignInState {
        val obj = JSONObject()
            .put("email", email)
            .put("password", password)
        return SignInState.fromJson(requestJson("POST", "/api/signin", obj))
    }

    suspend fun startBrowserSignIn(email: String, sessionToken: String): SignInState {
        val obj = JSONObject()
            .put("email", email)
            .put("session_token", sessionToken)
        return SignInState.fromJson(requestJson("POST", "/api/signin/browser", obj))
    }

    suspend fun openSignInProxy(port: Int = 0): String {
        val obj = JSONObject().put("port", port)
        val reply = requestJson("POST", "/api/signin/browser/proxy", obj)
        val address = reply.optString("address", "")
        if (address.isEmpty()) throw CoreApiException(0, "No sign-in proxy address returned")
        return address
    }

    suspend fun closeSignInProxy() {
        requestJson("DELETE", "/api/signin/browser/proxy")
    }

    suspend fun startSignInAgain(accountId: String): SignInState {
        val obj = JSONObject().put("account_id", accountId)
        return SignInState.fromJson(requestJson("POST", "/api/signin", obj))
    }

    suspend fun signInState(id: String): SignInState =
        SignInState.fromJson(requestJson("GET", "/api/signin/${seg(id)}"))

    suspend fun answerSignIn(id: String, value: String): SignInState {
        val obj = JSONObject().put("value", value)
        return SignInState.fromJson(requestJson("POST", "/api/signin/${seg(id)}", obj))
    }

    suspend fun cancelSignIn(id: String) {
        requestJson("DELETE", "/api/signin/${seg(id)}")
    }

    suspend fun settings(): JSONObject {
        val view = requestJson("GET", "/api/settings")
        return view.optJSONObject("config")
            ?: throw CoreApiException(500, "Settings response missing config")
    }

    suspend fun saveSettings(config: JSONObject) {
        val body = JSONObject().put("config", config)
        requestJson("PUT", "/api/settings", body)
    }

    suspend fun setPower(on: Boolean) {
        val obj = JSONObject().put("on", on)
        requestJson("POST", "/api/power", obj)
    }

    suspend fun retry() {
        requestJson("POST", "/api/retry", JSONObject())
    }

    suspend fun locations(): LocationList =
        LocationList.fromJson(requestJson("GET", "/api/locations", httpClient = slowClient))

    suspend fun pingLocations(codes: List<String> = emptyList()): LocationList {
        val obj = JSONObject().put("codes", JSONArray(codes))
        return LocationList.fromJson(requestJson("POST", "/api/locations/ping", obj, slowestClient))
    }

    suspend fun setLocation(country: String) {
        val obj = JSONObject().put("country", country)
        requestJson("POST", "/api/location", obj)
    }

    suspend fun logs(after: Long): List<LogEntry> {
        val j = requestJson("GET", "/api/logs?after=$after")
        val arr = j.optJSONArray("entries") ?: return emptyList()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { add(LogEntry.fromJson(it)) }
            }
        }
    }

    suspend fun routing(): RoutingInfo =
        RoutingInfo.fromJson(requestJson("GET", "/api/routing"))

    suspend fun updateRoutingLists(): RoutingInfo =
        RoutingInfo.fromJson(requestJson("POST", "/api/routing/update", JSONObject(), slowestClient))

    suspend fun testDestination(target: String): DestinationTest {
        val obj = JSONObject().put("target", target)
        return DestinationTest.fromJson(requestJson("POST", "/api/routing/test", obj, slowClient))
    }

    suspend fun connections(): List<ProxyConnection> =
        parseConnections(requestJson("GET", "/api/connections"))

    suspend fun clearConnections(): List<ProxyConnection> =
        parseConnections(requestJson("DELETE", "/api/connections"))

    suspend fun testDns(servers: List<String>): List<DnsTestHost> {
        val obj = JSONObject().put("servers", JSONArray(servers))
        val j = requestJson("POST", "/api/dns/test", obj, slowClient)
        val arr = j.optJSONArray("results") ?: return emptyList()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { add(DnsTestHost.fromJson(it)) }
            }
        }
    }

    suspend fun exportBackup(): Pair<String, ByteArray> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${endpoint.baseUrl}/api/backup")
            .header("X-Local-Token", endpoint.token)
            .get()
            .build()
        try {
            slowClient.newCall(req).execute().use { res ->
                val bytes = res.body?.bytes() ?: ByteArray(0)
                if (!res.isSuccessful) {
                    val msg = runCatching {
                        JSONObject(bytes.decodeToString()).optString("error", "")
                    }.getOrDefault("").ifEmpty { "HTTP ${res.code}" }
                    throw CoreApiException(res.code, msg)
                }
                val disp = res.header("Content-Disposition").orEmpty()
                val match = Regex("""filename="([^"]+)"""").find(disp)
                val name = match?.groupValues?.getOrNull(1) ?: "CLXY-backup.zip"
                name to bytes
            }
        } catch (e: CoreApiException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw CoreUnavailable(CoreProblem.NOT_ANSWERING, "${endpoint.baseUrl}: timeout")
        } catch (e: IOException) {
            throw CoreUnavailable(CoreProblem.NOT_ANSWERING, e.message ?: "I/O error")
        }
    }

    suspend fun inspectBackup(zipBytes: ByteArray): BackupContents {
        val encoded = Base64.encodeToString(zipBytes, Base64.NO_WRAP)
        val obj = JSONObject().put("data", encoded)
        return BackupContents.fromJson(requestJson("POST", "/api/backup/inspect", obj, slowClient))
    }

    suspend fun importBackup(zipBytes: ByteArray): BackupImported {
        val encoded = Base64.encodeToString(zipBytes, Base64.NO_WRAP)
        val obj = JSONObject().put("data", encoded)
        return BackupImported.fromJson(requestJson("POST", "/api/backup/import", obj, slowClient))
    }

    fun testRelay(url: String): Flow<RelayTestEvent> = callbackFlow {
        val streamClient = client.newBuilder()
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
        val payload = JSONObject()
            .put("url", url)
            .put("stream", true)
            .toString()
            .toRequestBody(jsonMediaType)
        val req = Request.Builder()
            .url("${endpoint.baseUrl}/api/relay/test")
            .header("X-Local-Token", endpoint.token)
            .post(payload)
            .build()
        val call = streamClient.newCall(req)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) {
                    close(CoreApiException(0, "Relay diagnostic connection ended: ${e.message ?: "I/O error"}"))
                } else {
                    close()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    if (!res.isSuccessful) {
                        close(CoreApiException(res.code, "Relay diagnostics API returned HTTP ${res.code}"))
                        return
                    }
                    val stream = res.body?.byteStream()
                    if (stream == null) {
                        close()
                        return
                    }
                    try {
                        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isBlank()) continue
                                val obj = runCatching { JSONObject(line) }.getOrNull() ?: continue
                                val event = RelayTestEvent.fromJson(obj)
                                if (event != null) trySend(event)
                            }
                        }
                        close()
                    } catch (e: Exception) {
                        if (!call.isCanceled()) {
                            close(CoreApiException(0, e.message ?: "Relay diagnostic stream failed"))
                        } else {
                            close()
                        }
                    }
                }
            }
        })
        awaitClose { call.cancel() }
    }

    fun close() {
        client.dispatcher.executorService.shutdownNow()
        slowClient.dispatcher.executorService.shutdownNow()
        slowestClient.dispatcher.executorService.shutdownNow()
    }

    private fun parseConnections(j: JSONObject): List<ProxyConnection> {
        val arr = j.optJSONArray("connections") ?: return emptyList()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { add(ProxyConnection.fromJson(it)) }
            }
        }
    }

    private suspend fun requestJson(
        method: String,
        path: String,
        body: JSONObject? = null,
        httpClient: OkHttpClient = client,
    ): JSONObject = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url("${endpoint.baseUrl}$path")
            .header("X-Local-Token", endpoint.token)
            .header("Accept", "application/json")
        val payload = body ?: if (method == "GET") null else JSONObject()
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(payload.toString().toRequestBody(jsonMediaType))
            "PUT" -> builder.put(payload.toString().toRequestBody(jsonMediaType))
            "DELETE" -> if (payload != null) {
                builder.delete(payload.toString().toRequestBody(jsonMediaType))
            } else {
                builder.delete()
            }
            else -> builder.method(method, payload?.toString()?.toRequestBody(jsonMediaType))
        }
        try {
            httpClient.newCall(builder.build()).execute().use { res ->
                val text = res.body?.string().orEmpty()
                val decoded = if (text.isBlank()) JSONObject() else runCatching { JSONObject(text) }.getOrNull()
                    ?: throw CoreUnavailable(CoreProblem.NOT_ANSWERING, "Unreadable response from core")
                if (res.code >= 400) {
                    val err = decoded.optString("error", "").ifEmpty { "HTTP ${res.code}" }
                    throw CoreApiException(res.code, err)
                }
                decoded
            }
        } catch (e: CoreApiException) {
            throw e
        } catch (e: CoreUnavailable) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw CoreUnavailable(CoreProblem.NOT_ANSWERING, "${endpoint.baseUrl}: timeout")
        } catch (e: IOException) {
            throw CoreUnavailable(CoreProblem.NOT_ANSWERING, "${endpoint.baseUrl}: ${e.message ?: "connection failed"}")
        }
    }

    private fun seg(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
