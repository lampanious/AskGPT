package com.example.askgpt

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.askgpt.data.SelectedTextItem
import com.example.askgpt.data.SelectedTextManager
import com.example.askgpt.services.TextMonitoringService
import com.example.askgpt.ui.theme.AskGPTTheme
import com.example.askgpt.utils.PermissionHelper
import com.example.askgpt.utils.LogManager
import com.example.askgpt.utils.LogLevel
import com.example.askgpt.utils.LogEntry
import java.text.SimpleDateFormat
import java.util.*

sealed class AppState {
    object Loading : AppState()
    object Normal : AppState()
    data class Error(val message: String) : AppState()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            enableEdgeToEdge()
            
            // Log app startup
            LogManager.addLog("MainActivity", "App started successfully", LogLevel.INFO)
            
            // Handle intent from notification safely
            handleIncomingIntent(intent)
            
            setContent {
                AskGPTTheme {
                    MainScreen()
                }
            }
        } catch (e: Exception) {
            // Log any startup errors
            LogManager.addLog("MainActivity", "Error during app startup: ${e.message}", LogLevel.ERROR)
            // Set error state without try-catch around setContent
            setContent {
                AskGPTTheme {
                    ErrorScreen("Failed to initialize app: ${e.message}")
                }
            }
            return
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
    val debugLogs by LogManager.logs.collectAsStateWithLifecycle()
    
    var hasAccessibilityPermission by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(false) }
    
    // Permission launchers
    val accessibilitySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasAccessibilityPermission = PermissionHelper.isAccessibilityServiceEnabled(context)
    }
    
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
    }
    
    // Check permissions on composition
    LaunchedEffect(Unit) {
        try {
            hasAccessibilityPermission = PermissionHelper.isAccessibilityServiceEnabled(context)
            hasOverlayPermission = Settings.canDrawOverlays(context)
            
            LogManager.addLog(
                "MainActivity", 
                "Permissions check - Accessibility: $hasAccessibilityPermission, Overlay: $hasOverlayPermission", 
                LogLevel.INFO
            )
            
        } catch (e: Exception) {
            // Handle any permission checking errors
            LogManager.addLog("MainActivity", "Error checking permissions: ${e.message}", LogLevel.ERROR)
            e.printStackTrace()
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
            // Permissions Section
            PermissionsCard(
                hasAccessibilityPermission = hasAccessibilityPermission,
                hasOverlayPermission = hasOverlayPermission,
                onRequestAccessibilityPermission = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    accessibilitySettingsLauncher.launch(intent)
                },
                onRequestOverlayPermission = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    overlayPermissionLauncher.launch(intent)
                }
            )
            
            // Latest Text Section
            LatestTextCard(latestText)
            
            // Debug Logs Section
            DebugLogsSection(debugLogs)
            
            // Text History Section
            TextHistorySection(selectedTexts)
        }
    }
}

@Composable
fun PermissionsCard(
    hasAccessibilityPermission: Boolean,
    hasOverlayPermission: Boolean,
    onRequestAccessibilityPermission: () -> Unit,
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
                text = "Required Permissions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            // Accessibility Permission
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Accessibility Service")
                if (hasAccessibilityPermission) {
                    Text("✓", color = MaterialTheme.colorScheme.primary)
                } else {
                    Button(onClick = onRequestAccessibilityPermission) {
                        Text("Enable")
                    }
                }
            }
            
            // Overlay Permission
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Screen Overlay")
                if (hasOverlayPermission) {
                    Text("✓", color = MaterialTheme.colorScheme.primary)
                } else {
                    Button(onClick = onRequestOverlayPermission) {
                        Text("Enable")
                    }
                }
            }
            
            if (!hasAccessibilityPermission) {
                Text(
                    text = "Enable 'AskGPT' in Accessibility settings to detect selected text",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (!hasOverlayPermission) {
                Text(
                    text = "Allow overlay permission to show selected text popup",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun LatestTextCard(latestText: String?) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Latest Selected Text from Chrome",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (latestText != null) {
                Text(
                    text = latestText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            } else {
                Text(
                    text = "No text selected yet. Select any text in Chrome browser to see it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TextHistorySection(selectedTexts: List<SelectedTextItem>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Text History (${selectedTexts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                if (selectedTexts.isNotEmpty()) {
                    TextButton(onClick = { SelectedTextManager.clearAllTexts() }) {
                        Text("Clear All")
                    }
                }
            }
            
            if (selectedTexts.isEmpty()) {
                Text(
                    text = "No text history available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedTexts) { item ->
                        TextHistoryItem(
                            item = item,
                            onRemove = { SelectedTextManager.removeText(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TextHistoryItem(
    item: SelectedTextItem,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                        .format(Date(item.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            TextButton(onClick = onRemove) {
                Text("Remove")
            }
        }
    }
}

@Composable
fun DebugLogsSection(logs: List<LogEntry>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Debug Logs (${logs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                if (logs.isNotEmpty()) {
                    TextButton(onClick = { LogManager.clearLogs() }) {
                        Text("Clear")
                    }
                }
            }
            
            if (logs.isEmpty()) {
                Text(
                    text = "No debug logs yet. Logs will appear here when the accessibility service detects events.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs.reversed()) { log ->
                        DebugLogItem(log)
                    }
                }
            }
        }
    }
}

@Composable
fun DebugLogItem(log: LogEntry) {
    val logColor = when (log.level) {
        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
        LogLevel.SUCCESS -> MaterialTheme.colorScheme.tertiary
        LogLevel.WARN -> MaterialTheme.colorScheme.secondary
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = log.tag,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = logColor
                )
                Text(
                    text = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        .format(Date(log.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodySmall,
                color = logColor
            )
        }
    }
}