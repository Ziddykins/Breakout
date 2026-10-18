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
    val permissionOperationStatus: Map<String, String> = emptyMap(),
    val isSettingsScanning: Boolean = false,
    val settingsAuditOutput: List<String> = emptyList(),
    val deviceSettings: Map<String, Map<String, String>> = emptyMap(),
    val settingsScanError: String? = null,
    val settingsActionMessage: String? = null,
    val isSettingsActionRunning: Boolean = false,
    val isPackageListing: Boolean = false,
    val packageList: List<String> = emptyList(),
    val systemPackageList: List<String> = emptyList(),
    val userPackageList: List<String> = emptyList(),
    val packageListingError: String? = null,
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

    private data class SettingsReadResult(
        val namespaces: Map<String, Map<String, String>>,
        val error: String? = null,
    )

    private suspend fun readDeviceSettings(): SettingsReadResult = withContext(Dispatchers.IO) {
        val namespaces = linkedMapOf<String, Map<String, String>>()
        for (namespace in listOf("secure", "system", "global")) {
            val result = ShizukuShell.exec("settings list $namespace")
            if (result.exitCode != 0 || result.timedOut) {
                return@withContext SettingsReadResult(
                    emptyMap(),
                    result.stderr.ifBlank { result.stdout }.ifBlank {
                        "Could not read the $namespace settings namespace."
                    }.trim(),
                )
            }
            namespaces[namespace] = result.stdout.lineSequence()
                .mapNotNull { line ->
                    val separator = line.indexOf('=')
                    if (separator <= 0) null
                    else line.substring(0, separator).trim() to line.substring(separator + 1)
                }
                .toMap()
                .toSortedMap()
        }
        SettingsReadResult(namespaces)
    }

    fun startSettingsAudit() {
        if (_uiState.value.isSettingsScanning) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSettingsScanning = true, settingsScanError = null) }
            val result = readDeviceSettings()
            _uiState.update {
                it.copy(
                    isSettingsScanning = false,
                    deviceSettings = result.namespaces,
                    settingsScanError = result.error,
                    settingsActionMessage = null,
                )
            }
        }
    }

    fun applyDeviceSetting(namespace: String, key: String, value: String) {
        val allowedNamespaces = setOf("secure", "system", "global")
        if (namespace !in allowedNamespaces ||
            !key.matches(Regex("[A-Za-z0-9_]+")) ||
            !value.matches(Regex("[A-Za-z0-9_.-]+")) ||
            key !in _uiState.value.deviceSettings[namespace].orEmpty()
        ) {
            _uiState.update {
                it.copy(settingsActionMessage = "Setting was not changed: it is not in the current device settings list.")
            }
            return
        }
        if (_uiState.value.isSettingsActionRunning) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSettingsActionRunning = true, settingsActionMessage = null) }
            val result = withContext(Dispatchers.IO) {
                ShizukuShell.exec("settings put $namespace $key $value")
            }
            if (result.exitCode != 0 || result.timedOut) {
                _uiState.update {
                    it.copy(
                        isSettingsActionRunning = false,
                        settingsActionMessage = result.stderr.ifBlank { result.stdout }
                            .ifBlank { "Android rejected the change to $namespace.$key." }.trim(),
                    )
                }
            } else {
                val refreshed = readDeviceSettings()
                _uiState.update {
                    it.copy(
                        isSettingsActionRunning = false,
                        deviceSettings = refreshed.namespaces,
                        settingsScanError = refreshed.error,
                        settingsActionMessage = if (refreshed.error == null) {
                            "Applied $namespace.$key = $value"
                        } else {
                            "Change sent, but settings could not be refreshed: ${refreshed.error}"
                        },
                    )
                }
            }
        }
    }

    fun listPackages() {
        if (_uiState.value.isPackageListing) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isPackageListing = true,
                    packageList = emptyList(),
                    systemPackageList = emptyList(),
                    userPackageList = emptyList(),
                    packageListingError = null,
                )
            }
            val result = withContext(Dispatchers.IO) {
                val systemResult = ShizukuShell.exec("pm list packages -s")
                val userResult = ShizukuShell.exec("pm list packages -3")
                val failure = listOf(systemResult, userResult).firstOrNull {
                    it.exitCode != 0 || it.timedOut
                }
                if (failure != null) {
                    Result.failure(IllegalStateException(failure.stderr.ifBlank { "Package listing failed." }))
                } else {
                    fun parsePackages(output: String) = output.lines()
                        .map { it.removePrefix("package:").trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .sorted()

                    Result.success(
                        parsePackages(systemResult.stdout) to parsePackages(userResult.stdout),
                    )
                }
            }
            _uiState.update { state ->
                val packageGroups = result.getOrNull()
                state.copy(
                    isPackageListing = false,
                    packageList = packageGroups?.let { (system, user) -> (system + user).distinct().sorted() }
                        ?: emptyList(),
                    systemPackageList = packageGroups?.first ?: emptyList(),
                    userPackageList = packageGroups?.second ?: emptyList(),
                    packageListingError = result.exceptionOrNull()?.message,
                )
            }
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
                        permissionOperationStatus = it.permissionOperationStatus.mapValues { (_, status) ->
                            if (status == "in_progress") "Changed; audit refreshed." else status
                        },
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isPermissionAuditScanning = false,
                        permissionAuditError = error.message ?: "Permission audit failed.",
                        permissionOperationStatus = it.permissionOperationStatus.mapValues { (_, status) ->
                            if (status == "in_progress") "Changed; audit refresh failed." else status
                        },
                    )
                }
            }
        }
    }

    fun setPermissionGranted(packageName: String, permissionName: String, granted: Boolean) {
        val validPackage = packageName.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))
        val validPermission = permissionName.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))
        val finding = _uiState.value.permissionAuditReport?.findings
            ?.firstOrNull { it.packageName == packageName }
        val permissionFound = finding?.permissions?.any { it.name == permissionName } == true
        if (!validPackage || !validPermission || !permissionFound) {
            _uiState.update {
                it.copy(permissionOperationStatus = it.permissionOperationStatus + (
                    "$packageName|$permissionName" to "Permission is not part of the current audit results."
                ))
            }
            return
        }

        val operationKey = "$packageName|$permissionName"
        if (_uiState.value.isPermissionAuditScanning ||
            _uiState.value.permissionOperationStatus[operationKey] == "in_progress"
        ) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(permissionOperationStatus = it.permissionOperationStatus + (operationKey to "in_progress"))
            }
            val action = if (granted) "grant" else "revoke"
            val result = withContext(Dispatchers.IO) {
                ShizukuShell.exec("pm $action --user 0 $packageName $permissionName")
            }
            if (result.exitCode != 0 || result.timedOut) {
                val details = result.stderr.ifBlank { result.stdout }
                    .ifBlank { "Android rejected the $action request." }.trim()
                _uiState.update {
                    it.copy(
                        permissionOperationStatus = it.permissionOperationStatus +
                            (operationKey to "Could not $action: $details"),
                    )
                }
            } else {
                startPermissionAudit()
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
