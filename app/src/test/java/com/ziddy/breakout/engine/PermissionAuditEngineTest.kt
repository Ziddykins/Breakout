package com.ziddy.breakout.engine

import com.ziddy.breakout.model.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionAuditEngineTest {
    @Test
    fun analyzeRanksSeverityBeforeScoreAndThenSortsByScore() {
        val internet = "android.permission.INTERNET"
        val camera = "android.permission.CAMERA"
        val capture = "android.permission.CAPTURE_SCREEN"
        val packages = listOf(
            PermissionAuditEngine.PackagePermissions(
                packageName = "example.low",
                label = "Low",
                versionName = null,
                isSystem = false,
                requested = listOf(internet to true),
            ),
            PermissionAuditEngine.PackagePermissions(
                packageName = "example.medium",
                label = "Medium",
                versionName = null,
                isSystem = false,
                requested = listOf(camera to false),
            ),
            PermissionAuditEngine.PackagePermissions(
                packageName = "example.critical",
                label = "Critical",
                versionName = null,
                isSystem = false,
                requested = listOf(internet to false, capture to false),
            ),
            PermissionAuditEngine.PackagePermissions(
                packageName = "example.medium.more",
                label = "Medium plus",
                versionName = null,
                isSystem = false,
                requested = listOf(camera to false, "android.permission.RECORD_AUDIO" to false),
            ),
        )

        val findings = PermissionAuditEngine.analyze(packages, setOf(internet, camera, capture, "android.permission.RECORD_AUDIO"))

        assertEquals(
            listOf("example.critical", "example.medium.more", "example.medium", "example.low"),
            findings.map { it.packageName },
        )
        assertEquals(Severity.CRITICAL, findings.first().severity)
        assertEquals(11, findings.first().score)
        assertEquals(Severity.MEDIUM, findings[1].severity)
        assertTrue(findings[1].score > findings[2].score)
        assertFalse(findings[1].permissions.first().granted)
    }

    @Test
    fun analyzeIgnoresPermissionsUnavailableOnTheDevice() {
        val findings = PermissionAuditEngine.analyze(
            packages = listOf(
                PermissionAuditEngine.PackagePermissions(
                    packageName = "example.app",
                    label = "Example",
                    versionName = null,
                    isSystem = false,
                    requested = listOf(
                        "android.permission.CAPTURE_SCREEN" to true,
                        "android.permission.INTERNET" to true,
                    ),
                ),
            ),
            availablePermissions = setOf("android.permission.INTERNET"),
        )

        assertEquals(1, findings.size)
        assertEquals(Severity.LOW, findings.single().severity)
        assertEquals(listOf("android.permission.INTERNET"), findings.single().permissions.map { it.name })
    }

    @Test
    fun definitionsRespectTheAndroidApiLevel() {
        val projectionPermission = "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"

        val beforeIntroduction = PermissionAuditEngine.definitionsForDevice(
            apiLevel = 33,
            platformPermissions = setOf(projectionPermission),
        )
        val introduced = PermissionAuditEngine.definitionsForDevice(
            apiLevel = 34,
            platformPermissions = setOf(projectionPermission),
        )

        assertFalse(beforeIntroduction.any { it.name == projectionPermission })
        assertTrue(introduced.any { it.name == projectionPermission })
    }
}
