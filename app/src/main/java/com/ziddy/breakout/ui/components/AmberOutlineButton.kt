package com.ziddy.breakout.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ziddy.breakout.ui.theme.AppColors

@Composable
fun AmberOutlineButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val accent = AppColors.NeonBlue
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 40.dp)
            .shadow(
                elevation = if (enabled) 4.dp else 0.dp,
                shape = shape,
                ambientColor = accent.copy(alpha = 0.08f),
                spotColor = accent.copy(alpha = 0.12f),
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = if (enabled) 0.2f else 0.05f),
                        AppColors.SurfaceHigh.copy(alpha = if (enabled) 0.94f else 0.45f),
                    ),
                ),
            )
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = if (enabled) 0.64f else 0.18f),
                            accent.copy(alpha = if (enabled) 0.18f else 0.06f),
                        ),
                    ),
                ),
                shape,
            ),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color(0xFFE7F8FF),
            disabledContainerColor = Color.Transparent,
            disabledContentColor = AppColors.TextSecondary,
        ),
        content = {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
            }
            content()
        },
    )
}

@Composable
fun TileActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val accent = AppColors.NeonBlue
    Button(
        onClick = onClick,
        modifier = modifier
            .shadow(
                elevation = if (enabled) 7.dp else 0.dp,
                shape = shape,
                ambientColor = accent.copy(alpha = 0.12f),
                spotColor = accent.copy(alpha = 0.16f),
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = if (enabled) 0.28f else 0.06f),
                        AppColors.SurfaceHigh.copy(alpha = if (enabled) 0.94f else 0.48f),
                    ),
                ),
            )
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = if (enabled) 0.82f else 0.2f),
                            accent.copy(alpha = if (enabled) 0.25f else 0.08f),
                        ),
                    ),
                ),
                shape,
            ),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color(0xFFE7F8FF),
            disabledContainerColor = Color.Transparent,
            disabledContentColor = AppColors.TextSecondary,
        ),
        content = {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
            }
            content()
        },
    )
}
