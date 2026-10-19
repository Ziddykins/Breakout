package com.ziddy.breakout.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziddy.breakout.R
import com.ziddy.breakout.ui.components.ScreenHeader
import com.ziddy.breakout.ui.theme.AppColors
import com.ziddy.breakout.ui.theme.Mono

@Composable
fun DeveloperScreen() {
    Surface(Modifier.fillMaxSize(), color = AppColors.Background) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScreenHeader(
                title = "Developer",
                icon = Icons.Rounded.Code,
                subtitle = "You found the hidden room",
                horizontalPadding = 0.dp,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = AppColors.Surface,
            ) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_kitty),
                        contentDescription = "A kitty",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    )
                    Text(
                        "KITTY DETECTED",
                        color = AppColors.WarningAmber,
                        fontFamily = Mono,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                    Text(
                        "The developer appreciates your curiosity.",
                        color = AppColors.TextSecondary,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}
