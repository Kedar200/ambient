package com.ambient.os.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ambient.os.ConnectionState
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val CARD_HEIGHT_DP = 240

@Composable
fun DeviceCard(state: ConnectionState, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CARD_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF0E1628),
                    1f to Color(0xFF07090F),
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is ConnectionState.Searching -> SearchingCard(state)
            is ConnectionState.Connecting -> SearchingCard(ConnectionState.Searching(1), label = "Locking on ${state.name}…")
            is ConnectionState.Connected -> ConnectedCard(state)
            ConnectionState.Disconnected -> DisconnectedCard()
        }
    }
}

@Composable
private fun SearchingCard(state: ConnectionState.Searching, label: String = "Searching the ether…") {
    val transition = rememberInfiniteTransition(label = "searching")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )

    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val minDim = min(size.width, size.height)
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minDim * 0.42f

        // Holographic grid
        val gridSpacing = 22f
        var x = 0f
        while (x < size.width) {
            drawLine(
                color = AmbientColors.GridLine,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f,
            )
            x += gridSpacing
        }
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = AmbientColors.GridLine,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
            y += gridSpacing
        }

        // Concentric radar rings
        for (i in 1..3) {
            drawCircle(
                color = AmbientColors.AccentCyan.copy(alpha = 0.18f / i),
                radius = radius * i / 3f,
                center = center,
                style = Stroke(width = 1.2f),
            )
        }

        // Sweeping arc
        rotate(sweep, pivot = center) {
            drawArc(
                brush = Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.85f to AmbientColors.AccentCyan.copy(alpha = 0.0f),
                    0.98f to AmbientColors.AccentCyan,
                    1f to AmbientColors.AccentCyan,
                    center = center,
                ),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = 2.4f, cap = StrokeCap.Round),
            )
        }

        // Pulsing core dot
        drawCircle(
            color = AmbientColors.AccentCyan.copy(alpha = 0.35f + 0.4f * (1f - shimmer)),
            radius = 6f + 8f * shimmer,
            center = center,
        )
        drawCircle(
            color = AmbientColors.AccentCyan,
            radius = 3.5f,
            center = center,
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "• SEARCHING",
            color = AmbientColors.AccentCyan,
            fontSize = 11.sp,
            letterSpacing = 3.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Column {
            ShimmerText(text = label, shimmer = shimmer)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "attempt ${state.attempt}".uppercase(),
                color = AmbientColors.TextSecondary,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
            )
        }
    }
}

@Composable
private fun ShimmerText(text: String, shimmer: Float) {
    val shimmerBrush = Brush.linearGradient(
        0f to AmbientColors.TextSecondary,
        shimmer.coerceIn(0f, 1f) to AmbientColors.AccentCyan,
        (shimmer + 0.2f).coerceIn(0f, 1f) to AmbientColors.TextSecondary,
        start = Offset(0f, 0f),
        end = Offset(600f, 0f),
    )
    Text(
        text = text,
        style = androidx.compose.ui.text.TextStyle(
            brush = shimmerBrush,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
        ),
    )
}

@Composable
private fun ConnectedCard(state: ConnectionState.Connected) {
    // Glitch-in per-char offsets, settle after 600ms.
    val glitch = remember { Animatable(1f) }
    LaunchedEffect(state.name) {
        glitch.snapTo(1f)
        glitch.animateTo(0f, animationSpec = tween(600, easing = FastOutSlowInEasing))
    }
    val transition = rememberInfiniteTransition(label = "connected")
    val corona by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "corona",
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val base = min(size.width, size.height) * 0.22f
        for (i in 0..4) {
            val r = base + i * 18f + corona * 10f
            drawCircle(
                color = AmbientColors.AccentCyan.copy(alpha = (0.16f - i * 0.03f).coerceAtLeast(0f)),
                radius = r,
                center = center,
                style = Stroke(width = 1.6f),
            )
        }
        // Solid inner glow
        drawCircle(
            brush = Brush.radialGradient(
                0f to AmbientColors.AccentCyan.copy(alpha = 0.55f),
                1f to Color.Transparent,
                center = center,
                radius = base,
            ),
            radius = base,
            center = center,
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(AmbientColors.Success),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "• LINKED",
                color = AmbientColors.Success,
                fontSize = 11.sp,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "${state.host}:${state.port}",
                color = AmbientColors.TextSecondary,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
            )
        }

        GlitchText(text = state.name, amount = glitch.value)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatPill(label = "BATTERY", value = state.battery?.let { "$it%" } ?: "—")
            StatPill(label = "VERSION", value = "v${state.version ?: "1"}")
            StatPill(label = "LINK", value = "mDNS")
        }
    }
}

@Composable
private fun GlitchText(text: String, amount: Float) {
    val glitchR = amount * (if (amount > 0.05f) 6f else 0f)
    val glitchB = amount * (if (amount > 0.05f) -6f else 0f)
    Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
        if (amount > 0.02f) {
            Text(
                text = text,
                color = Color(0xFFFF3B6B),
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 3.sp,
                modifier = Modifier.offset(x = (glitchR + Random.nextFloat() * 2f).dp),
            )
            Text(
                text = text,
                color = Color(0xFF3DE8FF),
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 3.sp,
                modifier = Modifier.offset(x = (glitchB - Random.nextFloat() * 2f).dp),
            )
        }
        Text(
            text = text,
            color = AmbientColors.TextPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 3.sp,
        )
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Column {
        Text(
            text = label,
            color = AmbientColors.TextSecondary,
            fontSize = 9.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = AmbientColors.AccentCyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun DisconnectedCard() {
    val transition = rememberInfiniteTransition(label = "disconnected")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val r = min(size.width, size.height) * 0.3f
        drawCircle(
            color = AmbientColors.Danger.copy(alpha = 0.12f + 0.18f * pulse),
            radius = r + 10f * pulse,
            center = center,
            style = Stroke(width = 1.6f),
        )
        // Broken radial marks
        for (i in 0 until 12) {
            val angle = (i * 30f) * Math.PI.toFloat() / 180f
            val inner = r - 14f
            val outer = r - 4f
            drawLine(
                color = AmbientColors.Danger.copy(alpha = 0.35f),
                start = Offset(center.x + inner * cos(angle), center.y + inner * sin(angle)),
                end = Offset(center.x + outer * cos(angle), center.y + outer * sin(angle)),
                strokeWidth = 2f,
            )
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "OFFLINE",
            color = AmbientColors.Danger,
            fontSize = 14.sp,
            letterSpacing = 6.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Waiting for agent broadcast",
            color = AmbientColors.TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}
