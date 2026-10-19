package com.ziddy.breakout.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.ziddy.breakout.AppViewModel
import com.ziddy.breakout.data.PackageIntegrityRecord
import com.ziddy.breakout.data.PackageIntegrityStatus
import com.ziddy.breakout.model.PackageAction
import com.ziddy.breakout.model.ShizukuState
import com.ziddy.breakout.ui.components.AmberOutlineButton
import com.ziddy.breakout.ui.components.ScreenHeader
import com.ziddy.breakout.ui.components.TreeGroupHeader
import com.ziddy.breakout.ui.theme.AppColors

@Composable
fun PackagesScreen(viewModel: AppViewModel) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()
	val shizukuState by viewModel.shizukuState.collectAsStateWithLifecycle()
	val shellReady = shizukuState == ShizukuState.READY
	val selected = remember { mutableStateListOf<String>() }
	var query by remember { mutableStateOf("") }
	var showConfirm by remember { mutableStateOf(false) }
	var pendingPackageAction by remember { mutableStateOf<Pair<String, PackageAction>?>(null) }
	val expandedSections = remember { mutableStateMapOf<String, Boolean>() }
	val expandedVendors = remember { mutableStateMapOf<String, Boolean>() }
	val snackbarHostState = remember { SnackbarHostState() }
	val scope = rememberCoroutineScope()
	val hasIntegrityWarning: (String) -> Boolean = { packageName ->
		when (state.packageIntegrityRecords[packageName]?.status) {
			PackageIntegrityStatus.APK_CHANGED,
			PackageIntegrityStatus.SIGNER_CHANGED,
			PackageIntegrityStatus.CHECK_FAILED -> true
			else -> false
		}
	}
	val filteredSystemPackages = remember(state.systemPackageList, query) {
		filterPackagesByVendor(state.systemPackageList, query)
	}
	val filteredUserPackages = remember(state.userPackageList, query) {
		filterPackagesByVendor(state.userPackageList, query)
	}

	LaunchedEffect(shellReady) {
		viewModel.refreshPackageIntegrity()
		if (shellReady && state.packageList.isEmpty() && !state.isPackageListing) {
			viewModel.listPackages()
		}
	}

	Surface(Modifier.fillMaxSize(), color = AppColors.Background) {
		Column(Modifier.fillMaxSize()) {
			ScreenHeader(
				title = "Packages",
				icon = Icons.Rounded.Apps,
				subtitle = "Installed applications",
				actions = {
					IconButton(
						onClick = { viewModel.listPackages() },
						enabled = !state.isPackageListing && shellReady,
					) {
						Icon(
							Icons.Rounded.Refresh,
							contentDescription = "Refresh packages",
							tint = if (shellReady) AppColors.NeonBlue else AppColors.TextSecondary,
						)
					}
				},
			)

			LazyColumn(
				modifier = Modifier.weight(1f),
				contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 3.dp),
				verticalArrangement = Arrangement.spacedBy(4.dp),
			) {
				// progress
				if (state.packageOperationProgress in 1..99) {
					item {
						LinearProgressIndicator(
							progress = { state.packageOperationProgress / 100f },
							modifier = Modifier.fillMaxWidth(),
						)
					}
				}

				item {
					Card(
						shape = RoundedCornerShape(16.dp),
						colors = CardDefaults.cardColors(containerColor = AppColors.Surface.copy(alpha = 0.92f)),
					) {
						Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
							OutlinedTextField(
								value = query,
								onValueChange = {
									query = it
									expandedVendors.clear()
									expandedSections.keys.forEach { section -> expandedSections[section] = true }
								},
								placeholder = { Text("Filter installed packages") },
								leadingIcon = {
									Icon(Icons.Rounded.Search, contentDescription = null, tint = AppColors.TextSecondary)
								},
								modifier = Modifier.fillMaxWidth(),
								singleLine = true,
							)
							Text(
								"Select packages to freeze. Tap the menu on any package for more actions.",
								color = AppColors.TextSecondary,
								fontSize = 11.sp,
							)
							if (state.packageMonitoringEnabled) {
								Text(
									"Local hash baseline · reinstalling Breakout may change its APK hash.",
									color = AppColors.TextSecondary,
									fontSize = 9.sp,
								)
							}
							state.packageListingError?.let { error ->
								Text("Could not load packages: $error", color = AppColors.Red)
							}
						}
					}
				}

				val hasSamsung = state.packageList.any { it.startsWith("com.samsung") }
				if (hasSamsung) {
					item {
						Card(
							shape = RoundedCornerShape(16.dp),
							colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
						) {
							Column(Modifier.padding(10.dp)) {
								Text(
									"Detected Samsung packages",
									color = AppColors.TextPrimary,
									fontWeight = FontWeight.SemiBold,
									fontSize = 13.sp,
								)
								viewModel.samsungGroups.forEach { (groupName, groupPkgs) ->
									val present = groupPkgs.filter { state.packageList.contains(it) }
									if (present.isNotEmpty()) {
										Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
											var checked by remember { mutableStateOf(false) }
											Checkbox(checked = checked, enabled = shellReady, onCheckedChange = {
												checked = it
												if (it) selected.addAll(present.filter { !selected.contains(it) }) else selected.removeAll(present)
											})
											Spacer(modifier = Modifier.size(8.dp))
											Text(
												groupName.replace('_', ' ').uppercase(),
												color = AppColors.TextPrimary,
												fontSize = 12.sp,
											)
										}
									}
								}
							}
						}
					}
				}

				listOf(
					"System apps" to filteredSystemPackages,
					"User apps" to filteredUserPackages,
				).forEach { (sectionName, vendors) ->
					if (vendors.isNotEmpty()) {
						val sectionKey = sectionName
						val sectionExpanded = expandedSections[sectionKey] ?: true
						item(key = "section:$sectionKey") {
							PackageGroupHeader(
								title = sectionName,
								count = vendors.values.sumOf { it.size },
								expanded = sectionExpanded,
								onClick = { expandedSections[sectionKey] = !sectionExpanded },
								level = 0,
								hasWarning = vendors.values.flatten().any { hasIntegrityWarning(it) },
							)
						}

						if (sectionExpanded) {
							vendors.forEach { (vendor, packages) ->
								val vendorKey = "$sectionKey:$vendor"
								val vendorExpanded = expandedVendors[vendorKey] ?: query.isNotBlank()
								item(key = "vendor:$vendorKey") {
									PackageGroupHeader(
										title = vendor,
										count = packages.size,
										expanded = vendorExpanded,
										onClick = { expandedVendors[vendorKey] = !vendorExpanded },
										level = 1,
										hasWarning = packages.any { hasIntegrityWarning(it) },
									)
								}

								if (vendorExpanded) {
									items(packages, key = { "package:$it" }) { pkg ->
										PackageRow(
											pkg = pkg,
											query = query,
											selected = selected.contains(pkg),
											shellReady = shellReady,
											isDisabled = pkg in state.disabledPackageList,
											isApex = pkg in state.apexPackageList,
											status = state.packageOperationStatus[pkg],
											integrityRecord = state.packageIntegrityRecords[pkg],
											integrityMonitoringEnabled = state.packageMonitoringEnabled,
											onSelectedChange = { checked ->
												if (checked) {
													if (!selected.contains(pkg)) selected.add(pkg)
												} else selected.remove(pkg)
											},
											onAction = { action -> pendingPackageAction = pkg to action },
										)
									}
								}
							}
						}
					}
				}

				item {
					Row(
						Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						AmberOutlineButton(
							icon = Icons.Rounded.RemoveCircleOutline,
							onClick = { showConfirm = true },
							enabled = selected.isNotEmpty() && shellReady,
							modifier = Modifier.weight(1f),
						) { Text("Freeze ${selected.size}") }
						AmberOutlineButton(
							icon = Icons.Rounded.Refresh,
							onClick = { viewModel.reEnablePackages(viewModel.getLastDisabled()) },
							enabled = viewModel.getLastDisabled().isNotEmpty() && shellReady,
							modifier = Modifier.weight(1f),
						) { Text("Undo last") }
					}
				}

				item {
					SnackbarHost(hostState = snackbarHostState)
				}
			}

			if (showConfirm) {
				AlertDialog(
					onDismissRequest = { showConfirm = false },
					confirmButton = {
						TextButton(
							onClick = {
								showConfirm = false
								val toDisable = selected.toList()
								viewModel.disablePackages(toDisable)
								scope.launch {
									snackbarHostState.showSnackbar(
										"Started operation for ${toDisable.size} package(s); each result will be shown next to its package.",
									)
								}
							},
							enabled = shellReady,
						) { Text("Confirm", color = AppColors.NeonBlue) }
					},
					dismissButton = {
						TextButton(onClick = { showConfirm = false }) { Text("Cancel", color = AppColors.TextSecondary) }
					},
					title = { Text("Freeze packages") },
					text = {
						Text(
							"Freeze ${selected.size} selected packages for user 0. This does not clear their app data.",
						)
					},
				)
			}
		}
	}
	pendingPackageAction?.let { (packageName, action) ->
		AlertDialog(
			onDismissRequest = { pendingPackageAction = null },
			title = { Text(action.title) },
			text = {
				Text(
					when (action) {
						PackageAction.REINSTALL_AS_USER_0 ->
							"Make $packageName available again for Android user 0. The package must still exist on the device."
						PackageAction.UNINSTALL ->
							"Uninstall $packageName for user 0. This removes the app and its local data for that user."
						PackageAction.CLEAR_DATA ->
							"Clear all local app data for $packageName. This cannot be undone."
						PackageAction.FREEZE ->
							"Disable $packageName for user 0 without clearing its data. You can undo this from Packages."
					},
				)
			},
			confirmButton = {
				TextButton(
					onClick = {
						pendingPackageAction = null
						viewModel.runPackageAction(packageName, action)
					},
					enabled = shellReady,
				) { Text("Confirm", color = AppColors.NeonBlue) }
			},
			dismissButton = {
				TextButton(onClick = { pendingPackageAction = null }) {
					Text("Cancel", color = AppColors.TextSecondary)
				}
			},
		)
	}
}

