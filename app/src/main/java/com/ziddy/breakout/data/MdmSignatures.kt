package com.ziddy.breakout.data

import com.ziddy.breakout.model.MdmSignature
import com.ziddy.breakout.model.Severity

/**
 * Signature database of known MDM agents, enterprise mobility suites,
 * and device-control / supervision software.
 */
object MdmSignatures {

    val all: List<MdmSignature> = listOf(
        MdmSignature(
            vendor = "Alliance Shield",
            product = "Alliance Shield Agent",
            severity = Severity.CRITICAL,
            packages = listOf("com.allianceshield.agent", "com.allianceshield.xagent"),
            keywords = listOf("allianceshield"),
            note = "Frequently abused to lock or control a device the user physically owns.",
        ),
        MdmSignature(
            vendor = "Samsung",
            product = "Samsung Knox / Samsung MDM",
            severity = Severity.HIGH,
            packages = listOf("com.samsung.android.mdm", "com.samsung.knox.knoxagent"),
            prefixes = listOf(
                "com.samsung.knox",
                "com.samsung.android.knox",
                "com.sec.enterprise.knox",
                "com.sec.enterprise",
            ),
            keywords = listOf("knoxagent"),
            note = "Knox enterprise agents and the Samsung MDM policy service.",
        ),
        MdmSignature(
            vendor = "Microsoft",
            product = "Microsoft Intune / Company Portal",
            severity = Severity.HIGH,
            packages = listOf(
                "com.microsoft.intune",
                "com.microsoft.windowsintune",
                "com.microsoft.azureauthenticator",
            ),
            keywords = listOf("windowsintune"),
            note = "Company Portal and its compliance components.",
        ),
        MdmSignature(
            vendor = "Zoho (ManageEngine)",
            product = "ManageEngine MDM",
            severity = Severity.HIGH,
            packages = listOf("com.manageengine.mdm.android"),
            keywords = listOf("manageengine"),
        ),
        MdmSignature(
            vendor = "VMware (Broadcom)",
            product = "Workspace ONE / AirWatch",
            severity = Severity.HIGH,
            packages = listOf("com.airwatch.androidagent", "com.airwatch.contentlocker"),
            keywords = listOf("airwatch", "intelligenthub"),
        ),
        MdmSignature(
            vendor = "Ivanti",
            product = "MobileIron / Ivanti Endpoint Manager",
            severity = Severity.HIGH,
            packages = listOf("com.mobileiron.android"),
            prefixes = listOf("com.mobileiron", "com.ivanti.mdm"),
            keywords = listOf("mobileiron"),
        ),
        MdmSignature(
            vendor = "SOTI",
            product = "SOTI MobiControl",
            severity = Severity.HIGH,
            packages = listOf("net.soti.agent", "net.soti.mobicontrol.androidagent"),
            prefixes = listOf("net.soti"),
            keywords = listOf("mobicontrol"),
        ),
        MdmSignature(
            vendor = "Jamf",
            product = "Jamf Trust / Jamf Now",
            severity = Severity.HIGH,
            prefixes = listOf("com.jamf"),
            keywords = listOf("jamf"),
        ),
        MdmSignature(
            vendor = "Hexnode (ManageEngine)",
            product = "Hexnode MDM",
            severity = Severity.HIGH,
            prefixes = listOf("com.hexnode"),
            keywords = listOf("hexnode"),
        ),
        MdmSignature(
            vendor = "Miradore",
            product = "Miradore MDM",
            severity = Severity.HIGH,
            prefixes = listOf("com.miradore"),
            keywords = listOf("miradore"),
        ),
        MdmSignature(
            vendor = "IBM",
            product = "MaaS360",
            severity = Severity.HIGH,
            prefixes = listOf("com.fiberlink"),
            keywords = listOf("maas360"),
        ),
        MdmSignature(
            vendor = "Sophos",
            product = "Sophos Mobile Control",
            severity = Severity.HIGH,
            packages = listOf("com.sophos.smsec"),
        ),
        MdmSignature(
            vendor = "Google",
            product = "Google Device Policy / CloudDPC",
            severity = Severity.HIGH,
            packages = listOf(
                "com.google.android.apps.work.clouddpc",
                "com.google.android.apps.enterprise.dmagent",
            ),
        ),
        MdmSignature(
            vendor = "Citrix",
            product = "Citrix XenMobile",
            severity = Severity.HIGH,
            prefixes = listOf("com.zenprise"),
            keywords = listOf("zenprise", "xenmobile"),
        ),
        MdmSignature(
            vendor = "Cisco",
            product = "Meraki Systems Manager",
            severity = Severity.HIGH,
            keywords = listOf("meraki"),
        ),
        MdmSignature(
            vendor = "Esper",
            product = "Esper Foundation / Agent",
            severity = Severity.MEDIUM,
            prefixes = listOf("io.esper", "com.esper"),
        ),
        MdmSignature(
            vendor = "AOSP",
            product = "TestDPC (device policy testing app)",
            severity = Severity.MEDIUM,
            packages = listOf("com.afwsamples.testdpc"),
        ),
        MdmSignature(
            vendor = "Google",
            product = "Family Link (supervision)",
            severity = Severity.MEDIUM,
            packages = listOf("com.google.android.apps.nbu.parent.app"),
            keywords = listOf("nbu.parent"),
            note = "Parental supervision agent. Removal requires releasing supervision.",
        ),
        MdmSignature(
            vendor = "MMH",
            product = "MMGuardian Parental Control",
            severity = Severity.MEDIUM,
            keywords = listOf("mmguardian"),
        ),
        MdmSignature(
            vendor = "Wondershare",
            product = "FamiSafe",
            severity = Severity.MEDIUM,
            keywords = listOf("famisafe"),
        ),
        MdmSignature(
            vendor = "Qustodio",
            product = "Qustodio Parental Control",
            severity = Severity.MEDIUM,
            keywords = listOf("qustodio"),
        ),
        MdmSignature(
            vendor = "ContentWatch",
            product = "Net Nanny",
            severity = Severity.MEDIUM,
            keywords = listOf("netnanny"),
        ),
        MdmSignature(
            vendor = "Kaspersky",
            product = "Kaspersky SafeKids",
            severity = Severity.MEDIUM,
            packages = listOf("com.kaspersky.safekids"),
            keywords = listOf("safekids"),
        ),
    )

