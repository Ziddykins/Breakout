package com.ziddy.breakout.engine

import com.ziddy.breakout.model.RemovalStep
import com.ziddy.breakout.model.Threat

/**
 * Builds the removal playbook for a threat: a sequence of shell commands that
 * gradually strips an MDM of every lever it has — processes, admin rights,
 * owner slots, background execution, boot receivers, account/token storage,
 * and finally the package itself. Failed steps are non-fatal; later steps
 * still attempt full removal.
 */
object Playbook {

    fun stepsFor(threat: Threat): List<RemovalStep> {
        val pkg = threat.packageName
        val steps = mutableListOf<RemovalStep>()

        // -= Recon (read-only) =──────────────────────────────────────────-
        steps += RemovalStep(
            id = "recon_users",
            title = "List device users & profiles",
            detail = "Read-only: reveals secondary users, work profiles, secure folders, or guest accounts that may hold another instance of the agent.",
            command = "pm list users",
            readOnly = true,
        )
        steps += RemovalStep(
            id = "recon_accounts",
            title = "Dump account registry",
            detail = "Read-only: maps accounts, sync adapters, and auth tokens so the MDM's managed account can be spotted before it is cleared.",
            command = "dumpsys account",
            readOnly = true,
        )

        // ── Kill runtime state ───────────────────────────────────────────
        steps += RemovalStep(
            id = "force_stop",
            title = "Force stop",
            detail = "Kill every running process, service, and scheduled job owned by the package.",
            command = "am force-stop $pkg",
        )

        // ── Strip policy power ───────────────────────────────────────────
        if (threat.isDeviceOwner) {
            steps += RemovalStep(
                id = "clear_device_owner",
                title = "Clear device owner",
                detail = "Release the device-owner slot so the app loses device-wide policy power.",
                command = "dpm clear-device-owner",
            )
        }
        if (threat.isProfileOwner) {
            steps += RemovalStep(
                id = "clear_profile_owner",
                title = "Clear profile owner",
                detail = "Release the profile-owner slot (work profile / managed profile).",
                command = "dpm clear-profile-owner",
            )
        }
        if (threat.adminComponent != null) {
            steps += RemovalStep(
                id = "remove_admin",
                title = "Deactivate device admin",
                detail = "Strip the active administrator component so the app can no longer enforce policies or block its own uninstall.",
                command = "dpm remove-active-admin ${threat.adminComponent}",
            )
            steps += RemovalStep(
                id = "disable_admin_component",
                title = "Disable admin receiver component",
                detail = "Disable the exact receiver component so it cannot re-register as an administrator even if the package survives.",
                command = "pm disable ${threat.adminComponent}",
            )
        }

        // ── Cut every restart path ───────────────────────────────────────
        steps += RemovalStep(
            id = "appops_background",
            title = "Block background execution",
            detail = "Deny RUN_ANY_IN_BACKGROUND and RUN_IN_BACKGROUND app-ops so the agent cannot restart itself in the background.",
            command = "cmd appops set $pkg RUN_ANY_IN_BACKGROUND ignore && cmd appops set $pkg RUN_IN_BACKGROUND ignore",
        )
        steps += RemovalStep(
            id = "appops_boot",
            title = "Block auto-start on boot",
            detail = "Deny the BOOT_COMPLETED app-op so receivers cannot relaunch the agent after a reboot.",
            command = "cmd appops set $pkg BOOT_COMPLETED ignore",
        )
        steps += RemovalStep(
            id = "appops_foreground",
            title = "Block foreground-service launches",
            detail = "Deny START_FOREGROUND so the agent cannot promote itself to a persistent notification.",
            command = "cmd appops set $pkg START_FOREGROUND ignore",
        )
        steps += RemovalStep(
            id = "standby_bucket",
            title = "Restrict via standby bucket",
            detail = "Force the package into the RESTRICTED standby bucket — the OS itself throttles its jobs, alarms, and network.",
            command = "am set-standby-bucket $pkg restricted",
        )

        // ── Neutralize the package ───────────────────────────────────────
        steps += RemovalStep(
            id = "freeze",
            title = "Freeze package (user 0)",
            detail = "Disable the package for user 0 — components, receivers, and services all stop responding.",
            command = "pm disable-user --user 0 $pkg",
        )
        steps += RemovalStep(
            id = "suspend",
            title = "Suspend package",
            detail = "Freeze execution across the whole framework: activities refuse to launch and notifications are hidden.",
            command = "pm suspend $pkg",
        )
        steps += RemovalStep(
            id = "clear_data",
            title = "Wipe app data & tokens",
            detail = "Clear the app's data, caches, databases, and locally stored credentials — kills the managed account's token storage. Destructive to the app's data only.",
            command = "pm clear $pkg",
        )
        // ── Remove & verify ──────────────────────────────────────────────
        steps += RemovalStep(
            id = "uninstall",
            title = "Uninstall for user 0",
            detail = "Remove the package (keeping data, `-k`) for the main user. Works even for apps installed on the system image.",
            command = "pm uninstall -k --user 0 $pkg",
        )
        steps += RemovalStep(
            id = "restore_user_0",
            title = "Restore package for user 0 (conditional recovery)",
            detail = "Only runs after a package/user-specific shell restriction is reported. Restores this already-installed package for the main user, then retries only the failed, eligible playbook commands once. This does not grant root or bypass device-owner policy.",
            command = "pm install-existing --user 0 $pkg",
            conditional = true,
        )
        steps += RemovalStep(
            id = "verify",
            title = "Verify removal",
            detail = "Re-query the package manager to confirm the agent is gone from user 0.",
            command = "pm list packages --user 0 $pkg",
        )

        return steps
    }

    fun needsUser0Recovery(output: String): Boolean {
        val normalized = output.lowercase()
        return listOf(
            "only root can uninstall",
            "only root may uninstall",
            "shell cannot change component state",
            "not installed for user",
            "package is not installed for user",
            "requires root",
            "must be root",
        ).any(normalized::contains)
    }

    fun canRetryAfterUser0Restore(stepId: String): Boolean = stepId in user0RetryableStepIds

    private val user0RetryableStepIds = setOf(
        "force_stop",
        "disable_admin_component",
        "appops_background",
        "appops_boot",
        "appops_foreground",
        "standby_bucket",
        "freeze",
        "suspend",
        "clear_data",
        "uninstall",
    )

    /** Step ids that, when failing, should surface the testOnly re-sign workaround. */
    val adminStepIds: Set<String> = setOf("remove_admin", "clear_device_owner", "clear_profile_owner")
}
