package com.ziddy.breakout.engine

import com.ziddy.breakout.model.AttributionSignal
import com.ziddy.breakout.model.ForensicAccount
import com.ziddy.breakout.model.ForensicAccountEvent
import com.ziddy.breakout.model.ForensicReport
import com.ziddy.breakout.model.ForensicUser
import com.ziddy.breakout.model.ShizukuState
import com.ziddy.breakout.shell.ShizukuShell

object ForensicEngine {
    data class Output(
        val report: ForensicReport,
        val errors: List<String>,
    )

    fun collect(onProgress: (String) -> Unit = {}): Output {
        if (ShizukuShell.state.value != ShizukuState.READY) {
            return Output(
                report = ForensicReport(sourceStatus = mapOf("Shizuku" to "Not connected")),
                errors = listOf("Connect Shizuku to collect forensic data."),
            )
        }

        val sourceStatus = linkedMapOf<String, String>()
        val errors = mutableListOf<String>()

        onProgress("Reading Android accounts…")
        val accountResult = ShizukuShell.exec("dumpsys account")
        val accountData = if (accountResult.exitCode == 0 && accountResult.stdout.isNotBlank()) {
            sourceStatus["Account registry"] = "Collected"
            parseAccounts(accountResult.stdout)
        } else {
            sourceStatus["Account registry"] = "Unavailable"
            errors += "Account registry could not be read."
            ParsedAccounts()
        }

        onProgress("Reading users and profiles…")
        val usersResult = ShizukuShell.exec("pm list users")
        val users = if (usersResult.exitCode == 0) {
            sourceStatus["Users and profiles"] = "Collected"
            parseUsers(usersResult.stdout)
        } else {
            sourceStatus["Users and profiles"] = "Unavailable"
            errors += "Users and profiles could not be read."
            emptyList()
        }

        onProgress("Reading device policy ownership…")
        val policyResult = ShizukuShell.exec("dumpsys device_policy")
        val owners = if (policyResult.exitCode == 0) {
            sourceStatus["Device policy"] = "Collected"
            ScanEngine.parseOwners(policyResult.stdout)
        } else {
            sourceStatus["Device policy"] = "Unavailable"
            errors += "Device policy ownership could not be read."
            null to null
        }
        val activeAdmins = if (policyResult.exitCode == 0) {
            parseActiveAdmins(policyResult.stdout)
        } else {
            emptyList()
        }

        val attributionSignals = buildAttributionSignals(owners, activeAdmins)

        return Output(
            report = ForensicReport(
                accounts = accountData.accounts,
                authenticatorPackages = accountData.authenticatorPackages,
                users = users,
                activeAdmins = activeAdmins,
                deviceOwner = owners.first,
                profileOwner = owners.second,
                accountEvents = accountData.events,
                attributionSignals = attributionSignals,
                sourceStatus = sourceStatus,
                collectedAt = System.currentTimeMillis(),
            ),
            errors = errors,
        )
    }

    private data class ParsedAccounts(
        val accounts: List<ForensicAccount> = emptyList(),
        val authenticatorPackages: List<String> = emptyList(),
        val events: List<ForensicAccountEvent> = emptyList(),
    )

    private val userHeaderRegex = Regex("User UserInfo\\{(\\d+):([^:}]+)")
    private val accountRegex = Regex("Account \\{name=([^,}]+),\\s*type=([^}]+)", RegexOption.IGNORE_CASE)
    private val packageRegex = Regex("packageName=([A-Za-z0-9._]+)")
    private val componentRegex = Regex("\\{([A-Za-z][A-Za-z0-9._]{2,})/[^}]*\\}")
    private val componentInfoRegex = Regex("ComponentInfo\\{([A-Za-z0-9._]+)/[^}]*\\}")
    private val authenticatorRegex = Regex(
        "AuthenticatorDescription \\{type=([^,}]+)\\},\\s*ComponentInfo\\{([A-Za-z0-9._]+)/[^}]*\\}",
        RegexOption.IGNORE_CASE,
    )

