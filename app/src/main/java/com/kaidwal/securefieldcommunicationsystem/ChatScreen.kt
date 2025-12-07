package com.kaidwal.securefieldcommunicationsystem

import android.bluetooth.BluetoothDevice
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Chat Screen - Beautiful encrypted messaging interface with walkie-talkie integration
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    deviceId: String,
    deviceName: String = "Unknown",
    bluetoothDevice: BluetoothDevice? = null,
    currentTransport: TransportMode,
    onNavigateBack: () -> Unit,
    onNavigateToWalkieTalkie: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val encryptionStatus by viewModel.encryptionStatus.collectAsState()

    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Load messages when screen opens
    LaunchedEffect(deviceId) {
        // Create DiscoveredDevice if bluetoothDevice is available
        if (bluetoothDevice != null) {
            val discoveredDevice = DiscoveredDevice(
                bluetoothDevice = bluetoothDevice,
                name = deviceName,
                address = deviceId
            )
            viewModel.connectToDevice(discoveredDevice)
        }
        viewModel.loadMessages(deviceId)
    }

    // AUTO-REFRESH: Reload messages every 2 seconds to catch incoming messages
    LaunchedEffect(deviceId, connectionState) {
        if (connectionState is ChatViewModel.ConnectionState.Connected) {
            while (true) {
                kotlinx.coroutines.delay(2000L)
                viewModel.loadMessages(deviceId)
            }
        }
    }

    // Auto-scroll when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                deviceId = deviceId,
                connectionState = connectionState,
                encryptionStatus = encryptionStatus,
                currentTransport = currentTransport,
                onNavigateBack = onNavigateBack,
                onNavigateToWalkieTalkie = onNavigateToWalkieTalkie
            )
        },
        bottomBar = {
            ChatInputBar(
                messageText = messageText,
                onMessageChange = { messageText = it },
                onSendClick = {
                    if (messageText.isNotBlank()) {
                        viewModel.sendMessage(messageText, deviceId)
                        messageText = ""

                        coroutineScope.launch {
                            listState.animateScrollToItem(messages.size)
                        }
                    }
                },
                isEnabled = connectionState is ChatViewModel.ConnectionState.Connected
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
                )
        ) {
            if (messages.isEmpty()) {
                EmptyChatView()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages, key = { it.messageId }) { message ->
                        val isOwnMessage = message.senderId == viewModel.currentDeviceId

                        AnimatedMessageBubble(
                            message = message,
                            isOwnMessage = isOwnMessage,
                            onMessageRead = {
                                // Mark as read - method exists in ChatViewModel
                                if (!isOwnMessage) {
                                    // This function needs to be added to ChatViewModel
                                    // For now, do nothing or add stub
                                }
                            }
                        )
                    }
                }
            }

            // Connection status overlay
            AnimatedVisibility(
                visible = connectionState is ChatViewModel.ConnectionState.Connecting,
                modifier = Modifier.align(Alignment.Center)
            ) {
                ConnectingOverlay()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    deviceId: String,
    connectionState: ChatViewModel.ConnectionState,
    encryptionStatus: ChatViewModel.EncryptionStatus,
    currentTransport: TransportMode,
    onNavigateBack: () -> Unit,
    onNavigateToWalkieTalkie: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "Device ${deviceId.take(8)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Connection status
                    ConnectionStatusIndicator(connectionState)

                    Text(
                        "•",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )

                    // Encryption status
                    EncryptionIndicator(encryptionStatus)

                    // Transport indicator
                    Text(
                        "•",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                    Text(
                        currentTransport.icon,
                        fontSize = 11.sp
                    )

                    // Demo mode indicator
                    if (connectionState is ChatViewModel.ConnectionState.Connected) {
                        Text(
                            "•",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                        Text(
                            "Demo",
                            fontSize = 10.sp,
                            color = Color.Yellow,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        },
        actions = {
            // Voice call button
            IconButton(onClick = onNavigateToWalkieTalkie) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Voice Mode",
                    tint = Color(0xFF00D4AA)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF1A1A2E),
            titleContentColor = Color.White
        )
    )
}

@Composable
fun ConnectionStatusIndicator(state: ChatViewModel.ConnectionState) {
    val (color, text) = when (state) {
        is ChatViewModel.ConnectionState.Connected -> Color.Green to "Connected"
        is ChatViewModel.ConnectionState.Connecting -> Color.Yellow to "Connecting"
        is ChatViewModel.ConnectionState.Disconnected -> Color.Gray to "Offline"
        is ChatViewModel.ConnectionState.Error -> Color.Red to "Error"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "connection")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    color.copy(
                        alpha = if (state is ChatViewModel.ConnectionState.Connected) 1f else alpha
                    )
                )
        )

        Text(
            text = text,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun EncryptionIndicator(status: ChatViewModel.EncryptionStatus) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = if (status.isEncrypted) Icons.Default.Lock else Icons.Default.LockOpen,
            contentDescription = "Encryption",
            tint = if (status.isEncrypted) Color.Green else Color.Red,
            modifier = Modifier.size(14.dp)
        )

        Text(
            text = "E2EE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun AnimatedMessageBubble(
    message: MessageManager.Message,
    isOwnMessage: Boolean,
    onMessageRead: () -> Unit
) {
    val enterTransition = remember {
        slideInHorizontally(
            initialOffsetX = { if (isOwnMessage) it else -it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn()
    }

    LaunchedEffect(Unit) {
        if (!isOwnMessage && message.status == MessageManager.MessageStatus.DELIVERED) {
            kotlinx.coroutines.delay(500)
            onMessageRead()
        }
    }

    AnimatedVisibility(
        visible = true,
        enter = enterTransition
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start
        ) {
            MessageBubble(
                message = message,
                isOwnMessage = isOwnMessage
            )
        }
    }
}

@Composable
fun MessageBubble(
    message: MessageManager.Message,
    isOwnMessage: Boolean
) {
    Surface(
        shape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = if (isOwnMessage) 16.dp else 4.dp,
            bottomEnd = if (isOwnMessage) 4.dp else 16.dp
        ),
        color = if (isOwnMessage) Color(0xFF00D4AA) else Color(0xFF2D2D44),
        shadowElevation = 4.dp,
        modifier = Modifier.widthIn(max = 280.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Message content
            Text(
                text = message.content,
                fontSize = 15.sp,
                color = if (isOwnMessage) Color.Black else Color.White,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Message metadata
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timestamp
                Text(
                    text = formatTimestamp(message.timestamp),
                    fontSize = 11.sp,
                    color = if (isOwnMessage)
                        Color.Black.copy(alpha = 0.6f)
                    else Color.White.copy(alpha = 0.6f)
                )

                // Encryption badge
                if (message.isEncrypted) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Encrypted",
                        modifier = Modifier.size(10.dp),
                        tint = if (isOwnMessage)
                            Color.Black.copy(alpha = 0.5f)
                        else Color.White.copy(alpha = 0.5f)
                    )
                }

                // Hop count
                if (message.hopCount > 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Hub,
                            contentDescription = "Hops",
                            modifier = Modifier.size(10.dp),
                            tint = if (isOwnMessage)
                                Color.Black.copy(alpha = 0.5f)
                            else Color.White.copy(alpha = 0.5f)
                        )
                        Text(
                            "${message.hopCount}",
                            fontSize = 10.sp,
                            color = if (isOwnMessage)
                                Color.Black.copy(alpha = 0.6f)
                            else Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                // Message status (for sent messages)
                if (isOwnMessage) {
                    MessageStatusIcon(message.status)
                }
            }
        }
    }
}

