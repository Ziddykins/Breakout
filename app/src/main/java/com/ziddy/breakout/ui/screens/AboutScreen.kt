package com.ziddy.breakout.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ziddy.breakout.AppViewModel
import com.ziddy.breakout.BuildConfig
import com.ziddy.breakout.ui.components.ScreenHeader
import com.ziddy.breakout.ui.theme.AppColors
import com.ziddy.breakout.ui.theme.Mono

private const val APP_NAME = "Breakout"
private const val AUTHOR = "Jeff Baldwin"
private const val DESCRIPTION =
    "Breakout helps people buying second-hand Android devices stay secure and avoid paying for a device that is still managed, restricted, or quietly targeted.\n\n" +
        "It identifies device-management threats, including devices accidentally or coercively added to corporate profiles, and provides focused removal playbooks through Shizuku. The app is built for clear diagnostics, informed purchases, and helping you keep control of your device.\n\n" +
        "Available playbooks and the actions they perform are shown plainly before you run them. You choose whether to run those actions and accept responsibility for the outcome, including data loss, device damage, or a bricked device. The author is not responsible for damage caused by use of the program."

@Composable
fun AboutScreen(
    viewModel: AppViewModel,
    onDeveloperUnlock: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var versionTaps by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        if (state.permissionAuditReport == null && !state.isPermissionAuditScanning) {
            viewModel.startPermissionAudit()
        }
    }
    LaunchedEffect(versionTaps) {
        if (versionTaps == 9) onDeveloperUnlock()
    }

    Surface(Modifier.fillMaxSize(), color = AppColors.Background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(horizontal = 16.dp, vertical = 12.dp)),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScreenHeader(
                title = "About",
                icon = Icons.Rounded.Info,
                subtitle = "Inspect packages. Understand risks. Take control",
                horizontalPadding = 0.dp,
            )
            state.permissionAuditReport?.let { report ->
                DeviceProfileCard(report)
            } ?: AboutValue(
                "Device Profile",
                when {
                    state.isPermissionAuditScanning -> "Reading device profile…"
                    state.permissionAuditError != null -> state.permissionAuditError.orEmpty()
                    else -> "Device profile is not available."
                },
            )
            AboutValue("App name", APP_NAME)
            AboutValue("Author", AUTHOR)
            AboutValue(
                label = "Version (build ${BuildConfig.VERSION_CODE})",
                value = BuildConfig.VERSION_NAME,
                modifier = Modifier.clickable {
                    if (versionTaps < 9) versionTaps++
                },
            )
            AboutValue("Description", DESCRIPTION)
        }
    }
}

@Composable
private fun AboutValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AppColors.Surface,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = AppColors.WarningAmber, fontFamily = Mono, fontSize = 11.sp)
            if (label == "Description") {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    value.split("\n\n").forEach { paragraph ->
                        Text(
                            paragraph,
                            color = AppColors.TextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                    }
                }
            } else {
                Text(value, color = AppColors.TextPrimary, fontSize = 14.sp)
            }
        }
    }
}
