package com.ziddy.breakout.engine

import com.ziddy.breakout.model.Severity
import com.ziddy.breakout.model.Threat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybookTest {
    @Test
    fun detectsPackageAndUserSpecificShellFailures() {
        assertTrue(Playbook.needsUser0Recovery("Failure: only root can uninstall system packages"))
        assertTrue(Playbook.needsUser0Recovery("Shell cannot change component state for this package"))
        assertTrue(Playbook.needsUser0Recovery("Package is not installed for user 0"))

        assertFalse(Playbook.needsUser0Recovery("Operation completed successfully"))
        assertFalse(Playbook.needsUser0Recovery("Device owner policy prevents this action"))
    }

    @Test
    fun retriesOnlySupportedPackagePlaybookSteps() {
        assertTrue(Playbook.canRetryAfterUser0Restore("uninstall"))
        assertTrue(Playbook.canRetryAfterUser0Restore("disable_admin_component"))
        assertFalse(Playbook.canRetryAfterUser0Restore("clear_device_owner"))
        assertFalse(Playbook.canRetryAfterUser0Restore("verify"))
        assertFalse(Playbook.canRetryAfterUser0Restore("restore_user_0"))
    }

    @Test
    fun includesConditionalUser0RestoreBeforeVerification() {
        val steps = Playbook.stepsFor(threat("com.example.agent"))
        val recovery = steps.single { it.id == "restore_user_0" }

        assertEquals("pm install-existing --user 0 com.example.agent", recovery.command)
        assertTrue(recovery.conditional)
        assertTrue(steps.indexOf(recovery) > steps.indexOfFirst { it.id == "uninstall" })
        assertTrue(steps.indexOf(recovery) < steps.indexOfFirst { it.id == "verify" })
    }

    private fun threat(packageName: String) = Threat(
        packageName = packageName,
        label = "Example",
        versionName = null,
        isSystem = true,
        vendor = "Example",
        product = "Agent",
        severity = Severity.HIGH,
        matchedBy = "signature",
        adminComponent = null,
        isDeviceOwner = false,
        isProfileOwner = false,
        reasons = emptyList(),
    )
}
