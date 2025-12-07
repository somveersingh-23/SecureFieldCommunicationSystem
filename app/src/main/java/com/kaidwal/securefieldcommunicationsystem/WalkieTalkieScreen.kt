package com.kaidwal.securefieldcommunicationsystem

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkieTalkieScreen(
    deviceId: String,
    currentTransport: TransportMode,
    onNavigateBack: () -> Unit,
    viewModel: WalkieTalkieViewModel = viewModel()
) {
    val isPTTActive by viewModel.isPTTActive.collectAsState()
    val isReceiving by viewModel.isReceiving.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(deviceId, currentTransport) {
        viewModel.connectToDevice(deviceId, currentTransport)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Walkie-Talkie", fontWeight = FontWeight.Bold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(currentTransport.icon, fontSize = 12.sp)
                            Text(
                                currentTransport.displayName,
                                fontSize = 12.sp,
                                color = Color(0xFF00D4AA)
                            )
                            Text("•", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                            ConnectionDot(connectionState)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleSpeaker() }) {
                        Icon(
                            if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            "Speaker",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                verticalArrangement = Arrangement.spacedBy(32.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
            ) {
                Spacer(modifier = Modifier.weight(0.5f))

                DeviceInfoCard(deviceId)

                PTTStatusCard(
                    isPTTActive = isPTTActive,
                    isReceiving = isReceiving,
                    audioLevel = audioLevel
                )

                Spacer(modifier = Modifier.weight(1f))

                AudioLevelVisualizer(
                    audioLevel = audioLevel,
                    isActive = isPTTActive || isReceiving
                )

                Spacer(modifier = Modifier.weight(0.5f))

                PTTButton(
                    isPTTActive = isPTTActive,
                    onPTTStart = {
                        scope.launch {
                            viewModel.startTransmitting()
                        }
                    },
                    onPTTStop = {
                        scope.launch {
                            viewModel.stopTransmitting()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    if (isPTTActive) "TRANSMITTING - RELEASE TO STOP"
                    else "HOLD TO TALK",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPTTActive) Color(0xFF00D4AA) else Color.White.copy(alpha = 0.6f),
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.weight(0.5f))
            }
        }
    }
}

@Composable
fun ConnectionDot(state: WalkieTalkieViewModel.PTTConnectionState) {
    val color = when (state) {
        WalkieTalkieViewModel.PTTConnectionState.CONNECTED -> Color.Green
        WalkieTalkieViewModel.PTTConnectionState.CONNECTING -> Color.Yellow
        WalkieTalkieViewModel.PTTConnectionState.DISCONNECTED -> Color.Red
    }

    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun DeviceInfoCard(deviceId: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E).copy(alpha = 0.8f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = Color(0xFF00D4AA),
                modifier = Modifier.size(32.dp)
            )
            Column {
                Text(
                    "Connected Device",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Text(
                    deviceId.take(12),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun PTTStatusCard(
    isPTTActive: Boolean,
    isReceiving: Boolean,
    audioLevel: Float
) {
    val statusText = when {
        isPTTActive -> "🎙️ TRANSMITTING"
        isReceiving -> "🔊 RECEIVING"
        else -> "🔇 STANDBY"
    }

    val statusColor = when {
        isPTTActive -> Color(0xFF00D4AA)
        isReceiving -> Color(0xFF0099CC)
        else -> Color.Gray
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        Text(
            statusText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = statusColor,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            letterSpacing = 2.sp
        )
    }
}

@Composable
fun AudioLevelVisualizer(
    audioLevel: Float,
    isActive: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(60.dp)
    ) {
        repeat(20) { index ->
            val height = if (isActive && index < (audioLevel * 20).toInt()) {
                ((index + 1) * 3).dp
            } else {
                8.dp
            }

            val color = if (isActive && index < (audioLevel * 20).toInt()) {
                Color(0xFF00D4AA)
            } else {
                Color.White.copy(alpha = 0.2f)
            }

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height)
                    .background(color, shape = RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
fun PTTButton(
    isPTTActive: Boolean,
    onPTTStart: () -> Unit,
    onPTTStop: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isPTTActive) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(200.dp)
    ) {
        if (isPTTActive) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00D4AA).copy(alpha = pulseAlpha * 0.3f))
            )
        }

        Surface(
            modifier = Modifier
                .size(180.dp)
                .scale(scale)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onPTTStart()
                            tryAwaitRelease()
                            onPTTStop()
                        }
                    )
                },
            shape = CircleShape,
            color = if (isPTTActive) Color(0xFF00D4AA) else Color(0xFF1A1A2E),
            shadowElevation = 12.dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "PTT",
                        tint = if (isPTTActive) Color.Black else Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        if (isPTTActive) "RELEASE" else "PUSH",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPTTActive) Color.Black else Color.White,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}
