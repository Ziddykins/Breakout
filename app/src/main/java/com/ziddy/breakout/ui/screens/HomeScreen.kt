package com.ziddy.breakout.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ziddy.breakout.AppUiState
import com.ziddy.breakout.AppViewModel
import com.ziddy.breakout.model.PlaybookRun
import com.ziddy.breakout.model.ShizukuState
import com.ziddy.breakout.model.StepRun
import com.ziddy.breakout.model.StepState
import com.ziddy.breakout.model.Threat
import com.ziddy.breakout.model.UserPlaybook
import com.ziddy.breakout.model.UserPlaybookRunResult
import com.ziddy.breakout.engine.Playbook
import com.ziddy.breakout.engine.UserPlaybookEngine
import com.ziddy.breakout.ui.components.AmberOutlineButton
import com.ziddy.breakout.ui.components.TileActionButton
import com.ziddy.breakout.ui.theme.AppColors
import com.ziddy.breakout.ui.theme.Mono
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val scanDateFormat = SimpleDateFormat("HH:mm", Locale.US)

@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    shellReady: Boolean,
    onPermissions: () -> Unit,
    onPackages: () -> Unit,
    onForensic: () -> Unit,
    onHarden: () -> Unit,
    onUserPlaybooks: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val shizukuState by viewModel.shizukuState.collectAsStateWithLifecycle()
    val shizukuError by viewModel.shizukuError.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val openGuide: () -> Unit = {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, "https://shizuku.rikka.app/".toUri()))
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(AppColors.Background),
    ) {
        Image(
            painter = painterResource(com.ziddy.breakout.R.drawable.breakout_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .aspectRatio(1f)
                .alpha(0.09f),
        )
        Column(Modifier.fillMaxSize()) {
            TopHeader(state)
            ShizukuCard(
                state = shizukuState,
                error = shizukuError,
                onGrant = viewModel::requestShizuku,
                onRefresh = viewModel::refreshShizuku,
                onGuide = openGuide,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "DEVICE TOOLS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp,
                        color = AppColors.WarningAmber,
                        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
                    )
                    MainActionGrid(
                        shellReady = shellReady,
                        onPermissions = onPermissions,
                        onPackages = onPackages,
                        onForensic = onForensic,
                        onHarden = onHarden,
                        onUserPlaybooks = onUserPlaybooks,
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceScannerPanel(viewModel: AppViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val shizukuState by viewModel.shizukuState.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Threat?>(null) }
    val haptics = LocalHapticFeedback.current
    var wasScanning by remember { mutableStateOf(false) }

    LaunchedEffect(state.isScanning) {
        if (wasScanning && !state.isScanning) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        wasScanning = state.isScanning
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScanCard(state, onScan = viewModel::startScan)
        if (state.threats.isEmpty() && !state.isScanning && state.lastScanAt != null) {
            AllClearCard(state.usedShellForScan)
        }
        state.threats.forEach { threat ->
            ThreatCard(
                threat = threat,
                run = state.playbookRuns[threat.packageName],
                onClick = { selected = threat },
            )
        }
        if (state.threats.isNotEmpty()) {
            Text(
                "Tap a finding for the full removal playbook.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
    selected?.let { threat ->
        ThreatDetailSheet(
            threat = threat,
            run = state.playbookRuns[threat.packageName],
            userPlaybooks = state.userPlaybooks.filter { it.id in state.scannerPlaybookIds },
            userPlaybookRuns = state.userPlaybookRuns[threat.packageName].orEmpty(),
            userPlaybookError = state.userPlaybookError,
            isUserPlaybookRunning = state.isUserPlaybookRunning,
            shizukuState = shizukuState,
            onDismiss = { selected = null },
            onRunPlaybook = { viewModel.runPlaybook(threat.packageName) },
            onRunUserPlaybook = { playbookId ->
                viewModel.runUserPlaybook(
                    playbookId = playbookId,
                    packageVariable = threat.packageName,
                    scannerPackageName = threat.packageName,
                )
            },
            onRunStep = { id -> viewModel.runSingleStep(threat.packageName, id) },
        )
    }
}

@Composable
private fun MainActionGrid(
    shellReady: Boolean,
    onPermissions: () -> Unit,
    onPackages: () -> Unit,
    onForensic: () -> Unit,
    onHarden: () -> Unit,
    onUserPlaybooks: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MainActionCard(
                "Permissions",
                "Review sensitive access",
                Icons.Rounded.Key,
                shellReady,
                onPermissions,
                Modifier.weight(1f),
            )
            MainActionCard(
                "Packages",
                "Inspect installed apps",
                Icons.Rounded.Apps,
                shellReady,
                onPackages,
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MainActionCard(
                "Forensic",
                "Scan device activity",
                Icons.Rounded.Security,
                shellReady,
                onForensic,
                Modifier.weight(1f),
            )
            MainActionCard(
                "Harden",
                "Check secure settings",
                Icons.Rounded.Tune,
                shellReady,
                onHarden,
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MainActionCard(
                "Playbooks",
                "Build conditional command flows",
                Icons.Rounded.AccountTree,
                true,
                onUserPlaybooks,
                Modifier.weight(1f),
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun MainActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(132.dp)
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = AppColors.Amber.copy(alpha = if (enabled) 0.12f else 0f),
                spotColor = AppColors.Amber.copy(alpha = if (enabled) 0.12f else 0f),
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        AppColors.SurfaceHigh.copy(alpha = if (enabled) 0.78f else 0.42f),
                        AppColors.Background.copy(alpha = if (enabled) 0.9f else 0.64f),
                    ),
                ),
            )
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            AppColors.Amber.copy(alpha = if (enabled) 0.46f else 0.1f),
                            AppColors.Amber.copy(alpha = if (enabled) 0.12f else 0.04f),
                        ),
                    ),
                ),
                shape,
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = AppColors.Amber.copy(alpha = if (enabled) 0.07f else 0.025f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .size(88.dp),
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = AppColors.Amber.copy(alpha = if (enabled) 0.13f else 0.04f),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = AppColors.Amber.copy(alpha = if (enabled) 0.95f else 0.25f),
                    modifier = Modifier.padding(8.dp).size(20.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                title,
                color = AppColors.TextPrimary.copy(alpha = if (enabled) 1f else 0.45f),
                fontWeight = FontWeight.Bold,
            )
            Text(
                subtitle,
                color = AppColors.TextSecondary.copy(alpha = if (enabled) 0.9f else 0.45f),
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TopHeader(state: AppUiState) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Home,
                    contentDescription = null,
                    tint = AppColors.NeonBlue,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "BREAKOUT",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                    color = AppColors.TextPrimary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "MDM detection & removal",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary,
                letterSpacing = 1.sp,
            )
        }
        if (state.isScanning) PulsingChip("SCANNING", AppColors.Amber)
    }
}

@Composable
private fun PulsingChip(text: String, color: Color) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alpha",
    )
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha)),
            )
            Spacer(Modifier.width(6.dp))
            Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun ShizukuCard(
    state: ShizukuState,
    error: String?,
    onGrant: () -> Unit,
    onRefresh: () -> Unit,
    onGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (color, title, body) = when (state) {
        ShizukuState.READY -> Triple(
            AppColors.Green,
            "Shell connected",
            "Commands run as shell (same power as ADB). The full removal playbook is unlocked.",
        )
        ShizukuState.NO_PERMISSION -> Triple(
            AppColors.Amber,
            "Shizuku is running",
            "Grant Breakout permission to execute shell commands through Shizuku.",
        )
        ShizukuState.NOT_RUNNING -> Triple(
            AppColors.Grey,
            "Shell not connected",
            "Breakout needs Shizuku to act on detected MDMs. Install and start Shizuku (wireless debugging or root), then re-check.",
        )
        ShizukuState.CONNECTING -> Triple(
            AppColors.Amber,
            "Connecting shell",
            "Shizuku permission is granted. Waiting for Breakout's shell service to connect.",
        )
        ShizukuState.SERVICE_ERROR -> Triple(
            AppColors.Red,
            "Shell unavailable",
            error ?: "Breakout could not connect to the Shizuku shell service. Re-check the connection.",
        )
    }
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state == ShizukuState.READY) Icons.Rounded.Security else Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(title, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                Spacer(Modifier.weight(1f))
                MiniStatusDot(color)
            }
            Spacer(Modifier.height(10.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    ShizukuState.READY -> {}
                    ShizukuState.NO_PERMISSION -> AmberOutlineButton(
                        onClick = onGrant,
                    ) { Text("Grant access") }
                    ShizukuState.NOT_RUNNING -> AmberOutlineButton(onClick = onGuide) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(14.dp), tint = AppColors.Amber)
                        Spacer(Modifier.width(6.dp))
                        Text("Set-up guide", color = AppColors.Amber)
                    }
                    ShizukuState.CONNECTING, ShizukuState.SERVICE_ERROR -> {}
                }
                if (state != ShizukuState.READY) {
                    TextButton(onClick = onRefresh) { Text("Re-check", color = AppColors.TextSecondary) }
                }
            }
        }
    }
}

