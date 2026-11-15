package com.example.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.AndroidPlatformHelper
import com.example.core.BackupContents
import com.example.core.ClxyViewModel
import com.example.core.CountryNames
import com.example.core.SignInError
import com.example.core.SignInState
import com.example.core.SignInStep
import com.example.ui.theme.ClxyColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder

private const val FXA_SET_PASSWORD_URL = "https://accounts.firefox.com/reset_password/"
private const val FXA_ANDROID_UA = "Mozilla/5.0 (Android 14; Mobile; rv:145.0) Gecko/145.0 Firefox/145.0"

@Composable
fun clxyTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = ClxyColors.TextPrimary,
    unfocusedTextColor = ClxyColors.TextPrimary,
    focusedBorderColor = ClxyColors.CyanGlow,
    unfocusedBorderColor = Color(0xFF334155),
    focusedLabelColor = ClxyColors.CyanText,
    unfocusedLabelColor = ClxyColors.TextMuted,
    cursorColor = ClxyColors.CyanGlow,
    focusedContainerColor = Color(0xAA070B19),
    unfocusedContainerColor = Color(0x88070B19),
)

@Composable
fun SignInModalDialog(
    viewModel: ClxyViewModel,
    initialEmail: String,
    startedState: SignInState?,
    onDismiss: () -> Unit,
    onSwitchToBrowser: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var email by remember(initialEmail) { mutableStateOf(initialEmail) }
    var password by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var errorDismissed by remember { mutableStateOf(false) }
    var state by remember(startedState) { mutableStateOf(startedState) }

    LaunchedEffect(state?.id, state?.step, state?.errorCode, sending) {
        if (sending) return@LaunchedEffect
        val current = state ?: return@LaunchedEffect
        if (current.finished) {
            if (current.step == SignInStep.DONE) {
                viewModel.completeOnboarding()
                viewModel.refresh(accountsToo = true)
            }
            return@LaunchedEffect
        }
        while (isActive) {
            delay(1_000L)
            val next = runCatching { viewModel.signInState(current.id) }.getOrNull() ?: continue
            if (next.step != state?.step) {
                answer = ""
            }
            if ((next.errorCode != state?.errorCode || next.error != state?.error) &&
                (next.errorCode.isNotEmpty() || next.error.isNotEmpty())
            ) {
                errorDismissed = false
            }
            state = next
            if (next.finished) {
                if (next.step == SignInStep.DONE) {
                    viewModel.completeOnboarding()
                    viewModel.refresh(accountsToo = true)
                }
                break
            }
        }
    }

    fun startPasswordLogin() {
        val trimmed = email.trim()
        if (trimmed.isEmpty() || password.isEmpty()) {
            errorMsg = if (trimmed.isEmpty()) "Enter your username or email." else "Enter your password."
            errorDismissed = false
            return
        }
        scope.launch {
            sending = true
            errorMsg = null
            errorDismissed = false
            try {
                state = viewModel.startSignIn(trimmed, password)
                errorDismissed = false
            } catch (e: Exception) {
                errorMsg = e.message?.ifBlank { null }?.let { CountryNames.brandAsClxy(it) } ?: "Could not start sign-in"
            } finally {
                sending = false
            }
        }
    }

    fun submitAnswer() {
        val s = state ?: return
        val valTrimmed = answer.trim()
        if (valTrimmed.isEmpty()) {
            errorMsg = "Please enter the verification code."
            errorDismissed = false
            return
        }
        scope.launch {
            sending = true
            errorMsg = null
            errorDismissed = false
            try {
                state = viewModel.answerSignIn(s.id, valTrimmed)
                errorDismissed = false
            } catch (e: Exception) {
                errorMsg = e.message?.ifBlank { null }?.let { CountryNames.brandAsClxy(it) } ?: "Failed to submit verification"
            } finally {
                sending = false
            }
        }
    }

    Dialog(onDismissRequest = {
        val active = state
        if (active != null && !active.finished) {
            scope.launch { runCatching { viewModel.cancelSignIn(active.id) } }
        }
        onDismiss()
    }) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = ClxyColors.ModalSurface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ClxyColors.CyanBorder, RoundedCornerShape(18.dp)),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                val s = state
                Text(
                    text = if (s == null) "Sign in to CLXY Account" else s.email.ifEmpty { "Signing in" },
                    color = ClxyColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (s == null) {
                    Text(
                        text = "Enter your username/email and password to authenticate automatically.",
                        color = ClxyColors.TextMuted,
                        fontSize = 12.sp,
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            errorMsg = null
                            errorDismissed = true
                        },
                        label = { Text("Username or Email") },
                        singleLine = true,
                        isError = !errorMsg.isNullOrEmpty(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = clxyTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("signin_email_input"),
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMsg = null
                            errorDismissed = true
                        },
                        label = { Text("Password") },
                        singleLine = true,
                        isError = !errorMsg.isNullOrEmpty(),
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (showPassword) "Hide password" else "Show password",
                                    tint = ClxyColors.TextMuted,
                                )
                            }
                        },
                        colors = clxyTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("signin_password_input"),
                    )

                    if (!errorMsg.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ErrorBanner(errorMsg!!)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = ClxyColors.TextMuted)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { startPasswordLogin() },
                            enabled = !sending,
                            colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                            modifier = Modifier.testTag("signin_submit_button"),
                        ) {
                            if (sending) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            } else {
                                Text("Sign in", color = Color.White)
                            }
                        }
                    }
                } else {
                    val instruction = when (s.step) {
                        SignInStep.NEED_EMAIL_CODE -> "Enter the verification code sent to ${s.email}."
                        SignInStep.NEED_TOTP -> "Enter the 6-digit code from your authenticator app."
                        SignInStep.NEED_UNBLOCK_CODE -> "Enter the authorization/unblock code sent to ${s.email}."
                        SignInStep.NEED_CAPTCHA -> "Enter the characters shown in the CAPTCHA image."
                        SignInStep.NEED_EMAIL_LINK -> "Open the verification link sent to ${s.email} to continue."
                        SignInStep.WORKING -> CountryNames.brandAsClxy(s.message).ifEmpty { "Signing in with CLXY…" }
                        SignInStep.FAILED -> CountryNames.describeSignInError(s.errorCode, s.error)
                        SignInStep.CANCELLED -> "Sign-in cancelled."
                        SignInStep.DONE -> "Account ready! Added to CLXY."
                        else -> CountryNames.brandAsClxy(s.message)
                    }

                    val stepInlineError = errorMsg ?: if (!errorDismissed &&
                        s.step != SignInStep.FAILED &&
                        s.step != SignInStep.CANCELLED &&
                        s.step != SignInStep.DONE &&
                        (s.errorCode.isNotEmpty() || s.error.isNotEmpty())
                    ) {
                        CountryNames.describeSignInError(s.errorCode, s.error)
                    } else {
                        null
                    }

                    if (s.step == SignInStep.DONE) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 8.dp),
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = ClxyColors.EmeraldGood, modifier = Modifier.size(28.dp))
                            Text(instruction, color = ClxyColors.TextPrimary, fontSize = 13.5.sp)
                        }
                    } else {
                        if (s.step == SignInStep.WORKING || s.step == SignInStep.NEED_EMAIL_LINK) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(vertical = 6.dp),
                            ) {
                                CircularProgressIndicator(color = ClxyColors.CyanGlow, strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp))
                                Text(instruction, color = ClxyColors.TextSecondary, fontSize = 13.sp)
                            }
                        } else if (s.step == SignInStep.FAILED || s.step == SignInStep.CANCELLED) {
                            ErrorBanner(instruction)
                        } else {
                            Text(instruction, color = ClxyColors.TextSecondary, fontSize = 13.sp)
                        }

                        if (s.step == SignInStep.NEED_CAPTCHA && s.captcha.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            DecodeCaptchaImage(s.captcha)
                        }

                        if (s.needsAnswer) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = answer,
                                onValueChange = {
                                    answer = it
                                    errorMsg = null
                                    errorDismissed = true
                                },
                                label = { Text("Verification code") },
                                singleLine = true,
                                isError = !stepInlineError.isNullOrEmpty(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = if (s.step == SignInStep.NEED_EMAIL_CODE || s.step == SignInStep.NEED_TOTP) {
                                        KeyboardType.Number
                                    } else {
                                        KeyboardType.Text
                                    },
                                ),
                                colors = clxyTextFieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        if (!stepInlineError.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            ErrorBanner(stepInlineError)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        if (s.step == SignInStep.DONE) {
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                            ) {
                                Text("Done", color = Color.White)
                            }
                        } else {
                            TextButton(onClick = {
                                if (!s.finished) {
                                    scope.launch { runCatching { viewModel.cancelSignIn(s.id) } }
                                }
                                onDismiss()
                            }) {
                                Text(if (s.finished) "Close" else "Cancel", color = ClxyColors.TextMuted)
                            }
                            if (s.step == SignInStep.FAILED && s.errorCode == SignInError.NO_PASSWORD) {
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(onClick = { AndroidPlatformHelper.openUrl(context, FXA_SET_PASSWORD_URL) }) {
                                    Text("Set password", color = ClxyColors.CyanText)
                                }
                            } else if (s.step == SignInStep.FAILED || s.step == SignInStep.CANCELLED) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { state = null; errorMsg = null; errorDismissed = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                                ) {
                                    Text("Try again", color = Color.White)
                                }
                            } else if (s.needsAnswer) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { submitAnswer() },
                                    enabled = !sending,
                                    colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                                ) {
                                    Text("Continue", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DecodeCaptchaImage(dataUrl: String) {
    val bitmap: Bitmap? = remember(dataUrl) {
        runCatching {
            val comma = dataUrl.indexOf(',')
            val b64 = if (comma >= 0) dataUrl.substring(comma + 1) else dataUrl
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Sign-in CAPTCHA",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White),
        )
    }
}

@Composable
fun ErrorBanner(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ClxyColors.RedDisconnectStart.copy(alpha = 0.14f))
            .border(1.dp, ClxyColors.RedDisconnectStart.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        if (actionLabel != null && onAction != null) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = message,
                    color = Color(0xFFFDA4AF),
                    fontSize = 12.sp,
                )
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.testTag("login_open_relay_settings_button"),
                ) {
                    Text(actionLabel, color = ClxyColors.CyanText, fontSize = 12.sp)
                }
            }
        } else {
            Text(
                text = message,
                color = Color(0xFFFDA4AF),
                fontSize = 12.sp,
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserSignInDialog(
    viewModel: ClxyViewModel,
    initialEmail: String,
    onDismiss: () -> Unit,
    onFallbackToForm: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var proxyReady by remember { mutableStateOf(false) }
    var proxyUsed by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var activeSignInId by remember { mutableStateOf<String?>(null) }
    var seenToken by remember { mutableStateOf<String?>(null) }

    val loginUrl = remember(initialEmail) {
        val base = "https://accounts.firefox.com/?context=fx_desktop_v3&service=sync&action=email"
        if (initialEmail.isNotBlank()) {
            "$base&email=${URLEncoder.encode(initialEmail, "UTF-8")}"
        } else {
            base
        }
    }

    val hookJs = remember {
        """
        (function () {
          if (window.__armanHooked) return;
          window.__armanHooked = true;
          var send = function (o) { try { ArmanFxa.postMessage(JSON.stringify(o)); } catch (e) {} };
          var onChannel = function (e) {
            var d = e.detail;
            if (typeof d === 'string') { try { d = JSON.parse(d); } catch (e) { return; } }
            if (d && d.message) send({ kind: 'webchannel', detail: d });
          };
          window.addEventListener('WebChannelMessageToChrome', onChannel);
          document.addEventListener('WebChannelMessageToChrome', onChannel);
          window.__armanReply = function (id, messageId, command, data) {
            var detail = { id: id, message: { messageId: messageId, command: command, data: data } };
            window.dispatchEvent(new CustomEvent('WebChannelMessageToContent', { detail: detail }));
            document.dispatchEvent(new CustomEvent('WebChannelMessageToContent', { detail: detail }));
          };
        })();
        """.trimIndent()
    }

    LaunchedEffect(Unit) {
        try {
            val proxyAddr = runCatching { viewModel.openSignInProxy() }.getOrNull()
            if (!proxyAddr.isNullOrEmpty()) {
                proxyUsed = AndroidPlatformHelper.setWebViewProxy(context, proxyAddr)
            }
        } catch (_: Exception) {
        }
        proxyReady = true
    }

    LaunchedEffect(activeSignInId) {
        val id = activeSignInId ?: return@LaunchedEffect
        while (isActive) {
            delay(1_000L)
            val state = runCatching { viewModel.signInState(id) }.getOrNull() ?: continue
            if (state.finished) {
                viewModel.onBrowserSignInFinished(state)
                break
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val id = activeSignInId
            scope.launch {
                if (id != null) runCatching { viewModel.cancelSignIn(id) }
                if (proxyUsed) runCatching { AndroidPlatformHelper.setWebViewProxy(context, null) }
                runCatching { viewModel.closeSignInProxy() }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = ClxyColors.BgTop,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ClxyColors.ModalSurface)
                        .border(0.5.dp, ClxyColors.GlassBorder)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "CLXY Account Sign-In",
                            color = ClxyColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (proxyUsed) "Routed via CLXY Core proxy" else "Direct browser session",
                            color = ClxyColors.TextMuted,
                            fontSize = 11.sp,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onFallbackToForm(initialEmail) }) {
                            Text("Use Password", color = ClxyColors.CyanText, fontSize = 12.sp)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close Browser Sign-In", tint = ClxyColors.TextPrimary)
                        }
                    }
                }

                if (loading || !proxyReady) {
                    LinearProgressIndicator(
                        color = ClxyColors.CyanGlow,
                        trackColor = ClxyColors.CardSurfaceSolid,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (pageError != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ErrorBanner(pageError!!)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { onFallbackToForm(initialEmail) },
                            colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                        ) {
                            Text("Use Email & Password", color = Color.White)
                        }
                    }
                } else if (proxyReady) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewRef = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.userAgentString = FXA_ANDROID_UA
                                val jsBridge = object {
                                    @JavascriptInterface
                                    fun postMessage(raw: String) {
                                        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return
                                        if (root.optString("kind") != "webchannel") return
                                        val detail = root.optJSONObject("detail") ?: return
                                        val id = detail.optString("id", "")
                                        val msg = detail.optJSONObject("message") ?: return
                                        val command = msg.optString("command", "")
                                        val msgId = msg.opt("messageId")
                                        when (command) {
                                            "fxaccounts:fxa_status" -> {
                                                val payload = JSONObject()
                                                    .put("capabilities", JSONObject())
                                                    .put("signedInUser", JSONObject.NULL)
                                                val js = "window.__armanReply(${JSONObject.quote(id)}, ${JSONObject.wrap(msgId)}, ${JSONObject.quote(command)}, $payload)"
                                                post { evaluateJavascript(js, null) }
                                            }
                                            "fxaccounts:can_link_account" -> {
                                                val payload = JSONObject().put("ok", true)
                                                val js = "window.__armanReply(${JSONObject.quote(id)}, ${JSONObject.wrap(msgId)}, ${JSONObject.quote(command)}, $payload)"
                                                post { evaluateJavascript(js, null) }
                                            }
                                            "fxaccounts:login", "fxaccounts:oauth_login" -> {
                                                val data = msg.optJSONObject("data") ?: return
                                                val token = data.optString("sessionToken", "").ifEmpty { data.optString("token", "") }
                                                if (!token.matches(Regex("^[0-9a-fA-F]{64}$"))) return
                                                if (token == seenToken) return
                                                seenToken = token
                                                val capturedEmail = data.optString("email", "").ifEmpty { initialEmail }
                                                scope.launch {
                                                    try {
                                                        val started = viewModel.startBrowserSignIn(capturedEmail, token)
                                                        activeSignInId = started.id
                                                    } catch (e: Exception) {
                                                        pageError = e.message ?: "Could not hand session to CLXY core"
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                addJavascriptInterface(jsBridge, "ArmanFxa")
                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        loading = true
                                        view?.evaluateJavascript(hookJs, null)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        view?.evaluateJavascript(hookJs, null)
                                        loading = false
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                    ): Boolean = false
                                }
                                loadUrl(loginUrl)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun BackupImportPreviewDialog(
    zipBytes: ByteArray,
    contents: BackupContents,
    onDismiss: () -> Unit,
    onConfirmImport: (ByteArray) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = ClxyColors.ModalSurface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ClxyColors.CyanBorder, RoundedCornerShape(18.dp)),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Restore Accounts Backup",
                    color = ClxyColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${contents.accounts.size} account(s) found in backup archive.",
                    color = ClxyColors.TextMuted,
                    fontSize = 12.5.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    contents.accounts.forEach { acc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClxyColors.CardSurface)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(acc.email, color = ClxyColors.TextPrimary, fontSize = 12.5.sp)
                            Text(
                                text = if (acc.exists) "Update" else "New",
                                color = if (acc.exists) ClxyColors.AmberWarning else ClxyColors.EmeraldGood,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = ClxyColors.TextMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirmImport(zipBytes) },
                        colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                    ) {
                        Text("Import", color = Color.White)
                    }
                }
            }
        }
    }
}
