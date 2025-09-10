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
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
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
import com.example.askgpt.data.ClipboardHistoryManager
import com.example.askgpt.data.ClipboardHistoryEntry
import com.example.askgpt.services.ClipboardMonitoringService
import com.example.askgpt.ui.theme.AskGPTTheme
import com.example.askgpt.utils.LogManager
import com.example.askgpt.utils.LogLevel
import java.io.File
import java.io.FileWriter
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
                    // Use a safe composable that handles errors gracefully
                    SafeMainScreen()
                }
            }
            
            // Start clipboard service after UI is initialized to prevent black screen
            try {
                (application as? AskGPTApplication)?.startClipboardServiceSafely()
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to start clipboard service after UI init", e)
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
        
        // Export clipboard history before app is destroyed
        try {
            val exportPath = ClipboardHistoryManager.exportToFile(this)
            if (exportPath != null) {
                LogManager.addLog("MainActivity", "📄 Clipboard history exported to: $exportPath", LogLevel.SUCCESS)
            } else {
                LogManager.addLog("MainActivity", "❌ Failed to export clipboard history", LogLevel.ERROR)
            }
        } catch (e: Exception) {
            LogManager.addLog("MainActivity", "❌ Error exporting clipboard history: ${e.message}", LogLevel.ERROR)
        }
        
        // Stop continuous clipboard service when app is destroyed
        try {
            val intent = Intent(this, ClipboardMonitoringService::class.java)
            stopService(intent)
            LogManager.addLog("MainActivity", "🛑 App destroyed - stopped continuous clipboard service", LogLevel.INFO)
        } catch (e: Exception) {
            LogManager.addLog("MainActivity", "Error stopping continuous service on app destroy: ${e.message}", LogLevel.ERROR)
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

@Composable
fun SafeMainScreen() {
    var appState by remember { mutableStateOf<AppState>(AppState.Loading) }
    
    LaunchedEffect(Unit) {
        try {
            // Initialize app components safely
            kotlinx.coroutines.delay(100) // Brief delay to ensure initialization
            appState = AppState.Normal
        } catch (e: Exception) {
            Log.e("SafeMainScreen", "Error during initialization", e)
            appState = AppState.Error("Initialization failed: ${e.message}")
        }
    }
    
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    
    // Safe state collection with error protection
    val selectedTexts by SelectedTextManager.selectedTexts.collectAsStateWithLifecycle()
    
    val latestText = remember(selectedTexts) { 
        try {
            if (selectedTexts.isNotEmpty()) selectedTexts.first().text else "Clipboard monitoring starting..."
        } catch (e: Exception) {
            "Clipboard monitoring initializing..."
        }
    }
    
    var isClipboardServiceRunning by remember { mutableStateOf(false) }
    
    // Safe service initialization with error protection
    LaunchedEffect(Unit) {
        try {
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
        topBar = {
            TopAppBar(
                title = { Text("AskGPT - Clipboard Monitor") }
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
            // Unified Service Status Panel
            UnifiedServiceStatusCard(
                isClipboardServiceRunning = isClipboardServiceRunning
            )
            
            // Comprehensive Clipboard History Section
            ClipboardHistoryCard()
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

// Helper function to check if a service is running
private fun isServiceRunning(context: Context, serviceClassName: String): Boolean {
    return try {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val services = activityManager.getRunningServices(Integer.MAX_VALUE)
        services.any { it.service.className == serviceClassName }
    } catch (e: Exception) {
        LogManager.addLog("MainActivity", "❌ Error checking service status for $serviceClassName: ${e.message}", LogLevel.ERROR)
        false
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

@Composable
fun ClipboardHistoryCard() {
    val context = LocalContext.current
    val historyEntries = ClipboardHistoryManager.getHistory()
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with export action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📋 Clipboard History (${historyEntries.size} entries)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Export button
                    Button(
                        onClick = {
                            val exportPath = ClipboardHistoryManager.exportToFile(context)
                            if (exportPath != null) {
                                Toast.makeText(context, "History exported to: ${File(exportPath).name}", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.size(width = 80.dp, height = 32.dp)
                    ) {
                        Text("Export", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondary)
                    }
                }
            }
            
            // Table header
            if (historyEntries.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Time",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(0.25f),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Content",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(0.75f),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                // History entries in a scrollable list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp), // Limit height with scroll
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(historyEntries) { entry ->
                        ClipboardHistoryRow(
                            entry = entry,
                            dateFormat = dateFormat
                        )
                    }
                }
            } else {
                // Empty state
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        text = "📝 No clipboard history yet. Start copying text to see entries here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
            
            // Information footer
            Text(
                text = "💡 Unique clipboard entries are recorded with timestamps. Duplicate consecutive content is not saved. History is automatically exported when app closes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                fontStyle = FontStyle.Italic
            )
        }
    }
}

@Composable
fun ClipboardHistoryRow(
    entry: ClipboardHistoryEntry,
    dateFormat: SimpleDateFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Timestamp
            Text(
                text = dateFormat.format(Date(entry.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(0.25f),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium
            )
            
            // Content preview
            Text(
                text = entry.content.take(200) + if (entry.content.length > 200) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(0.75f),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
