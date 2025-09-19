package com.example.askgpt

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import com.example.askgpt.config.ChatGPTConfig
import com.example.askgpt.data.SelectedTextManager
import com.example.askgpt.data.ChatGPTHistoryManager
import com.example.askgpt.data.ChatGPTEntry
import com.example.askgpt.services.ClipboardMonitoringService
import com.example.askgpt.services.OverlayButtonService
import com.example.askgpt.ui.theme.AskGPTTheme
import com.example.askgpt.utils.LogManager
import com.example.askgpt.utils.LogLevel
import com.example.askgpt.utils.GlobalClipboardManager
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
            GlobalClipboardManager.initialize(this)
            requestNotificationPermission()
            
            LogManager.addLog("MainActivity", "App started successfully", LogLevel.INFO)
            handleIncomingIntent(intent)
            
            setContent {
                AskGPTTheme {
                    SafeMainScreen()
                }
            }
            
            // Start clipboard service after UI initialization
            (application as? AskGPTApplication)?.startClipboardServiceSafely()
        } catch (e: Exception) {
            Log.e("MainActivity", "App startup failed", e)
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
        
        // Export ChatGPT history for backup
        try {
            val exportPath = ChatGPTHistoryManager.exportToFile(this)
            if (exportPath != null) {
                LogManager.addLog("MainActivity", "History exported to: $exportPath", LogLevel.SUCCESS)
            }
        } catch (e: Exception) {
            LogManager.addLog("MainActivity", "Failed to export history: ${e.message}", LogLevel.ERROR)
        }
        
        // Stop clipboard service
        try {
            val intent = Intent(this, ClipboardMonitoringService::class.java)
            stopService(intent)
            LogManager.addLog("MainActivity", "Clipboard service stopped", LogLevel.INFO)
        } catch (e: Exception) {
            LogManager.addLog("MainActivity", "Error stopping service: ${e.message}", LogLevel.ERROR)
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Sync clipboard when app resumes
        try {
            val refreshedText = GlobalClipboardManager.refreshClipboardNow()
            if (refreshedText != null) {
                GlobalClipboardManager.forceSyncFromSystem()
                LogManager.addLog("MainActivity", "Clipboard synced: ${refreshedText.take(30)}...", LogLevel.DEBUG)
            }
        } catch (e: Exception) {
            LogManager.addLog("MainActivity", "Clipboard sync failed: ${e.message}", LogLevel.ERROR)
        }
    }
    
    override fun onPause() {
        super.onPause()
        LogManager.addLog("MainActivity", "App paused", LogLevel.DEBUG)
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        try {
            handleIncomingIntent(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // Store dark transition state globally
    internal var isDarkTransition = false
    
    // Background theme state (default: light)
    internal var isDarkBackground = false
    
    private fun handleIncomingIntent(intent: Intent?) {
        try {
            // Handle dark transition mode
            isDarkTransition = intent?.getBooleanExtra("DARK_TRANSITION", false) ?: false
            if (isDarkTransition) {
                LogManager.addLog("MainActivity", "🌑 Dark transition mode activated", LogLevel.INFO)
            }
            
            // Handle selected text from other apps
            intent?.getStringExtra("selected_text")?.let { text ->
                SelectedTextManager.addSelectedText(text)
            }
            
            // Handle float button restart
            val triggeredByFloatButton = intent?.getBooleanExtra("TRIGGERED_BY_FLOAT_BUTTON", false) ?: false
            val autoProcessClipboard = intent?.getBooleanExtra("AUTO_PROCESS_CLIPBOARD", false) ?: false
            
            if (triggeredByFloatButton && autoProcessClipboard) {
                LogManager.addLog("MainActivity", "🔄 App restarted by float button - auto-processing clipboard", LogLevel.SUCCESS)
                
                // Use a coroutine to handle the clipboard processing with proper delays
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        // Small delay to ensure app is fully loaded
                        delay(500)
                        
                        // Fresh clipboard sync
                        LogManager.addLog("MainActivity", "📋 Syncing clipboard after app restart", LogLevel.INFO)
                        val freshClipboard = GlobalClipboardManager.refreshClipboardNow()
                        
                        if (!freshClipboard.isNullOrBlank()) {
                            LogManager.addLog("MainActivity", "✅ Fresh clipboard content found: ${freshClipboard.take(50)}...", LogLevel.SUCCESS)
                            
                            // Trigger clipboard processing automatically
                            val serviceIntent = Intent(this@MainActivity, ClipboardMonitoringService::class.java).apply {
                                action = ClipboardMonitoringService.ACTION_TEST_CLIPBOARD
                            }
                            startForegroundService(serviceIntent)
                            
                            // Log success instead of toast
                            LogManager.addLog("MainActivity", "🚀 Auto-processing fresh clipboard content", LogLevel.INFO)
                            
                            LogManager.addLog("MainActivity", "🚀 Auto-processing initiated after restart", LogLevel.SUCCESS)
                        } else {
                            LogManager.addLog("MainActivity", "⚠️ No clipboard content found after restart", LogLevel.WARN)
                            LogManager.addLog("MainActivity", "⚠️ No clipboard content to process", LogLevel.WARN)
                        }
                        
                    } catch (e: Exception) {
                        LogManager.addLog("MainActivity", "❌ Error in auto-processing after restart: ${e.message}", LogLevel.ERROR)
                        LogManager.addLog("MainActivity", "❌ Error processing clipboard", LogLevel.ERROR)
                    }
                }
            }
            
            // Handle auto-launch after successful processing
            val autoLaunchedAfterSuccess = intent?.getBooleanExtra("AUTO_LAUNCHED_AFTER_SUCCESS", false) ?: false
            val showResultTab = intent?.getBooleanExtra("SHOW_RESULT_TAB", false) ?: false
            
            if (autoLaunchedAfterSuccess && showResultTab) {
                val lastQuestion = intent?.getStringExtra("LAST_QUESTION")
                val lastResponse = intent?.getStringExtra("LAST_RESPONSE") 
                val wasForced = intent?.getBooleanExtra("PROCESSING_WAS_FORCED", false) ?: false
                
                LogManager.addLog("MainActivity", "📱 App auto-launched after successful processing", LogLevel.SUCCESS)
                
                if (lastQuestion != null && lastResponse != null) {
                    LogManager.addLog("MainActivity", "📋 Showing result: Q: ${lastQuestion.take(30)}... A: ${lastResponse.take(30)}...", LogLevel.INFO)
                    
                    // Log success with processing info instead of toast
                    val processingType = if (wasForced) "Float Button" else "Auto Detection"
                    LogManager.addLog("MainActivity", "✅ $processingType processing completed!", LogLevel.SUCCESS)
                    
                    // TODO: Here you can add logic to switch to specific tab or show the result
                    // For example, switch to history tab to show the latest entry
                    LogManager.addLog("MainActivity", "💡 Future: Could switch to history tab or highlight latest entry", LogLevel.DEBUG)
                }
            }
            
        } catch (e: Exception) {
            LogManager.addLog("MainActivity", "❌ Error handling incoming intent: ${e.message}", LogLevel.ERROR)
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

@Composable
fun SafeMainScreen() {
    val context = LocalContext.current
    var appState by remember { mutableStateOf<AppState>(AppState.Loading) }
    
    // Cache dark transition mode check to avoid repeated casts
    val isDarkTransitionMode = remember {
        (context as? MainActivity)?.isDarkTransition ?: false
    }
    
    LaunchedEffect(Unit) {
        try {
            // Faster initialization - reduce delay
            kotlinx.coroutines.delay(50) // Reduced from 100ms to 50ms
            appState = AppState.Normal
        } catch (e: Exception) {
            Log.e("SafeMainScreen", "Error during initialization", e)
            appState = AppState.Error("Initialization failed: ${e.message}")
        }
    }
    
    // Use conditional rendering instead of Box overlay for better performance
    if (isDarkTransitionMode) {
        DarkTransitionOverlay()
    } else {
        when (appState) {
            is AppState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Starting AskGPT...")
                    }
                }
            }
            is AppState.Normal -> {
                MainScreen()
            }
            is AppState.Error -> {
                ErrorScreen((appState as AppState.Error).message)
            }
        }
    }
}

@Composable
fun DarkTransitionOverlay() {
    // Optimized dark overlay - minimal composable footprint
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Very minimal progress indicator - no complex animations
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = Color(0xFF444444), // Dark gray instead of bright color
            strokeWidth = 1.5.dp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    
    var isClipboardServiceRunning by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(false) }
    
    // Safe service initialization with error protection
    LaunchedEffect(Unit) {
        try {
            // Check overlay permission
            hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true // No overlay permission needed on older versions
            }
            
            // Start overlay service if permission is granted
            if (hasOverlayPermission) {
                try {
                    val overlayIntent = Intent(context, OverlayButtonService::class.java).apply {
                        action = OverlayButtonService.ACTION_START_OVERLAY
                    }
                    context.startService(overlayIntent)
                    LogManager.addLog("MainActivity", "🎯 Overlay button service started", LogLevel.SUCCESS)
                } catch (e: Exception) {
                    LogManager.addLog("MainActivity", "❌ Failed to start overlay service: ${e.message}", LogLevel.ERROR)
                }
            } else {
                LogManager.addLog("MainActivity", "⚠️ Overlay permission not granted", LogLevel.WARN)
            }
            
            // Safely check battery optimization status
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
                    powerManager.isIgnoringBatteryOptimizations(context.packageName)
                } else {
                    true // No battery optimization on older versions or if service unavailable
                }
                
                // Safe logging with error protection
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
                            "⚠️ Battery optimization enabled - service may be limited", 
                            LogLevel.WARN
                        )
                        // Request battery optimization exemption safely
                        try {
                            (context as? MainActivity)?.requestBatteryOptimizationExemption()
                        } catch (e: Exception) {
                            Log.e("MainScreen", "Failed to request battery exemption", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainScreen", "Error with logging battery status", e)
                }
            } catch (e: Exception) {
                Log.e("MainScreen", "Error checking battery optimization", e)
            }
            
            // Auto-start clipboard service with crash protection - Now handled by Application class
            // Service starts automatically when app launches for continuous operation
            if (!isClipboardServiceRunning) {
                LogManager.addLog(
                    "MainActivity", 
                    "📋 Clipboard service auto-started by Application class for continuous monitoring", 
                    LogLevel.INFO
                )
                isClipboardServiceRunning = true // Mark as running since Application started it
            }
            
            // Log service status safely
            try {
                LogManager.addLog(
                    "MainActivity", 
                    "Service status - Continuous Clipboard: $isClipboardServiceRunning", 
                    LogLevel.INFO
                )
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    LogManager.addLog("MainActivity", "Error logging service status: ${e.message}", LogLevel.ERROR)
                } catch (logError: Exception) {
                    logError.printStackTrace()
                }
            }
            
        } catch (e: Exception) {
            // Handle any permission checking errors
            e.printStackTrace()
            try {
                LogManager.addLog("MainActivity", "Error in service startup: ${e.message}", LogLevel.ERROR)
            } catch (logError: Exception) {
                logError.printStackTrace()
            }
        }
    }

    Scaffold(
        // No top bar - removed app title
    ) { innerPadding ->
        // Get background color based on theme
        val backgroundColor = if ((context as? MainActivity)?.isDarkBackground == true) {
            Color.Black
        } else {
            Color.White
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor) // Apply background color
                .padding(innerPadding) // Remove extra padding for full screen
        ) {
            // Main content
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp) // Remove spacing for full screen
            ) {
                // Check all system statuses
                val localContext = LocalContext.current
                var hasAccessibilityPermission by remember { mutableStateOf(false) }
                
                LaunchedEffect(Unit) {
                    hasAccessibilityPermission = isAccessibilityServiceEnabled(localContext)
                }
                
                // Check if all systems are active
                val allSystemsActive = hasOverlayPermission && isClipboardServiceRunning && hasAccessibilityPermission
                
                // Only show status panel if not all systems are active
                if (!allSystemsActive) {
                    // Overlay Permission Status
                    if (!hasOverlayPermission) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "🎯 Overlay Permission Required",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "The floating overlay button needs permission to appear on top of other apps.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                                data = android.net.Uri.parse("package:${localContext.packageName}")
                                            }
                                            localContext.startActivity(intent)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Grant Permission", color = MaterialTheme.colorScheme.onError)
                                }
                            }
                        }
                    }
                    
                    // Unified Service Status Panel (only when not all active)
                    UnifiedServiceStatusCard(
                        isClipboardServiceRunning = isClipboardServiceRunning
                    )
                }
                
                // ChatGPT Question/Answer History Section (always visible)
                ChatGPTHistoryCard()
            }
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
fun UnifiedServiceStatusCard(
    isClipboardServiceRunning: Boolean
) {
    val context = LocalContext.current
    var hasAccessibilityPermission by remember { mutableStateOf(false) }
    
    // Check all service statuses
    LaunchedEffect(Unit) {
        hasAccessibilityPermission = isAccessibilityServiceEnabled(context)
    }
    
    // Determine overall status and color (removed watchdog dependency)
    val allServicesRunning = isClipboardServiceRunning && hasAccessibilityPermission
    val criticalServicesRunning = isClipboardServiceRunning && hasAccessibilityPermission
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                allServicesRunning -> MaterialTheme.colorScheme.primaryContainer
                criticalServicesRunning -> MaterialTheme.colorScheme.tertiaryContainer  
                else -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with overall status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "� System Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        allServicesRunning -> MaterialTheme.colorScheme.onPrimaryContainer
                        criticalServicesRunning -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onErrorContainer
                    }
                )
                Text(
                    text = when {
                        allServicesRunning -> "✅ All Active"
                        criticalServicesRunning -> "⚠️ Partial"
                        else -> "❌ Issues"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        allServicesRunning -> MaterialTheme.colorScheme.onPrimaryContainer
                        criticalServicesRunning -> MaterialTheme.colorScheme.onTertiaryContainer  
                        else -> MaterialTheme.colorScheme.onErrorContainer
                    }
                )
            }
            
            // Divider
            Divider(
                color = when {
                    allServicesRunning -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                    criticalServicesRunning -> MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f)
                }
            )
            
            // Main App Service (Clipboard Monitoring)
            ServiceStatusRow(
                icon = "📋",
                name = "Main App Service",
                description = "Clipboard monitoring & processing",
                isRunning = isClipboardServiceRunning,
                isCritical = true,
                containerColor = when {
                    allServicesRunning -> MaterialTheme.colorScheme.primaryContainer
                    criticalServicesRunning -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.errorContainer
                }
            )
            
            // Accessibility Service
            ServiceStatusRow(
                icon = "🔐",
                name = "Accessibility Service", 
                description = "Enhanced text monitoring & permissions",
                isRunning = hasAccessibilityPermission,
                isCritical = true,
                containerColor = when {
                    allServicesRunning -> MaterialTheme.colorScheme.primaryContainer
                    criticalServicesRunning -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.errorContainer
                },
                actionButton = if (!hasAccessibilityPermission) {
                    {
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
                            ),
                            modifier = Modifier.size(width = 80.dp, height = 32.dp)
                        ) {
                            Text("Enable", fontSize = 12.sp, color = MaterialTheme.colorScheme.onError)
                        }
                    }
                } else null
            )
            
            // Status summary
            if (!allServicesRunning) {
                Text(
                    text = when {
                        !isClipboardServiceRunning -> "💡 Main clipboard service is starting..."
                        !hasAccessibilityPermission -> "💡 Enable accessibility in Settings > Accessibility > AskGPT"
                        else -> "💡 Services are initializing..."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        criticalServicesRunning -> MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        else -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    },
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                criticalServicesRunning -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                                else -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                            },
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun ServiceStatusRow(
    icon: String,
    name: String,
    description: String,
    isRunning: Boolean,
    isCritical: Boolean,
    containerColor: androidx.compose.ui.graphics.Color,
    actionButton: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = icon,
                fontSize = 20.sp
            )
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (containerColor == MaterialTheme.colorScheme.primaryContainer) 
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else if (containerColor == MaterialTheme.colorScheme.tertiaryContainer)
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (containerColor == MaterialTheme.colorScheme.primaryContainer) 
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else if (containerColor == MaterialTheme.colorScheme.tertiaryContainer)
                        MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                )
            }
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (isRunning) "✅ Active" else if (isCritical) "❌ Inactive" else "⏳ Optional",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (isRunning) {
                    if (containerColor == MaterialTheme.colorScheme.primaryContainer) 
                        MaterialTheme.colorScheme.primary
                    else if (containerColor == MaterialTheme.colorScheme.tertiaryContainer)
                        MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.primary
                } else {
                    if (isCritical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                }
            )
            
            actionButton?.invoke()
        }
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    return try {
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

@Composable
fun ChatGPTHistoryCard() {
    val historyEntries by ChatGPTHistoryManager.chatGPTHistory.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val context = LocalContext.current
    val isDarkTheme = (context as? MainActivity)?.isDarkBackground ?: false
    
    // Display only the data records without header or title
    if (historyEntries.isNotEmpty()) {
        // History entries in a full screen scrollable list
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(), // Use full screen height
            verticalArrangement = Arrangement.spacedBy(2.dp), // Reduced spacing for more data
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp) // Minimal padding
        ) {
            items(historyEntries) { entry ->
                ChatGPTHistoryRow(
                    entry = entry,
                    dateFormat = dateFormat
                )
            }
        }
    } else {
        // Empty state - minimal with adaptive colors
        val textColor = if (isDarkTheme) Color.White else Color.Black
        val borderColor = if (isDarkTheme) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f)
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            border = BorderStroke(
                width = 1.dp,
                color = borderColor
            )
        ) {
            Text(
                text = "No data yet",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.7f),
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontStyle = FontStyle.Italic
            )
        }
    }
}

