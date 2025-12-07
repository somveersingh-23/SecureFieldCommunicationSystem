package com.kaidwal.securefieldcommunicationsystem

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateToDiscovery: () -> Unit
) {
    var startAnimation by remember { mutableStateOf(false) }

    val alphaAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(1500),
        label = "alpha"
    )

    val scaleAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(3000L)
        onNavigateToDiscovery()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F2027),
                        Color(0xFF203A43),
                        Color(0xFF2C5364)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .alpha(alphaAnim)
                .scale(scaleAnim)
        ) {
            EncryptionLockIcon(
                modifier = Modifier.size(120.dp)
            )

            Text(
                text = "SFCS",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00D4AA),
                letterSpacing = 4.sp
            )

            Text(
                text = "Secure Field Communication System",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f),
                letterSpacing = 1.sp
            )

            // NEW: Feature highlights
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                FeatureBadge("📱 P2P Chat")
                FeatureBadge("🎙️ PTT Voice")
                FeatureBadge("🔐 E2EE")
            }

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedLoadingBar()
        }

        Text(
            text = "v2.0.0 | Walkie-Talkie System",
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}

@Composable
fun FeatureBadge(text: String) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = Color(0xFF1A1A2E).copy(alpha = 0.6f)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun EncryptionLockIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "lock")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawCircle(
                color = Color(0xFF00D4AA).copy(alpha = 0.3f),
                radius = size.minDimension / 2
            )
        }

        Text(
            text = "🔒",
            fontSize = 64.sp,
            modifier = Modifier.alpha(0.9f)
        )
    }
}

@Composable
fun AnimatedLoadingBar() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    Box(
        modifier = Modifier
            .width(200.dp)
            .height(4.dp)
            .background(Color.White.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(Color(0xFF00D4AA))
        )
    }
}
