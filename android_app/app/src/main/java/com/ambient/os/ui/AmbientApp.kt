package com.ambient.os.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambient.os.ConnectionStateHolder

@Composable
fun AmbientApp(
    clipboardEnabled: Boolean,
    onClipboardToggle: (Boolean) -> Unit,
    photoWallEnabled: Boolean,
    onPhotoWallToggle: (Boolean) -> Unit,
) {
    val state by ConnectionStateHolder.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to AmbientColors.BackgroundDeep,
                    1f to Color(0xFF02040A),
                )
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Text(
                text = "AMBIENT OS",
                color = AmbientColors.AccentCyan,
                fontSize = 13.sp,
                letterSpacing = 6.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Field Link",
                color = AmbientColors.TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(24.dp))

            DeviceCard(state = state)

            Spacer(Modifier.height(28.dp))

            Text(
                text = "CHANNELS",
                color = AmbientColors.TextSecondary,
                fontSize = 10.sp,
                letterSpacing = 4.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))

            ToggleTile(
                title = "Clipboard Sync",
                subtitle = if (clipboardEnabled) "Relaying across the link" else "Offline",
                enabled = clipboardEnabled,
                onToggle = onClipboardToggle,
            )
            Spacer(Modifier.height(12.dp))
            ToggleTile(
                title = "Photo Wall",
                subtitle = if (photoWallEnabled) "Watching for new frames" else "Standby",
                enabled = photoWallEnabled,
                onToggle = onPhotoWallToggle,
            )

            Spacer(Modifier.height(32.dp))
            Text(
                text = "Pairing is automatic. Keep the agent running on your Mac and this device on the same Wi-Fi.",
                color = AmbientColors.TextSecondary,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
            )
        }
    }
}
