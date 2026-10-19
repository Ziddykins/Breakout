package com.ziddy.breakout.model

/** Severity of a detected threat, ordered from most to least urgent. */
enum class Severity(val label: String, val order: Int) {
    CRITICAL("Critical", 0),
    HIGH("High", 1),
    MEDIUM("Medium", 2),
    LOW("Low", 3),
}

/** A known MDM / control-software signature used by the scanner. */
data class MdmSignature(
    val vendor: String,
    val product: String,
    val severity: Severity,
    val packages: List<String> = emptyList(),
    val prefixes: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val note: String = "",
)

/** A detected MDM / control app. */
data class Threat(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val isSystem: Boolean,
    val vendor: String,
    val product: String,
    val severity: Severity,
    /** How it was detected: "signature" | "device-admin" | "keyword". */
    val matchedBy: String,
    /** Flattened ComponentName if this app is an active device admin. */
    val adminComponent: String?,
    val isDeviceOwner: Boolean,
    val isProfileOwner: Boolean,
    val reasons: List<String>,
)

/** A single step of a removal playbook. */
data class RemovalStep(
    val id: String,
    val title: String,
    val detail: String,
    val command: String,
    /** Read-only investigation steps that change nothing but surface useful state. */
    val readOnly: Boolean = false,
    /** Recovery steps are invoked automatically only after a matching failure. */
    val conditional: Boolean = false,
)

enum class StepState { PENDING, RUNNING, DONE, FAILED, SKIPPED }

data class StepRun(
    val step: RemovalStep,
    val state: StepState = StepState.PENDING,
    val output: String = "",
)

enum class RunState { IDLE, RUNNING, DONE, FAILED }

/** Progress of a removal playbook run for one package. */
data class PlaybookRun(
    val steps: List<StepRun>,
    val state: RunState = RunState.IDLE,
)

enum class LogKind { INFO, COMMAND, OUTPUT, ERROR, SUCCESS, WARN }

data class LogLine(
    val timestamp: Long,
    val kind: LogKind,
    val text: String,
)

data class ForensicAccount(
    val name: String,
    val type: String,
    val userId: Int?,
)

data class ForensicUser(
    val userId: Int,
    val label: String,
    val raw: String,
)

data class ForensicAccountEvent(
    val event: String,
    val timestamp: String? = null,
    val userId: Int? = null,
    val source: String? = null,
    val details: String = "",
    val uid: Int? = null,
    val packageName: String? = null,
    val accountId: Int? = null,
    val actionType: String? = null,
    val tableName: String? = null,
    val key: String? = null,
)

data class AttributionSignal(
    val packageName: String,
    val installer: String? = null,
    val versionName: String? = null,
    val signingInfo: String? = null,
    val source: String = "dumpsys package",
)

data class ForensicReport(
    val accounts: List<ForensicAccount> = emptyList(),
    val authenticatorPackages: List<String> = emptyList(),
    val users: List<ForensicUser> = emptyList(),
    val activeAdmins: List<String> = emptyList(),
    val deviceOwner: String? = null,
    val profileOwner: String? = null,
    val accountEvents: List<ForensicAccountEvent> = emptyList(),
    val attributionSignals: List<AttributionSignal> = emptyList(),
    val sourceStatus: Map<String, String> = emptyMap(),
    val collectedAt: Long? = null,
)

/** State of the Shizuku shell connection. */
enum class ShizukuState { NOT_RUNNING, NO_PERMISSION, CONNECTING, READY, SERVICE_ERROR }

enum class PackageAction(val title: String) {
    REINSTALL_AS_USER_0("Reinstall for user 0"),
    UNINSTALL("Uninstall for user 0"),
    CLEAR_DATA("Clear app data"),
    FREEZE("Freeze for user 0"),
}

@kotlinx.serialization.Serializable
data class UserPlaybook(
    val id: String,
    val name: String,
    val steps: List<UserPlaybookStep>,
)

@kotlinx.serialization.Serializable
data class UserPlaybookStep(
    val id: String,
    val title: String,
    val command: String,
    val onSuccessStepId: String? = null,
    val onFailureStepId: String? = null,
)

data class UserPlaybookStepResult(
    val stepId: String,
    val title: String,
    val command: String,
    val succeeded: Boolean,
    val output: String,
    val timedOut: Boolean,
)

data class UserPlaybookRunResult(
    val playbookId: String,
    val steps: List<UserPlaybookStepResult>,
    val reachedStepLimit: Boolean = false,
)
