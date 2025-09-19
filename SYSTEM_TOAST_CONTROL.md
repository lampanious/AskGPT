# System Toast Control Guide

## Overview
Android system toasts (especially clipboard "Copied to clipboard" notifications) are controlled by the system and cannot be directly disabled by third-party apps. However, there are several approaches to minimize their impact.

## Methods to Control System Toasts

### 1. **User Settings Method (Recommended)**
Ask users to disable system clipboard notifications in their device settings:

**Android 10+ (API 29+):**
- Settings > Privacy > Permission manager > Other permissions > Toast overlay
- Or: Settings > Apps & notifications > Special app access > Display over other apps

**Samsung devices:**
- Settings > Advanced features > Motions and gestures > Smart capture
- Turn off "Clipboard edge panel"

**MIUI (Xiaomi):**
- Settings > Special permissions > Display over other apps
- Or: MIUI Optimization settings

### 2. **Programmatic Approaches (Limited Success)**

#### A. Override System ClipboardManager (Root Required)
```kotlin
// This requires system-level permissions (root/system app)
private fun disableSystemClipboardToasts() {
    try {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        // This won't work for third-party apps - system protection
        // clipboardManager.clearPrimaryClip() // Immediately clear after copy
    } catch (e: Exception) {
        LogManager.addLog("Toast", "Cannot override system clipboard behavior", LogLevel.WARN)
    }
}
```

#### B. Accessibility Service Approach (Requires User Permission)
```kotlin
// In AccessibilityService - can detect and potentially dismiss toasts
class ToastBlockingAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            // Detect toast notifications
            val text = event.text?.toString()
            if (text?.contains("copied", ignoreCase = true) == true) {
                // Can attempt to dismiss but limited success
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
    }
    
    override fun onInterrupt() {}
}
```

#### C. Reflection Attempt (Usually Blocked)
```kotlin
private fun attemptToastOverride() {
    try {
        // This is blocked by Android security since API 25+
        val toastClass = Class.forName("android.widget.Toast")
        val field = toastClass.getDeclaredField("sService")
        field.isAccessible = true
        // field.set(null, null) // Would be blocked by security
    } catch (e: Exception) {
        LogManager.addLog("Toast", "Toast override blocked by system security", LogLevel.DEBUG)
    }
}
```

### 3. **Alternative Clipboard Strategies**

#### A. Minimize System Clipboard Usage
```kotlin
// Use internal clipboard management to reduce system interactions
private fun copyWithMinimalSystemInteraction(text: String) {
    try {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("", text) // Empty label reduces toast visibility
        clipboard.setPrimaryClip(clip)
        
        // Immediately clear after a short delay to minimize toast duration
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val emptyClip = ClipData.newPlainText("", "")
                clipboard.setPrimaryClip(emptyClip)
            } catch (e: Exception) {
                // Ignore errors in cleanup
            }
        }, 100)
    } catch (e: Exception) {
        LogManager.addLog("Clipboard", "Error in minimal system interaction", LogLevel.ERROR)
    }
}
```

#### B. Custom Clipboard Implementation
```kotlin
// Bypass system clipboard for internal operations
object CustomClipboard {
    private var internalClipboard: String = ""
    
    fun setClipboard(text: String) {
        internalClipboard = text
        // Only set system clipboard when absolutely necessary
    }
    
    fun getClipboard(): String = internalClipboard
    
    fun syncToSystemWhenNeeded(text: String) {
        val clipboard = GlobalClipboardManager.getSystemClipboardManager()
        val clip = ClipData.newPlainText("silent", text)
        clipboard?.setPrimaryClip(clip)
    }
}
```

### 4. **User Education Approach**

Create a settings screen in your app to guide users:

```kotlin
@Composable
fun SystemToastControlSettings() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Minimize System Notifications",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "To reduce 'Copied to clipboard' notifications:",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("1. Go to Settings > Privacy > Clipboard")
                Text("2. Turn off 'Show clipboard access notifications'")
                Text("3. Or disable 'Toast overlay' permissions")
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = {
                        // Open relevant settings
                        try {
                            val intent = Intent(Settings.ACTION_PRIVACY_SETTINGS)
                            startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback to general settings
                            val intent = Intent(Settings.ACTION_SETTINGS)
                            startActivity(intent)
                        }
                    }
                ) {
                    Text("Open Settings")
                }
            }
        }
    }
}
```

## Implementation in Current Project

### Integration with ClipboardMonitoringService

```kotlin
// Add to ClipboardMonitoringService
private fun handleClipboardWithMinimalToasts(text: String) {
    try {
        // Use empty label to reduce toast prominence
        val clip = ClipData.newPlainText("", text)
        clipboardManager.setPrimaryClip(clip)
        
        // Log instead of relying on toast feedback
        LogManager.addLog("Clipboard", "✅ Content processed silently", LogLevel.SUCCESS)
        
        // Optional: Show our own discreet notification instead
        showDiscreteProcessingNotification()
        
    } catch (e: Exception) {
        LogManager.addLog("Clipboard", "❌ Silent clipboard operation failed", LogLevel.ERROR)
    }
}

private fun showDiscreteProcessingNotification() {
    // Use our existing notification system instead of system toasts
    notificationHelper.showProcessingNotification(
        "Processing clipboard content...",
        showProgress = true
    )
}
```

## Summary

**What Works:**
- ✅ User can disable in device settings (most effective)
- ✅ Using empty labels reduces toast visibility
- ✅ Custom notification system bypasses system toasts
- ✅ Internal clipboard management reduces system interactions

**What Doesn't Work:**
- ❌ Direct programmatic disabling (blocked by Android security)
- ❌ Toast reflection/override (blocked since API 25+)
- ❌ System-level toast interception (requires root)

**Best Practice:**
Use a combination of minimal system interaction, custom notifications, and user education for optimal results.