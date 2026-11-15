package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.core.AppUpdateInfo
import com.example.core.CountryNames
import com.example.ui.theme.ClxyColors

@Composable
fun AppUpdateDialog(
    checking: Boolean,
    updateInfo: AppUpdateInfo?,
    errorMessage: String?,
    onCheckAgain: () -> Unit,
    onOpenDownload: (String) -> Unit,
    onOpenReleasePage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = ClxyColors.ModalSurface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .widthIn(max = 460.dp)
                .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(18.dp)),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(ClxyColors.BlueDeep.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SystemUpdate,
                                contentDescription = null,
                                tint = ClxyColors.CyanGlow,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column {
                            Text(
                                text = "Check for Update",
                                color = ClxyColors.TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "GitHub • ClxybyCloner/CLXY-Android",
                                color = ClxyColors.TextMuted,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("update_dialog_close"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close update dialog",
                            tint = ClxyColors.TextMuted,
                        )
                    }
                }

                // Version comparison card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ClxyColors.CardSurface)
                        .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Installed Version", color = ClxyColors.TextDim, fontSize = 10.5.sp)
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            color = ClxyColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (updateInfo != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Latest on GitHub", color = ClxyColors.TextDim, fontSize = 10.5.sp)
                            Text(
                                text = updateInfo.latestTag,
                                color = if (updateInfo.isUpdateAvailable) ClxyColors.CyanText else ClxyColors.EmeraldGood,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                when {
                    checking -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                                .testTag("update_checking_indicator"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(
                                color = ClxyColors.CyanGlow,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Checking GitHub releases…",
                                color = ClxyColors.TextSecondary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    errorMessage != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ClxyColors.RedDisconnectStart.copy(alpha = 0.14f))
                                .border(1.dp, ClxyColors.RedDisconnectStart.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                                .testTag("update_error_box"),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "Update check failed",
                                color = ClxyColors.RedDisconnectStart,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = errorMessage,
                                color = ClxyColors.TextSecondary,
                                fontSize = 11.5.sp,
                            )
                        }
                    }
                    updateInfo != null && updateInfo.isUpdateAvailable -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ClxyColors.CyanGlow.copy(alpha = 0.08f))
                                .border(1.dp, ClxyColors.CyanBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                                .testTag("update_available_card"),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "New version available: ${updateInfo.title}",
                                color = ClxyColors.CyanText,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (updateInfo.apkAssetName != null) {
                                val sizeLabel = if (updateInfo.apkAssetSizeBytes > 0L) {
                                    " (${CountryNames.formatBytes(updateInfo.apkAssetSizeBytes)})"
                                } else {
                                    ""
                                }
                                Text(
                                    text = "Package: ${updateInfo.apkAssetName}$sizeLabel",
                                    color = ClxyColors.TextMuted,
                                    fontSize = 11.sp,
                                )
                            }
                            if (updateInfo.releaseNotes.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 150.dp)
                                        .verticalScroll(rememberScrollState()),
                                ) {
                                    Text(
                                        text = updateInfo.releaseNotes,
                                        color = ClxyColors.TextSecondary,
                                        fontSize = 11.5.sp,
                                        lineHeight = 16.sp,
                                    )
                                }
                            }
                        }

                        val downloadUrl = updateInfo.apkAssetUrl ?: updateInfo.releasePageUrl
                        Button(
                            onClick = { onOpenDownload(downloadUrl) },
                            colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("update_download_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.OpenInBrowser,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (updateInfo.apkAssetUrl != null) {
                                    "Download APK in Browser"
                                } else {
                                    "Open Release on GitHub"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        if (updateInfo.apkAssetUrl != null) {
                            OutlinedButton(
                                onClick = { onOpenReleasePage(updateInfo.releasePageUrl) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("update_release_page_button"),
                            ) {
                                Text("View Release Notes on GitHub", color = ClxyColors.CyanText)
                            }
                        }
                    }
                    updateInfo != null -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ClxyColors.EmeraldGood.copy(alpha = 0.12f))
                                .border(1.dp, ClxyColors.EmeraldGood.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                                .testTag("update_up_to_date_box"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = ClxyColors.EmeraldGood,
                                modifier = Modifier.size(20.dp),
                            )
                            Column {
                                Text(
                                    text = "You're up to date",
                                    color = ClxyColors.TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = if (updateInfo.title.isNotEmpty() && !updateInfo.title.startsWith("No GitHub")) {
                                        "Latest release ${updateInfo.latestTag} matches your version."
                                    } else {
                                        "No newer release found on GitHub."
                                    },
                                    color = ClxyColors.TextMuted,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onCheckAgain,
                    enabled = !checking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("update_recheck_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = ClxyColors.CyanText,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Check Again", color = ClxyColors.CyanText)
                }
            }
        }
    }
}

@Composable
fun InAppUpdateBanner(
    updateInfo: AppUpdateInfo,
    onOpenUpdate: () -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xEB0F1B38), Color(0xEB151238)),
                ),
            )
            .border(1.dp, ClxyColors.CyanGlow.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .clickable(onClick = onOpenUpdate)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
            .testTag("in_app_update_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = Icons.Rounded.SystemUpdate,
                contentDescription = null,
                tint = ClxyColors.CyanGlow,
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    text = "Update available: ${updateInfo.latestTag}",
                    color = ClxyColors.TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Tap to view release notes and download",
                    color = ClxyColors.CyanText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(
            onClick = onDismissBanner,
            modifier = Modifier
                .size(48.dp)
                .testTag("in_app_update_banner_dismiss"),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Dismiss update banner",
                tint = ClxyColors.TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
