package com.example.askgpt

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.askgpt.data.SelectedTextItem
import com.example.askgpt.data.SelectedTextManager
import com.example.askgpt.services.ClipboardMonitoringService
import com.example.askgpt.ui.theme.AskGPTTheme
import com.example.askgpt.utils.LogManager
import com.example.askgpt.utils.LogLevel
import java.text.SimpleDateFormat
import java.util.*

sealed class AppState {
    object Loading : AppState()
    object Normal : AppState()
    data class Error(val message: String) : AppState()
}

class MainActivity : ComponentActivity() {
    
    // Notification permission launcher for Android 13+
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            LogManager.addLog("MainActivity", "✅ Notification permission granted", LogLevel.SUCCESS)
        } else {
            LogManager.addLog("MainActivity", "⚠️ Notification permission denied", LogLevel.WARN)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            enableEdgeToEdge()
            
            // Request notification permission for Android 13+
            requestNotificationPermission()
            
            // Log app startup with crash protection
            try {
                LogManager.addLog("MainActivity", "App started successfully", LogLevel.INFO)
            } catch (e: Exception) {
                e.printStackTrace() // Don't let logging crash the app
            }
            
            // Handle intent from notification safely
            try {
                handleIncomingIntent(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            setContent {
                AskGPTTheme {
                    MainScreen()
                }
            }
        } catch (e: Exception) {
            // Emergency error handling - show basic error screen
            e.printStackTrace()
            setContent {
                AskGPTTheme {
                    ErrorScreen("App startup failed: ${e.message}")
                }
            }
        }
    }
    
    internal fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent().apply {
                        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                    LogManager.addLog("MainActivity", "🔋 Requesting battery optimization exemption", LogLevel.INFO)
                } catch (e: Exception) {
                    LogManager.addLog("MainActivity", "❌ Failed to request battery exemption: ${e.message}", LogLevel.ERROR)
                }
            } else {
                LogManager.addLog("MainActivity", "✅ Battery optimization already disabled", LogLevel.SUCCESS)
            }
        }
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    LogManager.addLog("MainActivity", "✅ Notification permission already granted", LogLevel.SUCCESS)
                }
                else -> {
                    LogManager.addLog("MainActivity", "🔔 Requesting notification permission", LogLevel.INFO)
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            LogManager.addLog("MainActivity", "✅ Pre-Android 13: Notification permission not required", LogLevel.INFO)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Stop clipboard service when app is destroyed
        try {
            val intent = Intent(this, ClipboardMonitoringService::class.java)
            stopService(intent)
            LogManager.addLog("MainActivity", "🛑 App destroyed - stopped clipboard service", LogLevel.INFO)
        } catch (e: Exception) {
            LogManager.addLog("MainActivity", "Error stopping service on app destroy: ${e.message}", LogLevel.ERROR)
        }
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        try {
            handleIncomingIntent(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun handleIncomingIntent(intent: Intent?) {
        try {
            intent?.getStringExtra("selected_text")?.let { text ->
                SelectedTextManager.addSelectedText(text)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun ErrorScreen(errorMessage: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Application Error",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Please restart the app or contact support.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    
    // Safe state collection without try-catch around composables
    val selectedTexts by SelectedTextManager.selectedTexts.collectAsStateWithLifecycle()
    val latestText by SelectedTextManager.latestText.collectAsStateWithLifecycle()
    
    var isClipboardServiceRunning by remember { mutableStateOf(false) }
    
    // Check permissions and start clipboard service on composition
    LaunchedEffect(Unit) {
        try {
            // Check battery optimization status for better service persistence
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                true // No battery optimization on older versions
            }
            
            // Log battery optimization status
            try {
                if (isIgnoringBatteryOptimizations) {
                    LogManager.addLog(
                        "MainActivity", 
                        "✅ Battery optimization disabled - service will persist", 
                        LogLevel.SUCCESS
                    )
                } else {
                    LogManager.addLog(
                        "MainActivity", 
                        "⚠️ Battery optimization enabled - service may be killed by system", 
                        LogLevel.WARN
                    )
                    // Request battery optimization exemption for better background operation
                    (context as? MainActivity)?.requestBatteryOptimizationExemption()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // Auto-start clipboard service with crash protection
            if (!isClipboardServiceRunning) {
                try {
                    // Start the main clipboard service
                    val intent = Intent(context, ClipboardMonitoringService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                    
                    isClipboardServiceRunning = true
                    
                    // Log success safely
                    try {
                        LogManager.addLog(
                            "MainActivity", 
                            "✅ Clipboard monitoring service auto-started (notification mode)", 
                            LogLevel.SUCCESS
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Log error safely
                    try {
                        LogManager.addLog(
                            "MainActivity", 
                            "❌ Failed to start clipboard service: ${e.message}", 
                            LogLevel.ERROR
                        )
                    } catch (logError: Exception) {
                        logError.printStackTrace()
                    }
                }
            }
            
            // Log service status safely
            try {
                LogManager.addLog(
                    "MainActivity", 
                    "Service status - Clipboard: $isClipboardServiceRunning, Battery opt: ${!isIgnoringBatteryOptimizations}", 
                    LogLevel.INFO
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
        } catch (e: Exception) {
            // Handle any permission checking errors
            e.printStackTrace()
            try {
                LogManager.addLog("MainActivity", "Error checking permissions: ${e.message}", LogLevel.ERROR)
            } catch (logError: Exception) {
                logError.printStackTrace()
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AskGPT - Text Monitor") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Service Status Section
            ServiceStatusCard(
                isClipboardServiceRunning = isClipboardServiceRunning
            )
            
            // Accessibility Status and Control Section
            AccessibilityControlCard()
        }
    }
}

@Composable
fun PermissionsCard(
    hasOverlayPermission: Boolean,
    isClipboardServiceRunning: Boolean,
    onRequestOverlayPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Service Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            // Clipboard Service Status (Always Running)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Clipboard Monitoring")
                    Text(
                        text = "📋 Monitors text copied to clipboard",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isClipboardServiceRunning) "✓ Active" else "⏳ Starting...",
                        color = if (isClipboardServiceRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                    // Auto-managed service - no manual stop button
                }
            }
            
            // Overlay Permission
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Screen Overlay")
                    Text(
                        text = "🔔 Shows selected text popup across all apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (hasOverlayPermission) {
                    Text("✓ Enabled", color = MaterialTheme.colorScheme.primary)
                } else {
                    Button(onClick = onRequestOverlayPermission) {
                        Text("Enable")
                    }
                }
            }
            
            if (!hasOverlayPermission) {
                Text(
                    text = "⚠️ REQUIRED: Enable overlay permission to show clipboard text across all apps (Chrome, etc.)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun ServiceStatusCard(
    isClipboardServiceRunning: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "📋 Clipboard Word Count Monitor",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            // Clipboard Service Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Monitoring Service")
                    Text(
                        text = "🔔 Shows notification with word count character",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (isClipboardServiceRunning) "✓ Active" else "⏳ Starting...",
                    color = if (isClipboardServiceRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            }
            
            // Word count explanation
            Text(
                text = "Word Count Rules: A (>2 words), B (>7 words), C (0 words), D (null)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AccessibilityControlCard() {
    val context = LocalContext.current
    var hasAccessibilityPermission by remember { mutableStateOf(false) }
    
    // Check accessibility permission status
    LaunchedEffect(Unit) {
        hasAccessibilityPermission = isAccessibilityServiceEnabled(context)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasAccessibilityPermission) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🔐 Accessibility Service",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (hasAccessibilityPermission) 
                    MaterialTheme.colorScheme.onPrimaryContainer 
                else 
                    MaterialTheme.colorScheme.onErrorContainer
            )
            
            // Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasAccessibilityPermission) "✅ Enabled" else "❌ Disabled",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (hasAccessibilityPermission) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = if (hasAccessibilityPermission) 
                            "Accessibility service is active and working"
                        else 
                            "Required for enhanced text monitoring",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasAccessibilityPermission) 
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else 
                            MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    )
                }
                
                if (!hasAccessibilityPermission) {
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                LogManager.addLog("MainActivity", "❌ Failed to open accessibility settings: ${e.message}", LogLevel.ERROR)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Enable", color = MaterialTheme.colorScheme.onError)
                    }
                }
            }
            
            if (!hasAccessibilityPermission) {
                Text(
                    text = "💡 Go to Settings > Accessibility > AskGPT and turn it on",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                )
            }
        }
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    return try {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        enabledServices?.contains(context.packageName) == true
    } catch (e: Exception) {
        LogManager.addLog("MainActivity", "❌ Error checking accessibility service: ${e.message}", LogLevel.ERROR)
        false
    }
}