@Composable
fun ChatGPTHistoryRow(
    entry: ChatGPTEntry,
    dateFormat: SimpleDateFormat
) {
    val context = LocalContext.current
    val isDarkTheme = (context as? MainActivity)?.isDarkBackground ?: false
    
    // Adaptive colors based on background
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val borderColor = if (isDarkTheme) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent // Transparent background
        ),
        border = BorderStroke(
            width = 1.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp), // Reduced padding for more compact display
            horizontalArrangement = Arrangement.spacedBy(6.dp), // Reduced spacing
            verticalAlignment = Alignment.Top
        ) {
            // Timestamp
            Text(
                text = dateFormat.format(Date(entry.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(0.2f),
                color = textColor.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium
            )
            
            // Question preview
            Text(
                text = entry.question.take(100) + if (entry.question.length > 100) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(0.4f),
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            // Answer preview
            Text(
                text = entry.answer.take(80) + if (entry.answer.length > 80) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(0.3f),
                color = if (isDarkTheme) Color.Cyan else Color.Blue,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            
            // Response time
            Text(
                text = "${entry.responseTime}ms",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(0.1f),
                color = textColor.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun ChatGPTConfigCard() {
    val isConfigured = ChatGPTConfig.isApiKeyConfigured()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConfigured) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isConfigured) "🤖" else "⚠️",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "ChatGPT Integration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isConfigured) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else 
                        MaterialTheme.colorScheme.onErrorContainer
                )
            }
            
            if (isConfigured) {
                Text(
                    text = "✅ API key configured - Multiple choice questions will be answered by ChatGPT",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Icons A, B, C, D now represent ChatGPT answers for multiple choice questions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    fontStyle = FontStyle.Italic
                )
            } else {
                Text(
                    text = "❌ No API key configured - Using fallback word count analysis",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "To enable ChatGPT:\n1. Edit ChatGPTConfig.kt\n2. Add your OpenAI API key\n3. Rebuild the app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}
