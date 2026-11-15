package com.example

import com.example.core.AccountTrafficStore
import com.example.core.AppSettingsStore
import com.example.core.AppUpdateChecker
import com.example.core.CoreApi
import com.example.core.CoreLocator
import com.example.core.CoreStatus
import com.example.core.CoreUnavailable
import com.example.core.CountryNames
import com.example.core.EnginePhase
import com.example.core.JsonHelpers
import com.example.core.PassInfo
import com.example.core.RelayFallbackDetector
import com.example.core.RelayTestEvent
import com.example.core.SignInMethod
import com.example.core.Traffic
import com.example.core.VersionComparator
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.io.path.createTempDirectory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ClxyCoreUnitTest {

    @Test
    fun `json helpers reject Go zero time and parse valid RFC3339 timestamps`() {
        assertNull(JsonHelpers.parseRfc3339("0001-01-01T00:00:00Z"))
        assertNull(JsonHelpers.parseRfc3339(""))
        val parsed = JsonHelpers.parseRfc3339("2026-05-01T12:30:45.123Z")
        assertTrue(parsed != null && parsed > 0L)
    }

    @Test
    fun `core status parses accounts exit mismatch and confirmation state`() {
        val json = JSONObject(
            """
            {
              "state": {"phase": "running", "since": "2026-05-01T10:00:00Z"},
              "proxy": {
                "listen": "127.0.0.1:10810",
                "country_code": "DE",
                "transport": "h2",
                "started_at": "2026-05-01T10:00:05Z"
              },
              "last_test": {
                "at": "2026-05-01T10:00:10Z",
                "ok": true,
                "response_ms": 88,
                "total_ms": 140
              },
              "accounts": {"total": 2, "ready": 1},
              "selection": "de",
              "selection_name": "Germany"
            }
            """.trimIndent(),
        )
        val status = CoreStatus.fromJson(json)
        assertEquals(EnginePhase.RUNNING, status.state.phase)
        assertEquals(2, status.totalAccounts)
        assertEquals(1, status.readyAccounts)
        assertTrue(status.proxyOn)
        assertTrue(status.confirmedBy(status.lastTest))
    }

    @Test
    fun `core locator reads token and wildcard config listen address`() {
        val dir = createTempDirectory("clxy-loc").toFile()
        try {
            try {
                CoreLocator.locate(dir.absolutePath)
                error("Expected CoreUnavailable")
            } catch (_: CoreUnavailable) {
            }
            File(dir, CoreLocator.TOKEN_FILE).writeText("secret-token-123\n")
            File(dir, CoreLocator.CONFIG_FILE).writeText("""{"dashboard":{"listen":"0.0.0.0:19444"}}""")
            val endpoint = CoreLocator.locate(dir.absolutePath)
            assertEquals("http://127.0.0.1:19444", endpoint.baseUrl)
            assertEquals("secret-token-123", endpoint.token)
            assertEquals("127.0.0.1:10999", CoreLocator.withPort("0.0.0.0:10820", 10999).replace("0.0.0.0", "127.0.0.1"))
            assertEquals(19444, CoreLocator.portOf("0.0.0.0:19444"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `app settings store persists onboarding and initial relay defaults flag`() {
        val dir = createTempDirectory("clxy-settings").toFile()
        try {
            val store1 = AppSettingsStore(dir.absolutePath)
            assertTrue(store1.current.firstRun)
            assertFalse(store1.current.initialRelayDefaultsApplied)
            assertFalse(store1.current.onboardingCompleted)
            assertEquals(SignInMethod.FORM, store1.current.signInMethod)
            store1.setSignInMethod(SignInMethod.FORM)
            store1.markOnboardingCompleted()

            val store2 = AppSettingsStore(dir.absolutePath)
            assertEquals(SignInMethod.FORM, store2.current.signInMethod)
            assertTrue(store2.current.onboardingCompleted)
            assertFalse(store2.current.initialRelayDefaultsApplied)

            store2.markInitialRelayDefaultsApplied()
            store2.markRelayUserConfigured()
            val store3 = AppSettingsStore(dir.absolutePath)
            assertTrue(store3.current.initialRelayDefaultsApplied)
            assertTrue(store3.current.relayUserConfigured)
            assertTrue(store3.current.onboardingCompleted)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `app routing config toggles and selects regional suggested apps`() {
        val initial = AppRoutingConfig(
            onlyChosen = false,
            bypass = setOf("com.bank.app"),
            only = emptySet(),
            suggested = setOf("ir.app.one", "ir.app.two"),
            declined = emptySet(),
        )
        val afterDecline = initial.toggle("ir.app.one", on = false)
        assertTrue(afterDecline.declined.contains("ir.app.one"))

        val (checked, added) = afterDecline.checkAllSuggested(setOf("ir.app.one", "ir.app.two", "com.other"))
        assertEquals(2, added)
        assertTrue(checked.bypass.containsAll(listOf("com.bank.app", "ir.app.one", "ir.app.two")))
        assertTrue(checked.declined.isEmpty())
    }

    @Test
    fun `country names and formatting helpers match expected formats`() {
        assertEquals("Germany", CountryNames.locationName("de"))
        assertEquals("Recommended", CountryNames.locationName("REC"))
        assertEquals("00" to ":40:38", CountryNames.formatTimerParts(40 * 60 + 38))
        assertEquals("20,3", CountryNames.formatMbpsComma((20.3 * 1_000_000 / 8).toLong()))
        assertEquals("2.00 GB", CountryNames.formatBytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun `censorLogText removes all mozilla firefox fxa guardian and fastly identifiers from logs`() {
        val raw = "control plane: trying Mozilla directly via https://vpn.mozilla.org and api.accounts.firefox.com:443 " +
            "proxy=us.m1.fastly-masque.net:2499; Guardian rejected FxA OAuth for Firefox Account"
        val censored = CountryNames.censorLogText(raw)
        val lower = censored.lowercase()
        assertFalse(lower.contains("mozilla"))
        assertFalse(lower.contains("firefox"))
        assertFalse(lower.contains("fxa"))
        assertFalse(lower.contains("guardian"))
        assertFalse(lower.contains("fastly"))
        assertTrue(censored.contains("service-endpoint.invalid:443"))
        assertTrue(censored.contains("service-endpoint.invalid:2499"))
    }

    @Test
    fun `brandAsClxy replaces upstream mozilla and firefox DNS or UI strings with CLXY`() {
        assertEquals("api.clxy.network", CountryNames.brandAsClxy("vpn.mozilla.org"))
        assertEquals("auth.clxy.network", CountryNames.brandAsClxy("api.accounts.firefox.com"))
        assertEquals("edge.clxy.network:2499", CountryNames.brandAsClxy("us.m1.fastly-masque.net:2499"))
        assertEquals("CLXY API", CountryNames.brandAsClxy("Mozilla VPN API"))
        assertEquals("CLXY Account sign-in required", CountryNames.brandAsClxy("Firefox Account sign-in required"))
        assertEquals("CLXY refused token", CountryNames.brandAsClxy("Guardian refused token"))
    }

    @Test
    fun `core api decodes status locations and streaming relay test over loopback socket`() = runBlocking {
        val server = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
        val t = thread(isDaemon = true) {
            while (!server.isClosed) {
                val sock = runCatching { server.accept() }.getOrNull() ?: break
                sock.use { s ->
                    val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                    val requestLine = reader.readLine().orEmpty()
                    var contentLen = 0
                    while (true) {
                        val header = reader.readLine() ?: break
                        if (header.isEmpty()) break
                        if (header.lowercase().startsWith("content-length:")) {
                            contentLen = header.substringAfter(':').trim().toIntOrNull() ?: 0
                        }
                    }
                    if (contentLen > 0) {
                        val buf = CharArray(contentLen)
                        reader.read(buf, 0, contentLen)
                    }
                    val path = requestLine.split(' ').getOrNull(1).orEmpty()
                    val (ctype, body) = when (path) {
                        "/api/status" -> "application/json" to """{"state":{"phase":"running"},"accounts":{"total":1,"ready":1},"selection":"nl","selection_name":"Netherlands"}"""
                        "/api/locations" -> "application/json" to """{"locations":[{"code":"NL","name":"Netherlands","city":"Amsterdam","server":"nl1.mullvad.net","ping_ms":55}]}"""
                        "/api/relay/test" -> "application/x-ndjson" to (
                            """{"type":"step","step":{"stage":"dns","status":"ok","code":"","detail":"resolved","elapsed_ms":12}}""" + "\n" +
                                """{"type":"result","result":{"ok":true,"host":"relay.example","total_ms":48,"steps":[]}}""" + "\n"
                            )
                        else -> "application/json" to "{}"
                    }
                    val bytes = body.toByteArray()
                    val resp = "HTTP/1.1 200 OK\r\nContent-Type: $ctype\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                    s.getOutputStream().write(resp.toByteArray())
                    s.getOutputStream().write(bytes)
                    s.getOutputStream().flush()
                }
            }
        }
        try {
            val api = CoreApi(com.example.core.CoreEndpoint("http://127.0.0.1:${server.localPort}", "tok"))
            val status = api.status()
            assertEquals(EnginePhase.RUNNING, status.state.phase)
            assertEquals("nl", status.selection)

            val locs = api.locations()
            assertEquals(1, locs.locations.size)
            assertEquals("Amsterdam", locs.locations.first().city)

            val relayEvents = api.testRelay("https://relay.example/?secret=abc").toList()
            assertEquals(2, relayEvents.size)
            assertTrue(relayEvents[0] is RelayTestEvent.Step)
            assertTrue((relayEvents[1] as RelayTestEvent.Result).result.ok)
            api.close()
        } finally {
            server.close()
            t.join(1000)
        }
    }

    @Test
    fun `account traffic store persists cumulative account bytes without double counting across restarts`() {
        val dir = createTempDirectory("clxy-traffic").toFile()
        try {
            val s1 = AccountTrafficStore(dir.absolutePath)
            assertEquals(150_000L, s1.recordTraffic("acc1", Traffic(up = 50_000L, down = 100_000L)))
            assertEquals(300_000L, s1.recordTraffic("acc1", Traffic(up = 100_000L, down = 200_000L)))
            s1.flush()

            // Reopening mid-session with higher total adds only the new delta
            val s2 = AccountTrafficStore(dir.absolutePath)
            assertEquals(300_000L, s2.bytesFor("acc1"))
            assertEquals(450_000L, s2.recordTraffic("acc1", Traffic(up = 150_000L, down = 300_000L)))

            // Core restart resets session counter to smaller number; new traffic still accumulates
            assertEquals(550_000L, s2.recordTraffic("acc1", Traffic(up = 20_000L, down = 80_000L)))
            s2.flush()

            val s3 = AccountTrafficStore(dir.absolutePath)
            assertEquals(550_000L, s3.bytesFor("acc1"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `pass info parses guardian quota fields as fallback usage`() {
        val json = JSONObject(
            """
            {
              "proxy_pass_expires_at": "2026-05-01T12:00:00Z",
              "seconds_until_expiry": 300,
              "quota_unlimited": true,
              "quota_limit_bytes": 0,
              "quota_remaining_bytes": 0
            }
            """.trimIndent(),
        )
        val pass = PassInfo.fromJson(json)
        val fallback = pass.toFallbackUsage()
        assertTrue(fallback != null && fallback.unlimited)
    }

    @Test
    fun `relay fallback detector identifies direct control plane timeouts without false positives`() {
        assertTrue(
            RelayFallbackDetector.isDirectControlPlaneFailure(
                "fetching server list failed: fetching server list: Get \"https://firefox.settings.services.mozilla.com/v1/buckets/main/collections/vpn-serverlist/records\": context deadline exceeded",
            ),
        )
        assertTrue(
            RelayFallbackDetector.isDirectControlPlaneFailure(
                "reading the latest firefox version failed; using 160.0a1 err=fetching firefox versions: Get \"https://product-details.mozilla.org/1.0/firefox_versions.json\": context deadline exceeded",
            ),
        )
        assertTrue(
            RelayFallbackDetector.isDirectControlPlaneFailure(
                "Acquire pass timed out against https://vpn.mozilla.org",
            ),
        )
        assertFalse(
            RelayFallbackDetector.isDirectControlPlaneFailure(
                "SOCKS5 proxy started listen=127.0.0.1:10810 transport=h2",
            ),
        )
        assertFalse(
            RelayFallbackDetector.isDirectControlPlaneFailure(
                "Connection test to example.com timed out",
            ),
        )
    }

    @Test
    fun `version comparator compares numeric and semver release tags accurately`() {
        assertTrue(VersionComparator.isRemoteNewer("v142.0", "141.0", 141))
        assertTrue(VersionComparator.isRemoteNewer("141.1", "141.0", 141))
        assertTrue(VersionComparator.isRemoteNewer("v142", "141.0", 141))
        assertFalse(VersionComparator.isRemoteNewer("v141.0", "141.0", 141))
        assertFalse(VersionComparator.isRemoteNewer("141", "141.0", 141))
        assertFalse(VersionComparator.isRemoteNewer("v1.0.19", "141.0", 141))
    }

    @Test
    fun `app update checker parses github release json and detects newer apk release`() = runBlocking {
        val server = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
        val t = thread(isDaemon = true) {
            val sock = runCatching { server.accept() }.getOrNull() ?: return@thread
            sock.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }
                val body = """
                    {
                      "tag_name": "v142.0",
                      "name": "CLXY v142.0",
                      "body": "Performance & stability fixes",
                      "html_url": "https://github.com/ClxybyCloner/CLXY-Android/releases/tag/v142.0",
                      "published_at": "2026-11-14T18:00:00Z",
                      "draft": false,
                      "assets": [
                        {
                          "name": "CLXY-v142.0.apk",
                          "size": 24117248,
                          "browser_download_url": "https://github.com/ClxybyCloner/CLXY-Android/releases/download/v142.0/CLXY-v142.0.apk"
                        }
                      ]
                    }
                """.trimIndent()
                val bytes = body.toByteArray()
                val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                s.getOutputStream().write(resp.toByteArray())
                s.getOutputStream().write(bytes)
                s.getOutputStream().flush()
            }
        }
        try {
            val url = "http://127.0.0.1:${server.localPort}/releases/latest"
            val info = AppUpdateChecker().checkLatest(
                currentVersionName = "141.0",
                currentVersionCode = 141,
                latestReleaseUrl = url,
                releasesListUrl = url,
            )
            assertTrue(info.isUpdateAvailable)
            assertEquals("v142.0", info.latestTag)
            assertEquals("CLXY-v142.0.apk", info.apkAssetName)
            assertEquals(
                "https://github.com/ClxybyCloner/CLXY-Android/releases/download/v142.0/CLXY-v142.0.apk",
                info.apkAssetUrl,
            )
        } finally {
            server.close()
            t.join(1000)
        }
    }
}
