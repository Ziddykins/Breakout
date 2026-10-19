package com.ziddy.breakout.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziddy.breakout.ui.theme.AppColors

@Composable
fun ScreenHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    horizontalPadding: Dp = 18.dp,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = horizontalPadding, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = AppColors.NeonBlue.copy(alpha = 0.12f),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppColors.NeonBlue,
                modifier = Modifier.padding(7.dp).size(19.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = AppColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp,
                maxLines = 1,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = AppColors.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
        actions()
    }
}
