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
import com.ziddy.breakout.BuildConfig
import com.ziddy.breakout.ui.theme.AppColors
import com.ziddy.breakout.ui.theme.Mono

private const val APP_NAME = "Breakout"
private const val AUTHOR = "Jeff Baldwin"
private const val DESCRIPTION =
    "Breakout helps people buying second-hand Android devices stay secure and avoid " +
        "paying for a device that is still managed, restricted, or quietly targeted. " +
        "It identifies device-management threats, including devices accidentally or " +
        "coercively added to corporate profiles, and provides focused removal playbooks " +
        "through Shizuku. Built for clear diagnostics, informed purchases, and keeping " +
        "control of your device. The available playbooks and the actions they perform " +
        "are shown plainly before you run them. By using Breakout, you acknowledge that " +
        "you choose to run those actions and accept responsibility for the outcome, " +
        "including data loss, device damage, or a bricked device. The author is not " +
        "responsible for damage caused by your use of the program."

@Composable
fun AboutScreen(onDeveloperUnlock: () -> Unit = {}) {
    var versionTaps by remember { mutableIntStateOf(0) }
    LaunchedEffect(versionTaps) {
        if (versionTaps == 9) onDeveloperUnlock()
    }

    Surface(Modifier.fillMaxSize(), color = AppColors.Background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(horizontal = 16.dp, vertical = 12.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = AppColors.Amber,
                )
                Text(
                    text = "about",
                    color = AppColors.TextPrimary,
                    fontFamily = Mono,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = "Inspect packages. Understand risks. Take control.",
                    color = AppColors.TextSecondary,
                    fontSize = 14.sp,
                )
            }
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
        Column(Modifier.padding(16.dp)) {
            Text(label, color = AppColors.Amber, fontFamily = Mono, fontSize = 11.sp)
            Text(value, color = AppColors.TextPrimary, fontSize = 14.sp)
        }
    }
}
