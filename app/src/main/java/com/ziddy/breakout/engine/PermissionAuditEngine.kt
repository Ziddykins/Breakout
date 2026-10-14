package com.ziddy.breakout.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.ziddy.breakout.model.AppPermissionFinding
import com.ziddy.breakout.model.DeviceProfile
import com.ziddy.breakout.model.PermissionAuditReport
import com.ziddy.breakout.model.PermissionDefinition
import com.ziddy.breakout.model.PermissionMatch
import com.ziddy.breakout.model.Severity
import com.ziddy.breakout.model.ShizukuState
import com.ziddy.breakout.shell.ShizukuShell

object PermissionAuditEngine {
    private val permissionDefinitions = listOf(
        PermissionDefinition("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION", "Foreground media projection", Severity.CRITICAL, 11, introducedApi = 34),
        PermissionDefinition("android.permission.MANAGE_MEDIA_PROJECTION", "Manage media projection", Severity.CRITICAL, 11),
        PermissionDefinition("android.permission.CAPTURE_SCREEN", "Capture screen", Severity.CRITICAL, 10),
        PermissionDefinition("android.permission.CAPTURE_SECURE_VIDEO_OUTPUT", "Capture secure video output", Severity.CRITICAL, 10),
        PermissionDefinition("android.permission.CAPTURE_VIDEO_OUTPUT", "Capture video output", Severity.CRITICAL, 10),
        PermissionDefinition("android.permission.BIND_DEVICE_ADMIN", "Device administrator binding", Severity.CRITICAL, 10),
        PermissionDefinition("android.permission.CAPTURE_AUDIO_OUTPUT", "Capture audio output", Severity.CRITICAL, 10),

        PermissionDefinition("android.permission.BIND_ACCESSIBILITY_SERVICE", "Accessibility service binding", Severity.HIGH, 8),
        PermissionDefinition("android.permission.CONTROL_KEYGUARD", "Control keyguard", Severity.HIGH, 8),
        PermissionDefinition("android.permission.DUMP", "Read system diagnostic dumps", Severity.HIGH, 8),
        PermissionDefinition("android.permission.MANAGE_DEVICE_ADMINS", "Manage device administrators", Severity.HIGH, 8),
        PermissionDefinition("android.permission.MODIFY_PHONE_STATE", "Modify phone state", Severity.HIGH, 8),
        PermissionDefinition("android.permission.READ_LOGS", "Read system logs", Severity.HIGH, 8),
        PermissionDefinition("android.permission.REAL_GET_TASKS", "Read all running tasks", Severity.HIGH, 8),
        PermissionDefinition("android.permission.WRITE_SECURE_SETTINGS", "Modify secure settings", Severity.HIGH, 8),

        PermissionDefinition("android.permission.CAMERA", "Camera", Severity.MEDIUM, 6),
        PermissionDefinition("android.permission.READ_CALL_LOG", "Read call history", Severity.MEDIUM, 6),
        PermissionDefinition("android.permission.READ_CONTACTS", "Read contacts", Severity.MEDIUM, 6),
        PermissionDefinition("android.permission.READ_SMS", "Read SMS", Severity.MEDIUM, 6),
        PermissionDefinition("android.permission.RECORD_AUDIO", "Record audio", Severity.MEDIUM, 6),
        PermissionDefinition("android.permission.SEND_SMS", "Send SMS", Severity.MEDIUM, 6),

        PermissionDefinition("android.permission.SYSTEM_ALERT_WINDOW", "Display over other apps", Severity.LOW, 3),
        PermissionDefinition("android.permission.GET_TASKS", "Read recent tasks", Severity.LOW, 3),
        PermissionDefinition("android.permission.PACKAGE_USAGE_STATS", "Access app usage history", Severity.LOW, 3),
        PermissionDefinition("android.permission.QUERY_ALL_PACKAGES", "View installed apps", Severity.LOW, 3),
        PermissionDefinition("android.permission.RECEIVE_BOOT_COMPLETED", "Run after device startup", Severity.LOW, 3),
        PermissionDefinition("android.permission.FOREGROUND_SERVICE", "Run foreground services", Severity.LOW, 3),
        PermissionDefinition("android.permission.WRITE_SETTINGS", "Modify system settings", Severity.LOW, 3),
        PermissionDefinition("android.permission.INTERNET", "Network access", Severity.LOW, 1),
    )

