package com.ambient.os.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ToggleTile(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetGlow = if (enabled) 0.6f else 0f
    val glow by animateFloatAsState(targetValue = targetGlow, animationSpec = tween(350), label = "glow")
    val borderColor by animateColorAsState(
        targetValue = if (enabled) AmbientColors.AccentCyan else Color(0x1FFFFFFF),
        animationSpec = tween(350),
        label = "border",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (enabled) Brush.horizontalGradient(
                    0f to Color(0xFF0C1B2A),
                    1f to Color(0xFF0E1022),
                ) else Brush.horizontalGradient(
                    0f to Color(0xFF0A0E16),
                    1f to Color(0xFF0A0E16),
                )
            )
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable { onToggle(!enabled) }
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = AmbientColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = if (enabled) AmbientColors.AccentCyan else AmbientColors.TextSecondary,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            PowerToggle(enabled = enabled, glow = glow)
        }
    }
}

@Composable
private fun PowerToggle(enabled: Boolean, glow: Float) {
    val thumbOffset by animateDpAsState(
        targetValue = if (enabled) 22.dp else 0.dp,
        animationSpec = tween(300),
        label = "thumb",
    )
    Box(
        modifier = Modifier
            .width(52.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) AmbientColors.AccentCyan.copy(alpha = 0.25f) else Color(0x22FFFFFF))
            .border(1.dp, if (enabled) AmbientColors.AccentCyan else Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = 3.dp + thumbOffset, top = 3.dp, bottom = 3.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (enabled) Brush.radialGradient(
                        0f to AmbientColors.AccentCyan,
                        1f to AmbientColors.AccentViolet,
                    ) else Brush.radialGradient(
                        0f to Color(0xFF3A4252),
                        1f to Color(0xFF1C2230),
                    )
                ),
        )
        if (enabled && glow > 0.1f) {
            // soft halo hint
            Box(
                modifier = Modifier
                    .padding(start = thumbOffset)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(AmbientColors.AccentCyan.copy(alpha = 0.12f * glow)),
            )
        }
    }
}
