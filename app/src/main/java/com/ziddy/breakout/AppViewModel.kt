package com.ziddy.breakout

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ziddy.breakout.engine.Playbook
import com.ziddy.breakout.engine.ScanEngine
import com.ziddy.breakout.model.LogKind
import com.ziddy.breakout.model.LogLine
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
    val scanPhase: String = "",
    val threats: List<Threat> = emptyList(),
    val playbookRuns: Map<String, PlaybookRun> = emptyMap(),
    val scannedPackages: Int = 0,
    val lastScanAt: Long? = null,
    val usedShellForScan: Boolean = false,
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
