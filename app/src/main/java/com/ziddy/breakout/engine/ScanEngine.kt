package com.ziddy.breakout.engine

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.ziddy.breakout.data.MdmSignatures
import com.ziddy.breakout.model.Severity
import com.ziddy.breakout.model.ShizukuState
import com.ziddy.breakout.model.Threat
import com.ziddy.breakout.shell.ShizukuShell

/**
 * Heuristic scanner: enumerates packages, inspects device administrators,
 * matches a curated MDM signature database, and (with Shizuku) reads the
 * system policy dump to catch device/profile owners that hide from the
 * public APIs.
 */
object ScanEngine {

    data class ScanOutput(
        val threats: List<Threat>,
        val scannedPackages: Int,
        val activeAdmins: Int,
        val deviceOwner: String?,
        val profileOwner: String?,
        val usedShell: Boolean,
    )

    fun scan(context: Context, onProgress: (String) -> Unit = {}): ScanOutput {
        val pm = context.packageManager

        onProgress("Enumerating installed packages…")
        val installed: List<PackageInfo> = runCatching {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_META_DATA)
        }.getOrDefault(emptyList())

        onProgress("Querying device administrators…")
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val admins = dpm?.activeAdmins?.toList().orEmpty()
        val deviceOwner = admins.firstOrNull { dpm?.isDeviceOwnerApp(it.packageName) == true }?.packageName
        val profileOwner = admins.firstOrNull { dpm?.isProfileOwnerApp(it.packageName) == true }?.packageName

        data class Meta(val label: String, val isSystem: Boolean, val version: String?)

