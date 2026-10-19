package com.ziddy.breakout.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.ziddy.breakout.engine.Playbook
import com.ziddy.breakout.engine.UserPlaybookEngine
import com.ziddy.breakout.model.Severity
import com.ziddy.breakout.model.ShizukuState
import com.ziddy.breakout.model.Threat
import com.ziddy.breakout.model.UserPlaybook
import com.ziddy.breakout.model.UserPlaybookRunResult
import com.ziddy.breakout.model.UserPlaybookStep
import com.ziddy.breakout.ui.components.AmberOutlineButton
import com.ziddy.breakout.ui.components.ScreenHeader
import com.ziddy.breakout.ui.theme.AppColors
import com.ziddy.breakout.ui.theme.Mono
import java.util.UUID

private data class PlaybookDraftStep(
    val id: String,
    val title: String,
    val command: String,
    val onSuccessStepId: String? = null,
    val onFailureStepId: String? = null,
) {
    fun toModel() = UserPlaybookStep(id, title, command, onSuccessStepId, onFailureStepId)
}

@Composable
fun UserPlaybooksScreen(viewModel: AppViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val shizukuState by viewModel.shizukuState.collectAsStateWithLifecycle()
    var playbookName by remember { mutableStateOf("") }
    var playbookId by remember { mutableStateOf<String?>(null) }
    val steps = remember { mutableStateListOf<PlaybookDraftStep>() }
    var runConfirmation by remember { mutableStateOf<UserPlaybook?>(null) }
    var packageVariable by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<UserPlaybook?>(null) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    val draft = playbookId?.let { UserPlaybook(it, playbookName, steps.map(PlaybookDraftStep::toModel)) }
    val validationError = draft?.let(UserPlaybookEngine::validate)

    LaunchedEffect(state.userPlaybookSaveStatus) {
        state.userPlaybookSaveStatus?.let { savedMessage = it }
    }

    fun edit(playbook: UserPlaybook) {
        playbookId = playbook.id
        playbookName = playbook.name
        steps.clear()
        steps.addAll(playbook.steps.map {
            PlaybookDraftStep(it.id, it.title, it.command, it.onSuccessStepId, it.onFailureStepId)
        })
        savedMessage = null
    }

    fun startNew() {
        val firstStep = PlaybookDraftStep(UUID.randomUUID().toString(), "Step 1", "")
        playbookId = "pb-${UUID.randomUUID()}"
        playbookName = ""
        steps.clear()
        steps += firstStep
        savedMessage = null
    }

    Surface(Modifier.fillMaxSize(), color = AppColors.Background) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Playbooks",
                icon = Icons.Rounded.AccountTree,
                subtitle = "Default scanner playbook and custom flows",
                actions = {
                    IconButton(onClick = ::startNew) {
                        Icon(Icons.Rounded.Add, contentDescription = "Create playbook", tint = AppColors.NeonBlue)
                    }
                },
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Commands can change or remove device data.", color = AppColors.WarningAmber, fontWeight = FontWeight.Bold)
                            Text(
                                "Playbooks run commands with Shizuku shell privileges. Success routes are selected when a command exits with code 0; otherwise the failure route runs. A repeated route is stopped by a run limit.",
                                color = AppColors.TextSecondary,
                                fontSize = 11.sp,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AmberOutlineButton(
                                    onClick = ::startNew,
                                    icon = Icons.Rounded.Add,
                                ) { Text("New playbook") }
                            }
                            state.userPlaybookError?.let { Text(it, color = AppColors.Red, fontSize = 12.sp) }
                        }
                    }
                }
                item(key = "default-playbook") {
                    DefaultPlaybookCard()
                }
                items(state.userPlaybooks, key = UserPlaybook::id) { playbook ->
                    SavedPlaybookCard(
                        playbook = playbook,
                        shellReady = shizukuState == ShizukuState.READY,
                        isRunning = state.isUserPlaybookRunning,
                        runResult = state.userPlaybookRunResult?.takeIf { it.playbookId == playbook.id },
                        isInScanner = playbook.id in state.scannerPlaybookIds,
                        onToggleScanner = {
                            viewModel.setUserPlaybookInScanner(
                                playbook.id,
                                playbook.id !in state.scannerPlaybookIds,
                            )
                        },
                        onEdit = { edit(playbook) },
                        onRun = {
                            packageVariable = ""
                            runConfirmation = playbook
                        },
                        onDelete = { pendingDelete = playbook },
                    )
                }
                if (playbookId != null) {
                    item(key = "editor") {
                        PlaybookEditor(
                            name = playbookName,
                            steps = steps,
                            onNameChange = { playbookName = it },
                            onStepChange = { index, step -> steps[index] = step },
                            onAddStep = {
                                if (steps.size < UserPlaybookEngine.MAX_STEPS) {
                                    steps += PlaybookDraftStep(
                                        id = UUID.randomUUID().toString(),
                                        title = "Step ${steps.size + 1}",
                                        command = "",
                                    )
                                }
                            },
                            onRemoveStep = { index ->
                                val removedId = steps[index].id
                                steps.removeAt(index)
                                steps.indices.forEach { stepIndex ->
                                    val step = steps[stepIndex]
                                    steps[stepIndex] = step.copy(
                                        onSuccessStepId = step.onSuccessStepId.takeUnless { it == removedId },
                                        onFailureStepId = step.onFailureStepId.takeUnless { it == removedId },
                                    )
                                }
                            },
                            onSave = {
                                draft?.let(viewModel::saveUserPlaybook)
                                savedMessage = null
                            },
                            onClose = {
                                playbookId = null
                                steps.clear()
                            },
                            validationError = validationError,
                            savedMessage = savedMessage,
                        )
                    }
                }
            }
        }
    }

    runConfirmation?.let { playbook ->
        val needsPackageVariable = UserPlaybookEngine.requiresPackageVariable(playbook)
        val packageVariableError = if (needsPackageVariable) {
            UserPlaybookEngine.validatePackageVariable(packageVariable)
        } else {
            null
        }
        AlertDialog(
            onDismissRequest = { runConfirmation = null },
            title = { Text("Run ${playbook.name}?") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "This executes user-authored shell commands with Shizuku. Commands may permanently modify the device or its data. Only run a playbook you trust.",
                        color = AppColors.WarningAmber,
                    )
                    if (needsPackageVariable) {
                        OutlinedTextField(
                            value = packageVariable,
                            onValueChange = { packageVariable = it.take(256) },
                            label = { Text("Package value for {{ package }}") },
                            supportingText = { Text("Example: com.example.mdm") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            singleLine = true,
                        )
                        packageVariableError?.let { Text(it, color = AppColors.Red, fontSize = 11.sp) }
                    }
                    playbook.steps.forEachIndexed { index, step ->
                        val command = if (needsPackageVariable && packageVariableError == null) {
                            UserPlaybookEngine.renderCommand(step.command, packageVariable)
                        } else {
                            step.command
                        }
                        Text(
                            "${index + 1}. ${step.title}: $command",
                            color = AppColors.TextSecondary,
                            fontFamily = Mono,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        runConfirmation = null
                        viewModel.runUserPlaybook(
                            playbookId = playbook.id,
                            packageVariable = packageVariable.takeIf { needsPackageVariable },
                        )
                    },
                    enabled = shizukuState == ShizukuState.READY &&
                        !state.isUserPlaybookRunning &&
                        packageVariableError == null,
                ) { Text("Run commands", color = AppColors.WarningAmber) }
            },
            dismissButton = {
                TextButton(onClick = { runConfirmation = null }) {
                    Text("Cancel", color = AppColors.TextSecondary)
                }
            },
            containerColor = AppColors.SurfaceHigh,
        )
    }

    pendingDelete?.let { playbook ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${playbook.name}?") },
            text = { Text("This removes the saved playbook. It does not affect commands already run.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        viewModel.deleteUserPlaybook(playbook.id)
                    },
                ) { Text("Delete", color = AppColors.Red) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", color = AppColors.TextSecondary)
                }
            },
            containerColor = AppColors.SurfaceHigh,
        )
    }
}