    /**
     * Generic keywords flagging packages as *possible* control software.
     * Matches are low-confidence and always require manual review.
     */
    val genericKeywords: List<String> = listOf(
        "mdm",
        "kiosklauncher",
        "deviceowner",
        "device_owner",
        "devicepolicy",
        "parentalcontrol",
        "parent_control",
        "supervision",
    )

    sealed interface MatchResult {
        data class Signature(val signature: MdmSignature, val how: String) : MatchResult
        data class Keyword(val keyword: String) : MatchResult
    }

    /** Match a package against the signature database (exact, prefix, then keyword). */
    fun match(packageName: String, label: String): MatchResult? {
        val pkg = packageName.lowercase()
        val lbl = label.lowercase()
        for (signature in all) {
            if (signature.packages.any { it.equals(packageName, ignoreCase = true) }) {
                return MatchResult.Signature(signature, "exact package")
            }
        }
        for (signature in all) {
            if (signature.prefixes.any { pkg.startsWith(it) || lbl.startsWith(it) }) {
                return MatchResult.Signature(signature, "package prefix")
            }
        }
        for (signature in all) {
            val keyword = signature.keywords.firstOrNull { pkg.contains(it) || lbl.contains(it) }
            if (keyword != null) return MatchResult.Signature(signature, "keyword \"$keyword\"")
        }
        return null
    }

    /** Low-confidence generic keyword match, used only when no signature matched. */
    fun genericKeywordMatch(packageName: String, label: String): String? {
        val pkg = packageName.lowercase()
        val lbl = label.lowercase()
        return genericKeywords.firstOrNull { pkg.contains(it) || lbl.contains(it) }
    }
}
