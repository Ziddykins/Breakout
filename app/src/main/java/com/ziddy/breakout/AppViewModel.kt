package com.ziddy.breakout

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ziddy.breakout.data.HardeningRecommendations
import com.ziddy.breakout.data.PackageIntegrityMonitor
import com.ziddy.breakout.data.PackageIntegrityRecord
import com.ziddy.breakout.data.UserPlaybookStore
import com.ziddy.breakout.engine.Playbook
import com.ziddy.breakout.engine.UserPlaybookEngine
import com.ziddy.breakout.engine.ForensicEngine
import com.ziddy.breakout.engine.PermissionAuditEngine
import com.ziddy.breakout.engine.ScanEngine
import com.ziddy.breakout.model.ForensicReport
import com.ziddy.breakout.model.LogKind
import com.ziddy.breakout.model.LogLine
import com.ziddy.breakout.model.PermissionAuditReport
import com.ziddy.breakout.model.PackageAction
import com.ziddy.breakout.model.PlaybookRun
import com.ziddy.breakout.model.RemovalStep
import com.ziddy.breakout.model.RunState
import com.ziddy.breakout.model.ShizukuState
import com.ziddy.breakout.model.StepRun
import com.ziddy.breakout.model.StepState
import com.ziddy.breakout.model.Threat
import com.ziddy.breakout.model.UserPlaybook
import com.ziddy.breakout.model.UserPlaybookRunResult
import com.ziddy.breakout.shell.ShizukuShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppUiState(
    val isScanning: Boolean = false,
    val isConsoleCommandRunning: Boolean = false,
    val consoleRisksAcknowledged: Boolean = false,
    val disableConsoleRiskAcknowledgment: Boolean = false,
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
    val disabledPackageList: Set<String> = emptySet(),
    val apexPackageList: Set<String> = emptySet(),
    val packageListingError: String? = null,
    val packageOperationStatus: Map<String, String> = emptyMap(), // package -> pending|in_progress|success|failed
    val packageOperationProgress: Int = 0, // 0..100
    val packageMonitoringEnabled: Boolean = false,
    val isPackageMonitoringSetup: Boolean = false,
    val packageMonitoringMessage: String? = null,
    val packageIntegrityRecords: Map<String, PackageIntegrityRecord> = emptyMap(),
    val userPlaybooks: List<UserPlaybook> = emptyList(),
    val scannerPlaybookIds: Set<String> = emptySet(),
    val userPlaybookRuns: Map<String, Map<String, UserPlaybookRunResult>> = emptyMap(),
    val userPlaybookError: String? = null,
    val userPlaybookSaveStatus: String? = null,
    val isUserPlaybookRunning: Boolean = false,
    val userPlaybookRunResult: UserPlaybookRunResult? = null,
)

