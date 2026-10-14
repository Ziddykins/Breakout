package com.ziddy.breakout.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ziddy.breakout.AppViewModel
import com.ziddy.breakout.ui.theme.AppColors
import com.ziddy.breakout.ui.theme.Mono

@Composable
fun SettingsAuditScreen(viewModel: AppViewModel) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()

	// Local recommended defaults mirrored from ViewModel
	val secure = listOf(
		"lock_screen_allow_private_notifications" to "0",
		"lock_screen_allow_private_notifications_when_unsecure" to "0",
		"clipboard_show_access_notifications" to "1",
		"face_stay_on_lock_screen" to "0",
		"speak_password" to "0",
		"rampart_main_switch_enabled" to "1",
		"rampart_strict_protection_switch_enabled" to "1",
		"rampart_blocked_adb_cmd" to "1",
		"rampart_blocked_unknown_apps" to "1",
		"rampart_enabled_message_guard" to "1",
		"theft_protection_default_on" to "1",
		"wifi_ap_smart_hotspot_connection" to "0",
		"touch_and_hold_to_search" to "0",
	)

	val system = listOf(
		"screen_off_timeout" to "15000",
		"nearby_scanning_enabled" to "0",
		"direct_share" to "0",
		"aod_mode" to "0",
		"mcf_continuity" to "0",
		"multi_control_enabled" to "0",
	)

	val global = listOf(
		"wifi_p2p_device_name" to "Denied",
		"thread_enabled" to "0",
		"wifi_display_on" to "0",
		"heads_up_notifications_enabled" to "0",
		"wifi_wakeup_enabled" to "0",
		"sem_wifi_carrier_network_offload_enabled" to "0",
		"wifi_networks_available_notification_on" to "0",
		"wifi_scan_always_enabled" to "0",
		"ble_scan_always_enabled" to "0",
		"bluetooth_scan_always_enabled" to "0",
		"activity_starts_logging_enabled" to "0",
		"package_verifier_user_consent" to "0",
		"upload_apk_enable" to "0",
		"send_action_app_error" to "0",
		"add_users_when_locked" to "0",
		"user_switcher_enabled" to "0",
	)

	// Editable state
	val secureState = remember { mutableStateMapOf<String, String>().apply { secure.forEach { put(it.first, it.second) } } }
	val systemState = remember { mutableStateMapOf<String, String>().apply { system.forEach { put(it.first, it.second) } } }
	val globalState = remember { mutableStateMapOf<String, String>().apply { global.forEach { put(it.first, it.second) } } }

	// Short descriptions for settings
	val descriptions = mapOf(
		"lock_screen_allow_private_notifications" to "Allow private content to show on lock screen (recommended: 0).",
		"lock_screen_allow_private_notifications_when_unsecure" to "Allow private notifications when device is not secure.",
		"clipboard_show_access_notifications" to "Show notifications when apps access the clipboard.",
		"face_stay_on_lock_screen" to "Keep face unlock active on the lock screen.",
		"speak_password" to "Speak password aloud for accessibility (disable for security).",
		"rampart_main_switch_enabled" to "Samsung Rampart security main switch.",
		"rampart_strict_protection_switch_enabled" to "Enable strict protection in Rampart.",
		"rampart_blocked_adb_cmd" to "Block certain ADB commands for security.",
		"rampart_blocked_unknown_apps" to "Block unknown apps via Rampart.",
		"rampart_enabled_message_guard" to "Enable message guard to protect messages.",
		"theft_protection_default_on" to "Default theft protection state.",
		"wifi_ap_smart_hotspot_connection" to "Control smart hotspot connection behavior.",
		"touch_and_hold_to_search" to "Trigger search with touch-and-hold gesture.",
		"screen_off_timeout" to "Screen timeout in milliseconds.",
		"nearby_scanning_enabled" to "Allow nearby device scanning.",
		"direct_share" to "Enable direct share targets.",
		"aod_mode" to "Always-on display mode.",
		"mcf_continuity" to "Samsung multi-control continuity feature.",
		"multi_control_enabled" to "Allow multi-control features.",
		"wifi_p2p_device_name" to "Wi-Fi P2P device name / policy.",
		"thread_enabled" to "Thread networking enabled.",
		"wifi_display_on" to "Wi-Fi display / Miracast enablement.",
		"heads_up_notifications_enabled" to "Heads-up notifications enablement.",
		"wifi_wakeup_enabled" to "Allow Wi-Fi to wake device for networks.",
		"sem_wifi_carrier_network_offload_enabled" to "Carrier offload for Wi-Fi networks.",
		"wifi_networks_available_notification_on" to "Notify when Wi-Fi networks are available.",
		"wifi_scan_always_enabled" to "Allow background Wi-Fi scanning.",
		"ble_scan_always_enabled" to "Allow background BLE scanning.",
		"bluetooth_scan_always_enabled" to "Allow background Bluetooth scanning.",
		"activity_starts_logging_enabled" to "Log activity starts.",
		"package_verifier_user_consent" to "Package verifier user consent setting.",
		"upload_apk_enable" to "Allow APK uploads.",
		"send_action_app_error" to "Send app error actions.",
		"add_users_when_locked" to "Allow adding new users when device locked.",
		"user_switcher_enabled" to "Enable user switcher UI.",
	)

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
				Text("SETTINGS AUDIT", color = AppColors.TextPrimary, fontFamily = Mono)
				Spacer(modifier = Modifier.weight(1f))
				IconButton(onClick = { viewModel.startSettingsAudit() }, enabled = !state.isSettingsScanning) {
					Icon(Icons.Rounded.Refresh, contentDescription = "Run settings audit")
				}
			}

			LazyColumn(
				modifier = Modifier.fillMaxSize(),
				contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp),
			) {
				item {
					Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
						Column(Modifier.padding(12.dp)) {
							Text("SECURE NAMESPACE", fontFamily = Mono)
							secure.forEach { (key, recommended) ->
								Column(Modifier.padding(top = 8.dp)) {
									OutlinedTextField(
										value = secureState[key] ?: recommended,
										onValueChange = { secureState[key] = it },
										label = { Text(key) },
										modifier = Modifier.fillMaxWidth(),
									)
									Text("Recommended: $recommended", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
									Text(descriptions[key] ?: "", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
								}
							}
						}
					}
				}

				item {
					Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
						Column(Modifier.padding(12.dp)) {
							Text("SYSTEM NAMESPACE", fontFamily = Mono)
							system.forEach { (key, recommended) ->
								Column(Modifier.padding(top = 8.dp)) {
									OutlinedTextField(
										value = systemState[key] ?: recommended,
										onValueChange = { systemState[key] = it },
										label = { Text(key) },
										modifier = Modifier.fillMaxWidth(),
									)
									Text("Recommended: $recommended", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
								}
							}
						}
					}
				}

				item {
					Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
						Column(Modifier.padding(12.dp)) {
							Text("GLOBAL NAMESPACE", fontFamily = Mono)
							global.forEach { (key, recommended) ->
								Column(Modifier.padding(top = 8.dp)) {
									OutlinedTextField(
										value = globalState[key] ?: recommended,
										onValueChange = { globalState[key] = it },
										label = { Text(key) },
										modifier = Modifier.fillMaxWidth(),
									)
									Text("Recommended: $recommended", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
								}
							}
						}
					}
				}

				item {
					Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
						Button(onClick = {
							// reset local states to recommended
							secure.forEach { secureState[it.first] = it.second }
							system.forEach { systemState[it.first] = it.second }
							global.forEach { globalState[it.first] = it.second }
							viewModel.resetSettingsToRecommended()
						}) {
							Text("Reset")
						}
						Spacer(modifier = Modifier.size(8.dp))
						Button(onClick = {
							viewModel.startSettingsAudit(secureState.toMap(), systemState.toMap(), globalState.toMap())
						}) {
							Text("Scan")
						}
					}
				}

				item {
					Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
						Column(Modifier.padding(12.dp)) {
							Text("Output", fontFamily = Mono)
							(state.settingsAuditOutput).forEach { line ->
								Text(line, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
							}
						}
					}
				}
			}
		}
	}
}
