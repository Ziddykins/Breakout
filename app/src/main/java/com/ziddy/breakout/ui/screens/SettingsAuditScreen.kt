package com.ziddy.breakout.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ziddy.breakout.AppViewModel
import com.ziddy.breakout.ui.theme.AppColors
import com.ziddy.breakout.ui.theme.Mono

private val hardeningRecommendations = mapOf(
	"secure" to mapOf(
		"lock_screen_allow_private_notifications" to "0",
		"lock_screen_allow_private_notifications_when_unsecure" to "0",
		"speak_password" to "0",
		"add_users_when_locked" to "0",
		"rampart_main_switch_enabled" to "1",
		"rampart_strict_protection_switch_enabled" to "1",
		"rampart_blocked_adb_cmd" to "1",
		"rampart_blocked_unknown_apps" to "1",
		"rampart_enabled_message_guard" to "1",
		"theft_protection_default_on" to "1",
	),
	"system" to emptyMap(),
	"global" to mapOf(
		"add_users_when_locked" to "0",
		"wifi_scan_always_enabled" to "0",
		"ble_scan_always_enabled" to "0",
		"bluetooth_scan_always_enabled" to "0",
	),
)

@Composable
fun SettingsAuditScreen(viewModel: AppViewModel) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()
	var query by remember { mutableStateOf("") }
	val expandedNamespaces = remember { mutableStateMapOf<String, Boolean>() }

	LaunchedEffect(Unit) {
		if (state.deviceSettings.isEmpty() && !state.isSettingsScanning) {
			viewModel.startSettingsAudit()
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
				Icon(Icons.Rounded.Settings, contentDescription = null, tint = AppColors.Amber)
				Spacer(modifier = Modifier.size(8.dp))
				Text("HARDEN", color = AppColors.TextPrimary, fontFamily = Mono)
				Spacer(modifier = Modifier.weight(1f))
				IconButton(onClick = viewModel::startSettingsAudit, enabled = !state.isSettingsScanning) {
					Icon(Icons.Rounded.Refresh, contentDescription = "Refresh device settings")
				}
			}

			LazyColumn(
				modifier = Modifier.fillMaxSize(),
				contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp),
			) {
				item {
					Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
						Column(Modifier.padding(14.dp)) {
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
							state.settingsScanError?.let { error ->
								Text(error, color = AppColors.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
							}
							state.settingsActionMessage?.let { message ->
								Text(
									message,
									color = if (message.contains("not changed", ignoreCase = true) ||
										message.contains("rejected", ignoreCase = true) ||
										message.contains("could not", ignoreCase = true)
									) AppColors.Red else AppColors.Amber,
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
								onClick = { expandedNamespaces[namespace] = !expanded },
							)
						}
						if (expanded) {
							items(settings.toList(), key = { "$namespace:${it.first}" }) { (key, value) ->
								val recommended = hardeningRecommendations[namespace]?.get(key)
								SettingRow(
									namespace = namespace,
									key = key,
									value = value,
									recommendedValue = recommended,
									isApplying = state.isSettingsActionRunning,
									onApply = { viewModel.applyDeviceSetting(namespace, key, recommended.orEmpty()) },
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
}

@Composable
private fun NamespaceHeader(
	namespace: String,
	count: Int,
	expanded: Boolean,
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
			Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text(
				"${namespace.uppercase()} NAMESPACE",
				modifier = Modifier.weight(1f),
				color = AppColors.Amber,
				fontFamily = Mono,
				fontSize = 11.sp,
				fontWeight = FontWeight.Bold,
			)
			Text("$count settings", color = AppColors.TextSecondary, fontSize = 11.sp)
			Icon(
				if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
				contentDescription = if (expanded) "Collapse $namespace settings" else "Expand $namespace settings",
				tint = AppColors.TextSecondary,
				modifier = Modifier.padding(start = 8.dp),
			)
		}
	}
}

@Composable
private fun SettingRow(
	namespace: String,
	key: String,
	value: String,
	recommendedValue: String?,
	isApplying: Boolean,
	onApply: () -> Unit,
) {
	Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
		Column(Modifier.padding(12.dp)) {
			Text(key, color = AppColors.TextPrimary, fontFamily = Mono, fontSize = 12.sp)
			Text("Current: ${value.ifBlank { "(empty)" }}", color = AppColors.TextSecondary, fontSize = 12.sp)
			if (recommendedValue != null) {
				Text(
					"Security recommendation: $recommendedValue",
					color = AppColors.Green,
					fontSize = 11.sp,
					modifier = Modifier.padding(top = 4.dp),
				)
				if (value != recommendedValue) {
					Button(
						onClick = onApply,
						enabled = !isApplying,
						modifier = Modifier.padding(top = 6.dp),
					) {
						Text("Apply recommendation")
					}
				} else {
					Text("Already matches", color = AppColors.Green, fontSize = 11.sp)
				}
			}
		}
	}
}
