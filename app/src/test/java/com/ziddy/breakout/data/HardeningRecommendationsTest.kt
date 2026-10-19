package com.ziddy.breakout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HardeningRecommendationsTest {
    @Test
    fun pendingChangesIncludesOnlyExposedSettingsThatDifferFromRecommendations() {
        val changes = HardeningRecommendations.pendingChanges(
            mapOf(
                "secure" to mapOf(
                    "speak_password" to "1",
                    "theft_protection_default_on" to "1",
                    "unrecognized_setting" to "1",
                ),
                "global" to mapOf("wifi_scan_always_enabled" to "1"),
            ),
        )

        assertEquals(
            setOf(
                "secure.speak_password",
                "global.wifi_scan_always_enabled",
            ),
            changes.map { "${it.namespace}.${it.key}" }.toSet(),
        )
        assertEquals(2, changes.size)
    }

    @Test
    fun pendingChangesIsEmptyWhenNoRecognizedSettingIsExposed() {
        assertTrue(HardeningRecommendations.pendingChanges(emptyMap()).isEmpty())
    }
}