    data class PackagePermissions(
        val packageName: String,
        val label: String,
        val versionName: String?,
        val isSystem: Boolean,
        val requested: List<Pair<String, Boolean>>,
    )

    fun scan(context: Context): PermissionAuditReport {
        val packageManager = context.packageManager
        val apiLevel = Build.VERSION.SDK_INT
        val platformPermissions = permissionDefinitions
            .asSequence()
            .filter { it.introducedApi <= apiLevel }
            .filter { isPlatformPermissionAvailable(packageManager, it.name) }
            .map { it.name }
            .toSet()
        val availableDefinitions = definitionsForDevice(apiLevel, platformPermissions)
        val availableNames = availableDefinitions.mapTo(mutableSetOf()) { it.name }

        @Suppress("DEPRECATION")
        val installed = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        val packages = installed.map { info ->
            val appInfo = info.applicationInfo
            val requested = info.requestedPermissions.orEmpty()
                .filter { it in availableNames }
                .distinct()
                .map { permission ->
                    val index = info.requestedPermissions.orEmpty().indexOf(permission)
                    val flags = info.requestedPermissionsFlags?.getOrNull(index) ?: 0
                    permission to ((flags and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0)
                }
            PackagePermissions(
                packageName = info.packageName,
                label = appInfo?.loadLabel(packageManager)?.toString().orEmpty().ifBlank { info.packageName },
                versionName = info.versionName,
                isSystem = ((appInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) != 0,
                requested = requested,
            )
        }
        val (oneUiVersion, knoxVersion) = readSamsungVersions()

        return PermissionAuditReport(
            device = DeviceProfile(
                manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { "Unknown" },
                model = Build.MODEL.orEmpty().ifBlank { "Unknown" },
                androidRelease = Build.VERSION.RELEASE.orEmpty().ifBlank { "Unknown" },
                apiLevel = apiLevel,
                securityPatch = Build.VERSION.SECURITY_PATCH.takeIf { it.isNotBlank() },
                oneUiVersion = oneUiVersion,
                knoxVersion = knoxVersion,
            ),
            scannedPackages = installed.size,
            availablePermissions = availableDefinitions,
            findings = analyze(packages, availableNames),
        )
    }

    internal fun analyze(
        packages: List<PackagePermissions>,
        availablePermissions: Set<String>,
    ): List<AppPermissionFinding> {
        val definitions = permissionDefinitions
            .filter { it.name in availablePermissions }
            .associateBy { it.name }

        return packages.mapNotNull { packagePermissions ->
            val matches = packagePermissions.requested
                .distinctBy { it.first }
                .mapNotNull { (name, granted) ->
                    definitions[name]?.let { definition ->
                        PermissionMatch(
                            name = name,
                            label = definition.label,
                            severity = definition.severity,
                            score = definition.score,
                            granted = granted,
                        )
                    }
                }
                .sortedWith(compareBy({ it.severity.order }, { -it.score }, { it.label }))
            if (matches.isEmpty()) return@mapNotNull null

            AppPermissionFinding(
                packageName = packagePermissions.packageName,
                label = packagePermissions.label,
                versionName = packagePermissions.versionName,
                isSystem = packagePermissions.isSystem,
                severity = matches.minBy { it.severity.order }.severity,
                score = matches.sumOf { it.score },
                permissions = matches,
            )
        }.sortedWith(
            compareBy<AppPermissionFinding>({ it.severity.order }, { -it.score }, { it.label.lowercase() }),
        )
    }

    internal fun definitionsForDevice(
        apiLevel: Int,
        platformPermissions: Set<String>,
    ): List<PermissionDefinition> = permissionDefinitions.filter {
        it.introducedApi <= apiLevel && it.name in platformPermissions
    }

    private fun isPlatformPermissionAvailable(packageManager: PackageManager, name: String): Boolean {
        return try {
            @Suppress("DEPRECATION")
            packageManager.getPermissionInfo(name, 0).packageName == "android"
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun readSamsungVersions(): Pair<String?, String?> {
        if (ShizukuShell.state.value != ShizukuState.READY) return null to null
        val oneUi = ShizukuShell.exec("getprop ro.build.version.oneui", timeoutMs = 5_000L)
            .stdout.trim().takeIf { it.isNotBlank() }
        val knox = ShizukuShell.exec("getprop ro.build.version.knox", timeoutMs = 5_000L)
            .stdout.trim().takeIf { it.isNotBlank() }
        return oneUi to knox
    }
}
