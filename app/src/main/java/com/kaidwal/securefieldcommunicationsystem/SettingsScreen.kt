package com.kaidwal.securefieldcommunicationsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val deviceId by viewModel.deviceId.collectAsState()
    val encryptionEnabled by viewModel.encryptionEnabled.collectAsState()
    val autoDeleteEnabled by viewModel.autoDeleteEnabled.collectAsState()
    val meshNetworkEnabled by viewModel.meshNetworkEnabled.collectAsState()
    val connectionType by viewModel.connectionType.collectAsState()

    // Voice settings
    val highQualityAudio by viewModel.highQualityAudio.collectAsState()
    val noiseSuppression by viewModel.noiseSuppression.collectAsState()
    val autoGainControl by viewModel.autoGainControl.collectAsState()
    val pttSensitivity by viewModel.pttSensitivity.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        fontWeight = FontWeight.Bold
                    )
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
                )
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Device Information
                item {
                    SettingsSection(title = "Device Information") {
                        InfoCard(
                            icon = Icons.Default.PhoneAndroid,
                            title = "Device ID",
                            value = deviceId.take(16) + "...",
                            description = "Your anonymous device identifier"
                        )
                    }
                }

                // Security Settings
                item {
                    SettingsSection(title = "Security") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SwitchSettingCard(
                                icon = Icons.Default.Lock,
                                title = "End-to-End Encryption",
                                description = "AES-256-GCM + X25519",
                                checked = encryptionEnabled,
                                onCheckedChange = { viewModel.toggleEncryption(it) }
                            )

                            SwitchSettingCard(
                                icon = Icons.Default.DeleteForever,
                                title = "Auto-Delete Messages",
                                description = "Delete after delivery",
                                checked = autoDeleteEnabled,
                                onCheckedChange = { viewModel.toggleAutoDelete(it) }
                            )

                            ClickableSettingCard(
                                icon = Icons.Default.Key,
                                title = "Rotate Encryption Keys",
                                description = "Generate new session keys",
                                onClick = { viewModel.rotateKeys() }
                            )
                        }
                    }
                }

                // Voice Communication Settings
                item {
                    SettingsSection(title = "Voice Communication") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SwitchSettingCard(
                                icon = Icons.Default.HighQuality,
                                title = "High Quality Audio",
                                description = "32kHz sampling (higher battery usage)",
                                checked = highQualityAudio,
                                onCheckedChange = { viewModel.setHighQualityAudio(it) }
                            )

                            SwitchSettingCard(
                                icon = Icons.Default.VolumeOff,
                                title = "Noise Suppression",
                                description = "Reduce background noise",
                                checked = noiseSuppression,
                                onCheckedChange = { viewModel.setNoiseSuppression(it) }
                            )

                            SwitchSettingCard(
                                icon = Icons.Default.VolumeUp,
                                title = "Auto-Gain Control",
                                description = "Automatic volume adjustment",
                                checked = autoGainControl,
                                onCheckedChange = { viewModel.setAutoGainControl(it) }
                            )

                            // PTT Sensitivity Slider
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF2D2D44)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.TouchApp,
                                            contentDescription = "Sensitivity",
                                            tint = Color(0xFF00D4AA),
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "PTT Button Sensitivity",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                "Adjust touch response",
                                                fontSize = 12.sp,
                                                color = Color.White.copy(alpha = 0.6f)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Slider(
                                        value = pttSensitivity,
                                        onValueChange = { viewModel.setPTTSensitivity(it) },
                                        valueRange = 0f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFF00D4AA),
                                            activeTrackColor = Color(0xFF00D4AA),
                                            inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                                        )
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Low",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                        Text(
                                            "High",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Network Settings
                item {
                    SettingsSection(title = "Network") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SwitchSettingCard(
                                icon = Icons.Default.Hub,
                                title = "Mesh Networking",
                                description = "Multi-hop message relay",
                                checked = meshNetworkEnabled,
                                onCheckedChange = { viewModel.toggleMeshNetwork(it) }
                            )

                            RadioSettingCard(
                                icon = Icons.Default.Wifi,
                                title = "Connection Type",
                                options = listOf("Bluetooth", "WiFi Direct", "Auto"),
                                selectedOption = connectionType,
                                onOptionSelected = { viewModel.setConnectionType(it) }
                            )
                        }
                    }
                }

                // Database Management
                item {
                    SettingsSection(title = "Data Management") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ClickableSettingCard(
                                icon = Icons.Default.DeleteSweep,
                                title = "Clear All Messages",
                                description = "Delete message history",
                                onClick = { viewModel.clearMessages() },
                                isDestructive = true
                            )

                            ClickableSettingCard(
                                icon = Icons.Default.DevicesOther,
                                title = "Clear Device List",
                                description = "Remove saved devices",
                                onClick = { viewModel.clearDevices() },
                                isDestructive = true
                            )
                        }
                    }
                }

                // About
                item {
                    SettingsSection(title = "About") {
                        InfoCard(
                            icon = Icons.Default.Info,
                            title = "Version",
                            value = "2.0.0",
                            description = "Walkie-Talkie System | Research Prototype"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00D4AA),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        content()
    }
}

@Composable
fun InfoCard(
    icon: ImageVector,
    title: String,
    value: String,
    description: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D44)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = Color(0xFF00D4AA),
                modifier = Modifier.size(40.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    description,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun SwitchSettingCard(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D44)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = Color(0xFF00D4AA),
                modifier = Modifier.size(32.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    description,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF00D4AA),
                    checkedTrackColor = Color(0xFF00D4AA).copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
fun ClickableSettingCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isDestructive)
                Color(0xFF5A2D2D) else Color(0xFF2D2D44)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = if (isDestructive) Color.Red else Color(0xFF00D4AA),
                modifier = Modifier.size(32.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDestructive) Color.Red else Color.White
                )
                Text(
                    description,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Action",
                tint = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun RadioSettingCard(
    icon: ImageVector,
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D44)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = Color(0xFF00D4AA),
                    modifier = Modifier.size(32.dp)
                )

                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOptionSelected(option) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selectedOption,
                            onClick = { onOptionSelected(option) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF00D4AA)
                            )
                        )

                        Text(
                            option,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
