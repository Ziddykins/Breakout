package com.ziddy.breakout.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.ziddy.breakout.AppViewModel
import com.ziddy.breakout.ui.components.ScreenHeader
import com.ziddy.breakout.ui.theme.AppColors
import com.ziddy.breakout.ui.theme.Mono

@Composable
fun AppSettingsScreen(viewModel: AppViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize(), color = AppColors.Background) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScreenHeader(
                title = "App settings",
                icon = Icons.Rounded.Settings,
                horizontalPadding = 0.dp,
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Skip console risk phrase",
                            color = AppColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Allow console commands without typing “i understand the risks”. Commands still require a connected Shizuku shell and can damage the device.",
                            color = AppColors.TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 5.dp, end = 12.dp),
                        )
                    }
                    Switch(
                        checked = state.disableConsoleRiskAcknowledgment,
                        onCheckedChange = viewModel::setConsoleRiskAcknowledgmentDisabled,
                    )
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Monitor installed apps",
                                color = AppColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (state.isPackageMonitoringSetup) {
                                    "Hashing installed APKs to create the initial baseline…"
                                } else {
                                    "Record APK SHA-256 hashes and signing-certificate fingerprints, then check package installs and updates."
                                },
                                color = AppColors.TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 5.dp, end = 12.dp),
                            )
                        }
                        Switch(
                            checked = state.packageMonitoringEnabled,
                            enabled = !state.isPackageMonitoringSetup,
                            onCheckedChange = viewModel::setPackageMonitoringEnabled,
                        )
                    }
                    if (state.isPackageMonitoringSetup) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                    }
                    Text(
                        "Hashes are compared with the baseline captured on this device; a normal update changes the APK hash too. Newly installed apps get a first-seen baseline after installation, not a trusted original. Android does not provide a trusted public per-version Google Play APK hash for this check, so this cannot prove that an installed file matches Play. The signing certificate is checked locally. Package broadcasts are monitored without a permanently running service.",
                        color = AppColors.TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    state.packageMonitoringMessage?.let { message ->
                        Text(
                            message,
                            color = when {
                                "could not" in message.lowercase() || "failed" in message.lowercase() -> AppColors.Red
                                "changed" in message.lowercase() -> AppColors.Amber
                                else -> AppColors.Green
                            },
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
