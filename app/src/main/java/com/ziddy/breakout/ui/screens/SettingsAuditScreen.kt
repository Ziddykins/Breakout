package com.ziddy.breakout.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ziddy.breakout.AppViewModel
import com.ziddy.breakout.data.HardeningRecommendation
import com.ziddy.breakout.data.HardeningRecommendations
import com.ziddy.breakout.model.ShizukuState
import com.ziddy.breakout.ui.components.AmberOutlineButton
import com.ziddy.breakout.ui.components.ScreenHeader
import com.ziddy.breakout.ui.components.TreeGroupHeader
import com.ziddy.breakout.ui.theme.AppColors
import com.ziddy.breakout.ui.theme.Mono

@Composable
fun SettingsAuditScreen(viewModel: AppViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val shizukuState by viewModel.shizukuState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var showSetAllConfirmation by remember { mutableStateOf(false) }
    val expandedNamespaces = remember { mutableStateMapOf<String, Boolean>() }
    val pendingChanges = remember(state.deviceSettings) {
        HardeningRecommendations.pendingChanges(state.deviceSettings)
    }

    LaunchedEffect(Unit) {
        if (state.deviceSettings.isEmpty() && !state.isSettingsScanning) {
            viewModel.startSettingsAudit()
        }
    }

    Surface(Modifier.fillMaxSize(), color = AppColors.Background) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Harden",
                icon = Icons.Rounded.Tune,
                actions = {
                    IconButton(
                        onClick = viewModel::startSettingsAudit,
                        enabled = !state.isSettingsScanning,
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Refresh device settings",
                            tint = AppColors.NeonBlue,
                        )
                    }
                },
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Device-specific settings", fontWeight = FontWeight.Bold)
                            Text(
                                "Breakout reads the settings keys actually exposed on this device. Only recognized hardening recommendations can be applied; other discovered values remain read-only.",
                                color = AppColors.TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                            if (state.isSettingsScanning) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                )
                            }
                            if (pendingChanges.isNotEmpty()) {
                                AmberOutlineButton(
                                    onClick = { showSetAllConfirmation = true },
                                    enabled = !state.isSettingsActionRunning &&
                                        !state.isSettingsScanning &&
                                        shizukuState == ShizukuState.READY,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                ) {
                                    Text("Set all recommended (${pendingChanges.size})")
                                }
                            } else if (state.deviceSettings.isNotEmpty() && !state.isSettingsScanning) {
                                Text(
                                    "No available settings differ from their recommendations.",
                                    color = AppColors.Green,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 10.dp),
                                )
                            }
                            state.settingsScanError?.let { error ->
                                Text(
                                    error,
                                    color = AppColors.Red,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            state.settingsActionMessage?.let { message ->
                                val isError = listOf(
                                    "not changed",
                                    "rejected",
                                    "could not",
                                    "did not",
                                    "failed",
                                    "command errors",
                                ).any { message.contains(it, ignoreCase = true) }
                                Text(
                                    message,
                                    color = if (isError) AppColors.Red else AppColors.Amber,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            androidx.compose.material3.OutlinedTextField(
                                value = query,
                                onValueChange = {
                                    query = it
                                    expandedNamespaces.clear()
                                },
                                label = { Text("Filter settings") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                singleLine = true,
                            )
                        }
                    }
                }

                listOf("secure", "system", "global").forEach { namespace ->
                    val allSettings = state.deviceSettings[namespace].orEmpty()
                    val settings = allSettings.filter { (key, value) ->
                        query.isBlank() ||
                            key.contains(query, ignoreCase = true) ||
                            value.contains(query, ignoreCase = true)
                    }
                    if (settings.isNotEmpty()) {
                        val expanded = expandedNamespaces[namespace] ?: query.isNotBlank()
                        item(key = "namespace:$namespace") {
                            NamespaceHeader(
                                namespace = namespace,
                                count = settings.size,
                                expanded = expanded,
                                warning = pendingChanges.any { it.namespace == namespace },
                                onClick = { expandedNamespaces[namespace] = !expanded },
                            )
                        }
                        if (expanded) {
                            items(settings.toList(), key = { "$namespace:${it.first}" }) { (key, value) ->
                                val recommendation = HardeningRecommendations.byNamespace[namespace]?.get(key)
                                SettingRow(
                                    key = key,
                                    value = value,
                                    recommendation = recommendation,
                                    isApplying = state.isSettingsActionRunning,
                                    isShellReady = shizukuState == ShizukuState.READY,
                                    onApply = {
                                        viewModel.applyDeviceSetting(
                                            namespace,
                                            key,
                                            recommendation?.value.orEmpty(),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                if (!state.isSettingsScanning && state.settingsScanError == null &&
                    state.deviceSettings.values.all { it.isEmpty() }
                ) {
                    item {
                        Text(
                            "No settings were returned. Start Shizuku and refresh to read this device's settings namespaces.",
                            color = AppColors.TextSecondary,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
        }
    }

    if (showSetAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showSetAllConfirmation = false },
            title = { Text("Apply all recommendations?") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "${pendingChanges.size} setting${if (pendingChanges.size == 1) "" else "s"} will be changed. " +
                            "Only recognized settings currently exposed by this device are included.",
                        color = AppColors.TextPrimary,
                    )
                    val changesByNamespace = pendingChanges.groupBy { it.namespace }
                    listOf("secure", "system", "global").forEach { namespace ->
                        val changes = changesByNamespace[namespace].orEmpty()
                        Text(
                            "${namespace.uppercase()} (${changes.size})",
                            color = AppColors.WarningAmber,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        if (changes.isEmpty()) {
                            val noChangeSummary = if (
                                HardeningRecommendations.byNamespace[namespace].isNullOrEmpty()
                            ) {
                                "No recommendations are currently defined for this namespace."
                            } else {
                                "No changes needed."
                            }
                            Text(
                                noChangeSummary,
                                color = AppColors.TextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        changes.forEach { change ->
                            Text(
                                "${change.key}: ${change.currentValue.ifBlank { "(empty)" }} → ${change.recommendedValue}",
                                color = AppColors.TextSecondary,
                                fontFamily = Mono,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    Text(
                        "Android or the device manufacturer may reject some changes. Breakout will verify the values afterward; rejected or unverified changes will be reported.",
                        color = AppColors.TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
            },
            confirmButton = {
                AmberOutlineButton(
                    onClick = {
                        showSetAllConfirmation = false
                        viewModel.applyAllRecommendedDeviceSettings()
                    },
                ) {
                    Text("Apply ${pendingChanges.size} changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetAllConfirmation = false }) {
                    Text("Cancel", color = AppColors.TextSecondary)
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.92f),
        )
    }
}

@Composable
private fun NamespaceHeader(
    namespace: String,
    count: Int,
    expanded: Boolean,
    warning: Boolean,
    onClick: () -> Unit,
) {
    TreeGroupHeader(
        title = "${namespace.uppercase()} NAMESPACE",
        countLabel = "$count settings",
        expanded = expanded,
        warning = warning,
        onClick = onClick,
    )
}

@Composable
private fun SettingRow(
    key: String,
    value: String,
    recommendation: HardeningRecommendation?,
    isApplying: Boolean,
    isShellReady: Boolean,
    onApply: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
        ) {
            Text(
                key,
                color = AppColors.TextPrimary,
                fontFamily = Mono,
                fontSize = 12.sp,
                maxLines = 2,
            )
            Text(
                "Current: ${value.ifBlank { "(empty)" }}",
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 2,
            )
            if (recommendation == null) {
                Text(
                    "No recommendation for this setting.",
                    color = AppColors.TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
                Spacer(Modifier.weight(1f))
            } else {
                Text(
                    recommendation.rationale,
                    color = AppColors.TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 5.dp),
                    maxLines = 3,
                )
                if (value != recommendation.value) {
                    Text(
                        "Not recommended: ${value.ifBlank { "(empty)" }} → ${recommendation.value}",
                        color = AppColors.Amber,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    AmberOutlineButton(
                        onClick = onApply,
                        enabled = !isApplying && isShellReady,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        Text("Apply recommendation")
                    }
                } else {
                    Text(
                        "Recommended value: ${recommendation.value}",
                        color = AppColors.Green,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 5.dp, bottom = 4.dp),
                    )
                }
            }
        }
    }
}
