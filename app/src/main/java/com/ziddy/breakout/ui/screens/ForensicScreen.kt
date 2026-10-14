package com.ziddy.breakout.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ziddy.breakout.AppUiState
import com.ziddy.breakout.AppViewModel
import com.ziddy.breakout.model.ForensicReport
import com.ziddy.breakout.model.ForensicUser
import com.ziddy.breakout.model.ShizukuState
import com.ziddy.breakout.ui.theme.AppColors
import com.ziddy.breakout.ui.theme.Mono

@Composable
fun ForensicScreen(viewModel: AppViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val shizukuState by viewModel.shizukuState.collectAsStateWithLifecycle()
    val report = state.forensicReport

    LaunchedEffect(shizukuState, report, state.isForensicScanning) {
        val shouldCollect = shizukuState == ShizukuState.READY &&
            !state.isForensicScanning &&
            (report == null || report.accountEvents.isEmpty() || report.sourceStatus["Shizuku"] == "Not connected")
        if (shouldCollect) {
            viewModel.startForensicScan()
        }
    }

    Surface(Modifier.fillMaxSize(), color = AppColors.Background) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Security, contentDescription = null, tint = AppColors.Amber, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("forensic", color = AppColors.TextPrimary, fontFamily = Mono, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = viewModel::startForensicScan, enabled = !state.isForensicScanning) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh forensic data", tint = AppColors.TextSecondary)
                    }
                }
                Text("Trace control signals, users, and accounts.", color = AppColors.TextSecondary, fontSize = 14.sp)
            }

            if (state.isForensicScanning) {
                StatusSection("COLLECTING", state.forensicPhase.ifBlank { "Reading read-only system sources…" }, AppColors.Amber)
            }

            if (report == null) {
                EmptyForensicState(shizukuState, onCollect = viewModel::startForensicScan)
            } else {
                ForensicSections(state, report)
            }
        }
    }
}

@Composable
private fun EmptyForensicState(shizukuState: ShizukuState, onCollect: () -> Unit) {
    val body = when (shizukuState) {
        ShizukuState.READY -> "Collect read-only account, profile, and device-policy data from this phone."
        ShizukuState.NO_PERMISSION -> "Shizuku is running but Breakout does not have shell permission yet."
        ShizukuState.NOT_RUNNING -> "Connect Shizuku to inspect system account and policy registries."
    }
    SurfaceSection("FORENSIC COLLECTION") {
        Text(body, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
        Spacer(Modifier.size(12.dp))
        Button(
            onClick = onCollect,
            enabled = shizukuState == ShizukuState.READY,
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Amber, contentColor = AppColors.Background),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text("Collect read-only data", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ForensicSections(state: AppUiState, report: ForensicReport) {
    SurfaceSection("CONTROL SIGNALS") {
        SignalRow("Device owner", report.deviceOwner ?: "None reported", report.deviceOwner != null)
        SignalRow("Profile owner", report.profileOwner ?: "None reported", report.profileOwner != null)
        if (report.activeAdmins.isNotEmpty()) {
            Text("Active administrators", color = AppColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            report.activeAdmins.forEach { admin ->
                Text(admin, color = AppColors.Amber, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        } else {
            SignalRow("Active administrators", "None reported", false)
        }
        Text(
            "These packages are control signals, not proof of who installed or controls the phone.",
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    SurfaceSection("ATTRIBUTION SIGNALS") {
        if (report.attributionSignals.isEmpty()) {
            Text("No package-origin metadata was exposed by the read-only package dump.", color = AppColors.TextSecondary, fontSize = 13.sp)
        } else {
            report.attributionSignals.forEach { signal ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(signal.packageName, color = AppColors.TextPrimary, fontFamily = Mono, fontSize = 12.sp)
                    signal.installer?.let {
                        Text("installer: $it", color = AppColors.TextSecondary, fontSize = 12.sp)
                    }
                    signal.versionName?.let {
                        Text("version: $it", color = AppColors.TextSecondary, fontSize = 12.sp)
                    }
                    signal.signingInfo?.let {
                        Text("signing: ${it.take(140)}", color = AppColors.TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }

    SurfaceSection("USERS & PROFILES") {
        if (report.users.isEmpty()) {
            Text("No user records were returned.", color = AppColors.TextSecondary, fontSize = 13.sp)
        } else {
            report.users.forEach { UserRow(it) }
        }
    }

    SurfaceSection("ACCOUNTS") {
        if (report.accounts.isEmpty()) {
            Text("No account records were returned.", color = AppColors.TextSecondary, fontSize = 13.sp)
        } else {
            report.accounts.forEach { account ->
                Column(Modifier.padding(vertical = 5.dp)) {
                    Text(account.name, color = AppColors.TextPrimary, fontSize = 14.sp)
                    Text(
                        "${account.type} • user ${account.userId ?: "unknown"}",
                        color = AppColors.TextSecondary,
                        fontFamily = Mono,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }

    SurfaceSection("AUTHENTICATOR PACKAGES") {
        if (report.authenticatorPackages.isEmpty()) {
            Text("No authenticator package names were exposed.", color = AppColors.TextSecondary, fontSize = 13.sp)
        } else {
            report.authenticatorPackages.forEach { packageName ->
                Text(packageName, color = AppColors.TextPrimary, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.padding(vertical = 3.dp))
            }
        }
    }

    SurfaceSection("ACCOUNT ACTIVITY") {
        if (report.accountEvents.isEmpty()) {
            Text("No account lifecycle events were surfaced in the raw dump.", color = AppColors.TextSecondary, fontSize = 13.sp)
        } else {
            Column(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AccountActivityTableHeader()
                report.accountEvents.forEach { event ->
                    AccountActivityTableRow(event)
                }
            }
        }
    }

    SurfaceSection("COLLECTION STATUS") {
        report.sourceStatus.forEach { (source, status) ->
            SignalRow(source, status, status == "Collected")
        }
        state.forensicErrors.forEach { error ->
            Text(error, color = AppColors.Red, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        }
        Text(
            "Collected data comes from read-only shell dumps. Android and device manufacturers may omit or rename fields.",
            color = AppColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun AccountActivityTableHeader() {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minWidth = 640.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("ID", "Action", "Time", "UID", "Table", "Key").forEach { label ->
            Text(
                text = label,
                color = AppColors.Amber,
                fontFamily = Mono,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .width(56.dp),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun AccountActivityTableRow(event: com.ziddy.breakout.model.ForensicAccountEvent) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minWidth = 640.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            event.accountId?.toString() ?: "-",
            event.actionType ?: event.event,
            event.timestamp ?: "-",
            event.uid?.toString() ?: "-",
            event.tableName ?: event.source ?: "-",
            event.key ?: "-",
        ).forEach { value ->
            Text(
                text = value,
                color = AppColors.TextPrimary,
                fontFamily = Mono,
                fontSize = 10.sp,
                modifier = Modifier
                    .weight(1f)
                    .width(56.dp),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun UserRow(user: ForensicUser) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Text(user.label, color = AppColors.TextPrimary, fontSize = 14.sp)
        Text("user ${user.userId}", color = AppColors.TextSecondary, fontFamily = Mono, fontSize = 11.sp)
    }
}

@Composable
private fun SignalRow(label: String, value: String, highlighted: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AppColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = if (highlighted) AppColors.Amber else AppColors.TextPrimary, fontFamily = Mono, fontSize = 12.sp)
    }
}

@Composable
private fun StatusSection(title: String, body: String, color: androidx.compose.ui.graphics.Color) {
    SurfaceSection(title) {
        Text(body, color = color, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SurfaceSection(title: String, content: @Composable () -> Unit) {
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