    private fun parseAccounts(output: String): ParsedAccounts {
        var currentUser: Int? = null
        val accounts = mutableListOf<ForensicAccount>()
        val packages = linkedSetOf<String>()
        val events = mutableListOf<ForensicAccountEvent>()
        for (line in output.lineSequence()) {
            userHeaderRegex.find(line)?.let { currentUser = it.groupValues[1].toIntOrNull() }
            accountRegex.find(line)?.let {
                accounts += ForensicAccount(
                    name = it.groupValues[1].trim(),
                    type = it.groupValues[2].trim(),
                    userId = currentUser,
                )
            }
            authenticatorRegex.find(line)?.let {
                val authType = it.groupValues[1].trim()
                val pkg = it.groupValues[2].trim()
                if (authType.isNotBlank()) {
                    accounts += ForensicAccount(
                        name = "authenticator:$authType",
                        type = authType,
                        userId = currentUser,
                    )
                }
                if (pkg.isNotBlank()) packages += pkg
            }
            parseAccountEvent(line)?.let(events::add)
            componentInfoRegex.findAll(line).forEach { match ->
                match.groupValues[1].trim().takeIf { it.isNotBlank() }?.let(packages::add)
            }
            componentRegex.findAll(line).forEach { match ->
                match.groupValues[1].trim().takeIf { it.isNotBlank() }?.let(packages::add)
            }
            packageRegex.find(line)?.groupValues?.get(1)?.let(packages::add)
        }
        return ParsedAccounts(accounts.distinct(), packages.toList(), events.distinctBy { it.event + it.timestamp + it.details })
    }

