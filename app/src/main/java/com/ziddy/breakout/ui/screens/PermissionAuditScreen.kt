package com.ziddy.breakout.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ziddy.breakout.AppViewModel
import com.ziddy.breakout.model.AppPermissionFinding
import com.ziddy.breakout.model.PermissionAuditReport
import com.ziddy.breakout.model.Severity
import com.ziddy.breakout.model.ShizukuState
import com.ziddy.breakout.ui.components.AmberOutlineButton
import com.ziddy.breakout.ui.components.ScreenHeader
import com.ziddy.breakout.ui.theme.AppColors
import com.ziddy.breakout.ui.theme.Mono

@Composable
fun PermissionAuditScreen(viewModel: AppViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val shizukuState by viewModel.shizukuState.collectAsStateWithLifecycle()
    val shellReady = shizukuState == ShizukuState.READY
    val context = LocalContext.current
    val expandedSeverities = remember { mutableStateMapOf<Severity, Boolean>() }
    val expandedFindings = remember { mutableStateMapOf<String, Boolean>() }
    var permissionResultMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.permissionResultEvents.collect { message ->
            permissionResultMessage = message
        }
    }
    LaunchedEffect(Unit) {
        if (state.permissionAuditReport == null && !state.isPermissionAuditScanning) {
            viewModel.startPermissionAudit()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxSize(), color = AppColors.Background) {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader(
                    title = "Permissions",
                    icon = Icons.Rounded.Key,
                    subtitle = "Sensitive access audit",
                    actions = {
                        IconButton(
                            onClick = viewModel::startPermissionAudit,
                            enabled = !state.isPermissionAuditScanning,
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = "Refresh permission audit",
                                tint = AppColors.NeonBlue,
                            )
                        }
                    },
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    state.permissionAuditReport?.let { report ->
                        item {
                            AuditSection("AUDIT SUMMARY") {
                                Text(
                                    "${report.scannedPackages} packages checked · ${report.findings.size} with listed sensitive permissions",
                                    color = AppColors.TextPrimary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    "Requested permissions are not proof of use. Checkboxes try Android grant/revoke commands; Android may reject permissions that are signature-protected, fixed, or controlled by a special settings screen.",
                                    color = AppColors.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                        for (severity in Severity.entries) {
                            val findings = report.findings.filter { it.severity == severity }
                            if (findings.isNotEmpty()) {
                                val expanded = expandedSeverities[severity] ?: false
                                item(key = "severity-${severity.name}") {
                                    PermissionGroupHeader(
                                        title = severity.label.uppercase(),
                                        subtitle = "${findings.size} app(s)",
                                        expanded = expanded,
                                        tint = AppColors.forSeverity(severity),
                                        onClick = { expandedSeverities[severity] = !expanded },
                                    )
                                }
                                if (expanded) {
                                    items(findings, key = { it.packageName }) { finding ->
                                        FindingCard(
                                            finding = finding,
                                            expanded = expandedFindings[finding.packageName] ?: false,
                                            operationStatus = state.permissionOperationStatus,
                                            isAuditing = state.isPermissionAuditScanning,
                                            isShellReady = shellReady,
                                            onToggleExpanded = {
                                                expandedFindings[finding.packageName] =
                                                    !(expandedFindings[finding.packageName] ?: false)
                                            },
                                            onTogglePermission = viewModel::setPermissionGranted,
                                            onOpenSettings = {
                                                context.startActivity(
                                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                        data = "package:${finding.packageName}".toUri()
                                                    },
                                                )
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
                            AmberOutlineButton(
                                onClick = viewModel::startPermissionAudit,
                                enabled = !state.isPermissionAuditScanning,
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
        permissionResultMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { permissionResultMessage = null },
                title = {
                    Text(
                        if (message.startsWith("Could not") || message.contains("failed", ignoreCase = true)) {
                            "Permission update failed"
                        } else {
                            "Permission update"
                        },
                        color = AppColors.TextPrimary,
                    )
                },
                text = {
                    Text(
                        message,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState()),
                        color = AppColors.TextSecondary,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { permissionResultMessage = null }) {
                        Text("Close", color = AppColors.NeonBlue)
                    }
                },
                containerColor = AppColors.SurfaceHigh,
                properties = DialogProperties(usePlatformDefaultWidth = true),
            )
        }
    }
}

@Composable
fun DeviceProfileCard(report: PermissionAuditReport) {
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
private fun FindingCard(
	finding: AppPermissionFinding,
	expanded: Boolean,
	operationStatus: Map<String, String>,
	isAuditing: Boolean,
	isShellReady: Boolean,
	onToggleExpanded: () -> Unit,
	onTogglePermission: (String, String, Boolean) -> Unit,
	onOpenSettings: () -> Unit,
) {
	Card(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onToggleExpanded),
		colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
		shape = RoundedCornerShape(14.dp),
	) {
		Column(Modifier.padding(9.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Column(Modifier.weight(1f)) {
					Text(finding.label, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
					Text(finding.packageName, color = AppColors.TextSecondary, fontFamily = Mono, fontSize = 11.sp)
				}
				Text(
					"${finding.permissions.count { it.granted }} / ${finding.permissions.size} granted",
					color = AppColors.forSeverity(finding.severity),
					fontFamily = Mono,
					fontSize = 11.sp,
					fontWeight = FontWeight.Bold,
				)
			}
			if (expanded) {
				Text(
					if (finding.isSystem) "System app · some permissions are fixed by the OS or signer" else "Installed app",
					color = AppColors.TextSecondary,
					fontSize = 11.sp,
					modifier = Modifier.padding(top = 4.dp),
				)
				finding.permissions.forEachIndexed { index, permission ->
					val operationKey = "${finding.packageName}|${permission.name}"
					val status = operationStatus[operationKey]
					Row(
						Modifier
							.fillMaxWidth()
							.padding(top = 4.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						Checkbox(
							checked = permission.granted,
							enabled = !isAuditing && status != "in_progress" && isShellReady,
							onCheckedChange = { granted ->
								onTogglePermission(finding.packageName, permission.name, granted)
							},
							modifier = Modifier.scale(0.78f),
						)
						Column(Modifier.weight(1f)) {
							Text(permission.label, color = AppColors.TextPrimary, fontSize = 12.sp)
							Text(permission.name, color = AppColors.TextSecondary, fontFamily = Mono, fontSize = 9.sp)
						}
					}
					if (index < finding.permissions.lastIndex) {
						Box(
							Modifier
								.fillMaxWidth()
								.padding(start = 42.dp)
								.height(1.dp)
								.background(AppColors.Outline.copy(alpha = 0.55f)),
						)
					}
				}
				AmberOutlineButton(
					onClick = onOpenSettings,
					icon = Icons.AutoMirrored.Rounded.OpenInNew,
					modifier = Modifier.padding(top = 8.dp),
				) {
					Text("Review in Android settings", fontSize = 12.sp)
				}
			}
		}
	}
}

@Composable
private fun PermissionGroupHeader(
	title: String,
	subtitle: String,
	expanded: Boolean,
	tint: Color,
	onClick: () -> Unit,
) {
	Surface(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick),
		shape = RoundedCornerShape(10.dp),
		color = AppColors.Surface,
	) {
		Row(
			Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text(
				title,
				modifier = Modifier.weight(1f),
				color = tint,
				fontFamily = Mono,
				fontSize = 12.sp,
				fontWeight = FontWeight.Bold,
			)
			Text(subtitle, color = AppColors.TextSecondary, fontSize = 11.sp)
			Icon(
				if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
				contentDescription = if (expanded) "Collapse $title" else "Expand $title",
				tint = AppColors.TextSecondary,
				modifier = Modifier.padding(start = 8.dp),
			)
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
		Column(Modifier.padding(12.dp)) {
			Text(title, color = AppColors.WarningAmber, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
			Spacer(Modifier.size(8.dp))
			content()
		}
	}
}
