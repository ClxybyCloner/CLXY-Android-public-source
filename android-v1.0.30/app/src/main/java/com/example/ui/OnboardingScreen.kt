package com.example.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.CountryNames
import com.example.core.SignInError
import com.example.core.SignInState
import com.example.core.SignInStep
import com.example.ui.theme.ClxyColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onStartSignIn: suspend (email: String, password: String) -> SignInState,
    onPollSignIn: suspend (id: String) -> SignInState,
    onAnswerSignIn: suspend (id: String, value: String) -> SignInState,
    onCancelSignIn: suspend (id: String) -> Unit,
    onSignedIn: () -> Unit,
    onOpenRelaySettings: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var usernameOrEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var verificationCode by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var errorDismissed by remember { mutableStateOf(false) }
    var signInState by remember { mutableStateOf<SignInState?>(null) }
    var completedNotified by remember { mutableStateOf(false) }

    LaunchedEffect(signInState?.id, signInState?.step, signInState?.errorCode, submitting) {
        if (submitting) return@LaunchedEffect
        val current = signInState ?: return@LaunchedEffect
        if (current.finished) {
            if (current.step == SignInStep.DONE && !completedNotified) {
                completedNotified = true
                onSignedIn()
            }
            return@LaunchedEffect
        }
        while (isActive) {
            delay(800L)
            val next = runCatching { onPollSignIn(current.id) }.getOrNull() ?: continue
            val stepChanged = next.step != signInState?.step
            val errChanged = next.errorCode != signInState?.errorCode || next.error != signInState?.error
            if (stepChanged) {
                verificationCode = ""
            }
            if (errChanged && (next.errorCode.isNotEmpty() || next.error.isNotEmpty())) {
                errorDismissed = false
            }
            signInState = next
            if (next.finished) {
                if (next.step == SignInStep.DONE && !completedNotified) {
                    completedNotified = true
                    onSignedIn()
                }
                break
            }
        }
    }

    fun submitCredentials() {
        val trimmedUser = usernameOrEmail.trim()
        if (trimmedUser.isEmpty()) {
            errorMsg = "Enter your username or email."
            errorDismissed = false
            return
        }
        if (password.isEmpty()) {
            errorMsg = "Enter your password."
            errorDismissed = false
            return
        }
        focusManager.clearFocus()
        scope.launch {
            submitting = true
            errorMsg = null
            errorDismissed = false
            completedNotified = false
            try {
                val started = onStartSignIn(trimmedUser, password)
                signInState = started
                errorDismissed = false
                if (started.step == SignInStep.DONE && !completedNotified) {
                    completedNotified = true
                    onSignedIn()
                }
            } catch (e: Exception) {
                errorMsg = e.message?.ifBlank { null }?.let { CountryNames.brandAsClxy(it) }
                    ?: "Authentication failed. Check your connection or credentials."
            } finally {
                submitting = false
            }
        }
    }

    fun submitVerification() {
        val current = signInState ?: return
        val code = verificationCode.trim()
        if (code.isEmpty()) {
            errorMsg = "Please enter the verification code."
            errorDismissed = false
            return
        }
        focusManager.clearFocus()
        scope.launch {
            submitting = true
            errorMsg = null
            errorDismissed = false
            try {
                val updated = onAnswerSignIn(current.id, code)
                signInState = updated
                errorDismissed = false
                if (updated.step == SignInStep.DONE && !completedNotified) {
                    completedNotified = true
                    onSignedIn()
                }
            } catch (e: Exception) {
                errorMsg = e.message?.ifBlank { null }?.let { CountryNames.brandAsClxy(it) }
                    ?: "Verification failed. Please check the code and try again."
            } finally {
                submitting = false
            }
        }
    }

    fun resetAttempt() {
        val current = signInState
        signInState = null
        verificationCode = ""
        errorMsg = null
        errorDismissed = false
        completedNotified = false
        if (current != null && !current.finished) {
            scope.launch { runCatching { onCancelSignIn(current.id) } }
        }
    }

    ClxyCosmicBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            DottedWorldMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp),
                dotAlpha = 0.36f,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                ClxyBrandHeader(logoSize = 42.dp, labelSizeSp = 22f)
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Sign In to CLXY",
                    color = ClxyColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Enter your credentials for direct, automatic authentication.",
                    color = ClxyColors.TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 300.dp),
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Glassmorphic Credential Card
                val active = signInState
                val activeErrorText = if (!errorDismissed && active != null &&
                    (active.errorCode.isNotEmpty() || active.error.isNotEmpty() || active.step == SignInStep.FAILED)
                ) {
                    CountryNames.describeSignInError(active.errorCode, active.error)
                } else {
                    null
                }
                val displayError = errorMsg
                    ?: activeErrorText
                    ?: if (!errorDismissed && active?.step == SignInStep.CANCELLED) "Sign-in cancelled." else null
                val showRelaySettingsAction = active?.errorCode == SignInError.BLOCKED ||
                    active?.errorCode == SignInError.TOKEN_BLOCKED

                val cardStage = when {
                    active == null || active.step == SignInStep.FAILED || active.step == SignInStep.CANCELLED -> 0
                    active.step == SignInStep.DONE -> 1
                    else -> 2
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 440.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xE60F172E), Color(0xF20A1023)),
                            ),
                        )
                        .border(1.dp, ClxyColors.CyanBorder, RoundedCornerShape(20.dp))
                        .padding(20.dp),
                ) {
                    AnimatedContent(
                        targetState = cardStage,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "loginCardStage",
                    ) { stage ->
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            when (stage) {
                                0 -> {
                                    OutlinedTextField(
                                        value = usernameOrEmail,
                                        onValueChange = {
                                            usernameOrEmail = it
                                            errorMsg = null
                                            errorDismissed = true
                                        },
                                        label = { Text("Username or Email") },
                                        placeholder = { Text("name@example.com", color = ClxyColors.TextDim) },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Person, contentDescription = null, tint = ClxyColors.CyanText)
                                        },
                                        singleLine = true,
                                        isError = !displayError.isNullOrEmpty(),
                                        enabled = !submitting,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Email,
                                            imeAction = ImeAction.Next,
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                        ),
                                        colors = clxyTextFieldColors(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("login_email_input"),
                                    )

                                    OutlinedTextField(
                                        value = password,
                                        onValueChange = {
                                            password = it
                                            errorMsg = null
                                            errorDismissed = true
                                        },
                                        label = { Text("Password") },
                                        placeholder = { Text("Enter password", color = ClxyColors.TextDim) },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = ClxyColors.CyanText)
                                        },
                                        trailingIcon = {
                                            IconButton(
                                                onClick = { showPassword = !showPassword },
                                                modifier = Modifier.testTag("login_password_toggle"),
                                            ) {
                                                Icon(
                                                    imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                    contentDescription = if (showPassword) "Hide password" else "Show password",
                                                    tint = ClxyColors.TextMuted,
                                                )
                                            }
                                        },
                                        singleLine = true,
                                        isError = !displayError.isNullOrEmpty(),
                                        enabled = !submitting,
                                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Password,
                                            imeAction = ImeAction.Done,
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = { submitCredentials() },
                                        ),
                                        colors = clxyTextFieldColors(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("login_password_input"),
                                    )

                                    AnimatedVisibility(
                                        visible = !displayError.isNullOrEmpty(),
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically(),
                                    ) {
                                        Box(modifier = Modifier.testTag("login_error_banner")) {
                                            ErrorBanner(
                                                message = displayError.orEmpty(),
                                                actionLabel = if (showRelaySettingsAction) "Open Relay settings" else null,
                                                onAction = if (showRelaySettingsAction) onOpenRelaySettings else null,
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .testTag("login_submit_button")
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 50.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (submitting) {
                                                    Brush.horizontalGradient(listOf(ClxyColors.BlueDeep, Color(0xFF334155)))
                                                } else {
                                                    ClxyPrimaryButtonBrush
                                                },
                                            )
                                            .clickable(enabled = !submitting) { submitCredentials() }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (submitting) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                CircularProgressIndicator(
                                                    color = Color.White,
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                                Text(
                                                    text = "Authenticating…",
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "Sign In",
                                                color = Color.White,
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                }

                                1 -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.padding(vertical = 8.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = ClxyColors.EmeraldGood,
                                            modifier = Modifier.size(30.dp),
                                        )
                                        Column {
                                            Text(
                                                text = "Authenticated",
                                                color = ClxyColors.TextPrimary,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Text(
                                                text = active?.email?.ifEmpty { usernameOrEmail.trim() }.orEmpty(),
                                                color = ClxyColors.TextMuted,
                                                fontSize = 12.5.sp,
                                            )
                                        }
                                    }
                                }

                                else -> {
                                    val current = active ?: SignInState()
                                    Text(
                                        text = current.email.ifEmpty { usernameOrEmail.trim() },
                                        color = ClxyColors.CyanText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )

                                    val instruction = when (current.step) {
                                        SignInStep.NEED_EMAIL_CODE -> "Enter the verification code sent to your email."
                                        SignInStep.NEED_TOTP -> "Enter the 6-digit code from your authenticator app."
                                        SignInStep.NEED_UNBLOCK_CODE -> "Enter the authorization code sent to your email."
                                        SignInStep.NEED_CAPTCHA -> "Enter the characters shown below."
                                        SignInStep.NEED_EMAIL_LINK -> "Confirm the verification link sent to your email."
                                        else -> CountryNames.brandAsClxy(current.message).ifEmpty { "Verifying credentials automatically…" }
                                    }

                                    if (current.step == SignInStep.WORKING || current.step == SignInStep.NEED_EMAIL_LINK) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier
                                                .padding(vertical = 8.dp)
                                                .testTag("login_working_state"),
                                        ) {
                                            CircularProgressIndicator(
                                                color = ClxyColors.CyanGlow,
                                                strokeWidth = 2.5.dp,
                                                modifier = Modifier.size(22.dp),
                                            )
                                            Text(
                                                text = instruction,
                                                color = ClxyColors.TextPrimary,
                                                fontSize = 13.5.sp,
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = instruction,
                                            color = ClxyColors.TextSecondary,
                                            fontSize = 13.sp,
                                        )
                                    }

                                    if (current.step == SignInStep.NEED_CAPTCHA && current.captcha.isNotEmpty()) {
                                        InlineCaptchaImage(current.captcha)
                                    }

                                    if (current.needsAnswer) {
                                        OutlinedTextField(
                                            value = verificationCode,
                                            onValueChange = {
                                                verificationCode = it
                                                errorMsg = null
                                                errorDismissed = true
                                            },
                                            label = { Text("Verification code") },
                                            singleLine = true,
                                            isError = !displayError.isNullOrEmpty(),
                                            enabled = !submitting,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = if (current.step == SignInStep.NEED_EMAIL_CODE || current.step == SignInStep.NEED_TOTP) {
                                                    KeyboardType.Number
                                                } else {
                                                    KeyboardType.Text
                                                },
                                                imeAction = ImeAction.Done,
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onDone = { submitVerification() },
                                            ),
                                            colors = clxyTextFieldColors(),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("login_verification_input"),
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = !displayError.isNullOrEmpty(),
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically(),
                                    ) {
                                        Box(modifier = Modifier.testTag("login_error_banner")) {
                                            ErrorBanner(displayError.orEmpty())
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        TextButton(
                                            onClick = { resetAttempt() },
                                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                        ) {
                                            Text("Change account", color = ClxyColors.TextMuted, fontSize = 13.sp)
                                        }
                                        Spacer(modifier = Modifier.weight(1f))
                                        if (current.needsAnswer) {
                                            Box(
                                                modifier = Modifier
                                                    .testTag("login_verify_button")
                                                    .defaultMinSize(minWidth = 110.dp, minHeight = 48.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (submitting) {
                                                            Brush.horizontalGradient(listOf(ClxyColors.BlueDeep, Color(0xFF334155)))
                                                        } else {
                                                            ClxyPrimaryButtonBrush
                                                        },
                                                    )
                                                    .clickable(enabled = !submitting) {
                                                        submitVerification()
                                                    }
                                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = if (submitting) "Checking…" else "Verify",
                                                    color = Color.White,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                            }
                                        }
                                    }
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
private fun InlineCaptchaImage(dataUrl: String) {
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
            contentDescription = "Verification CAPTCHA",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White),
        )
    }
}

@Composable
fun ShieldIcon(color: Color, sizeDp: Float = 14f) {
    Canvas(modifier = Modifier.size(width = sizeDp.dp, height = (sizeDp * 1.14f).dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.03f)
            lineTo(w * 0.11f, h * 0.17f)
            lineTo(w * 0.11f, h * 0.46f)
            cubicTo(w * 0.11f, h * 0.69f, w * 0.27f, h * 0.91f, w * 0.50f, h * 0.97f)
            cubicTo(w * 0.73f, h * 0.91f, w * 0.89f, h * 0.69f, w * 0.89f, h * 0.46f)
            lineTo(w * 0.89f, h * 0.17f)
            close()
        }
        drawPath(path, color)
        val check = Path().apply {
            moveTo(w * 0.34f, h * 0.50f)
            lineTo(w * 0.45f, h * 0.61f)
            lineTo(w * 0.68f, h * 0.39f)
        }
        drawPath(
            check,
            color = ClxyColors.BgBottom,
            style = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