@Composable
private fun PackageGroupHeader(
	title: String,
	count: Int,
	expanded: Boolean,
	onClick: () -> Unit,
	level: Int,
	hasWarning: Boolean,
) {
	TreeGroupHeader(
		title = title,
		countLabel = "$count",
		expanded = expanded,
		onClick = onClick,
		modifier = Modifier.padding(start = (level * 16).dp),
		warning = hasWarning,
	)
}

@Composable
private fun PackageRow(
	pkg: String,
	query: String,
	selected: Boolean,
	shellReady: Boolean,
	isDisabled: Boolean,
	isApex: Boolean,
	status: String?,
	integrityRecord: PackageIntegrityRecord?,
	integrityMonitoringEnabled: Boolean,
	onSelectedChange: (Boolean) -> Unit,
	onAction: (PackageAction) -> Unit,
) {
	Row(
		Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(12.dp))
			.background(AppColors.Surface.copy(alpha = 0.58f))
			.padding(horizontal = 4.dp, vertical = 2.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Checkbox(checked = selected, enabled = shellReady, onCheckedChange = onSelectedChange)
		val annotated = buildAnnotatedString {
			if (query.isNotBlank()) {
				val idx = pkg.indexOf(query, ignoreCase = true)
				if (idx >= 0) {
					append(pkg.substring(0, idx))
					withStyle(style = SpanStyle(color = AppColors.NeonBlue, fontWeight = FontWeight.Bold)) {
						append(pkg.substring(idx, idx + query.length))
					}
					append(pkg.substring(idx + query.length))
				} else append(pkg)
			} else append(pkg)
		}
		Column(Modifier.weight(1f)) {
			Text(
				annotated,
				color = when {
					isDisabled -> AppColors.TextSecondary
					isApex -> Color(0xFFBEA0FF)
					else -> AppColors.TextPrimary
				},
				fontSize = 12.sp,
				fontWeight = FontWeight.Medium,
				fontFamily = FontFamily.SansSerif,
				letterSpacing = (-0.15).sp,
				maxLines = 1,
				softWrap = false,
			)
			Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
				if (isDisabled) IntegrityBadge("DISABLED", AppColors.TextSecondary)
				if (isApex) IntegrityBadge("APEX", Color(0xFFBEA0FF))
				if (integrityMonitoringEnabled) {
					IntegrityBadge(
						integrityRecord?.let(::integrityLabel) ?: "NO HASH",
						integrityRecord?.let(::integrityColor) ?: AppColors.TextSecondary,
					)
				}
			}
		}
		when (status) {
			"in_progress" -> IntegrityBadge("WORKING", AppColors.NeonBlue)
			"failed" -> IntegrityBadge("FAILED", AppColors.Red)
			"success" -> IntegrityBadge("DONE", AppColors.Green)
			else -> PackageActionsMenu(
				enabled = shellReady,
				onAction = onAction,
			)
		}
	}
}

