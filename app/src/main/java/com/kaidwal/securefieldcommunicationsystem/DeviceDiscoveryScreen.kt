package com.kaidwal.securefieldcommunicationsystem

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDiscoveryScreen(
    currentTransport: TransportMode,
    onTransportChange: (TransportMode) -> Unit,
    onDeviceSelected: (String) -> Unit,
    onNavigateToWalkieTalkie: (String) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    viewModel: DeviceViewModel = viewModel()
) {
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val isServerMode by viewModel.isServerMode.collectAsState()
    val meshNodes by viewModel.meshNodes.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    var showTransportSelector by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(connectionState) {
        when (connectionState) {
            is DeviceViewModel.DeviceConnectionState.Connecting -> {
                snackbarHostState.showSnackbar("Connecting to device...")
            }
            is DeviceViewModel.DeviceConnectionState.Connected -> {
                snackbarHostState.showSnackbar("Connected successfully!")
            }
            is DeviceViewModel.DeviceConnectionState.Error -> {
                val error = connectionState as DeviceViewModel.DeviceConnectionState.Error
                snackbarHostState.showSnackbar("Connection failed: ${error.message}")
            }
            else -> {}
        }
    }

    LaunchedEffect(currentTransport) {
        viewModel.startDiscovery(currentTransport)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "SFCS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Secure Field Communication",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { showTransportSelector = true }) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = "Switch Transport",
                            tint = Color(0xFF00D4AA)
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                FloatingActionButton(
                    onClick = {
                        if (isServerMode) {
                            viewModel.stopServerMode()
                        } else {
                            viewModel.startServerMode()
                        }
                    },
                    containerColor = if (isServerMode) Color.Green else Color.Gray,
                    contentColor = Color.White
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = if (isServerMode) "Stop Server" else "Start Server"
                    )
                }

                FloatingActionButton(
                    onClick = {
                        if (isScanning) viewModel.stopScanning()
                        else viewModel.startScanning()
                    },
                    containerColor = if (isScanning) Color.Red else Color(0xFF00D4AA),
                    contentColor = Color.White
                ) {
                    Icon(
                        imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.Radar,
                        contentDescription = if (isScanning) "Stop Scanning" else "Start Scanning"
                    )
                }
            }
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
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TransportModeCard(currentTransport)

                AnimatedVisibility(
                    visible = isServerMode,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    ServerModeIndicatorCard()
                }

                AnimatedVisibility(
                    visible = isScanning,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    ScanningIndicatorCard(currentTransport)
                }

                if (meshNodes.isNotEmpty()) {
                    MeshNetworkCard(meshNodes)
                }

                val stats = remember(discoveredDevices) {
                    derivedStateOf {
                        mapOf(
                            "total" to discoveredDevices.size,
                            "connected" to discoveredDevices.count { it.isConnected }
                        )
                    }
                }

                DeviceStatsCard(
                    totalDevices = stats.value["total"] ?: 0,
                    connectedDevices = stats.value["connected"] ?: 0
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(discoveredDevices, key = { it.deviceId }) { device ->
                        DeviceCardWithVoice(
                            device = device,
                            onChatClick = { onDeviceSelected(device.deviceId) },
                            onVoiceClick = { onNavigateToWalkieTalkie(device.deviceId) }
                        )
                    }

                    if (discoveredDevices.isEmpty() && !isScanning) {
                        item {
                            EmptyDeviceListCard()
                        }
                    }
                }
            }

            if (showTransportSelector) {
                TransportSelectorDialog(
                    currentTransport = currentTransport,
                    onTransportSelected = {
                        onTransportChange(it)
                        showTransportSelector = false
                    },
                    onDismiss = { showTransportSelector = false }
                )
            }
        }
    }
}

@Composable
fun TransportModeCard(transport: TransportMode) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = transport.icon, fontSize = 32.sp)
                Column {
                    Text(
                        "Active Transport",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        transport.displayName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00D4AA)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.Green)
            )
        }
    }
}

