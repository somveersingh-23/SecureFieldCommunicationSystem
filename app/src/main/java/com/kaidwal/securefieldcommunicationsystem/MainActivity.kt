package com.kaidwal.securefieldcommunicationsystem

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Timber.d("All permissions granted")
        } else {
            Timber.w("Some permissions denied: ${permissions.filter { !it.value }}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.d("MainActivity created")
        checkAndRequestPermissions()

        setContent {
            SFCSTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SFCSNavigation()
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            Timber.d("Requesting permissions: $permissionsToRequest")
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            Timber.d("All permissions already granted")
        }
    }
}

@Composable
fun SFCSNavigation() {
    val navController = rememberNavController()
    var currentTransport by remember { mutableStateOf(TransportMode.BLUETOOTH) }

    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        composable("splash") {
            SplashScreen(
                onNavigateToDiscovery = {
                    navController.navigate("device_discovery") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }

        composable("device_discovery") {
            DeviceDiscoveryScreen(
                currentTransport = currentTransport,
                onTransportChange = { currentTransport = it },
                onDeviceSelected = { deviceId ->
                    navController.navigate("chat/$deviceId")
                },
                onNavigateToWalkieTalkie = { deviceId ->
                    navController.navigate("walkie_talkie/$deviceId")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                }
            )
        }

        composable(
            route = "chat/{deviceId}",
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
            ChatScreen(
                deviceId = deviceId,
                currentTransport = currentTransport,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToWalkieTalkie = {
                    navController.navigate("walkie_talkie/$deviceId")
                }
            )
        }

        composable(
            route = "walkie_talkie/{deviceId}",
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
            WalkieTalkieScreen(
                deviceId = deviceId,
                currentTransport = currentTransport,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun SFCSTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF00D4AA),
            onPrimary = androidx.compose.ui.graphics.Color.Black,
            secondary = androidx.compose.ui.graphics.Color(0xFF0099CC),
            onSecondary = androidx.compose.ui.graphics.Color.White,
            background = androidx.compose.ui.graphics.Color(0xFF0F2027),
            onBackground = androidx.compose.ui.graphics.Color.White,
            surface = androidx.compose.ui.graphics.Color(0xFF1A1A2E),
            onSurface = androidx.compose.ui.graphics.Color.White
        ),
        content = content
    )
}

@Composable
fun darkColorScheme(
    primary: androidx.compose.ui.graphics.Color,
    onPrimary: androidx.compose.ui.graphics.Color,
    secondary: androidx.compose.ui.graphics.Color,
    onSecondary: androidx.compose.ui.graphics.Color,
    background: androidx.compose.ui.graphics.Color,
    onBackground: androidx.compose.ui.graphics.Color,
    surface: androidx.compose.ui.graphics.Color,
    onSurface: androidx.compose.ui.graphics.Color
): androidx.compose.material3.ColorScheme {
    return androidx.compose.material3.ColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primary,
        onPrimaryContainer = onPrimary,
        inversePrimary = onPrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondary,
        onSecondaryContainer = onSecondary,
        tertiary = secondary,
        onTertiary = onSecondary,
        tertiaryContainer = secondary,
        onTertiaryContainer = onSecondary,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surface,
        onSurfaceVariant = onSurface,
        surfaceTint = primary,
        inverseSurface = onSurface,
        inverseOnSurface = surface,
        error = androidx.compose.ui.graphics.Color(0xFFFF4444),
        onError = androidx.compose.ui.graphics.Color.White,
        errorContainer = androidx.compose.ui.graphics.Color(0xFFFF4444),
        onErrorContainer = androidx.compose.ui.graphics.Color.White,
        outline = androidx.compose.ui.graphics.Color.Gray,
        outlineVariant = androidx.compose.ui.graphics.Color.Gray,
        scrim = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
        surfaceBright = surface,
        surfaceDim = background,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surface,
        surfaceContainerLow = background,
        surfaceContainerLowest = background
    )
}