@Composable
fun MessageStatusIcon(status: MessageManager.MessageStatus) {
    val (icon, tint) = when (status) {
        MessageManager.MessageStatus.PENDING -> Icons.Default.Schedule to Color.Gray
        MessageManager.MessageStatus.SENDING -> Icons.Default.Schedule to Color.Yellow
        MessageManager.MessageStatus.SENT -> Icons.Default.Check to Color.White
        MessageManager.MessageStatus.DELIVERED -> Icons.Default.DoneAll to Color.White
        MessageManager.MessageStatus.READ -> Icons.Default.DoneAll to Color.Blue
        MessageManager.MessageStatus.FAILED -> Icons.Default.Error to Color.Red
        MessageManager.MessageStatus.DELETED -> Icons.Default.DeleteOutline to Color.Gray
    }

    Icon(
        imageVector = icon,
        contentDescription = status.name,
        modifier = Modifier.size(14.dp),
        tint = tint.copy(alpha = 0.8f)
    )
}

@Composable
fun ChatInputBar(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    isEnabled: Boolean
) {
    Surface(
        color = Color(0xFF1A1A2E),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Text input
            OutlinedTextField(
                value = messageText,
                onValueChange = onMessageChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Type encrypted message...",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                },
                enabled = isEnabled,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    disabledTextColor = Color.Gray,
                    focusedBorderColor = Color(0xFF00D4AA),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color(0xFF00D4AA)
                ),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4
            )

            // Send button
            Surface(
                shape = CircleShape,
                color = if (isEnabled && messageText.isNotBlank()) {
                    Color(0xFF00D4AA)
                } else {
                    Color.Gray
                },
                modifier = Modifier.size(48.dp)
            ) {
                IconButton(
                    onClick = onSendClick,
                    enabled = isEnabled && messageText.isNotBlank()
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyChatView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Message,
                contentDescription = "No messages",
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(72.dp)
            )

            Text(
                "No messages yet",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Start a secure conversation",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Encryption info card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2D2D44).copy(alpha = 0.5f)
                ),
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = "Secure",
                        tint = Color(0xFF00D4AA),
                        modifier = Modifier.size(32.dp)
                    )

                    Column {
                        Text(
                            "End-to-End Encrypted",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "AES-256-GCM • X25519",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectingOverlay() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E).copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = Color(0xFF00D4AA),
                modifier = Modifier.size(48.dp)
            )

            Text(
                "Establishing secure connection...",
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}

// Helper function to format timestamp
private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val format = SimpleDateFormat("HH:mm", Locale.getDefault())
    return format.format(date)
}