/** Shared app state: scan results, playbook runs, and the command console log. */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private companion object {
        const val PREFS_NAME = "breakout_prefs"
        const val PREF_DISABLED_HISTORY = "disabled_history"
        const val PREF_CONSOLE_RISK_BYPASS = "disable_console_risk_acknowledgment"
        const val PREF_SCANNER_PLAYBOOK_IDS = "scanner_playbook_ids"
    }

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private val userPlaybookStore = UserPlaybookStore(app)

    val shizukuState: StateFlow<ShizukuState> = ShizukuShell.state
    val shizukuError: StateFlow<String?> = ShizukuShell.connectionError

    private val _log = MutableStateFlow<List<LogLine>>(emptyList())
    val log: StateFlow<List<LogLine>> = _log.asStateFlow()

    private val _permissionResultEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val permissionResultEvents: SharedFlow<String> = _permissionResultEvents.asSharedFlow()
    private var lastDisabledPackages: List<String> = emptyList()

    init {
        val bypassRisksPhrase = getApplication<Application>()
            .getSharedPreferences(PREFS_NAME, 0)
            .getBoolean(PREF_CONSOLE_RISK_BYPASS, false)
        _uiState.update {
            it.copy(
                disableConsoleRiskAcknowledgment = bypassRisksPhrase,
                consoleRisksAcknowledged = bypassRisksPhrase,
                scannerPlaybookIds = getApplication<Application>()
                    .getSharedPreferences(PREFS_NAME, 0)
                    .getStringSet(PREF_SCANNER_PLAYBOOK_IDS, emptySet())
                    .orEmpty()
                    .toSet(),
            )
        }
        ShizukuShell.refreshState()
        logLine(LogKind.INFO, "Breakout ready. Connect Shizuku to unlock removal actions.")
        logLine(LogKind.WARN, "WARNING: Console commands can negatively affect your phone or brick it.")
        logLine(LogKind.ERROR, "i understand the risks")
        if (bypassRisksPhrase) {
            logLine(LogKind.INFO, "Risk phrase bypass is enabled in app settings.")
        } else {
            logLine(LogKind.INFO, "Type the phrase above at the prompt, then press Enter to unlock commands.")
        }
        lastDisabledPackages = loadDisabledHistory()
        loadPackageIntegrityState()
        try {
            _uiState.update { it.copy(userPlaybooks = userPlaybookStore.load()) }
        } catch (error: Exception) {
            _uiState.update {
                it.copy(userPlaybookError = error.message ?: "Could not load saved playbooks.")
            }
            logLine(LogKind.ERROR, "Could not load saved playbooks: ${error.message ?: "unknown error"}")
        }
        viewModelScope.launch {
            PackageIntegrityMonitor.packageUpdates.collect { packageName ->
                try {
                    val records = withContext(Dispatchers.IO) {
                        PackageIntegrityMonitor.loadRecords(getApplication())
                    }
                    val record = records[packageName]
                    _uiState.update {
                        it.copy(
                            packageIntegrityRecords = records,
                            packageMonitoringMessage = record?.let(::packageIntegrityMessage),
                        )
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    logLine(
                        LogKind.ERROR,
                        "Could not refresh package integrity results: ${error.message ?: "unknown error"}",
                    )
                }
            }
        }
    }

    private fun loadPackageIntegrityState() {
        val context = getApplication<Application>()
        val enabled = PackageIntegrityMonitor.isEnabled(context)
        try {
            PackageIntegrityMonitor.syncReceiverState(context)
            val records = PackageIntegrityMonitor.loadRecords(context)
            _uiState.update {
                it.copy(
                    packageMonitoringEnabled = enabled,
                    packageIntegrityRecords = records,
                )
            }
        } catch (error: Exception) {
            _uiState.update {
                it.copy(
                    packageMonitoringEnabled = enabled,
                    packageMonitoringMessage = "Could not load package integrity records: ${error.message ?: "unknown error"}",
                )
            }
            logLine(
                LogKind.ERROR,
                "Could not load package integrity records: ${error.message ?: "unknown error"}",
            )
        }
    }

    private fun packageIntegrityMessage(record: PackageIntegrityRecord): String = when (record.status) {
        com.ziddy.breakout.data.PackageIntegrityStatus.UNCHANGED ->
            "${record.packageName}: APK hash matches its locally captured baseline."
        com.ziddy.breakout.data.PackageIntegrityStatus.APK_CHANGED ->
            "${record.packageName}: APK hash changed since monitoring started; this can be a normal app update."
        com.ziddy.breakout.data.PackageIntegrityStatus.SIGNER_CHANGED ->
            "${record.packageName}: APK signing certificate changed since monitoring started."
        com.ziddy.breakout.data.PackageIntegrityStatus.BASELINE_UNAVAILABLE ->
            "${record.packageName}: no complete APK hash baseline is available."
        com.ziddy.breakout.data.PackageIntegrityStatus.CHECK_FAILED ->
            "${record.packageName}: hash check failed: ${record.error ?: "unknown error"}"
    }

    fun setPackageMonitoringEnabled(enabled: Boolean) {
        if (_uiState.value.isPackageMonitoringSetup ||
            _uiState.value.packageMonitoringEnabled == enabled
        ) return
        val context = getApplication<Application>()
        if (!enabled) {
            try {
                PackageIntegrityMonitor.setEnabled(context, false)
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        packageMonitoringEnabled = false,
                        packageMonitoringMessage =
                            "Monitoring is off, but Android could not disable its package receiver: ${error.message ?: "unknown error"}",
                    )
                }
                return
            }
            _uiState.update {
                it.copy(
                    packageMonitoringEnabled = false,
                    packageMonitoringMessage = "Package monitoring is off. Existing baselines are kept until monitoring is enabled again.",
                )
            }
            return
        }

        _uiState.update {
            it.copy(isPackageMonitoringSetup = true, packageMonitoringMessage = null)
        }
        viewModelScope.launch {
            try {
                val records = withContext(Dispatchers.IO) {
                    PackageIntegrityMonitor.captureBaseline(context)
                }
                PackageIntegrityMonitor.setEnabled(context, true)
                val unavailable = records.values.count {
                    it.status == com.ziddy.breakout.data.PackageIntegrityStatus.CHECK_FAILED ||
                        it.status == com.ziddy.breakout.data.PackageIntegrityStatus.BASELINE_UNAVAILABLE
                }
                _uiState.update {
                    it.copy(
                        packageMonitoringEnabled = true,
                        isPackageMonitoringSetup = false,
                        packageIntegrityRecords = records,
                        packageMonitoringMessage =
                            "Monitoring enabled; ${records.size - unavailable} of ${records.size} package baselines captured." +
                                if (unavailable > 0) " $unavailable could not be hashed; see package status." else "",
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                _uiState.update {
                    it.copy(
                        isPackageMonitoringSetup = false,
                        packageMonitoringMessage = "Baseline capture was interrupted. Monitoring was not enabled.",
                    )
                }
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isPackageMonitoringSetup = false,
                        packageMonitoringMessage =
                            "Could not start package monitoring: ${error.message ?: "unknown error"}",
                    )
                }
            }
        }
    }

    fun refreshPackageIntegrity() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            try {
                val records = withContext(Dispatchers.IO) {
                    PackageIntegrityMonitor.loadRecords(context)
                }
                _uiState.update { it.copy(packageIntegrityRecords = records) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = "Could not load package integrity records: ${error.message ?: "unknown error"}"
                _uiState.update { it.copy(packageMonitoringMessage = message) }
                logLine(LogKind.ERROR, message)
            }
        }
    }

    // Track last disabled packages to allow undo in-session
    // persistent history stored in SharedPreferences as JSON array of package names
    private fun saveDisabledHistory(packages: List<String>) {
        try {
            val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, 0)
            val json = org.json.JSONArray(packages).toString()
            prefs.edit {putString(PREF_DISABLED_HISTORY, json)}
        } catch (error: Exception) {
            logLine(LogKind.ERROR, "Could not save disabled package history: ${error.message ?: "unknown error"}")
        }
    }

    fun loadDisabledHistory(): List<String> {
        return try {
            val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, 0)
            val json = prefs.getString(PREF_DISABLED_HISTORY, "[]") ?: "[]"
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            logLine(LogKind.ERROR, "Could not read disabled package history: ${e.message ?: "invalid stored data"}")
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
                    val result = ShizukuShell.exec("pm enable --user 0 $pkg")
                    requireSuccessfulShellResult(result, "Re-enable $pkg")
                    results.add("Re-enabled $pkg")
                    statuses[pkg] = "success"
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    results.add("Failed to re-enable $pkg: ${e.message ?: "unknown error"}")
                    statuses[pkg] = "failed"
                }

                done += 1
                _uiState.update { it.copy(packageOperationStatus = it.packageOperationStatus + statuses, packageOperationProgress = (done * 100) / total) }
            }
            // remove re-enabled packages from persistent history
            val remaining = loadDisabledHistory().filterNot { it in packages && statuses[it] == "success" }
            saveDisabledHistory(remaining)
            lastDisabledPackages = packages.filter { statuses[it] != "success" }
            _uiState.update { it.copy(settingsAuditOutput = it.settingsAuditOutput + results, packageOperationProgress = 100) }
        }
    }

    private fun recordLastDisabled(packages: List<String>) {
        lastDisabledPackages = packages.toList()
    }

    fun getLastDisabled(): List<String> = lastDisabledPackages

    fun runPackageAction(packageName: String, action: PackageAction) {
        if (!packageName.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))) {
            logLine(LogKind.ERROR, "Package action rejected: invalid package name.")
            return
        }
        if (packageName !in _uiState.value.packageList) {
            logLine(LogKind.ERROR, "Package action rejected: $packageName is not in the current installed-package list.")
            return
        }
        if (ShizukuShell.state.value != ShizukuState.READY) {
            _uiState.update {
                it.copy(packageOperationStatus = it.packageOperationStatus + (packageName to "failed"))
            }
            logLine(LogKind.ERROR, "Could not ${action.title.lowercase()} $packageName: Shizuku is not connected.")
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    packageOperationStatus = it.packageOperationStatus + (packageName to "in_progress"),
                    packageOperationProgress = 0,
                )
            }
            val command = when (action) {
                PackageAction.REINSTALL_AS_USER_0 -> "pm install-existing --user 0 $packageName"
                PackageAction.UNINSTALL -> "pm uninstall --user 0 $packageName"
                PackageAction.CLEAR_DATA -> "pm clear $packageName"
                PackageAction.FREEZE -> "pm disable-user --user 0 $packageName"
            }
            try {
                val result = withContext(Dispatchers.IO) { ShizukuShell.exec(command) }
                if (result.timedOut || result.exitCode != 0) {
                    val details = result.stderr.ifBlank { result.stdout }
                        .ifBlank { "Command failed with exit code ${result.exitCode}." }.trim()
                    _uiState.update {
                        it.copy(
                            packageOperationStatus = it.packageOperationStatus + (packageName to "failed"),
                            packageOperationProgress = 100,
                        )
                    }
                    logLine(LogKind.ERROR, "${action.title} failed for $packageName: $details")
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        packageOperationStatus = it.packageOperationStatus + (packageName to "success"),
                        packageOperationProgress = 100,
                    )
                }
                logLine(LogKind.SUCCESS, "${action.title} completed for $packageName.")
                if (action == PackageAction.FREEZE) {
                    lastDisabledPackages = (lastDisabledPackages + packageName).distinct()
                    saveDisabledHistory(lastDisabledPackages)
                } else if (action == PackageAction.REINSTALL_AS_USER_0) {
                    val remaining = loadDisabledHistory().filterNot { it == packageName }
                    saveDisabledHistory(remaining)
                    lastDisabledPackages = remaining
                }
                listPackages()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                _uiState.update {
                    it.copy(
                        packageOperationStatus = it.packageOperationStatus + (packageName to "failed"),
                        packageOperationProgress = 100,
                    )
                }
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        packageOperationStatus = it.packageOperationStatus + (packageName to "failed"),
                        packageOperationProgress = 100,
                    )
                }
                logLine(LogKind.ERROR, "${action.title} failed for $packageName: ${error.message ?: "unknown error"}")
            }
        }
    }

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
                    val disableResult = ShizukuShell.exec("pm disable-user --user 0 $pkg")
                    requireSuccessfulShellResult(disableResult, "Disable $pkg")
                    statuses[pkg] = "success"
                    _uiState.update { it.copy(settingsAuditOutput = it.settingsAuditOutput + "Froze $pkg for user 0") }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    statuses[pkg] = "failed"
                    _uiState.update {
                        it.copy(
                            settingsAuditOutput = it.settingsAuditOutput +
                                "Failed to freeze $pkg: ${e.message ?: "unknown error"}.",
                        )
                    }
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

    private fun requireSuccessfulShellResult(
        result: ShizukuShell.ShellResult,
        operation: String,
    ) {
        if (result.timedOut || result.exitCode != 0) {
            val reason = result.stderr.ifBlank { result.stdout }
                .ifBlank { "command exited with code ${result.exitCode}" }
                .trim()
            throw IllegalStateException("$operation failed: $reason")
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
        if (ShizukuShell.state.value != ShizukuState.READY) {
            _uiState.update {
                it.copy(settingsActionMessage = "Setting was not changed: connect Shizuku before applying recommendations.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSettingsActionRunning = true, settingsActionMessage = null) }
            try {
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
                    val verified = refreshed.namespaces[namespace]?.get(key) == value
                    _uiState.update {
                        it.copy(
                            isSettingsActionRunning = false,
                            deviceSettings = refreshed.namespaces,
                            settingsScanError = refreshed.error,
                            settingsActionMessage = if (refreshed.error != null) {
                                "Change sent, but settings could not be refreshed: ${refreshed.error}"
                            } else if (verified) {
                                "Applied $namespace.$key = $value"
                            } else {
                                "Android accepted the command, but $namespace.$key did not read back as $value."
                            },
                        )
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                _uiState.update {
                    it.copy(isSettingsActionRunning = false, settingsActionMessage = "Change interrupted; refresh settings to verify the result.")
                }
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isSettingsActionRunning = false,
                        settingsActionMessage = "Could not apply $namespace.$key: ${error.message ?: "unknown error"}",
                    )
                }
            }
        }
    }

    fun applyAllRecommendedDeviceSettings() {
        val pendingChanges = HardeningRecommendations.pendingChanges(_uiState.value.deviceSettings)
        if (pendingChanges.isEmpty()) {
            _uiState.update { it.copy(settingsActionMessage = "All recognized settings already match the recommendations.") }
            return
        }
        if (_uiState.value.isSettingsActionRunning) return
        if (ShizukuShell.state.value != ShizukuState.READY) {
            _uiState.update {
                it.copy(settingsActionMessage = "No settings were changed: connect Shizuku before applying recommendations.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSettingsActionRunning = true, settingsActionMessage = null) }
            try {
                val commandFailures = mutableListOf<String>()
                for (change in pendingChanges) {
                    val result = withContext(Dispatchers.IO) {
                        ShizukuShell.exec(
                            "settings put ${change.namespace} ${change.key} ${change.recommendedValue}",
                        )
                    }
                    if (result.exitCode != 0 || result.timedOut) {
                        val reason = result.stderr.ifBlank { result.stdout }
                            .ifBlank { "command rejected or timed out" }
                            .trim()
                        commandFailures += "${change.namespace}.${change.key}: $reason"
                    }
                }

                val refreshed = readDeviceSettings()
                val appliedCount = pendingChanges.count { change ->
                    refreshed.namespaces[change.namespace]?.get(change.key) == change.recommendedValue
                }
                val notApplied = pendingChanges.size - appliedCount
                val resultMessage = when {
                    refreshed.error != null ->
                        "Changes were attempted, but read-back verification failed: ${refreshed.error}"
                    notApplied == 0 ->
                        "Verified all $appliedCount recommended setting changes."
                    else ->
                        "Verified $appliedCount of ${pendingChanges.size} changes; $notApplied did not match after refresh."
                }
                val failureDetails = commandFailures.takeIf { it.isNotEmpty() }
                    ?.joinToString(prefix = " Command errors: ", separator = "; ")
                    .orEmpty()
                _uiState.update {
                    it.copy(
                        isSettingsActionRunning = false,
                        deviceSettings = refreshed.namespaces,
                        settingsScanError = refreshed.error,
                        settingsActionMessage = resultMessage + failureDetails,
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                _uiState.update {
                    it.copy(
                        isSettingsActionRunning = false,
                        settingsActionMessage = "Bulk apply interrupted; refresh settings to verify any partial changes.",
                    )
                }
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isSettingsActionRunning = false,
                        settingsActionMessage = "Bulk apply could not finish: ${error.message ?: "unknown error"}. Refresh to verify any partial changes.",
                    )
                }
            }
        }
    }

    fun setConsoleRiskAcknowledgmentDisabled(disabled: Boolean) {
        getApplication<Application>()
            .getSharedPreferences(PREFS_NAME, 0)
            .edit {
                putBoolean(PREF_CONSOLE_RISK_BYPASS, disabled)
            }
        _uiState.update {
            it.copy(
                disableConsoleRiskAcknowledgment = disabled,
                consoleRisksAcknowledged = disabled,
            )
        }
        logLine(
            LogKind.INFO,
            if (disabled) "Console risk phrase bypass enabled." else "Console risk phrase bypass disabled.",
        )
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
                    disabledPackageList = emptySet(),
                    apexPackageList = emptySet(),
                    packageListingError = null,
                )
            }
            val result = withContext(Dispatchers.IO) {
                val systemResult = ShizukuShell.exec("pm list packages -s")
                val userResult = ShizukuShell.exec("pm list packages -3")
                val disabledResult = ShizukuShell.exec("pm list packages -d")
                val apexResult = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    ShizukuShell.exec("pm list packages --apex-only")
                } else {
                    null
                }
                val failure = (listOf(systemResult, userResult, disabledResult) + listOfNotNull(apexResult)).firstOrNull {
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
                        PackageInventory(
                            system = parsePackages(systemResult.stdout),
                            user = parsePackages(userResult.stdout),
                            disabled = parsePackages(disabledResult.stdout).toSet(),
                            apex = apexResult?.let { parsePackages(it.stdout).toSet() }.orEmpty(),
                        ),
                    )
                }
            }
            _uiState.update { state ->
                val inventory = result.getOrNull()
                state.copy(
                    isPackageListing = false,
                    packageList = inventory?.let { (it.system + it.user).distinct().sorted() }
                        ?: emptyList(),
                    systemPackageList = inventory?.system ?: emptyList(),
                    userPackageList = inventory?.user ?: emptyList(),
                    disabledPackageList = inventory?.disabled.orEmpty(),
                    apexPackageList = inventory?.apex.orEmpty(),
                    packageListingError = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    private data class PackageInventory(
        val system: List<String>,
        val user: List<String>,
        val disabled: Set<String>,
        val apex: Set<String>,
    )

    fun saveUserPlaybook(playbook: UserPlaybook) {
        val validationError = UserPlaybookEngine.validate(playbook)
        if (validationError != null) {
            _uiState.update { it.copy(userPlaybookError = validationError) }
            return
        }
        val updated = _uiState.value.userPlaybooks
            .filterNot { it.id == playbook.id } + playbook
        viewModelScope.launch {
            _uiState.update { it.copy(userPlaybookSaveStatus = "Saving playbook…", userPlaybookError = null) }
            try {
                withContext(Dispatchers.IO) { userPlaybookStore.save(updated) }
                _uiState.update {
                    it.copy(
                        userPlaybooks = updated,
                        userPlaybookError = null,
                        userPlaybookSaveStatus = "Saved '${playbook.name}' locally.",
                    )
                }
                logLine(LogKind.SUCCESS, "Saved playbook '${playbook.name}'.")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                _uiState.update { it.copy(userPlaybookSaveStatus = null) }
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        userPlaybookSaveStatus = null,
                        userPlaybookError = "Could not save playbook: ${error.message ?: "unknown error"}",
                    )
                }
                logLine(LogKind.ERROR, "Could not save playbook: ${error.message ?: "unknown error"}")
            }
        }
    }

    fun deleteUserPlaybook(playbookId: String) {
        val updated = _uiState.value.userPlaybooks.filterNot { it.id == playbookId }
        val scannerPlaybookIds = _uiState.value.scannerPlaybookIds - playbookId
        viewModelScope.launch {
            _uiState.update { it.copy(userPlaybookSaveStatus = "Deleting playbook…", userPlaybookError = null) }
            try {
                val preferences = getApplication<Application>().getSharedPreferences(PREFS_NAME, 0)
                withContext(Dispatchers.IO) {
                    if (!preferences.edit().putStringSet(PREF_SCANNER_PLAYBOOK_IDS, scannerPlaybookIds).commit()) {
                        throw IllegalStateException("Android could not persist scanner playbook selections.")
                    }
                    try {
                        userPlaybookStore.save(updated)
                    } catch (error: Exception) {
                        if (!preferences.edit(commit = true) {
                                    putStringSet(PREF_SCANNER_PLAYBOOK_IDS, _uiState.value.scannerPlaybookIds)
                                }
                        ) {
                            error.addSuppressed(
                                IllegalStateException("Scanner playbook selection rollback also failed."),
                            )
                        }
                        throw error
                    }
                }
                _uiState.update {
                    it.copy(
                        userPlaybooks = updated,
                        scannerPlaybookIds = scannerPlaybookIds,
                        userPlaybookError = null,
                        userPlaybookSaveStatus = "Playbook deleted.",
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                _uiState.update { it.copy(userPlaybookSaveStatus = null) }
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        userPlaybookSaveStatus = null,
                        userPlaybookError = "Could not delete playbook: ${error.message ?: "unknown error"}",
                    )
                }
                logLine(LogKind.ERROR, "Could not delete playbook: ${error.message ?: "unknown error"}")
            }
        }
    }

    fun setUserPlaybookInScanner(playbookId: String, included: Boolean) {
        if (_uiState.value.userPlaybooks.none { it.id == playbookId }) {
            _uiState.update { it.copy(userPlaybookError = "The selected playbook no longer exists.") }
            return
        }
        val updated = if (included) {
            _uiState.value.scannerPlaybookIds + playbookId
        } else {
            _uiState.value.scannerPlaybookIds - playbookId
        }
        val preferences = getApplication<Application>().getSharedPreferences(PREFS_NAME, 0)
        if (!preferences.edit(commit = true) {putStringSet(PREF_SCANNER_PLAYBOOK_IDS, updated)}) {
            _uiState.update { it.copy(userPlaybookError = "Could not save scanner playbook selection.") }
            logLine(LogKind.ERROR, "Could not save scanner playbook selection.")
            return
        }
        _uiState.update {
            it.copy(
                scannerPlaybookIds = updated,
                userPlaybookError = null,
                userPlaybookSaveStatus = if (included) "Added playbook to the scanner." else "Removed playbook from the scanner.",
            )
        }
    }

    fun runUserPlaybook(
        playbookId: String,
        packageVariable: String? = null,
        scannerPackageName: String? = null,
    ) {
        if (_uiState.value.isUserPlaybookRunning) return
        if (!ShizukuShell.isReady()) {
            _uiState.update { it.copy(userPlaybookError = "Connect Shizuku before running a playbook.") }
            return
        }
        val playbook = _uiState.value.userPlaybooks.firstOrNull { it.id == playbookId }
        if (playbook == null) {
            _uiState.update { it.copy(userPlaybookError = "The selected playbook no longer exists.") }
            return
        }
        if (scannerPackageName != null) {
            if (_uiState.value.threats.none { it.packageName == scannerPackageName }) {
                _uiState.update { it.copy(userPlaybookError = "The scanner finding is no longer available.") }
                return
            }
            if (playbookId !in _uiState.value.scannerPlaybookIds) {
                _uiState.update { it.copy(userPlaybookError = "Add this playbook to the scanner before running it here.") }
                return
            }
        }
        UserPlaybookEngine.validate(playbook)?.let { error ->
            _uiState.update { it.copy(userPlaybookError = error) }
            return
        }
        if (UserPlaybookEngine.requiresPackageVariable(playbook)) {
            UserPlaybookEngine.validatePackageVariable(packageVariable)?.let { error ->
                _uiState.update { it.copy(userPlaybookError = error) }
                return
            }
        }

        viewModelScope.launch {
            val initialResult = UserPlaybookRunResult(playbookId, emptyList(), isRunning = true)
            _uiState.update {
                it.copy(isUserPlaybookRunning = true, userPlaybookError = null)
            }
            recordUserPlaybookRun(playbookId, scannerPackageName, initialResult)
            logLine(
                LogKind.WARN,
                "Running user-created playbook '${playbook.name}'" +
                    (scannerPackageName?.let { " for $it" } ?: "") + ".",
            )
            try {
                val result = UserPlaybookEngine.run(
                    playbook = playbook,
                    packageVariable = packageVariable,
                    execute = { command ->
                        logLine(LogKind.COMMAND, command)
                        val shellResult = withContext(Dispatchers.IO) { ShizukuShell.exec(command) }
                        UserPlaybookEngine.CommandResult(
                            exitCode = shellResult.exitCode,
                            stdout = shellResult.stdout,
                            stderr = shellResult.stderr,
                            timedOut = shellResult.timedOut,
                        )
                    },
                    onStepStarted = { step ->
                        val current = if (scannerPackageName == null) {
                            _uiState.value.userPlaybookRunResult
                        } else {
                            _uiState.value.userPlaybookRuns[scannerPackageName]?.get(playbookId)
                        } ?: initialResult
                        recordUserPlaybookRun(
                            playbookId,
                            scannerPackageName,
                            current.copy(
                                isRunning = true,
                                currentStepId = step.id,
                                currentStepCommand = UserPlaybookEngine.renderCommand(
                                    step.command,
                                    packageVariable,
                                ),
                            ),
                        )
                    },
                    onStepFinished = { stepResult ->
                        val current = if (scannerPackageName == null) {
                            _uiState.value.userPlaybookRunResult
                        } else {
                            _uiState.value.userPlaybookRuns[scannerPackageName]?.get(playbookId)
                        } ?: initialResult
                        recordUserPlaybookRun(
                            playbookId,
                            scannerPackageName,
                            current.copy(
                                steps = current.steps + stepResult,
                                currentStepId = null,
                                currentStepCommand = null,
                            ),
                        )
                        logLine(
                            if (stepResult.succeeded) LogKind.SUCCESS else LogKind.ERROR,
                            "${stepResult.title}: ${if (stepResult.succeeded) "succeeded" else "failed"}" +
                                if (stepResult.timedOut) " (timed out)" else "",
                        )
                        if (stepResult.output.isNotBlank()) {
                            logLine(LogKind.OUTPUT, stepResult.output)
                        }
                    },
                )
                recordUserPlaybookRun(playbookId, scannerPackageName, result)
                _uiState.update { it.copy(isUserPlaybookRunning = false) }
                if (result.reachedStepLimit) {
                    logLine(LogKind.WARN, "Playbook stopped at the ${UserPlaybookEngine.MAX_RUN_STEPS}-step safety limit.")
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                _uiState.update { it.copy(isUserPlaybookRunning = false) }
                val current = if (scannerPackageName == null) {
                    _uiState.value.userPlaybookRunResult
                } else {
                    _uiState.value.userPlaybookRuns[scannerPackageName]?.get(playbookId)
                }
                current?.let {
                    recordUserPlaybookRun(
                        playbookId,
                        scannerPackageName,
                        it.copy(isRunning = false, currentStepId = null, currentStepCommand = null),
                    )
                }
                logLine(LogKind.WARN, "User playbook run cancelled; earlier device changes may remain.")
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isUserPlaybookRunning = false,
                        userPlaybookError = "Playbook stopped: ${error.message ?: "unknown error"}",
                    )
                }
                val current = if (scannerPackageName == null) {
                    _uiState.value.userPlaybookRunResult
                } else {
                    _uiState.value.userPlaybookRuns[scannerPackageName]?.get(playbookId)
                }
                current?.let {
                    recordUserPlaybookRun(
                        playbookId,
                        scannerPackageName,
                        it.copy(isRunning = false, currentStepId = null, currentStepCommand = null),
                    )
                }
                logLine(LogKind.ERROR, "User playbook failed: ${error.message ?: "unknown error"}")
            }
        }
    }

    private fun recordUserPlaybookRun(
        playbookId: String,
        scannerPackageName: String?,
        result: UserPlaybookRunResult,
    ) {
        _uiState.update { state ->
            if (scannerPackageName == null) {
                state.copy(userPlaybookRunResult = result)
            } else {
                val packageRuns = state.userPlaybookRuns[scannerPackageName].orEmpty() + (playbookId to result)
                state.copy(userPlaybookRuns = state.userPlaybookRuns + (scannerPackageName to packageRuns))
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
            val message = "Permission is not part of the current audit results."
            _uiState.update {
                it.copy(permissionOperationStatus = it.permissionOperationStatus + (
                    "$packageName|$permissionName" to message
                ))
            }
            _permissionResultEvents.tryEmit(message)
            return
        }

        val operationKey = "$packageName|$permissionName"
        if (ShizukuShell.state.value != ShizukuState.READY) {
            val message = "Permission was not changed: connect Shizuku first."
            _uiState.update {
                it.copy(permissionOperationStatus = it.permissionOperationStatus + (operationKey to message))
            }
            _permissionResultEvents.tryEmit(message)
            return
        }
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
                val message = "Could not $action permission: $details"
                _uiState.update {
                    it.copy(
                        permissionOperationStatus = it.permissionOperationStatus +
                            (operationKey to message),
                    )
                }
                _permissionResultEvents.emit(message)
            } else {
                try {
                    val report = withContext(Dispatchers.IO) {
                        PermissionAuditEngine.scan(getApplication())
                    }
                    val actual = report.findings
                        .firstOrNull { it.packageName == packageName }
                        ?.permissions
                        ?.firstOrNull { it.name == permissionName }
                        ?.granted
                    val confirmed = actual == granted
                    val message = when {
                        actual == null -> "Command completed, but the permission was not present in the refreshed audit."
                        confirmed -> "Permission ${if (granted) "granted" else "revoked"} and confirmed."
                        else -> "Command completed, but the refreshed audit does not show the requested permission state."
                    }
                    _uiState.update {
                        it.copy(
                            permissionAuditReport = report,
                            permissionAuditError = null,
                            permissionOperationStatus = it.permissionOperationStatus +
                                (operationKey to message),
                        )
                    }
                    _permissionResultEvents.emit(message)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    val message = "Permission command completed, but refreshing the audit failed: " +
                        (error.message ?: "unknown error")
                    _uiState.update {
                        it.copy(
                            permissionAuditError = error.message ?: "Permission audit failed.",
                            permissionOperationStatus = it.permissionOperationStatus +
                                (operationKey to message),
                        )
                    }
                    _permissionResultEvents.emit(message)
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
        if (ShizukuShell.state.value != ShizukuState.READY) {
            logLine(LogKind.ERROR, "Command not run: connect Shizuku first.")
            return
        }
        if (!_uiState.value.consoleRisksAcknowledged) {
            logLine(LogKind.ERROR, "Command not run: acknowledge the console risk phrase first.")
            return
        }
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
                if (step.conditional) {
                    val state = _uiState.value.playbookRuns[packageName]?.steps
                        ?.firstOrNull { it.step.id == stepId }?.state
                    if (state == StepState.PENDING) runUser0RecoveryIfNeeded(packageName, step)
                    continue
                }
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
                if (
                    result == StepState.FAILED &&
                    Playbook.canRetryAfterUser0Restore(step.id) &&
                    Playbook.needsUser0Recovery(output)
                ) {
                    val recoveryStep = currentStep(packageName, "restore_user_0")
                    val recoveryState = _uiState.value.playbookRuns[packageName]?.steps
                        ?.firstOrNull { it.step.id == "restore_user_0" }?.state
                    if (recoveryStep != null && recoveryState == StepState.PENDING) {
                        runUser0RecoveryIfNeeded(packageName, recoveryStep)
                    }
                }
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

    private suspend fun runUser0RecoveryIfNeeded(packageName: String, recoveryStep: RemovalStep) {
        val currentSteps = _uiState.value.playbookRuns[packageName]?.steps.orEmpty()
        if (currentSteps.any { it.step.id == "uninstall" && it.state == StepState.DONE }) {
            setStep(
                packageName,
                recoveryStep.id,
                StepState.SKIPPED,
                "Package uninstall already succeeded; recovery was skipped to avoid restoring it.",
            )
            return
        }
        val failedSteps = currentSteps
            .filter {
                it.state == StepState.FAILED &&
                    Playbook.canRetryAfterUser0Restore(it.step.id) &&
                    Playbook.needsUser0Recovery(it.output)
            }
        if (failedSteps.isEmpty()) {
            setStep(packageName, recoveryStep.id, StepState.SKIPPED, "No matching package/user-specific failure; recovery was not run.")
            return
        }

        setStep(packageName, recoveryStep.id, StepState.RUNNING, "")
        logLine(LogKind.WARN, "User-0 package recovery triggered for $packageName")
        logLine(LogKind.COMMAND, "\$ ${recoveryStep.command}")
        val (restoreState, restoreOutput) = runStepCommand(recoveryStep)
        if (restoreState != StepState.DONE) {
            val output = "Recovery failed; no playbook steps were retried.\n$restoreOutput"
            setStep(packageName, recoveryStep.id, StepState.FAILED, output)
            logLine(LogKind.ERROR, output)
            return
        }

        val recoveryOutput = "Package registration for user 0 succeeded. Retrying ${failedSteps.size} matching failed step(s) once.\n$restoreOutput"
        setStep(packageName, recoveryStep.id, StepState.DONE, recoveryOutput)
        logLine(LogKind.SUCCESS, recoveryOutput)
        failedSteps.forEach { failedStep ->
            setStep(packageName, failedStep.step.id, StepState.RUNNING, "")
            logLine(LogKind.COMMAND, "\$ retry: ${failedStep.step.command}")
            val (retryState, retryOutput) = runStepCommand(failedStep.step)
            val output = "Retried once after registering the package for user 0.\n$retryOutput"
            setStep(packageName, failedStep.step.id, retryState, output)
            logLine(
                if (retryState == StepState.DONE) LogKind.SUCCESS else LogKind.ERROR,
                output.take(500),
            )
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
            Playbook.needsUser0Recovery(combined) ->
                StepState.FAILED to combined.ifBlank { "Package/user-specific restriction reported" }
            step.id == "restore_user_0" &&
                (
                    res.exitCode != 0 ||
                        Regex("""\bfailure\b|\berror:""", RegexOption.IGNORE_CASE).containsMatchIn(combined) ||
                        Playbook.needsUser0Recovery(combined)
                    ) ->
                StepState.FAILED to combined.ifBlank { "Package restoration failed (exit code ${res.exitCode})" }
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