    private fun parseAccountEvent(line: String): ForensicAccountEvent? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("AccountId", ignoreCase = true) || trimmed.startsWith("Accounts History", ignoreCase = true)) {
            return null
        }
        if (trimmed.startsWith("Accounts:") || trimmed.startsWith("Account {")) {
            return null
        }

        val lower = trimmed.lowercase()
        if (lower.contains("authenticatordescription") || lower.contains("serviceinfo: authenticator")) {
            val typeMatch = Regex("type=([^,}]+)").find(trimmed)
            val pkgMatch = Regex("ComponentInfo\\{([A-Za-z0-9._]+)/[^}]*\\}").find(trimmed)
            val uidMatch = Regex("uid\\s+(\\d+)").find(trimmed)
            return ForensicAccountEvent(
                event = "authenticator_service",
                timestamp = null,
                userId = null,
                source = "RegisteredServicesCache",
                details = trimmed,
                uid = uidMatch?.groupValues?.get(1)?.toIntOrNull(),
                packageName = pkgMatch?.groupValues?.get(1)?.trim(),
            )
        }

        if (lower.contains("account visibility:") || lower.contains("registeredservicescache") || lower.contains("account event dump")) {
            return ForensicAccountEvent(
                event = "account_summary",
                details = trimmed,
            )
        }

        val csv = parseCsvFields(trimmed)
        if (csv.size >= 6 && csv[0].equals("AccountId", ignoreCase = true)) return null
        if (csv.size >= 6 && csv[0].equals("userId", ignoreCase = true)) return null

        val actionMatch = Regex(
            "^(-?\\d+)\\s*,\\s*([A-Za-z0-9_]+)\\s*,\\s*([^,]+)\\s*,\\s*(\\d+)\\s*,\\s*([A-Za-z0-9_.]+)\\s*,\\s*(.*)$",
            RegexOption.IGNORE_CASE,
        ).find(trimmed)

        val hasActionColumn = csv.size >= 6 && csv[1].contains("action_", ignoreCase = true)
        val hasActionInAnyPosition = csv.size >= 6 && csv.any { it.contains("action_", ignoreCase = true) }

        if (actionMatch != null || hasActionColumn || hasActionInAnyPosition) {
            val matchValues = actionMatch?.groupValues ?: run {
                val actionIndex = csv.indexOfFirst { it.contains("action_", ignoreCase = true) }
                if (actionIndex >= 0) {
                    val fields = MutableList(csv.size) { csv[it] }
                    repeat(maxOf(0, 6 - fields.size)) { fields.add("") }
                    val accountId = fields.getOrNull(0)?.trim()?.toIntOrNull() ?: fields.getOrNull(actionIndex - 1)?.trim()?.toIntOrNull()
                    val actionType = fields.getOrNull(actionIndex)?.trim() ?: fields.getOrNull(1)?.trim()
                    val timestamp = fields.getOrNull(actionIndex + 1)?.trim() ?: fields.getOrNull(2)?.trim()
                    val uid = fields.getOrNull(actionIndex + 2)?.trim()?.toIntOrNull() ?: fields.getOrNull(3)?.trim()?.toIntOrNull()
                    val tableName = fields.getOrNull(actionIndex + 3)?.trim() ?: fields.getOrNull(4)?.trim()
                    val key = fields.getOrNull(actionIndex + 4)?.trim() ?: fields.getOrNull(5)?.trim() ?: ""
                    listOf("", accountId?.toString() ?: "", actionType ?: "", timestamp ?: "", uid?.toString() ?: "", tableName ?: "", key)
                } else {
                    listOf("", csv.getOrNull(0) ?: "", csv.getOrNull(1) ?: "", csv.getOrNull(2) ?: "", csv.getOrNull(3) ?: "", csv.getOrNull(4) ?: "", csv.getOrNull(5) ?: "")
                }
            }
            val accountId = matchValues.getOrNull(1)?.trim()?.toIntOrNull() ?: csv.getOrNull(0)?.trim()?.toIntOrNull()
            val actionType = matchValues.getOrNull(2)?.trim() ?: csv.getOrNull(1)?.trim()
            val timestamp = matchValues.getOrNull(3)?.trim() ?: csv.getOrNull(2)?.trim()
            val uid = matchValues.getOrNull(4)?.trim()?.toIntOrNull() ?: csv.getOrNull(3)?.trim()?.toIntOrNull()
            val tableName = matchValues.getOrNull(5)?.trim() ?: csv.getOrNull(4)?.trim()
            val key = (matchValues.getOrNull(6)?.trim() ?: csv.getOrNull(5)?.trim() ?: "")
                .removePrefix("\"")
                .removeSuffix("\"")
            if (actionType == null || tableName == null) return null
            return ForensicAccountEvent(
                event = actionType,
                timestamp = timestamp,
                userId = null,
                source = tableName,
                details = trimmed,
                uid = uid,
                accountId = accountId,
                actionType = actionType,
                tableName = tableName,
                key = key,
            )
        }

        return null
    }

    private fun parseCsvFields(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i += 2
                        continue
                    }
                    inQuotes = !inQuotes
                }
                ch == ',' && !inQuotes -> {
                    fields += current.toString().trim()
                    current.setLength(0)
                }
                else -> current.append(ch)
            }
            i += 1
        }
        fields += current.toString().trim()
        return fields
    }

    private fun parseUsers(output: String): List<ForensicUser> = buildList {
        for (line in output.lineSequence()) {
            val match = userHeaderRegex.find(line) ?: continue
            val id = match.groupValues[1].toIntOrNull() ?: continue
            add(ForensicUser(id, match.groupValues[2].trim(), line.trim()))
        }
    }.distinctBy { it.userId }

    private fun parseActiveAdmins(output: String): List<String> = buildSet {
        for (line in output.lineSequence()) {
            val lower = line.lowercase()
            if (!lower.contains("active admin") &&
                !lower.contains("admin=") &&
                !lower.contains("madminlist") &&
                !lower.contains("activeadmin")
            ) continue
            componentRegex.findAll(line).forEach { add(it.groupValues[1]) }
        }
    }.toList()

    private fun buildAttributionSignals(
        owners: Pair<String?, String?>,
        activeAdmins: List<String>,
    ): List<AttributionSignal> {
        val candidatePackages = linkedSetOf<String>()
        owners.first?.let(candidatePackages::add)
        owners.second?.let(candidatePackages::add)
        activeAdmins.forEach { pkg ->
            if (pkg.isNotBlank()) candidatePackages += pkg
        }

        return candidatePackages.mapNotNull { packageName ->
            val dump = ShizukuShell.exec("dumpsys package $packageName")
            if (dump.exitCode != 0 || dump.stdout.isBlank()) return@mapNotNull null
            parseAttributionSignal(packageName, dump.stdout)
        }
    }

    internal fun parseAttributionSignal(packageName: String, dump: String): AttributionSignal {
        val installer = Regex("installerPackageName=([A-Za-z0-9._]+)").find(dump)?.groupValues?.get(1)
        val versionName = Regex("versionName=([^\\s]+)").find(dump)?.groupValues?.get(1)
        val signingInfo = Regex("(?:signingInfo|signingDetails)[:=]\\s*(.+)", RegexOption.IGNORE_CASE)
            .find(dump)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.take(200)

        return AttributionSignal(
            packageName = packageName,
            installer = installer,
            versionName = versionName,
            signingInfo = signingInfo,
        )
    }

    internal fun parseAccountEventForTest(line: String): ForensicAccountEvent? = parseAccountEvent(line)
}