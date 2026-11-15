package com.example

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.example.core.Account
import com.example.core.AccountList
import com.example.core.AppUpdateInfo
import com.example.core.ClxyUiState
import com.example.core.ConnectionModeUi
import com.example.core.CoreStatus
import com.example.core.EnginePhase
import com.example.core.EngineState
import com.example.core.ExitInfo
import com.example.core.LocationList
import com.example.core.SignInError
import com.example.core.SignInState
import com.example.core.SignInStep
import com.example.core.Traffic
import com.example.core.Usage
import com.example.core.VpnLocation
import com.example.ui.AppUpdateDialog
import com.example.ui.HomeScreen
import com.example.ui.InAppUpdateBanner
import com.example.ui.LoginScreen
import com.example.ui.ServersScreen
import com.example.ui.theme.ClxyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `clxy branding and resources match specification`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("CLXY", context.getString(R.string.app_name))
        assertEquals("com.clxy.android", context.packageName)
        assertTrue(context.assets.list("licenses")?.isNotEmpty() == true)
    }

    @Test
    fun `core service formats speeds flags and app mode preferences`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("↓ 0 B/s  ↑ 0 B/s", CoreService.speedText("0,0"))
        assertEquals("↓ 2.0 MB/s  ↑ 512 KB/s", CoreService.speedText("2097152,524288"))
        assertEquals("🇺🇸", CoreService.flag("us"))
        assertEquals("🇩🇪", CoreService.flag("DE"))

        CoreService.setConnectionMode(context, CoreService.MODE_PROXY)
        assertFalse(CoreService.usesVpn(context))
        assertEquals(CoreService.MODE_PROXY, CoreService.connectionMode(context))

        CoreService.setConnectionMode(context, CoreService.MODE_VPN)
        assertTrue(CoreService.usesVpn(context))

        assertTrue(CoreService.showSpeed(context))
        CoreService.setShowSpeed(context, false)
        assertFalse(CoreService.showSpeed(context))
        CoreService.setShowSpeed(context, true)
        assertTrue(CoreService.showSpeed(context))
    }

    @Test
    fun `login screen authenticates username and password directly without skip or social ui`() {
        var submittedUser: String? = null
        var submittedPass: String? = null
        var signedInCalled = false

        composeRule.setContent {
            ClxyTheme {
                LoginScreen(
                    onStartSignIn = { user, pass ->
                        submittedUser = user
                        submittedPass = pass
                        SignInState(id = "s1", email = user, step = SignInStep.DONE)
                    },
                    onPollSignIn = { SignInState(id = "s1", email = "user@example.com", step = SignInStep.DONE) },
                    onAnswerSignIn = { _, _ -> SignInState() },
                    onCancelSignIn = {},
                    onSignedIn = { signedInCalled = true },
                )
            }
        }

        // Old onboarding/social buttons, skip button, and login backup restore must be completely gone
        composeRule.onNodeWithText("Continue with Facebook").assertDoesNotExist()
        composeRule.onNodeWithText("Continue with Google").assertDoesNotExist()
        composeRule.onNodeWithText("Registration").assertDoesNotExist()
        composeRule.onNodeWithText("Stay Anonymous and Safe").assertDoesNotExist()
        composeRule.onNodeWithTag("login_restore_backup_button").assertDoesNotExist()
        composeRule.onNodeWithTag("login_skip_button").assertDoesNotExist()
        composeRule.onNodeWithText("Skip").assertDoesNotExist()

        // Empty submit shows inline validation error
        composeRule.onNodeWithTag("login_submit_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("login_error_banner").assertIsDisplayed()
        assertFalse(signedInCalled)

        // Filling username & password submits natively and signs in automatically
        composeRule.onNodeWithTag("login_email_input").performScrollTo().performTextInput("user@example.com")
        composeRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("secret-pass")
        composeRule.onNodeWithTag("login_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("user@example.com", submittedUser)
        assertEquals("secret-pass", submittedPass)
        assertTrue(signedInCalled)
    }

    @Test
    fun `login screen shows inline errors for wrong password and wrong email verification code`() {
        var signedInCalled = false
        var latestSession = SignInState()

        composeRule.setContent {
            ClxyTheme {
                LoginScreen(
                    onStartSignIn = { user, pass ->
                        latestSession = if (pass != "correct-pass") {
                            SignInState(
                                id = "s-bad-pass",
                                email = user,
                                step = SignInStep.FAILED,
                                errorCode = SignInError.WRONG_PASSWORD,
                                error = "wrong password",
                            )
                        } else {
                            SignInState(id = "s2", email = user, step = SignInStep.NEED_EMAIL_CODE)
                        }
                        latestSession
                    },
                    onPollSignIn = { latestSession },
                    onAnswerSignIn = { _, code ->
                        latestSession = if (code != "654321") {
                            SignInState(
                                id = "s2",
                                email = "user@example.com",
                                step = SignInStep.NEED_EMAIL_CODE,
                                errorCode = SignInError.WRONG_CODE,
                                error = "wrong verification code",
                            )
                        } else {
                            SignInState(id = "s2", email = "user@example.com", step = SignInStep.DONE)
                        }
                        latestSession
                    },
                    onCancelSignIn = {},
                    onSignedIn = { signedInCalled = true },
                )
            }
        }

        // Wrong password shows warning immediately
        composeRule.onNodeWithTag("login_email_input").performScrollTo().performTextInput("user@example.com")
        composeRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("wrong-pass")
        composeRule.onNodeWithTag("login_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("login_error_banner").assertIsDisplayed()
        composeRule.onNodeWithText("Incorrect email or password", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("login_open_relay_settings_button").assertDoesNotExist()
        assertFalse(signedInCalled)
    }

    @Test
    fun `blocked sign in shows relay settings action and invokes it`() {
        var openedRelaySettings = false
        var latestSession = SignInState()

        composeRule.setContent {
            ClxyTheme {
                LoginScreen(
                    onStartSignIn = { user, _ ->
                        latestSession = SignInState(
                            id = "s-blocked",
                            email = user,
                            step = SignInStep.FAILED,
                            errorCode = SignInError.BLOCKED,
                            error = "blocked",
                        )
                        latestSession
                    },
                    onPollSignIn = { latestSession },
                    onAnswerSignIn = { _, _ -> SignInState() },
                    onCancelSignIn = {},
                    onSignedIn = {},
                    onOpenRelaySettings = { openedRelaySettings = true },
                )
            }
        }

        composeRule.onNodeWithTag("login_email_input").performScrollTo().performTextInput("user@example.com")
        composeRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("not-a-real-password")
        composeRule.onNodeWithTag("login_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("login_error_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("login_open_relay_settings_button").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(openedRelaySettings)
    }

    @Test
    fun `login screen shows error banner when email verification code is rejected`() {
        var signedInCalled = false
        var latestSession = SignInState()

        composeRule.setContent {
            ClxyTheme {
                LoginScreen(
                    onStartSignIn = { user, _ ->
                        latestSession = SignInState(id = "s3", email = user, step = SignInStep.NEED_EMAIL_CODE)
                        latestSession
                    },
                    onPollSignIn = { latestSession },
                    onAnswerSignIn = { _, code ->
                        latestSession = if (code == "654321") {
                            SignInState(id = "s3", email = "user@example.com", step = SignInStep.DONE)
                        } else {
                            // Core keeps step at NEED_EMAIL_CODE and sets errorCode = WRONG_CODE
                            SignInState(
                                id = "s3",
                                email = "user@example.com",
                                step = SignInStep.NEED_EMAIL_CODE,
                                errorCode = SignInError.WRONG_CODE,
                                error = "wrong code",
                            )
                        }
                        latestSession
                    },
                    onCancelSignIn = {},
                    onSignedIn = { signedInCalled = true },
                )
            }
        }

        composeRule.onNodeWithTag("login_email_input").performScrollTo().performTextInput("user@example.com")
        composeRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("secret-pass")
        composeRule.onNodeWithTag("login_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Blank verify tap shows validation error
        composeRule.onNodeWithTag("login_verify_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("login_error_banner").assertIsDisplayed()

        // Wrong code shows incorrect verification code alert even though step stayed NEED_EMAIL_CODE
        composeRule.onNodeWithTag("login_verification_input").performScrollTo().performTextInput("111111")
        composeRule.onNodeWithTag("login_verify_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("login_error_banner").assertIsDisplayed()
        composeRule.onNodeWithText("Incorrect verification code", substring = true).assertIsDisplayed()
        assertFalse(signedInCalled)
    }

    @Test
    fun `home screen renders connected status, world map, account usage, and proxy or vpn selector`() {
        var selectedMode: ConnectionModeUi? = null
        var serversOpened = false
        val fakeState = ClxyUiState(
            showOnboarding = false,
            connectionMode = ConnectionModeUi.VPN,
            elapsedSeconds = 2438L,
            deviceOriginCountryCode = "US",
            deviceOriginLabel = "United States",
            accounts = AccountList(
                accounts = listOf(
                    Account(
                        id = "acc1",
                        email = "user@example.com",
                        active = true,
                        enabled = true,
                        usage = Usage(
                            unlimited = false,
                            limitBytes = 5_368_709_120L,
                            remainingBytes = 3_221_225_472L,
                        ),
                    ),
                ),
            ),
            status = CoreStatus(
                state = EngineState(phase = EnginePhase.RUNNING),
                traffic = Traffic(up = 2_097_152L, down = 12_582_912L, downRate = 2_500_000L, upRate = 1_250_000L),
                exit = ExitInfo(ipv4 = "203.0.113.10", countryCode = "DE", city = "Frankfurt"),
                selection = "de",
                selectionName = "Germany",
            ),
        )
        composeRule.setContent {
            ClxyTheme {
                HomeScreen(
                    uiState = fakeState,
                    onToggleConnection = {},
                    onSelectMode = { selectedMode = it },
                    onOpenServers = { serversOpened = true },
                    onOpenSignIn = {},
                    onOpenDiagnostics = {},
                )
            }
        }
        composeRule.onNodeWithTag("home_world_map").assertIsDisplayed()
        composeRule.onNodeWithTag("home_power_button").assertIsDisplayed()
        composeRule.onNodeWithTag("home_route_tunnel_box").assertIsDisplayed()
        composeRule.onNodeWithTag("home_data_usage_card").assertIsDisplayed()
        composeRule.onNodeWithTag("home_data_used_value").assertIsDisplayed()
        composeRule.onNodeWithText("2.00 GB").assertIsDisplayed()
        composeRule.onNodeWithTag("home_data_remaining_value").assertIsDisplayed()
        composeRule.onNodeWithTag("mode_pill_system").assertDoesNotExist()
        composeRule.onNodeWithTag("mode_pill_vpn").assertIsDisplayed()
        composeRule.onNodeWithTag("mode_pill_proxy").performClick()
        composeRule.onNodeWithTag("home_server_card").performClick()
        assertEquals(ConnectionModeUi.PROXY, selectedMode)
        assertTrue(serversOpened)
    }

    @Test
    fun `home screen renders tracked used bytes and unlimited quota when account has unlimited plan`() {
        val fakeState = ClxyUiState(
            showOnboarding = false,
            connectionMode = ConnectionModeUi.VPN,
            activeAccountTrackedBytes = 1_073_741_824L,
            accounts = AccountList(
                accounts = listOf(
                    Account(
                        id = "acc1",
                        email = "user@example.com",
                        active = true,
                        enabled = true,
                        usage = Usage(unlimited = true),
                    ),
                ),
            ),
            status = CoreStatus(
                state = EngineState(phase = EnginePhase.OFF),
                traffic = Traffic(up = 100L, down = 200L),
            ),
        )
        composeRule.setContent {
            ClxyTheme {
                HomeScreen(
                    uiState = fakeState,
                    onToggleConnection = {},
                    onSelectMode = {},
                    onOpenServers = {},
                    onOpenSignIn = {},
                    onOpenDiagnostics = {},
                )
            }
        }
        composeRule.onNodeWithTag("home_data_usage_card").assertIsDisplayed()
        composeRule.onNodeWithText("1.00 GB").assertIsDisplayed()
        composeRule.onNodeWithText("∞ Unlimited").assertIsDisplayed()
    }

    @Test
    fun `home screen uses active account usage instead of summing inactive accounts and avoids false unlimited`() {
        val fakeState = ClxyUiState(
            showOnboarding = false,
            connectionMode = ConnectionModeUi.VPN,
            accounts = AccountList(
                accounts = listOf(
                    Account(
                        id = "inactive_acc",
                        email = "old@example.com",
                        active = false,
                        enabled = false,
                        usage = Usage(
                            unlimited = false,
                            limitBytes = 10_737_418_240L,
                            remainingBytes = 5_368_709_120L,
                        ),
                    ),
                    Account(
                        id = "active_acc",
                        email = "active@example.com",
                        active = true,
                        enabled = true,
                        usage = Usage(
                            unlimited = false,
                            limitBytes = 5_368_709_120L,
                            remainingBytes = 3_221_225_472L,
                        ),
                    ),
                ),
            ),
            status = CoreStatus(
                state = EngineState(phase = EnginePhase.RUNNING),
                activeAccount = Account(
                    id = "active_acc",
                    email = "active@example.com",
                    active = true,
                    enabled = true,
                    usage = Usage(
                        unlimited = false,
                        limitBytes = 5_368_709_120L,
                        remainingBytes = 3_221_225_472L,
                        checkedAtMs = 9_999_999L,
                    ),
                ),
            ),
        )
        composeRule.setContent {
            ClxyTheme {
                HomeScreen(
                    uiState = fakeState,
                    onToggleConnection = {},
                    onSelectMode = {},
                    onOpenServers = {},
                    onOpenSignIn = {},
                    onOpenDiagnostics = {},
                )
            }
        }
        composeRule.onNodeWithTag("home_data_usage_card").assertIsDisplayed()
        composeRule.onNodeWithText("2.00 GB").assertIsDisplayed()
        composeRule.onNodeWithText("3.00 GB").assertIsDisplayed()
    }

    @Test
    fun `home screen shows pending quota rather than unlimited when account usage is unknown`() {
        val fakeState = ClxyUiState(
            showOnboarding = false,
            connectionMode = ConnectionModeUi.VPN,
            accounts = AccountList(
                accounts = listOf(
                    Account(
                        id = "acc_null",
                        email = "pending@example.com",
                        active = true,
                        enabled = true,
                        usage = null,
                    ),
                ),
            ),
            status = CoreStatus(state = EngineState(phase = EnginePhase.OFF)),
        )
        composeRule.setContent {
            ClxyTheme {
                HomeScreen(
                    uiState = fakeState,
                    onToggleConnection = {},
                    onSelectMode = {},
                    onOpenServers = {},
                    onOpenSignIn = {},
                    onOpenDiagnostics = {},
                )
            }
        }
        composeRule.onNodeWithTag("home_data_usage_card").assertIsDisplayed()
        composeRule.onNodeWithText("Pending").assertIsDisplayed()
        composeRule.onNodeWithText("∞ Unlimited").assertDoesNotExist()
    }

    @Test
    fun `servers screen filters global locations`() {
        var selectedCode: String? = null
        val fakeLocations = LocationList(
            locations = listOf(
                VpnLocation("REC", "Recommended", "", "", recommended = true, pingMs = 42, pingError = ""),
                VpnLocation("DE", "Germany", "Frankfurt", "198.51.100.1", recommended = false, pingMs = 68, pingError = ""),
                VpnLocation("JP", "Japan", "Tokyo", "198.51.100.2", recommended = false, pingMs = 190, pingError = ""),
            ),
        )
        composeRule.setContent {
            ClxyTheme {
                ServersScreen(
                    uiState = ClxyUiState(showOnboarding = false, locations = fakeLocations),
                    onSelectLocation = { selectedCode = it },
                    onDisconnect = {},
                    onPingAll = {},
                    onRefreshLocations = {},
                    onCloseToHome = {},
                )
            }
        }
        composeRule.onNodeWithText("CLXY101.M1.FASTLY", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Germany", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Japan", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("server_card_DE").performClick()
        assertEquals("DE", selectedCode)
    }

    @Test
    fun `servers screen shows retry and enable shared relay actions when empty and guards duplicate refreshes`() {
        var refreshCalls = 0
        var enableRelayCalls = 0

        composeRule.setContent {
            ClxyTheme {
                ServersScreen(
                    uiState = ClxyUiState(
                        showOnboarding = false,
                        locations = LocationList(emptyList()),
                        locationsLoading = false,
                        sharedRelayEnabled = false,
                    ),
                    onSelectLocation = {},
                    onDisconnect = {},
                    onPingAll = {},
                    onRefreshLocations = { refreshCalls++ },
                    onCloseToHome = {},
                    onEnableSharedRelay = { enableRelayCalls++ },
                )
            }
        }

        composeRule.waitForIdle()
        assertEquals(1, refreshCalls)
        composeRule.onNodeWithTag("servers_empty_retry_button").assertIsDisplayed().performClick()
        assertEquals(2, refreshCalls)
        composeRule.onNodeWithTag("servers_empty_enable_relay_button").assertIsDisplayed().performClick()
        assertEquals(1, enableRelayCalls)
    }

    @Test
    fun `launcher adaptive icon and layers report exact 1to1 square intrinsic dimensions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val launcher = ContextCompat.getDrawable(context, R.mipmap.ic_launcher) as AdaptiveIconDrawable
        assertTrue(launcher.intrinsicWidth > 0)
        assertEquals(launcher.intrinsicWidth, launcher.intrinsicHeight)

        val fg = launcher.foreground!!
        assertTrue(fg.intrinsicWidth > 0)
        assertEquals(fg.intrinsicWidth, fg.intrinsicHeight)

        val bg = launcher.background!!
        assertTrue(bg.intrinsicWidth > 0)
        assertEquals(bg.intrinsicWidth, bg.intrinsicHeight)
    }

    @Test
    fun `route tunnel box places origin flag adjacent to the arrow rather than far left`() {
        val fakeState = ClxyUiState(
            showOnboarding = false,
            connectionMode = ConnectionModeUi.VPN,
            deviceOriginCountryCode = "IR",
            deviceOriginLabel = "Iran",
            status = CoreStatus(
                state = EngineState(phase = EnginePhase.RUNNING),
                exit = ExitInfo(ipv4 = "45.58.235.52", countryCode = "NL", city = "Amsterdam"),
                selection = "nl",
                selectionName = "Netherlands",
            ),
        )
        composeRule.setContent {
            ClxyTheme {
                HomeScreen(
                    uiState = fakeState,
                    onToggleConnection = {},
                    onSelectMode = {},
                    onOpenServers = {},
                    onOpenSignIn = {},
                    onOpenDiagnostics = {},
                )
            }
        }
        val boxBounds = composeRule.onNodeWithTag("home_route_tunnel_box").getBoundsInRoot()
        val originFlagBounds = composeRule.onNodeWithTag("home_origin_flag").getBoundsInRoot()
        val arrowBounds = composeRule.onNodeWithTag("home_route_arrow").getBoundsInRoot()
        val destFlagBounds = composeRule.onNodeWithTag("home_dest_flag").getBoundsInRoot()

        val gapBeforeArrow = arrowBounds.left - originFlagBounds.right
        val gapAfterArrow = destFlagBounds.left - arrowBounds.right
        val gapFromBoxStart = originFlagBounds.left - boxBounds.left
        assertTrue(
            "Expected origin flag close to arrow ($gapBeforeArrow), got box start gap $gapFromBoxStart",
            gapBeforeArrow <= 12.dp && gapFromBoxStart > gapBeforeArrow,
        )
        assertTrue("Expected symmetrical flag gap after arrow, got $gapAfterArrow", gapAfterArrow <= 12.dp)
    }

    @Test
    fun `app update dialog and in-app banner show release details and open browser download`() {
        val sampleUpdate = AppUpdateInfo(
            latestTag = "v142.0",
            latestVersion = "142.0",
            title = "CLXY v142.0",
            releaseNotes = "Bug fixes",
            releasePageUrl = "https://github.com/ClxybyCloner/CLXY-Android/releases/tag/v142.0",
            apkAssetUrl = "https://github.com/ClxybyCloner/CLXY-Android/releases/download/v142.0/CLXY-v142.0.apk",
            apkAssetName = "CLXY-v142.0.apk",
            apkAssetSizeBytes = 24_000_000L,
            publishedAt = "2026-11-14T18:00:00Z",
            isUpdateAvailable = true,
        )
        var openedDialog = false
        var openedDownloadUrl: String? = null
        var rechecked = false

        composeRule.setContent {
            ClxyTheme {
                InAppUpdateBanner(
                    updateInfo = sampleUpdate,
                    onOpenUpdate = { openedDialog = true },
                    onDismissBanner = {},
                )
                AppUpdateDialog(
                    checking = false,
                    updateInfo = sampleUpdate,
                    errorMessage = null,
                    onCheckAgain = { rechecked = true },
                    onOpenDownload = { openedDownloadUrl = it },
                    onOpenReleasePage = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("in_app_update_banner").performClick()
        assertTrue(openedDialog)
        composeRule.onNodeWithTag("update_available_card").assertIsDisplayed()
        composeRule.onNodeWithTag("update_download_button").performClick()
        assertEquals(sampleUpdate.apkAssetUrl, openedDownloadUrl)
        composeRule.onNodeWithTag("update_recheck_button").performClick()
        assertTrue(rechecked)
    }
}

