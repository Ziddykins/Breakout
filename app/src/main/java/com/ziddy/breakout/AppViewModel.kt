package com.ziddy.breakout

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ziddy.breakout.engine.Playbook
import com.ziddy.breakout.engine.ForensicEngine
import com.ziddy.breakout.engine.PermissionAuditEngine
import com.ziddy.breakout.engine.ScanEngine
import com.ziddy.breakout.model.ForensicReport
import com.ziddy.breakout.model.LogKind
import com.ziddy.breakout.model.LogLine
import com.ziddy.breakout.model.PermissionAuditReport
import com.ziddy.breakout.model.PlaybookRun
import com.ziddy.breakout.model.RemovalStep
import com.ziddy.breakout.model.RunState
import com.ziddy.breakout.model.ShizukuState
import com.ziddy.breakout.model.StepRun
import com.ziddy.breakout.model.StepState
import com.ziddy.breakout.model.Threat
import com.ziddy.breakout.shell.ShizukuShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppUiState(
    val isScanning: Boolean = false,
    val isConsoleCommandRunning: Boolean = false,
    val consoleRisksAcknowledged: Boolean = false,
    val scanPhase: String = "",
    val threats: List<Threat> = emptyList(),
    val playbookRuns: Map<String, PlaybookRun> = emptyMap(),
    val scannedPackages: Int = 0,
    val lastScanAt: Long? = null,
    val usedShellForScan: Boolean = false,
    val isForensicScanning: Boolean = false,
    val forensicPhase: String = "",
    val forensicReport: ForensicReport? = null,
    val forensicErrors: List<String> = emptyList(),
    val isPermissionAuditScanning: Boolean = false,
    val permissionAuditReport: PermissionAuditReport? = null,
    val permissionAuditError: String? = null,
    val isSettingsScanning: Boolean = false,
    val settingsAuditOutput: List<String> = emptyList(),
    val isPackageListing: Boolean = false,
    val packageList: List<String> = emptyList(),
    val packageOperationStatus: Map<String, String> = emptyMap(), // package -> pending|in_progress|success|failed
    val packageOperationProgress: Int = 0, // 0..100
)