@Composable
private fun PackageActionsMenu(
	enabled: Boolean,
	onAction: (PackageAction) -> Unit,
) {
	var expanded by remember { mutableStateOf(false) }
	Box {
		IconButton(
			onClick = { expanded = true },
			enabled = enabled,
			modifier = Modifier.size(34.dp),
		) {
			Icon(Icons.Rounded.MoreVert, contentDescription = "Package actions", tint = AppColors.NeonBlue)
		}
		DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
			listOf(
				PackageAction.REINSTALL_AS_USER_0 to Icons.Rounded.Refresh,
				PackageAction.FREEZE to Icons.Rounded.Security,
				PackageAction.CLEAR_DATA to Icons.Rounded.RemoveCircleOutline,
				PackageAction.UNINSTALL to Icons.Rounded.DeleteOutline,
			).forEach { (action, icon) ->
				DropdownMenuItem(
					text = { Text(action.title, fontSize = 13.sp) },
					leadingIcon = { Icon(icon, contentDescription = null, tint = AppColors.NeonBlue) },
					onClick = {
						expanded = false
						onAction(action)
					},
				)
			}
		}
	}
}

@Composable
private fun IntegrityBadge(label: String, color: Color) {
	Surface(
		shape = RoundedCornerShape(5.dp),
		color = color.copy(alpha = 0.1f),
	) {
		Text(
			label,
			modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
			color = color,
			fontSize = 7.sp,
			fontWeight = FontWeight.SemiBold,
			letterSpacing = 0.25.sp,
		)
	}
}

