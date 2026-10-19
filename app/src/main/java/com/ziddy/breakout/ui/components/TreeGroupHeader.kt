package com.ziddy.breakout.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziddy.breakout.ui.theme.AppColors

@Composable
fun TreeGroupHeader(
    title: String,
    countLabel: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = AppColors.SurfaceHigh.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, AppColors.Outline.copy(alpha = 0.72f)),
    ) {
        Row(
            Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                color = if (warning) AppColors.WarningAmber else AppColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
            Text(countLabel, color = AppColors.TextSecondary, fontSize = 10.sp)
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = Color(0xFF8A93A6),
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