/** Shared app state: scan results, playbook runs, and the command console log. */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    val shizukuState: StateFlow<ShizukuState> = ShizukuShell.state

    private val _log = MutableStateFlow<List<LogLine>>(emptyList())
    val log: StateFlow<List<LogLine>> = _log.asStateFlow()

    init {
        ShizukuShell.refreshState()
        logLine(LogKind.INFO, "Breakout ready. Connect Shizuku to unlock removal actions.")
        logLine(LogKind.WARN, "WARNING: Console commands can negatively affect your phone or brick it.")
        logLine(LogKind.ERROR, "i understand the risks")
        logLine(LogKind.INFO, "Type the phrase above at the prompt, then press Enter to unlock commands.")
    }

    // Track last disabled packages to allow undo in-session
    private var lastDisabledPackages: List<String> = emptyList()
    // persistent history stored in SharedPreferences as JSON array of package names
    private val PREFS_NAME = "breakout_prefs"
    private val PREF_DISABLED_HISTORY = "disabled_history"

    private fun saveDisabledHistory(packages: List<String>) {
        try {
            val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, 0)
            val json = org.json.JSONArray(packages).toString()
            prefs.edit().putString(PREF_DISABLED_HISTORY, json).apply()
        } catch (_: Exception) { }
    }

    fun loadDisabledHistory(): List<String> {
        return try {
            val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, 0)
            val json = prefs.getString(PREF_DISABLED_HISTORY, "[]") ?: "[]"
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Common Samsung package groups for UI grouping
    val samsungGroups: Map<String, List<String>> = mapOf(
        "bloat" to listOf(
            "com.samsung.android.bixby.agent",
            "com.samsung.android.bixby.service",
            "com.samsung.android.bixby.wakeup",
            "com.samsung.android.app.spage",
            "com.samsung.android.stickercenter",
            "com.samsung.android.aremoji",
            "com.samsung.android.aremojieditor",
            "com.samsung.android.app.moments",
            "com.samsung.android.game.gos",
            "com.samsung.android.smartsuggestions",
        ),
        "mirror_share" to listOf(
            "com.samsung.android.audiomirroring",
            "com.samsung.android.smartmirroring",
            "com.samsung.android.app.sharelive",
            "com.samsung.android.mdx",
            "com.samsung.android.oneconnect",
            "com.samsung.android.allshare.service.mediashare",
            "com.samsung.android.visual.cloudcore",
        ),
        "mdm" to listOf(
            "com.samsung.android.mdm",
            "com.samsung.android.omcagent",
            "com.samsung.android.fmm",
        ),
    )

    fun reEnablePackages(packages: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            if (packages.isEmpty()) return@launch
            val results = mutableListOf<String>()
            val statuses = mutableMapOf<String, String>()
            var done = 0
            val total = packages.size
            for (pkg in packages) {
                statuses[pkg] = "in_progress"
                _uiState.update { it.copy(packageOperationStatus = it.packageOperationStatus + (pkg to "in_progress"), packageOperationProgress = (done * 100) / total) }
                try {
                    ShizukuShell.exec("pm enable --user 0 $pkg")
                    results.add("Re-enabled $pkg")
                    statuses[pkg] = "success"
                } catch (e: Exception) {
                    results.add("Failed to re-enable $pkg: ${'$'}{e.message}")
                    statuses[pkg] = "failed"
                }
                done += 1
                _uiState.update { it.copy(packageOperationStatus = it.packageOperationStatus + statuses, packageOperationProgress = (done * 100) / total) }
            }
            // remove re-enabled packages from persistent history
            val remaining = loadDisabledHistory().filter { it !in packages }
            saveDisabledHistory(remaining)
            lastDisabledPackages = emptyList()
            _uiState.update { it.copy(settingsAuditOutput = it.settingsAuditOutput + results, packageOperationProgress = 100) }
        }
    }

    private fun recordLastDisabled(packages: List<String>) {
        lastDisabledPackages = packages.toList()
    }

    fun getLastDisabled(): List<String> = lastDisabledPackages

    fun disablePackages(packages: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            if (packages.isEmpty()) return@launch
            _uiState.update { it.copy(packageOperationStatus = packages.associateWith { "pending" }, packageOperationProgress = 0) }
            val total = packages.size
            var done = 0
            val statuses = mutableMapOf<String, String>()
            for (pkg in packages) {
                statuses[pkg] = "in_progress"
                _uiState.update { it.copy(packageOperationStatus = statuses.toMap(), packageOperationProgress = (done * 100) / total) }
                try {
                    ShizukuShell.exec("pm clear $pkg")
                    ShizukuShell.exec("pm disable-user --user 0 $pkg")
                    statuses[pkg] = "success"
                    _uiState.update { it.copy(settingsAuditOutput = it.settingsAuditOutput + "Disabled $pkg") }
                } catch (e: Exception) {
                    statuses[pkg] = "failed"
                    _uiState.update { it.copy(settingsAuditOutput = it.settingsAuditOutput + "Failed to disable $pkg: ${e.message}") }
                }
                done += 1
                _uiState.update { it.copy(packageOperationStatus = statuses.toMap(), packageOperationProgress = (done * 100) / total) }
            }
            // record last disabled for undo and persist history
            lastDisabledPackages = packages.filter { statuses[it] == "success" }
            if (lastDisabledPackages.isNotEmpty()) saveDisabledHistory(lastDisabledPackages)
            _uiState.update { it.copy(packageOperationProgress = 100) }
        }
    }

    private val recommendedSecure = listOf(
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

    private val recommendedSystem = listOf(
        "screen_off_timeout" to "15000",
        "nearby_scanning_enabled" to "0",
        "direct_share" to "0",
        "aod_mode" to "0",
        "mcf_continuity" to "0",
        "multi_control_enabled" to "0",
    )

    private val recommendedGlobal = listOf(
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

    fun startSettingsAudit(
        secureOverrides: Map<String, String> = emptyMap(),
        systemOverrides: Map<String, String> = emptyMap(),
        globalOverrides: Map<String, String> = emptyMap(),
    ) {
        if (_uiState.value.isSettingsScanning) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSettingsScanning = true, settingsAuditOutput = emptyList()) }
            val output = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                try {
                    // Secure
                    output.add("Checking [SECURE] Namespace...")
                    for ((key, expectedDefault) in recommendedSecure) {
                        val expected = secureOverrides[key] ?: expectedDefault
                        val res = ShizukuShell.exec("settings get secure $key")
                        val current = res.stdout.trim()
                        if (current != expected) {
                            output.add("MISMATCH: secure $key was [$current]. Resetting to policy [$expected].")
                            ShizukuShell.exec("settings put secure $key $expected")
                        } else {
                            output.add("SECURE: secure $key matches policy ($expected)")
                        }
                    }

                    // System
                    output.add("Checking [SYSTEM] Namespace...")
                    for ((key, expectedDefault) in recommendedSystem) {
                        val expected = systemOverrides[key] ?: expectedDefault
                        val res = ShizukuShell.exec("settings get system $key")
                        val current = res.stdout.trim()
                        if (current != expected) {
                            output.add("MISMATCH: system $key was [$current]. Resetting to policy [$expected].")
                            ShizukuShell.exec("settings put system $key $expected")
                        } else {
                            output.add("SYSTEM: system $key matches policy ($expected)")
                        }
                    }

                    // Global
                    output.add("Checking [GLOBAL] Namespace...")
                    for ((key, expectedDefault) in recommendedGlobal) {
                        val expected = globalOverrides[key] ?: expectedDefault
                        val res = ShizukuShell.exec("settings get global $key")
                        val current = res.stdout.trim()
                        if (current != expected) {
                            output.add("MISMATCH: global $key was [$current]. Resetting to policy [$expected].")
                            ShizukuShell.exec("settings put global $key $expected")
                        } else {
                            output.add("GLOBAL: global $key matches policy ($expected)")
                        }
                    }
                } catch (e: Exception) {
                    output.add("Settings audit failed: ${'$'}{e.message}")
                }
            }
            _uiState.update { it.copy(isSettingsScanning = false, settingsAuditOutput = output) }
        }
    }

    fun resetSettingsToRecommended() {
        viewModelScope.launch(Dispatchers.IO) {
            for ((key, value) in recommendedSecure) {
                ShizukuShell.exec("settings put secure $key $value")
            }
            for ((key, value) in recommendedSystem) {
                ShizukuShell.exec("settings put system $key $value")
            }
            for ((key, value) in recommendedGlobal) {
                ShizukuShell.exec("settings put global $key $value")
            }
            _uiState.update { it.copy(settingsAuditOutput = listOf("Settings reset to recommended values.")) }
        }
    }

    fun listPackages() {
        if (_uiState.value.isPackageListing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isPackageListing = true, packageList = emptyList()) }
            val packages = withContext(Dispatchers.IO) {
                val res = ShizukuShell.exec("pm list packages")
                res.stdout.lines().map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }
            }
            _uiState.update { it.copy(isPackageListing = false, packageList = packages) }
        }
    }

    fun requestShizuku() {
        logLine(LogKind.COMMAND, "Requesting Shizuku shell permission…")
        ShizukuShell.requestPermission()
    }

    fun refreshShizuku() = ShizukuShell.refreshState()

    fun startScan() {
        if (_uiState.value.isScanning) return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scanPhase = "Preparing scan…") }
            logLine(LogKind.INFO, "Scan started")
            val output = withContext(Dispatchers.IO) {
                ScanEngine.scan(getApplication()) { phase ->
                    _uiState.update { it.copy(scanPhase = phase) }
                }
            }
            _uiState.update {
                it.copy(
                    isScanning = false,
                    scanPhase = "",
                    threats = output.threats,
                    scannedPackages = output.scannedPackages,
                    lastScanAt = System.currentTimeMillis(),
                    usedShellForScan = output.usedShell,
                )
            }
            logLine(
                LogKind.SUCCESS,
                "Scan finished — ${output.scannedPackages} packages, ${output.threats.size} finding(s)",
            )
            for (threat in output.threats) {
                logLine(LogKind.WARN, "[${threat.severity.label}] ${threat.packageName} — ${threat.product}")
            }
        }
    }

    fun startForensicScan() {
        if (_uiState.value.isForensicScanning) return
        viewModelScope.launch {
            _uiState.update { it.copy(isForensicScanning = true, forensicPhase = "Preparing collection…", forensicErrors = emptyList()) }
            val output = withContext(Dispatchers.IO) {
                ForensicEngine.collect { phase ->
                    _uiState.update { it.copy(forensicPhase = phase) }
                }
            }
            _uiState.update {
                it.copy(
                    isForensicScanning = false,
                    forensicPhase = "",
                    forensicReport = output.report,
                    forensicErrors = output.errors,
                )
            }
        }
    }

    fun startPermissionAudit() {
        if (_uiState.value.isPermissionAuditScanning) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isPermissionAuditScanning = true, permissionAuditError = null)
            }
            try {
                val report = withContext(Dispatchers.IO) {
                    PermissionAuditEngine.scan(getApplication())
                }
                _uiState.update {
                    it.copy(
                        isPermissionAuditScanning = false,
                        permissionAuditReport = report,
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isPermissionAuditScanning = false,
                        permissionAuditError = error.message ?: "Permission audit failed.",
                    )
                }
            }
        }
    }

    fun runPlaybook(packageName: String) {
        val state = _uiState.value
        val threat = state.threats.firstOrNull { it.packageName == packageName } ?: return
        val existing = state.playbookRuns[packageName]
        if (existing?.state == RunState.RUNNING) return
        val steps = existing?.steps?.map { it.copy(state = StepState.PENDING, output = "") }
            ?: Playbook.stepsFor(threat).map { StepRun(it) }
        _uiState.update {
            it.copy(playbookRuns = it.playbookRuns + (packageName to PlaybookRun(steps, RunState.RUNNING)))
        }
        logLine(LogKind.INFO, "Playbook started for $packageName — ${steps.size} steps")
        executeSteps(packageName, steps.map { it.step.id })
    }

    fun runSingleStep(packageName: String, stepId: String) {
        val run = _uiState.value.playbookRuns[packageName] ?: return
        if (run.state == RunState.RUNNING) return
        if (run.steps.none { it.step.id == stepId }) return
        _uiState.update {
            it.copy(playbookRuns = it.playbookRuns + (packageName to run.copy(state = RunState.RUNNING)))
        }
        executeSteps(packageName, listOf(stepId))
    }

    fun clearLog() {
        _log.value = emptyList()
    }

    fun acknowledgeConsoleRisks() {
        _uiState.update { it.copy(consoleRisksAcknowledged = true) }
    }

    fun runConsoleCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isBlank() || _uiState.value.isConsoleCommandRunning) return
        _uiState.update { it.copy(isConsoleCommandRunning = true) }
        logLine(LogKind.COMMAND, "\$ $trimmed")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = ShizukuShell.exec(trimmed)
                if (result.stdout.isNotBlank()) logLine(LogKind.OUTPUT, result.stdout)
                if (result.stderr.isNotBlank()) logLine(LogKind.ERROR, result.stderr)
                when {
                    result.timedOut -> logLine(LogKind.ERROR, "Command timed out")
                    result.exitCode == 0 -> logLine(LogKind.SUCCESS, "Command finished")
                    else -> logLine(LogKind.ERROR, "Command exited with code ${result.exitCode}")
                }
            } finally {
                _uiState.update { it.copy(isConsoleCommandRunning = false) }
            }
        }
    }

    private fun executeSteps(packageName: String, stepIds: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (stepId in stepIds) {
                val step = currentStep(packageName, stepId) ?: continue
                setStep(packageName, stepId, StepState.RUNNING, "")
                logLine(LogKind.COMMAND, "\$ ${step.command}")
                val (result, output) = runStepCommand(step)
                setStep(packageName, stepId, result, output)
                val kind = when (result) {
                    StepState.DONE -> LogKind.SUCCESS
                    StepState.SKIPPED -> LogKind.WARN
                    else -> LogKind.ERROR
                }
                logLine(kind, output.take(500))
            }
            val run = _uiState.value.playbookRuns[packageName] ?: return@launch
            if (run.state == RunState.RUNNING) {
                val failed = run.steps.any { it.state == StepState.FAILED }
                _uiState.update {
                    it.copy(
                        playbookRuns = it.playbookRuns +
                            (packageName to run.copy(state = if (failed) RunState.FAILED else RunState.DONE)),
                    )
                }
                logLine(
                    if (failed) LogKind.ERROR else LogKind.SUCCESS,
                    "Playbook finished for $packageName" + if (failed) " — with issues (see findings)" else " — all steps succeeded",
                )
            }
        }
    }

    private suspend fun runStepCommand(step: RemovalStep): Pair<StepState, String> {
        if (ShizukuShell.state.value != ShizukuState.READY) {
            return StepState.SKIPPED to "Shell not connected — step skipped"
        }
        val res = ShizukuShell.exec(step.command)
        val combined = listOf(res.stdout.trim(), res.stderr.trim())
            .filter { it.isNotBlank() }
            .joinToString("\n")
        return when {
            res.timedOut -> StepState.FAILED to "Command timed out"
            step.id == "verify" ->
                if (res.stdout.contains("package:")) {
                    StepState.FAILED to "Package still installed:\n$combined"
                } else {
                    StepState.DONE to "Confirmed: package no longer installed for user 0"
                }
            res.exitCode == 0 -> StepState.DONE to combined.ifBlank { "OK (no output)" }
            else -> StepState.FAILED to combined.ifBlank { "Failed (exit code ${res.exitCode})" }
        }
    }

    private fun currentStep(packageName: String, stepId: String): RemovalStep? {
        return _uiState.value.playbookRuns[packageName]?.steps
            ?.firstOrNull { it.step.id == stepId }?.step
    }

    private fun setStep(packageName: String, stepId: String, stepState: StepState, output: String) {
        _uiState.update { s ->
            val run = s.playbookRuns[packageName] ?: return@update s
            s.copy(
                playbookRuns = s.playbookRuns + (packageName to run.copy(
                    steps = run.steps.map {
                        if (it.step.id == stepId) it.copy(state = stepState, output = output) else it
                    },
                )),
            )
        }
    }

    private fun logLine(kind: LogKind, text: String) {
        _log.update { list -> (list + LogLine(System.currentTimeMillis(), kind, text)).takeLast(600) }
    }
}
