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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.ziddy.breakout.AppViewModel
import com.ziddy.breakout.ui.theme.AppColors
import com.ziddy.breakout.ui.theme.Mono

@Composable
fun PackagesScreen(viewModel: AppViewModel) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()
	val selected = remember { mutableStateListOf<String>() }
	var query by remember { mutableStateOf("") }
	var showConfirm by remember { mutableStateOf(false) }
	val snackbarHostState = remember { SnackbarHostState() }
	val scope = rememberCoroutineScope()

	Surface(Modifier.fillMaxSize(), color = AppColors.Background) {
		Column(Modifier.fillMaxSize()) {
			Row(
				Modifier
					.fillMaxWidth()
					.padding(horizontal = 20.dp, vertical = 16.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Icon(Icons.Rounded.Code, contentDescription = null, tint = AppColors.Amber)
				Spacer(modifier = Modifier.size(8.dp))
				Text("PACKAGES", color = AppColors.TextPrimary, fontFamily = Mono)
				Spacer(modifier = Modifier.weight(1f))
				IconButton(onClick = { viewModel.listPackages() }, enabled = !state.isPackageListing) {
					Icon(Icons.Rounded.Refresh, contentDescription = "Refresh packages")
				}
			}

			LazyColumn(
				modifier = Modifier.fillMaxSize(),
				contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				// progress
				if (state.packageOperationProgress in 1..99) {
					item {
						LinearProgressIndicator(progress = state.packageOperationProgress / 100f, modifier = Modifier.fillMaxWidth())
					}
				}

				item {
					Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
						Column(Modifier.padding(12.dp)) {
							Text("Installed packages")
							OutlinedTextField(
								value = query,
								onValueChange = { query = it },
								label = { Text("Filter packages") },
								modifier = Modifier.fillMaxWidth(),
							)
							Text("Select packages to disable or clear cache.", color = AppColors.TextSecondary)
						}
					}
				}

				val filtered = if (query.isBlank()) state.packageList else state.packageList.filter { it.contains(query, ignoreCase = true) }

				val hasSamsung = state.packageList.any { it.startsWith("com.samsung") }
				if (hasSamsung) {
					item {
						Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
							Column(Modifier.padding(12.dp)) {
								Text("Detected Samsung packages — grouped suggestions")
								Spacer(modifier = Modifier.size(6.dp))
								viewModel.samsungGroups.forEach { (groupName, groupPkgs) ->
									val present = groupPkgs.filter { state.packageList.contains(it) }
									if (present.isNotEmpty()) {
										Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
											var checked by remember { mutableStateOf(false) }
											Checkbox(checked = checked, onCheckedChange = {
												checked = it
												if (it) selected.addAll(present.filter { !selected.contains(it) }) else selected.removeAll(present)
											})
											Spacer(modifier = Modifier.size(8.dp))
											Text(groupName.replace('_', ' ').uppercase())
										}
									}
								}
							}
						}
					}
				}

				items(filtered) { pkg ->
					val status = state.packageOperationStatus[pkg]
					Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
						Checkbox(checked = selected.contains(pkg), onCheckedChange = {
							if (it) selected.add(pkg) else selected.remove(pkg)
						})
						Spacer(modifier = Modifier.size(8.dp))
						val annotated = buildAnnotatedString {
							if (query.isNotBlank()) {
								val idx = pkg.indexOf(query, ignoreCase = true)
								if (idx >= 0) {
									append(pkg.substring(0, idx))
									withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append(pkg.substring(idx, idx + query.length)) }
									append(pkg.substring(idx + query.length))
								} else append(pkg)
							} else append(pkg)
						}
						Text(annotated, modifier = Modifier.weight(1f))
						Spacer(modifier = Modifier.size(8.dp))
						when (status) {
							"in_progress" -> Text("…", color = AppColors.Amber)
							"success" -> Text("✓", color = Color(0xFF4CAF50))
							"failed" -> Text("✕", color = Color(0xFFF44336))
							else -> {
								Button(onClick = { viewModel.disablePackages(listOf(pkg)) }) { Text("Disable") }
							}
						}
					}
				}

				item {
					Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
						Button(onClick = { showConfirm = true }, enabled = selected.isNotEmpty()) { Text("Disable selected") }
						Spacer(modifier = Modifier.size(8.dp))
						Button(onClick = { viewModel.reEnablePackages(viewModel.getLastDisabled()) }, enabled = viewModel.getLastDisabled().isNotEmpty()) { Text("Undo last") }
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
						Button(onClick = {
							showConfirm = false
							val toDisable = selected.toList()
							viewModel.disablePackages(toDisable)
							scope.launch { snackbarHostState.showSnackbar("Disabled ${toDisable.size} package(s)") }
						}) { Text("Confirm") }
					},
					dismissButton = { Button(onClick = { showConfirm = false }) { Text("Cancel") } },
					title = { Text("Confirm disable") },
					text = { Text("Disable and clear cache for ${selected.size} selected packages?") },
				)
			}
		}
	}
}