private fun integrityColor(record: PackageIntegrityRecord): Color = when (record.status) {
	PackageIntegrityStatus.UNCHANGED -> AppColors.Green
	PackageIntegrityStatus.APK_CHANGED -> AppColors.Amber
	PackageIntegrityStatus.SIGNER_CHANGED -> AppColors.Red
	PackageIntegrityStatus.BASELINE_UNAVAILABLE -> AppColors.TextSecondary
	PackageIntegrityStatus.CHECK_FAILED -> AppColors.Red
}

private fun integrityLabel(record: PackageIntegrityRecord): String = when (record.status) {
	PackageIntegrityStatus.UNCHANGED -> "HASH MATCH"
	PackageIntegrityStatus.APK_CHANGED -> "APK CHANGED"
	PackageIntegrityStatus.SIGNER_CHANGED -> "SIGNER CHANGED"
	PackageIntegrityStatus.BASELINE_UNAVAILABLE -> "NO BASELINE"
	PackageIntegrityStatus.CHECK_FAILED -> "CHECK FAILED"
}

private fun filterPackagesByVendor(
	packages: List<String>,
	query: String,
): Map<String, List<String>> = packages
	.asSequence()
	.filter { query.isBlank() || it.contains(query, ignoreCase = true) }
	.groupBy(::packageVendor)
	.toSortedMap()

private fun packageVendor(packageName: String): String {
	val namespace = packageName.split('.').take(2).joinToString(".")
	return when (namespace) {
		"com.android" -> "Android"
		"com.google" -> "Google"
		"com.samsung", "com.sec" -> "Samsung"
		"com.qualcomm" -> "Qualcomm"
		"com.miui", "com.xiaomi" -> "Xiaomi"
		"com.oplus" -> "OPPO"
		"com.oneplus" -> "OnePlus"
		"com.huawei" -> "Huawei"
		"com.honor" -> "Honor"
		"com.motorola" -> "Motorola"
		"com.lge" -> "LG"
		"com.sony", "com.sonyericsson" -> "Sony"
		"com.mediatek" -> "MediaTek"
		"com.microsoft" -> "Microsoft"
		"com.facebook", "com.instagram", "com.whatsapp" -> "Meta"
		else -> namespace.split('.').lastOrNull()?.replaceFirstChar { it.uppercase() }
			?: "Other"
	}
}
