package com.ziddy.breakout.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PackageIntegrityRecordTest {
    @Test
    fun reportsUnchangedBaseline() {
        assertEquals(
            PackageIntegrityStatus.UNCHANGED,
            record("apk-a", "apk-a", "signer-a", "signer-a").status,
        )
    }

    @Test
    fun distinguishesApkChangesFromSignerChanges() {
        assertEquals(
            PackageIntegrityStatus.APK_CHANGED,
            record("apk-a", "apk-b", "signer-a", "signer-a").status,
        )
        assertEquals(
            PackageIntegrityStatus.SIGNER_CHANGED,
            record("apk-a", "apk-b", "signer-a", "signer-b").status,
        )
    }

    @Test
    fun representsMissingBaselineAndHashFailuresExplicitly() {
        assertEquals(
            PackageIntegrityStatus.BASELINE_UNAVAILABLE,
            PackageIntegrityRecord(
                packageName = "example.app",
                baselineApkHash = null,
                currentApkHash = null,
                baselineSignerHash = null,
                currentSignerHash = null,
                checkedAt = 1L,
            ).status,
        )
        assertEquals(
            PackageIntegrityStatus.CHECK_FAILED,
            record("apk-a", null, "signer-a", null, error = "unreadable APK").status,
        )
    }

    private fun record(
        baselineApkHash: String?,
        currentApkHash: String?,
        baselineSignerHash: String?,
        currentSignerHash: String?,
        error: String? = null,
    ) = PackageIntegrityRecord(
        packageName = "example.app",
        baselineApkHash = baselineApkHash,
        currentApkHash = currentApkHash,
        baselineSignerHash = baselineSignerHash,
        currentSignerHash = currentSignerHash,
        checkedAt = 1L,
        error = error,
    )
}