@Composable
private fun SavedPlaybookCard(
    playbook: UserPlaybook,
    shellReady: Boolean,
    isRunning: Boolean,
    runResult: UserPlaybookRunResult?,
    isInScanner: Boolean,
    onToggleScanner: () -> Unit,
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(playbook.name, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("${playbook.steps.size} step(s)", color = AppColors.TextSecondary, fontSize = 11.sp)
            }
            TextButton(onClick = onToggleScanner, enabled = !isRunning) {
                Text(
                    if (isInScanner) "In scanner" else "Add to scanner",
                    color = if (isInScanner) AppColors.Green else AppColors.NeonBlue,
                    fontSize = 10.sp,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit ${playbook.name}", tint = AppColors.NeonBlue)
            }
            IconButton(onClick = onRun, enabled = shellReady && !isRunning) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Run ${playbook.name}",
                    tint = if (shellReady && !isRunning) AppColors.WarningAmber else AppColors.TextSecondary,
                )
            }
            IconButton(onClick = onDelete, enabled = !isRunning) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete ${playbook.name}", tint = AppColors.TextSecondary)
            }
        }
        runResult?.let { result ->
            RunResultCard(playbook, result)
        }
    }
}

@Composable
private fun DefaultPlaybookCard() {
    val previewThreat = Threat(
        packageName = UserPlaybookEngine.PACKAGE_VARIABLE,
        label = "Detected MDM",
        versionName = null,
        isSystem = false,
        vendor = "Scanner",
        product = "Default",
        severity = Severity.MEDIUM,
        matchedBy = "signature",
        isDeviceOwner = true,
        isProfileOwner = true,
        adminComponent = "${UserPlaybookEngine.PACKAGE_VARIABLE}/.AdminReceiver",
        reasons = emptyList(),
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceHigh),
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Default", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Text("Built-in scanner playbook · view only", color = AppColors.TextSecondary, fontSize = 11.sp)
                }
                Text("DEFAULT", color = AppColors.Amber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "Preview includes the owner, administrator, and recovery steps. The scanner runs only the steps applicable to each finding.",
                color = AppColors.TextSecondary,
                fontSize = 11.sp,
            )
            Playbook.stepsFor(previewThreat).forEachIndexed { index, step ->
                Text(
                    "${index + 1}. ${step.title}",
                    color = AppColors.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    step.command,
                    color = AppColors.TextSecondary,
                    fontFamily = Mono,
                    fontSize = 9.sp,
                )
            }
        }
    }
}