@Composable
fun ServerModeIndicatorCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "server")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D4D2D)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Green.copy(alpha = alpha)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PhoneAndroid,
                    contentDescription = "Server Mode",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Server Mode Active",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    "Listening for incoming connections",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun ScanningIndicatorCard(transport: TransportMode) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D44)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00D4AA).copy(alpha = alpha)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Radar,
                    contentDescription = "Scanning",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Scanning for devices...",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    "Using ${transport.displayName}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun MeshNetworkCard(nodes: List<DeviceViewModel.MeshNode>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D44))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Hub,
                    contentDescription = "Mesh Network",
                    tint = Color(0xFF00D4AA),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Mesh Network",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "${nodes.count { it.isActive }} active nodes",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun DeviceStatsCard(totalDevices: Int, connectedDevices: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2D2D44).copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                icon = Icons.Default.Devices,
                label = "Total",
                value = totalDevices.toString(),
                color = Color(0xFF00D4AA)
            )

            StatItem(
                icon = Icons.Default.Link,
                label = "Connected",
                value = connectedDevices.toString(),
                color = Color(0xFF0099CC)
            )
        }
    }
}

@Composable
fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Text(
            value,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Text(
            label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
    }
}

@Composable
fun DeviceCardWithVoice(
    device: DeviceViewModel.DiscoveredDevice,
    onChatClick: () -> Unit,
    onVoiceClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D44)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF00D4AA),
                                    Color(0xFF0099CC)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (device.connectionType == "BLUETOOTH")
                            Icons.Default.Bluetooth
                        else Icons.Default.Wifi,
                        contentDescription = "Device Type",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        device.displayName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Text(
                        "ID: ${device.deviceId.take(12)}...",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SignalBadge(device.signalStrength)
                        ConnectionTypeBadge(device.connectionType)
                        if (device.isConnected) {
                            ConnectedBadge()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onChatClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4AA))
                ) {
                    Icon(
                        Icons.Default.Message,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Chat", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onVoiceClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0099CC))
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Voice", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SignalBadge(strength: Int) {
    val color = when {
        strength > 75 -> Color.Green
        strength > 50 -> Color.Yellow
        else -> Color.Red
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.SignalCellularAlt,
                contentDescription = "Signal",
                tint = color,
                modifier = Modifier.size(12.dp)
            )
            Text(
                "$strength%",
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ConnectionTypeBadge(type: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFF4A4A62)
    ) {
        Text(
            text = if (type == "BLUETOOTH") "BT" else "WiFi",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun ConnectedBadge() {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color.Green.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Connected",
                tint = Color.Green,
                modifier = Modifier.size(12.dp)
            )
            Text(
                "Active",
                color = Color.Green,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TransportSelectorDialog(
    currentTransport: TransportMode,
    onTransportSelected: (TransportMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Transport Mode", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TransportMode.values().forEach { mode ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTransportSelected(mode) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (mode == currentTransport)
                                Color(0xFF00D4AA).copy(alpha = 0.2f)
                            else Color(0xFF2D2D44)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(text = mode.icon, fontSize = 28.sp)
                            Column {
                                Text(
                                    mode.displayName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    when (mode) {
                                        TransportMode.BLUETOOTH -> "Short-range P2P • 10-100m"
                                        TransportMode.WIFI_DIRECT -> "High-speed P2P • 200m+"
                                        TransportMode.RADIO_FM -> "Long-range (Experimental)"
                                    },
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF00D4AA))
            }
        },
        containerColor = Color(0xFF1A1A2E)
    )
}

@Composable
fun EmptyDeviceListCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.DeviceHub,
                contentDescription = "No devices",
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(72.dp)
            )
            Text(
                "No devices found",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Tap the scan button to discover nearby devices",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp
            )
        }
    }
}
