package com.example.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.AppRoutingConfig
import com.example.CoreService
import com.example.InstalledAppItem
import com.example.core.ClxyViewModel
import com.example.ui.theme.ClxyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BypassAppsDialog(
    viewModel: ClxyViewModel,
    onDismiss: () -> Unit,
) {
    var config by remember { mutableStateOf<AppRoutingConfig?>(null) }
    var apps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    val icons = remember { mutableStateMapOf<String, Bitmap?>() }

    LaunchedEffect(Unit) {
        val loadedConfig = withContext(Dispatchers.IO) { viewModel.loadAppRouting() }
        val installed = withContext(Dispatchers.IO) { viewModel.installedApps() }
        config = loadedConfig
        apps = installed
        loading = false
    }

    val cfg = config
    val installedSet = remember(apps) { apps.map { it.packageName }.toSet() }
    val filteredApps = remember(apps, search, cfg) {
        val q = search.trim().lowercase()
        val chosen = cfg?.chosen ?: emptySet()
        apps
            .filter {
                q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
            .sortedWith(
                compareByDescending<InstalledAppItem> { chosen.contains(it.packageName) }
                    .thenBy { it.label.lowercase() },
            )
    }

    fun persist(next: AppRoutingConfig) {
        config = next
        viewModel.saveAppRouting(next, apply = CoreService.on && CoreService.usesVpn(viewModel.getApplication()))
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
                            text = "App Split Tunneling (TUN)",
                            color = ClxyColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Choose apps that bypass or exclusively use CLXY VPN",
                            color = ClxyColors.TextMuted,
                            fontSize = 11.sp,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = ClxyColors.TextPrimary)
                    }
                }

                if (loading || cfg == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ClxyColors.CyanGlow)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        // Exclude vs Only mode selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ClxyColors.CardSurface)
                                .border(1.dp, ClxyColors.GlassBorder, RoundedCornerShape(10.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(false to "Bypass selected", true to "VPN only for selected").forEach { (only, title) ->
                                val selected = cfg.onlyChosen == only
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) ClxyColors.BlueDeep.copy(alpha = 0.55f) else Color.Transparent)
                                        .clickable { persist(cfg.copy(onlyChosen = only)) }
                                        .padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = title,
                                        color = if (selected) Color.White else ClxyColors.TextMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }

                        if (!cfg.onlyChosen && cfg.suggested.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val missingCount = cfg.suggested.intersect(installedSet).minus(cfg.bypass).size
                            OutlinedButton(
                                onClick = {
                                    val (next, added) = cfg.checkAllSuggested(installedSet)
                                    persist(next)
                                    viewModel.showToast(if (added > 0) "Added $added regional bypass apps" else "All regional apps already bypassed")
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = if (missingCount > 0) "Select all regional bypass apps ($missingCount)" else "Regional bypass apps selected",
                                    color = ClxyColors.CyanText,
                                    fontSize = 12.sp,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            label = { Text("Filter installed apps") },
                            singleLine = true,
                            colors = clxyTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bypass_apps_search"),
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                LaunchedEffect(app.packageName) {
                                    if (!icons.containsKey(app.packageName)) {
                                        icons[app.packageName] = withContext(Dispatchers.IO) {
                                            viewModel.appIconBitmap(app.packageName)
                                        }
                                    }
                                }
                                val checked = cfg.isChecked(app.packageName)
                                val suggested = !cfg.onlyChosen && cfg.suggested.contains(app.packageName)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ClxyColors.CardSurface)
                                        .border(
                                            1.dp,
                                            if (checked) ClxyColors.CyanBorder else ClxyColors.GlassBorder,
                                            RoundedCornerShape(10.dp),
                                        )
                                        .clickable { persist(cfg.toggle(app.packageName, !checked)) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val bmp = icons[app.packageName]
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = app.label,
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                        )
                                    } else {
                                        Icon(
                                            Icons.Rounded.Apps,
                                            contentDescription = null,
                                            tint = ClxyColors.TextMuted,
                                            modifier = Modifier.size(34.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.label,
                                            color = ClxyColors.TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = if (suggested) "${app.packageName} • Regional" else app.packageName,
                                            color = if (suggested) ClxyColors.CyanText else ClxyColors.TextDim,
                                            fontSize = 10.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { persist(cfg.toggle(app.packageName, it)) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = ClxyColors.CyanGlow,
                                            uncheckedColor = ClxyColors.TextMuted,
                                            checkmarkColor = ClxyColors.BgTop,
                                        ),
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = ClxyColors.BlueDeep),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp),
                        ) {
                            Text("Done", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