@Composable
private fun MiniStatusDot(color: Color) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
private fun ScanCard(state: AppUiState, onScan: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                if (state.isScanning) {
                    RadarScan(Modifier.size(76.dp))
                }
                Icon(
                    Icons.Rounded.Security,
                    contentDescription = null,
                    tint = if (state.isScanning) AppColors.Amber else AppColors.TextSecondary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    state.isScanning -> state.scanPhase.ifBlank { "Scanning…" }
                    state.lastScanAt == null -> "Ready to scan this device"
                    else -> "Last scan at ${scanDateFormat.format(Date(state.lastScanAt))}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.isScanning) AppColors.Amber else AppColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            TileActionButton(
                onClick = onScan,
                icon = Icons.Rounded.Radar,
                enabled = !state.isScanning,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (state.isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = AppColors.NeonBlue,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Scanning…", fontWeight = FontWeight.Bold)
                } else {
                    Text("Scan device", fontWeight = FontWeight.Bold)
                }
            }
            if (!state.isScanning && state.lastScanAt != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "${state.scannedPackages} packages checked • ${state.threats.size} finding(s)" +
                        if (state.usedShellForScan) " • policy dump read" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun RadarScan(modifier: Modifier = Modifier, color: Color = AppColors.Amber) {
    val transition = rememberInfiniteTransition(label = "radar")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "sweep",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "pulse",
    )
    Canvas(modifier) {
        val radius = size.minDimension / 2f
        drawCircle(color = color.copy(alpha = (1f - pulse) * 0.25f), radius = radius * (0.4f + 0.6f * pulse))
        drawCircle(color = color.copy(alpha = 0.2f), radius = radius, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        drawCircle(color = color.copy(alpha = 0.12f), radius = radius * 0.6f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
        rotate(sweep) {
            drawArc(
                brush = Brush.sweepGradient(listOf(Color.Transparent, color.copy(alpha = 0.7f))),
                startAngle = -90f,
                sweepAngle = 100f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(6f),
            )
        }
    }
}

@Composable
private fun AllClearCard(usedShell: Boolean) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Green.copy(alpha = 0.08f)),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Shield, null, tint = AppColors.Green, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("No known MDMs found", fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                Text(
                    "The signature scan is clean. Any active device administrator would still appear above." +
                        if (!usedShell) " Connect Shizuku for a deeper policy-dump check." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ThreatCard(threat: Threat, run: PlaybookRun?, onClick: () -> Unit) {
    val severityColor = AppColors.forSeverity(threat.severity)
    val removed = run?.steps?.lastOrNull { it.step.id == "verify" }?.state == StepState.DONE
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
    ) {
        Row(Modifier.height(76.dp)) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxSize()
                    .background(severityColor),
            )
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        threat.severity.label.uppercase(),
                        color = severityColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(threat.vendor, fontSize = 11.sp, color = AppColors.TextSecondary)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    threat.product,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                )
                Text(
                    threat.packageName,
                    fontFamily = Mono,
                    fontSize = 11.sp,
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (threat.adminComponent != null) FlagChip("DEVICE ADMIN", AppColors.Red)
                    if (threat.isDeviceOwner) FlagChip("DEVICE OWNER", AppColors.Red)
                    if (threat.isProfileOwner) FlagChip("PROFILE OWNER", AppColors.Red)
                    if (threat.isSystem) FlagChip("SYSTEM APP", AppColors.Grey)
                    if (threat.matchedBy == "keyword") FlagChip("POSSIBLE", AppColors.Blue)
                    if (removed) FlagChip("REMOVED", AppColors.Green)
                }
                run?.let {
                    if (!removed) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when (it.state) {
                                com.ziddy.breakout.model.RunState.RUNNING -> "Playbook running…"
                                com.ziddy.breakout.model.RunState.DONE -> "Playbook finished"
                                com.ziddy.breakout.model.RunState.FAILED -> "Playbook finished with issues"
                                else -> ""
                            },
                            fontSize = 10.sp,
                            color = AppColors.TextSecondary,
                        )
                    }
                }
            }
            Box(Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ExpandMore, null, tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun FlagChip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(5.dp), color = color.copy(alpha = 0.12f)) {
        Text(
            text,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreatDetailSheet(
    threat: Threat,
    run: PlaybookRun?,
    userPlaybooks: List<UserPlaybook>,
    userPlaybookRuns: Map<String, UserPlaybookRunResult>,
    userPlaybookError: String?,
    isUserPlaybookRunning: Boolean,
    shizukuState: ShizukuState,
    onDismiss: () -> Unit,
    onRunPlaybook: () -> Unit,
    onRunUserPlaybook: (String) -> Unit,
    onRunStep: (String) -> Unit,
) {
    var userPlaybookMenuExpanded by remember { mutableStateOf(false) }
    var pendingUserPlaybook by remember { mutableStateOf<UserPlaybook?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            val severityColor = AppColors.forSeverity(threat.severity)
            Text(
                threat.severity.label.uppercase() + " • " + threat.vendor,
                color = severityColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(threat.product, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(threat.label, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
            Spacer(Modifier.height(14.dp))

            MetaRow("Package", threat.packageName, mono = true)
            threat.versionName?.let { MetaRow("Version", it) }
            threat.adminComponent?.let { MetaRow("Admin component", it, mono = true) }
            if (threat.isDeviceOwner) MetaRow("Owner slot", "Device owner", color = AppColors.Red)
            if (threat.isProfileOwner) MetaRow("Owner slot", "Profile owner", color = AppColors.Red)

            Spacer(Modifier.height(14.dp))
            threat.reasons.forEach { reason ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("•  ", color = severityColor)
                    Text(reason, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                }
            }

            Spacer(Modifier.height(18.dp))
            val isBuiltInPlaybookRunning = run?.state == com.ziddy.breakout.model.RunState.RUNNING
            if (shizukuState != ShizukuState.READY) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AppColors.Amber.copy(alpha = 0.1f),
                ) {
                    Text(
                        "Connect Shizuku to run the removal playbook. Steps can also be previewed below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.Amber,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AppColors.Amber.copy(alpha = 0.08f),
                ) {
                    Text(
                        "If a package/user-specific shell restriction is detected, the playbook will automatically register the package for user 0 and retry matching failed steps once. This does not grant root.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                TileActionButton(
                    onClick = onRunPlaybook,
                    icon = Icons.Rounded.PlayArrow,
                    enabled = !isBuiltInPlaybookRunning,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (isBuiltInPlaybookRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = AppColors.NeonBlue,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Running playbook…", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Run full removal playbook", fontWeight = FontWeight.Bold)
                    }
                }
                if (userPlaybooks.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Or run a saved playbook with {{ package }} set to this detected package:",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary,
                    )
                    Box {
                        TextButton(
                            onClick = { userPlaybookMenuExpanded = true },
                            enabled = !isBuiltInPlaybookRunning && !isUserPlaybookRunning,
                        ) {
                            Text("Choose saved playbook", color = AppColors.NeonBlue)
                        }
                        DropdownMenu(
                            expanded = userPlaybookMenuExpanded,
                            onDismissRequest = { userPlaybookMenuExpanded = false },
                        ) {
                            userPlaybooks.forEach { playbook ->
                                DropdownMenuItem(
                                    text = { Text(playbook.name) },
                                    onClick = {
                                        userPlaybookMenuExpanded = false
                                        pendingUserPlaybook = playbook
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("Playbook steps", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val steps = run?.steps ?: Playbook.stepsFor(threat).map { StepRun(it) }
            val recoveryEligible = run?.steps?.any {
                it.state == StepState.FAILED &&
                    Playbook.canRetryAfterUser0Restore(it.step.id) &&
                    Playbook.needsUser0Recovery(it.output)
            } == true
            steps.forEachIndexed { index, stepRun ->
                StepRow(
                    index = index,
                    stepRun = stepRun,
                    enabled = run?.state != com.ziddy.breakout.model.RunState.RUNNING &&
                        shizukuState == ShizukuState.READY &&
                        (!stepRun.step.conditional || recoveryEligible),
                    onRun = { onRunStep(stepRun.step.id) },
                )
            }
            if (userPlaybooks.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text("Added playbooks", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                userPlaybookError?.let {
                    Text(it, color = AppColors.Red, fontSize = 11.sp)
                }
                userPlaybooks.forEach { playbook ->
                    val playbookRun = userPlaybookRuns[playbook.id]
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AppColors.SurfaceHigh.copy(alpha = 0.7f),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    playbook.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = { pendingUserPlaybook = playbook },
                                    enabled = !isBuiltInPlaybookRunning && !isUserPlaybookRunning,
                                ) {
                                    Text("Run", color = AppColors.NeonBlue)
                                }
                            }
                            playbookRun?.let { UserPlaybookStepList(playbook, it) }
                        }
                    }
                }
            }
            val adminFailed = run?.steps?.any { it.step.id in Playbook.adminStepIds && it.state == StepState.FAILED } == true
            if (adminFailed) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.Red.copy(alpha = 0.08f),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "Administrator removal refused?",
                            fontWeight = FontWeight.Bold,
                            color = AppColors.Red,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Admins without the android:testOnly flag can reject dpm removal. From a computer, rebuild a copy of the same APK with the flag set, install it as an update, then retry the step:",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary,
                        )
                        Spacer(Modifier.height(8.dp))
                        listOf(
                            "adb shell pm path ${threat.packageName}",
                            "adb pull <path-from-above> app.apk",
                            "apktool d app.apk",
                            "→ add android:testOnly=\"true\" to <application>",
                            "apktool b decompiled_app -o app-modified.apk",
                            "keytool -genkey -v -keystore test.keystore -alias testkey",
                            "apksigner sign --ks test.keystore app-modified.apk",
                            "adb install -r -t app-modified.apk",
                            "→ re-run \"Deactivate device admin\"",
                        ).forEach { line ->
                            Text(
                                line,
                                fontFamily = Mono,
                                fontSize = 10.sp,
                                color = AppColors.Amber.copy(alpha = 0.9f),
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "The re-signed update carries the same package name, so device policies treat it as the same administrator — now removable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary,
                        )
                    }
                }

                pendingUserPlaybook?.let { playbook ->
                    AlertDialog(
                        onDismissRequest = { pendingUserPlaybook = null },
                        title = { Text("Run ${playbook.name}?") },
                        text = {
                            Column(
                                Modifier
                                    .heightIn(max = 360.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                Text(
                                    "This runs user-authored shell commands with Shizuku. Review every command before continuing.",
                                    color = AppColors.WarningAmber,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Package value: ${threat.packageName}",
                                    color = AppColors.TextSecondary,
                                    fontFamily = Mono,
                                    fontSize = 11.sp,
                                )
                                playbook.steps.forEachIndexed { index, step ->
                                    Text(
                                        "${index + 1}. ${step.title}: " +
                                            UserPlaybookEngine.renderCommand(step.command, threat.packageName),
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
                                    pendingUserPlaybook = null
                                    onRunUserPlaybook(playbook.id)
                                },
                                enabled = shizukuState == ShizukuState.READY && !isUserPlaybookRunning,
                            ) {
                                Text("Run commands", color = AppColors.WarningAmber)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingUserPlaybook = null }) {
                                Text("Cancel", color = AppColors.TextSecondary)
                            }
                        },
                        containerColor = AppColors.SurfaceHigh,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Commands run via Shizuku as the shell user (same as ADB). Failed steps are non-fatal — later steps still attempt full removal.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun UserPlaybookStepList(playbook: UserPlaybook, result: UserPlaybookRunResult) {
    playbook.steps.forEachIndexed { index, step ->
        val completed = result.steps.lastOrNull { it.stepId == step.id }
        val isCurrent = result.currentStepId == step.id
        val state = when {
            isCurrent -> StepState.RUNNING
            completed == null -> StepState.PENDING
            completed.succeeded -> StepState.DONE
            else -> StepState.FAILED
        }
        StepRow(
            index = index,
            stepRun = StepRun(
                step = com.ziddy.breakout.model.RemovalStep(
                    id = step.id,
                    title = step.title,
                    detail = if (step.onSuccessStepId != null || step.onFailureStepId != null) {
                        "Custom command · branch on success or failure"
                    } else {
                        "Custom command"
                    },
                    command = completed?.command
                        ?: if (isCurrent) result.currentStepCommand ?: step.command else step.command,
                ),
                state = state,
                output = completed?.output.orEmpty(),
            ),
            enabled = false,
            onRun = {},
        )
    }
    if (result.isRunning) {
        Text("Playbook is running…", color = AppColors.Amber, fontSize = 11.sp)
    } else if (result.reachedStepLimit) {
        Text("Stopped at the run safety limit.", color = AppColors.WarningAmber, fontSize = 11.sp)
    }
}

@Composable
private fun MetaRow(label: String, value: String, mono: Boolean = false, color: Color = AppColors.TextPrimary) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextSecondary,
            modifier = Modifier.width(120.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (mono) Mono else null,
            color = color,
        )
    }
}

@Composable
private fun StepRow(index: Int, stepRun: StepRun, enabled: Boolean, onRun: () -> Unit) {
    val stateColor = when (stepRun.state) {
        StepState.DONE -> AppColors.Green
        StepState.FAILED -> AppColors.Red
        StepState.RUNNING -> AppColors.Amber
        StepState.SKIPPED -> AppColors.Grey
        StepState.PENDING -> AppColors.TextSecondary
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.SurfaceHigh.copy(alpha = 0.6f))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${index + 1}",
                fontFamily = Mono,
                fontSize = 11.sp,
                color = AppColors.TextSecondary,
                modifier = Modifier.width(18.dp),
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stepRun.step.title, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary, fontSize = 14.sp)
                    if (stepRun.step.conditional) {
                        Spacer(Modifier.width(6.dp))
                        FlagChip("ON FAILURE", AppColors.Amber)
                    }
                    if (stepRun.step.readOnly) {
                        Spacer(Modifier.width(6.dp))
                        FlagChip("READ-ONLY", AppColors.Blue)
                    }
                }
                Text(
                    stepRun.step.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                )
            }
            when (stepRun.state) {
                StepState.PENDING -> IconButton(onClick = onRun, enabled = enabled, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.PlayArrow, "Run step", tint = if (enabled) AppColors.Amber else AppColors.TextSecondary, modifier = Modifier.size(18.dp))
                }
                StepState.RUNNING -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AppColors.Amber)
                StepState.DONE -> Icon(Icons.Rounded.CheckCircle, null, tint = AppColors.Green, modifier = Modifier.size(18.dp))
                StepState.FAILED -> Icon(Icons.Rounded.Cancel, null, tint = AppColors.Red, modifier = Modifier.size(18.dp))
                StepState.SKIPPED -> Icon(Icons.Rounded.RemoveCircleOutline, null, tint = AppColors.Grey, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stepRun.step.command,
            fontFamily = Mono,
            fontSize = 11.sp,
            color = AppColors.Amber.copy(alpha = 0.85f),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0A0C10))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
        if (stepRun.output.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                stepRun.output,
                fontFamily = Mono,
                fontSize = 10.sp,
                color = if (stepRun.state == StepState.FAILED) AppColors.Red else AppColors.TextSecondary,
                maxLines = 4,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
