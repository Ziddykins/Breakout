package com.ziddy.breakout.model

data class DeviceProfile(
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val apiLevel: Int,
    val securityPatch: String?,
    val oneUiVersion: String?,
    val knoxVersion: String?,
)

data class PermissionDefinition(
    val name: String,
    val label: String,
    val severity: Severity,
    val score: Int,
    val introducedApi: Int = 1,
)

data class PermissionMatch(
    val name: String,
    val label: String,
    val severity: Severity,
    val score: Int,
    val granted: Boolean,
)

data class AppPermissionFinding(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val isSystem: Boolean,
    val severity: Severity,
    val score: Int,
    val permissions: List<PermissionMatch>,
)

data class PermissionAuditReport(
    val device: DeviceProfile,
    val scannedPackages: Int,
    val availablePermissions: List<PermissionDefinition>,
    val findings: List<AppPermissionFinding>,
)
