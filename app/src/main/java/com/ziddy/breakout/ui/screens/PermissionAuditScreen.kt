package com.ziddy.breakout.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ziddy.breakout.AppViewModel
import com.ziddy.breakout.model.AppPermissionFinding
import com.ziddy.breakout.model.PermissionAuditReport
import com.ziddy.breakout.model.Severity
import com.ziddy.breakout.ui.theme.AppColors
import com.ziddy.breakout.ui.theme.Mono

@Composable
fun PermissionAuditScreen(viewModel: AppViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (state.permissionAuditReport == null && !state.isPermissionAuditScanning) {
            viewModel.startPermissionAudit()
        }
    }

    Surface(Modifier.fillMaxSize(), color = AppColors.Background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Key, contentDescription = null, tint = AppColors.Amber, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("PERMISSIONS", color = AppColors.TextPrimary, fontFamily = Mono, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = viewModel::startPermissionAudit, enabled = !state.isPermissionAuditScanning) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh permission audit", tint = AppColors.TextSecondary)
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.permissionAuditReport?.let { report ->
                    item { DeviceProfileCard(report) }
                    item {
                        AuditSection("AUDIT SUMMARY") {
                            Text(
                                "${report.scannedPackages} packages checked · ${report.findings.size} with listed sensitive permissions",
                                color = AppColors.TextPrimary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "Requested permissions are not proof of use or of an active capability. Granted status is shown where Android reports it.",
                                color = AppColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    for (severity in Severity.entries) {
                        val findings = report.findings.filter { it.severity == severity }
                        if (findings.isNotEmpty()) {
                            item(key = "severity-${severity.name}") {
                                Text(
                                    "${severity.label.uppercase()} · ${findings.size} app(s)",
                                    color = AppColors.forSeverity(severity),
                                    fontFamily = Mono,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                                )
                            }
                            items(findings, key = { it.packageName }) { finding ->
                                FindingCard(finding) {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${finding.packageName}")
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (report.findings.isEmpty()) {
                        item {
                            AuditSection("NO MATCHES") {
                                Text(
                                    "No installed packages requested the sensitive permissions defined by this audit on this Android version.",
                                    color = AppColors.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                } ?: item {
                    AuditSection("PERMISSION AUDIT") {
                        when {
                            state.isPermissionAuditScanning -> Text("Reading installed-app permission declarations…", color = AppColors.Amber)
                            state.permissionAuditError != null -> Text(state.permissionAuditError.orEmpty(), color = AppColors.Red)
                            else -> Text("Start a read-only audit of installed-app permission declarations.", color = AppColors.TextSecondary)
                        }
                        Spacer(Modifier.size(10.dp))
                        Button(
                            onClick = viewModel::startPermissionAudit,
                            enabled = !state.isPermissionAuditScanning,
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Amber, contentColor = AppColors.Background),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Run audit", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (state.permissionAuditError != null && state.permissionAuditReport != null) {
                    item {
                        Text(state.permissionAuditError.orEmpty(), color = AppColors.Red, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceProfileCard(report: PermissionAuditReport) {
    val device = report.device
    AuditSection("DEVICE PROFILE") {
        ProfileRow("Manufacturer", device.manufacturer)
        ProfileRow("Model", device.model)
        ProfileRow("Android", "${device.androidRelease} (API ${device.apiLevel})")
        ProfileRow("Security patch", device.securityPatch ?: "Not reported")
        ProfileRow("One UI", device.oneUiVersion ?: "Not reported")
        ProfileRow("Knox version", device.knoxVersion ?: "Not reported")
        Text(
            "One UI and Knox values are optional read-only system properties; no serial number or unique device identifier is collected.",
            color = AppColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun FindingCard(finding: AppPermissionFinding, onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(finding.label, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Text(finding.packageName, color = AppColors.TextSecondary, fontFamily = Mono, fontSize = 11.sp)
                }
                Text(
                    "SCORE ${finding.score}",
                    color = AppColors.forSeverity(finding.severity),
                    fontFamily = Mono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                if (finding.isSystem) "System app · review its role before changing permissions" else "Installed app",
                color = AppColors.TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            finding.permissions.forEach { permission ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(permission.label, color = AppColors.TextPrimary, fontSize = 13.sp)
                        Text(permission.name, color = AppColors.TextSecondary, fontFamily = Mono, fontSize = 9.sp)
                    }
                    Text(
                        if (permission.granted) "GRANTED" else "REQUESTED",
                        color = if (permission.granted) AppColors.Amber else AppColors.Grey,
                        fontFamily = Mono,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.padding(top = 10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.SurfaceHigh, contentColor = AppColors.TextPrimary),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Review in Android settings", fontSize = 12.sp)
                Spacer(Modifier.size(6.dp))
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = AppColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = AppColors.TextPrimary, fontFamily = Mono, fontSize = 12.sp)
    }
}

@Composable
private fun AuditSection(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AppColors.Surface,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = AppColors.Amber, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.size(8.dp))
            content()
        }
    }
}