        val metas = installed.associate { info ->
            info.packageName to Meta(
                label = runCatching {
                    info.applicationInfo?.loadLabel(pm)?.toString().orEmpty()
                }.getOrDefault("").ifBlank { info.packageName },
                isSystem = ((info.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) != 0,
                version = info.versionName,
            )
        }

        onProgress("Matching against MDM signature database…")
        val threats = linkedMapOf<String, Threat>()

        fun merge(threat: Threat) {
            val existing = threats[threat.packageName]
            if (existing == null) {
                threats[threat.packageName] = threat
                return
            }
            threats[threat.packageName] = existing.copy(
                severity = if (threat.severity.order < existing.severity.order) threat.severity else existing.severity,
                vendor = if (existing.vendor == "Unknown") threat.vendor else existing.vendor,
                product = if (existing.product.startsWith("Unidentified")) threat.product else existing.product,
                adminComponent = existing.adminComponent ?: threat.adminComponent,
                isDeviceOwner = existing.isDeviceOwner || threat.isDeviceOwner,
                isProfileOwner = existing.isProfileOwner || threat.isProfileOwner,
                reasons = (existing.reasons + threat.reasons).distinct(),
            )
        }

        for (info in installed) {
            val pkg = info.packageName
            val meta = metas[pkg] ?: continue
            val match = MdmSignatures.match(pkg, meta.label) ?: continue
            val signature = match as MdmSignatures.MatchResult.Signature
            val reasons = buildList {
                add("Signature match: ${signature.signature.product} (${signature.how})")
                if (meta.isSystem) add("Pre-installed on the system image")
                signature.signature.note.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            merge(
                Threat(
                    packageName = pkg,
                    label = meta.label,
                    versionName = meta.version,
                    isSystem = meta.isSystem,
                    vendor = signature.signature.vendor,
                    product = signature.signature.product,
                    severity = signature.signature.severity,
                    matchedBy = if (signature.how.startsWith("keyword")) "keyword" else "signature",
                    adminComponent = null,
                    isDeviceOwner = pkg == deviceOwner,
                    isProfileOwner = pkg == profileOwner,
                    reasons = reasons,
                ),
            )
        }

        for (admin in admins) {
            val pkg = admin.packageName
            val meta = metas[pkg]
            val reasons = listOf(
                "Currently holds device administrator rights",
                "Can enforce policies, block uninstallation, or wipe the device",
            )
            val existing = threats[pkg]
            if (existing != null) {
                merge(
                    existing.copy(
                        adminComponent = admin.flattenToString(),
                        reasons = existing.reasons + reasons,
                    ),
                )
                continue
            }
            merge(
                Threat(
                    packageName = pkg,
                    label = meta?.label ?: pkg,
                    versionName = meta?.version,
                    isSystem = meta?.isSystem ?: false,
                    vendor = "Unknown",
                    product = "Active device administrator",
                    severity = Severity.HIGH,
                    matchedBy = "device-admin",
                    adminComponent = admin.flattenToString(),
                    isDeviceOwner = pkg == deviceOwner,
                    isProfileOwner = pkg == profileOwner,
                    reasons = reasons,
                ),
            )
        }

        onProgress("Scanning for possible control software…")
        for ((pkg, meta) in metas) {
            if (threats.containsKey(pkg)) continue
            val keyword = MdmSignatures.genericKeywordMatch(pkg, meta.label) ?: continue
            merge(
                Threat(
                    packageName = pkg,
                    label = meta.label,
                    versionName = meta.version,
                    isSystem = meta.isSystem,
                    vendor = "Unknown",
                    product = "Possible MDM / control software",
                    severity = Severity.MEDIUM,
                    matchedBy = "keyword",
                    adminComponent = null,
                    isDeviceOwner = pkg == deviceOwner,
                    isProfileOwner = pkg == profileOwner,
                    reasons = listOf(
                        "Name suggests MDM or control software: \"$keyword\"",
                        "Low-confidence match — review manually before acting",
                    ),
                ),
            )
        }

        var usedShell = false
        if (ShizukuShell.state.value == ShizukuState.READY) {
            onProgress("Reading system policy via shell…")
            val dump = ShizukuShell.exec("dumpsys device_policy")
            if (dump.exitCode == 0 && dump.stdout.isNotBlank()) {
                usedShell = true
                val (dumpDeviceOwner, dumpProfileOwner) = parseOwners(dump.stdout)
                for ((ownerPkg, kind) in listOfNotNull(
                    dumpDeviceOwner?.let { it to "Device owner" },
                    dumpProfileOwner?.let { it to "Profile owner" },
                )) {
                    val meta = metas[ownerPkg]
                    val existing = threats[ownerPkg]
                    if (existing != null) {
                        merge(existing.copy(reasons = existing.reasons + "Reported by system policy dump ($kind)"))
                    } else {
                        merge(
                            Threat(
                                packageName = ownerPkg,
                                label = meta?.label ?: ownerPkg,
                                versionName = meta?.version,
                                isSystem = meta?.isSystem ?: false,
                                vendor = "Unknown",
                                product = kind,
                                severity = Severity.HIGH,
                                matchedBy = "device-admin",
                                adminComponent = null,
                                isDeviceOwner = kind == "Device owner",
                                isProfileOwner = kind == "Profile owner",
                                reasons = listOf(
                                    "$kind reported by the system policy service",
                                    "Not visible in the regular app list — likely hidden or in a work profile",
                                ),
                            ),
                        )
                    }
                }
            }
        }

        onProgress("Finalizing report…")
        val sorted = threats.values.sortedWith(compareBy({ it.severity.order }, { it.label }))
        return ScanOutput(
            threats = sorted,
            scannedPackages = installed.size,
            activeAdmins = admins.size,
            deviceOwner = deviceOwner,
            profileOwner = profileOwner,
            usedShell = usedShell,
        )
    }

    private val componentBraceRegex = Regex("\\{([A-Za-z][A-Za-z0-9._]{2,})/[^}]*\\}")
    private val slashComponentRegex = Regex("([A-Za-z][A-Za-z0-9._]{2,})/[A-Za-z0-9_.$]+")

    /**
     * Best-effort parse of `dumpsys device_policy` for device/profile owner
     * components. Returns package names, or null when not found.
     */
    fun parseOwners(dump: String): Pair<String?, String?> {
        var deviceOwner: String? = null
        var profileOwner: String? = null
        for (line in dump.lineSequence()) {
            val mentionsDevice = line.contains("Device Owner", ignoreCase = true) ||
                line.contains("mDeviceOwner", ignoreCase = false)
            val mentionsProfile = line.contains("Profile Owner", ignoreCase = true) ||
                line.contains("mProfileOwner", ignoreCase = false)
            if (!mentionsDevice && !mentionsProfile) continue
            val pkg = componentBraceRegex.find(line)?.groupValues?.get(1)
                ?: slashComponentRegex.find(line)?.groupValues?.get(1)
                ?: continue
            if (pkg.equals("android", ignoreCase = true) || !pkg.contains('.')) continue
            if (mentionsDevice && deviceOwner == null) deviceOwner = pkg
            if (mentionsProfile && profileOwner == null) profileOwner = pkg
        }
        return deviceOwner to profileOwner
    }
}