@Composable
private fun PlaybookEditor(
    name: String,
    steps: List<PlaybookDraftStep>,
    onNameChange: (String) -> Unit,
    onStepChange: (Int, PlaybookDraftStep) -> Unit,
    onAddStep: () -> Unit,
    onRemoveStep: (Int) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    validationError: String?,
    savedMessage: String?,
) {
    Card(colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceHigh)) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Playbook editor", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close editor", tint = AppColors.TextSecondary)
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { onNameChange(it.take(81)) },
                label = { Text("Playbook name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            steps.forEachIndexed { index, step ->
                Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Step ${index + 1}", modifier = Modifier.weight(1f), color = AppColors.WarningAmber, fontWeight = FontWeight.Bold)
                            if (steps.size > 1) {
                                IconButton(onClick = { onRemoveStep(index) }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove step", tint = AppColors.TextSecondary)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = step.title,
                            onValueChange = { onStepChange(index, step.copy(title = it.take(81))) },
                            label = { Text("Step title") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = step.command,
                            onValueChange = { onStepChange(index, step.copy(command = it.take(2001))) },
                            label = { Text("Shell command") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 5,
                        )
                        Text(
                            "Use {{ package }} to insert a package name when running this playbook.",
                            color = AppColors.TextSecondary,
                            fontSize = 10.sp,
                        )
                        RouteSelector(
                            label = "If successful",
                            selectedId = step.onSuccessStepId,
                            steps = steps,
                            currentStepId = step.id,
                            onSelected = { onStepChange(index, step.copy(onSuccessStepId = it)) },
                        )
                        RouteSelector(
                            label = "If failed",
                            selectedId = step.onFailureStepId,
                            steps = steps,
                            currentStepId = step.id,
                            onSelected = { onStepChange(index, step.copy(onFailureStepId = it)) },
                        )
                    }
                }
            }
            if (steps.size < UserPlaybookEngine.MAX_STEPS) {
                AmberOutlineButton(onClick = onAddStep, icon = Icons.Rounded.Add, modifier = Modifier.fillMaxWidth()) {
                    Text("Add step")
                }
            }
            validationError?.let { Text(it, color = AppColors.Red, fontSize = 11.sp) }
            savedMessage?.let { Text(it, color = AppColors.Green, fontSize = 11.sp) }
            AmberOutlineButton(
                onClick = onSave,
                enabled = validationError == null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save playbook") }
        }
    }
}

@Composable
private fun RouteSelector(
    label: String,
    selectedId: String?,
    steps: List<PlaybookDraftStep>,
    currentStepId: String,
    onSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = steps.firstOrNull { it.id == selectedId }?.title ?: "Finish"
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("$label: $selectedLabel", color = AppColors.TextSecondary, fontSize = 11.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Finish this branch") },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            steps.filterNot { it.id == currentStepId }.forEach { step ->
                DropdownMenuItem(
                    text = { Text("Continue to: ${step.title.ifBlank { "Untitled step" }}") },
                    onClick = {
                        onSelected(step.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RunResultCard(playbook: UserPlaybook, result: UserPlaybookRunResult) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(if (result.isRunning) "Running step by step…" else "Latest run", fontWeight = FontWeight.Bold)
        playbook.steps.forEachIndexed { index, playbookStep ->
            val step = result.steps.lastOrNull { it.stepId == playbookStep.id }
            val isCurrent = result.currentStepId == playbookStep.id
            Column {
                Text(
                    "${index + 1}. " + when {
                        isCurrent -> "RUNNING · ${playbookStep.title}"
                        step == null -> "NOT REACHED · ${playbookStep.title}"
                        step.succeeded -> "SUCCESS · ${playbookStep.title}"
                        else -> "FAILED · ${playbookStep.title}" + if (step.timedOut) " (timed out)" else ""
                    },
                    color = when {
                        isCurrent -> AppColors.Amber
                        step == null -> AppColors.TextSecondary
                        step.succeeded -> AppColors.Green
                        else -> AppColors.Red
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    step?.command ?: if (isCurrent) {
                        result.currentStepCommand ?: playbookStep.command
                    } else {
                        playbookStep.command
                    },
                    color = AppColors.TextSecondary,
                    fontFamily = Mono,
                    fontSize = 9.sp,
                )
                if (step?.output?.isNotBlank() == true) {
                    Text(step.output, color = AppColors.TextSecondary, fontFamily = Mono, fontSize = 9.sp)
                }
            }
        }
        if (result.reachedStepLimit) {
            Text("Stopped at the run safety limit to prevent an endless branch loop.", color = AppColors.WarningAmber)
        }
    }
}
