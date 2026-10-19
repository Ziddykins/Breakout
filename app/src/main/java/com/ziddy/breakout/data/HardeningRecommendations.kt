package com.ziddy.breakout.data

data class HardeningRecommendation(
    val value: String,
    val rationale: String,
)

data class RecommendedSettingChange(
    val namespace: String,
    val key: String,
    val currentValue: String,
    val recommendedValue: String,
)

object HardeningRecommendations {
    val byNamespace = mapOf(
        "secure" to mapOf(
            "lock_screen_allow_private_notifications" to HardeningRecommendation(
                "0",
                "Hides private notification content from the lock screen.",
            ),
            "lock_screen_allow_private_notifications_when_unsecure" to HardeningRecommendation(
                "0",
                "Keeps private notification content hidden when the lock screen is unsecured.",
            ),
            "speak_password" to HardeningRecommendation(
                "0",
                "Prevents spoken password characters from being exposed nearby.",
            ),
            "add_users_when_locked" to HardeningRecommendation(
                "0",
                "Prevents adding another user from the lock screen.",
            ),
            "rampart_main_switch_enabled" to HardeningRecommendation(
                "1",
                "Enables the device-provided Rampart protection feature.",
            ),
            "rampart_strict_protection_switch_enabled" to HardeningRecommendation(
                "1",
                "Enables the stricter mode for the device-provided Rampart protection feature.",
            ),
            "rampart_blocked_adb_cmd" to HardeningRecommendation(
                "1",
                "Enables the device-provided Rampart protection against restricted ADB commands.",
            ),
            "rampart_blocked_unknown_apps" to HardeningRecommendation(
                "1",
                "Enables the device-provided Rampart protection for unknown apps.",
            ),
            "rampart_enabled_message_guard" to HardeningRecommendation(
                "1",
                "Enables the device-provided Rampart message guard.",
            ),
            "theft_protection_default_on" to HardeningRecommendation(
                "1",
                "Enables the platform's theft-protection defaults.",
            ),
        ),
        "system" to emptyMap(),
        "global" to mapOf(
            "add_users_when_locked" to HardeningRecommendation(
                "0",
                "Prevents adding another user from the lock screen.",
            ),
            "wifi_scan_always_enabled" to HardeningRecommendation(
                "0",
                "Stops background Wi-Fi scanning when Wi-Fi is off; this can affect location and nearby-device features.",
            ),
            "ble_scan_always_enabled" to HardeningRecommendation(
                "0",
                "Stops background BLE scanning when Bluetooth is off; this can affect nearby-device features.",
            ),
            "bluetooth_scan_always_enabled" to HardeningRecommendation(
                "0",
                "Stops background Bluetooth scanning when Bluetooth is off; this can affect location and nearby-device features.",
            ),
        ),
    )

    fun pendingChanges(deviceSettings: Map<String, Map<String, String>>): List<RecommendedSettingChange> =
        byNamespace.flatMap { (namespace, recommendations) ->
            recommendations.mapNotNull { (key, recommendation) ->
                val currentValue = deviceSettings[namespace]?.get(key) ?: return@mapNotNull null
                if (currentValue == recommendation.value) return@mapNotNull null
                RecommendedSettingChange(namespace, key, currentValue, recommendation.value)
            }
        }
}
