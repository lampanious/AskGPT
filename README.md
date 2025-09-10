# AskGPT - Advanced Clipboard Monitoring System

**A sophisticated Android application that provides persistent background clipboard monitoring with intelligent word count analysis and immediate visual feedback.**

## 🎯 Overview

AskGPT is a production-ready Android clipboard monitoring application that runs continuously in the background, providing instant visual feedback through dynamic notification icons when clipboard content changes. The app categorizes text based on word count and maintains persistent operation across all Android applications.

## ✨ Key Features

### 🔄 **Persistent Background Operation**
- **True Background App**: Runs 24/7 with industrial-grade persistence
- **Service Watchdog**: Auto-restart mechanism prevents service interruption  
- **Boot Integration**: Automatically starts after device reboot
- **Battery Optimized**: Smart power management with exemption requests
- **HyperOS/MIUI Compatible**: Enhanced recognition for MIUI systems

### 📊 **Intelligent Word Count Analysis**
- **Dynamic Categorization**: Text classified into 4 categories (A, B, C, D)
- **Real-time Processing**: Instant analysis with regex-based word counting
- **Edge Case Handling**: Robust handling of empty, null, and special content
- **Smart Intervals**: Business-ready detection intervals (750ms base, adaptive 200ms-2s)
- **Content Change Detection**: Similarity checking prevents minor fluctuations
- **Battery Optimized**: Adaptive intervals save power during low activity

### 🔔 **Advanced Notification System**
- **Dynamic Icons**: Visual indicators change based on content analysis
- **Rich Content**: Descriptive notifications with word count details
- **Immediate Updates**: Instant response to clipboard changes
- **Persistent Display**: Maintains latest state until new clipboard signal

### 🎨 **Visual Feedback System**
- **A (⚠️)**: Exactly 3 words - Alert icon
- **B (ℹ️)**: 4-6 words - Info icon  
- **C (✖️)**: 7-9 words - Cancel icon
- **D (🗑️)**: Empty/Other count - Delete icon

### 📱 **External App Detection**
- **Cross-app Monitoring**: Detects clipboard changes from WhatsApp, Chrome, SMS, Email, etc.
- **Source App Identification**: Smart detection of source application
- **Instant Response**: 0-50ms detection time for external app copies
- **Comprehensive Logging**: Detailed logs for clipboard activities

## 🏗️ Architecture Overview

### **Core Components**

```
📱 AskGPT Application
├── 🎯 MainActivity (UI & User Interaction)
├── 🔄 ClipboardMonitoringService (Core Clipboard Logic)
├── 🐕 ServiceWatchdog (Persistence Guardian) 
├── ♿ AskGPTAccessibilityService (Enhanced Detection)
├── 🔔 NotificationHelper (Visual Feedback)
├── 📡 BootReceiver (Auto-start Handler)
└── 📊 LogManager (Debug & Analytics)
```

### **Service Architecture**

```
┌─────────────────────────────────────────┐
│           Application Launch            │
└─────────────────┬───────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────┐
│         MainActivity Startup           │
│  • UI Initialization                   │
│  • Permission Requests                 │
│  • Service Orchestration              │
└─────────────────┬───────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────┐
│    ClipboardMonitoringService          │
│  • Adaptive Interval Monitoring        │
│  • Content Change Detection            │
│  • Word Count Analysis                 │
│  • Dynamic Notification Management     │
└─────────────────┬───────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────┐
│         ServiceWatchdog                 │
│  • Health Monitoring (30s intervals)   │
│  • Auto-restart on Service Death       │
│  • System-level Persistence           │
└─────────────────────────────────────────┘
```

## 🚨 Bug Fixes & Solutions

### **Black Screen Issue Resolution**

**Problem**: App showing black screen on startup with heap size error:
```
Fail to check app heapsize due to java.lang.NoSuchMethodException
```

**Root Cause**: Aggressive service startup in `Application.onCreate()` causing resource conflicts.

**Solution**: Implemented delayed service startup pattern:
- **Minimal Application.onCreate()**: Only essential initialization
- **UI-First Approach**: Service starts after UI is fully rendered
- **Progressive Loading**: SafeMainScreen with Loading/Normal/Error states
- **Error Handling**: Comprehensive try-catch protection

**Code Changes**:
```kotlin
// Application.onCreate() - Minimal initialization
override fun onCreate() {
    super.onCreate()
    try {
        LogManager.addLog(TAG, "🚀 AskGPT Application started safely", LogLevel.INFO)
        // Service startup moved to MainActivity
    } catch (e: Exception) {
        Log.e(TAG, "Critical error in Application.onCreate", e)
    }
}

// MainActivity - Service startup after UI
setContent {
    AskGPTTheme {
        SafeMainScreen()
    }
}
// Start service after UI is initialized
(application as? AskGPTApplication)?.startClipboardServiceSafely()
```

### **Continuous Clipboard Monitoring**

**Enhancement**: Resource-optimized continuous monitoring with adaptive intervals:
- **Event-driven Detection**: ClipboardManager.OnPrimaryClipChangedListener (0% CPU when idle)
- **Adaptive Polling**: 100ms active → 300ms normal → 1000ms idle
- **Battery Optimization**: PARTIAL_WAKE_LOCK with 60-minute auto-release
- **Memory Management**: 5KB text limits, automatic cleanup
- **Cross-app Reliability**: 50ms delay + fallback mechanism

### **External App Detection**

**Feature**: Real-time detection and logging of clipboard changes from external apps:
- **Instant Detection**: Immediate response to external app clipboard changes
- **Source Identification**: Smart app detection (WhatsApp, Chrome, Email, etc.)
- **Content Analysis**: Word count, character count, content type detection
- **Comprehensive Logging**: Detailed logs with content previews

## 📂 Clean Source Code Structure

```
AskGPT/ (Clean & Focused)
├── 📁 Core Services (3 files)
│   ├── 🔄 ClipboardMonitoringService.kt    # Main clipboard logic + adaptive intervals
│   ├── 🐕 ServiceWatchdog.kt               # Persistence guardian (30s checks)
│   └── ♿ AskGPTAccessibilityService.kt     # Enhanced HyperOS/MIUI detection
│
├── 📁 Data Management (2 files)
│   ├── 📋 ClipboardHistoryManager.kt       # History tracking + export
│   └── 📝 SelectedTextManager.kt           # Selection memory
│
├── 📁 Utilities (3 files)
│   ├── 🔔 NotificationHelper.kt            # Dynamic notification icons
│   ├── 📊 LogManager.kt                    # Debug + analytics
│   └── 🛡️ PermissionHelper.kt              # Runtime permissions
│
├── 📁 UI & Integration (3 files)
│   ├── 🎯 MainActivity.kt                  # Modern Compose UI
│   ├── 📡 BootReceiver.kt                  # Auto-start handler
│   └── 📱 AskGPTApplication.kt             # App lifecycle
│
└── 📁 Theme (3 files - Compose UI)
    ├── 🎨 Color.kt, Theme.kt, Type.kt      # Material Design 3
```

## 🚀 Getting Started

### **Prerequisites**
- Android Studio Electric Eel or later
- Android SDK 24+ (Android 7.0)
- Kotlin 1.8+

### **Installation**

1. **Clone the repository**
```bash
git clone https://github.com/lampanious/AskGPT.git
cd AskGPT
```

2. **Build the project**
```bash
./gradlew assembleDebug
```

3. **Install on device**
```bash
./gradlew installDebug
```

### **First Run Setup**

1. **Grant Permissions**: Allow notification access and clipboard permissions
2. **Battery Optimization**: Disable battery optimization for persistent operation
3. **Accessibility Service**: Enable accessibility service for enhanced detection (optional)
4. **Verify Operation**: Check notification bar for clipboard monitoring indicator

## 🔧 Configuration

### **Adaptive Intervals**
```kotlin
// Resource-optimized intervals
private const val ULTRA_FAST_CHECK_INTERVAL = 100L  // Active monitoring
private const val CLIPBOARD_CHECK_INTERVAL = 300L   // Normal operation
private const val SLOW_CHECK_INTERVAL = 1000L       // Power saving
```

### **Word Count Categories**
```kotlin
// Categorization rules
when {
    wordCount == 3 -> "A"        // Exactly 3 words
    wordCount in 4..6 -> "B"     // 4-6 words
    wordCount in 7..9 -> "C"     // 7-9 words
    else -> "D"                  // All other counts
}
```

### **Resource Management**
```kotlin
// Memory and battery optimization
private const val MAX_TEXT_LENGTH = 5000             // Text length limit
private const val WAKE_LOCK_TIMEOUT = 60 * 60 * 1000L // 60 minutes
private const val HEALTH_CHECK_INTERVAL = 30000L     // 30 seconds
```

## 🧪 Testing

### **Manual Testing**
1. **Copy text in various apps** (WhatsApp, Chrome, SMS)
2. **Check notification updates** with different word counts
3. **Verify persistence** after device reboot
4. **Monitor battery usage** over extended periods

### **Automated Testing**
```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

## 📊 Performance Metrics

### **Resource Usage**
- **CPU**: 0% when idle, <1% during active monitoring
- **Memory**: ~10MB baseline, scales with clipboard history
- **Battery**: <1% per day with optimized intervals
- **Storage**: Minimal (clipboard history + logs)

### **Response Times**
- **Event Detection**: 0-50ms (event-driven)
- **Notification Update**: 50-100ms
- **Cross-app Detection**: 50-150ms (with fallback)
- **Service Recovery**: 1-3 seconds

## 🛠️ Troubleshooting

### **Common Issues**

**Black Screen on Startup**:
- Ensure device has sufficient memory
- Clear app cache and restart
- Check for conflicting accessibility services

**Service Not Starting**:
- Verify notification permissions granted
- Disable battery optimization for the app
- Check system-level clipboard access permissions

**Missing Notifications**:
- Ensure notification channel is enabled
- Check Do Not Disturb settings
- Verify foreground service permissions

**High Battery Usage**:
- Check adaptive interval configuration
- Verify wake lock auto-release functionality
- Monitor background app restrictions

### **Debug Logging**

Enable detailed logging for troubleshooting:
```kotlin
// Check logs in Android Studio Logcat
adb logcat | grep "ClipboardService"

// Or use in-app LogManager
LogManager.addLog(TAG, "Debug message", LogLevel.DEBUG)
```

## 🔐 Security & Privacy

### **Data Handling**
- **Local Storage Only**: All clipboard data stays on device
- **No Network Access**: No data transmission to external servers
- **Temporary Processing**: Content processed and discarded immediately
- **User Control**: Complete control over data retention

### **Permissions**
- **Clipboard Access**: Read clipboard content for analysis
- **Notification**: Display word count feedback
- **Boot Receiver**: Auto-start after device restart
- **Accessibility** (optional): Enhanced detection capabilities

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📞 Support

For support and questions:
- **Issues**: [GitHub Issues](https://github.com/lampanious/AskGPT/issues)
- **Discussions**: [GitHub Discussions](https://github.com/lampanious/AskGPT/discussions)

## 🙏 Acknowledgments

- Android Clipboard API documentation
- Material Design 3 guidelines
- Jetpack Compose community resources
- MIUI/HyperOS compatibility research
├── 🔔 NotificationHelper (Visual Feedback)
├── 📡 BootReceiver (Auto-start Handler)
└── 📊 LogManager (Debug & Analytics)
```

### **Service Architecture**

```
┌─────────────────────────────────────────┐
│           Application Launch            │
└─────────────────┬───────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────┐
│         MainActivity Startup           │
│  • UI Initialization                   │
│  • Permission Requests                 │
│  • Service Orchestration              │
└─────────────────┬───────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────┐
│    ClipboardMonitoringService          │
│  • Adaptive Interval Monitoring        │
│  • Content Change Detection            │
│  • Word Count Analysis                 │
│  • Dynamic Notification Management     │
└─────────────────┬───────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────┐
│         ServiceWatchdog                 │
│  • Health Monitoring (30s intervals)   │
│  • Auto-restart on Service Death       │
│  • System-level Persistence           │
└─────────────────────────────────────────┘
```

## 📂 Clean Source Code Structure Guide

> **📋 Complete Structure Guide**: See [STRUCTURE.md](STRUCTURE.md) for detailed architecture overview

### **🎯 Streamlined Project Architecture**

```
AskGPT/ (Clean & Focused)
├── 📁 Core Services (3 files)
│   ├── � ClipboardMonitoringService.kt    # Main clipboard logic + adaptive intervals
│   ├── � ServiceWatchdog.kt               # Persistence guardian (30s checks)
│   └── ♿ AskGPTAccessibilityService.kt     # Enhanced HyperOS/MIUI detection
├── 📁 User Interface (2 files)  
│   ├── 🎯 MainActivity.kt                  # App entry + service management
│   └── � NotificationHelper.kt            # Dynamic A/B/C/D icons
├── 📁 Infrastructure (4 files)
│   ├── � BootReceiver.kt                  # Auto-start on boot
│   ├── � LogManager.kt                    # Debug & analytics
│   ├── � PermissionHelper.kt              # Permission utilities  
│   └── � SelectedTextManager.kt           # Clipboard history
├── 📄 askgpt-build.sh/.bat                # Unified build scripts
└── 📄 README.md + STRUCTURE.md             # Complete documentation
```

**Total: 12 core files** (removed 11 unused/legacy files for clarity)

### **How to Read This Codebase**

#### **1. Start Here: Entry Points**
```kotlin
📄 AskGPTApplication.kt     // App initialization & lifecycle
📄 MainActivity.kt          // User interface & permissions
```

#### **2. Core Functionality**
```kotlin
📄 ClipboardMonitoringService.kt  // Main business logic
  ├── onCreate()                  // Service initialization
  ├── onStartCommand()           // Service startup handler  
  ├── startClipboardMonitoring() // Adaptive monitoring setup
  ├── checkClipboardForChanges() // Enhanced content analysis
  ├── calculateDisplayCharacter() // Word count logic
  ├── adjustCheckInterval()      // Smart interval adjustment
  └── updateForegroundNotificationImmediately() // Dynamic UI updates
```

#### **3. Persistence & Reliability**
```kotlin
📄 ServiceWatchdog.kt           // Service persistence guardian
  ├── scheduleNextCheck()       // Health monitoring
  ├── checkAndRestartService()  // Auto-restart logic
  ├── isServiceRunning()        // Service status check
  └── onDestroy()               // Self-restart mechanism

📄 BootReceiver.kt              // Auto-start on boot
  └── onReceive()               // Boot event handler
```

#### **4. User Interface & Feedback**
```kotlin
📄 NotificationHelper.kt        // Dynamic visual feedback
  ├── createNotificationChannels() // Channel setup
  ├── createWordCountNotification() // Adaptive notifications
  ├── getIconAndDescription()   // Smart icon mapping
  └── createPersistentNotification() // Fallback UI
```

#### **5. Enhanced Detection (Optional)**
```kotlin
📄 AskGPTAccessibilityService.kt // HyperOS/MIUI optimization
  ├── onServiceConnected()      // Enhanced clipboard detection
  ├── onAccessibilityEvent()    // Text event monitoring  
  └── onInterrupt()             // Graceful handling
```

#### **6. Supporting Infrastructure**
```kotlin
📄 SelectedTextManager.kt        // Clipboard history management
📄 LogManager.kt                 // Debug & analytics system
📄 PermissionHelper.kt           // Permission utilities
```

#### **7. Configuration**
```xml
📄 AndroidManifest.xml          // Permissions & services
📄 accessibility_service_config.xml // Accessibility setup
```

### **Reading Order for New Developers**

1. **📖 Start**: `README.md` (this file) - Understand the purpose and architecture
2. **🎯 Entry**: `MainActivity.kt` - See user interaction flow and service startup
3. **🔄 Core**: `ClipboardMonitoringService.kt` - Understand main logic and adaptive intervals
4. **📊 Algorithm**: `checkClipboardForChanges()` method - Content detection and word count rules
5. **🔔 UI**: `NotificationHelper.kt` - Visual feedback and dynamic icon system
6. **🐕 Persistence**: `ServiceWatchdog.kt` - Background reliability and auto-restart
7. **⚙️ Config**: `AndroidManifest.xml` - System integration and permissions

### **Key Methods to Understand**

#### **Enhanced Clipboard Detection**
```kotlin
// In ClipboardMonitoringService.kt
suspend fun checkClipboardForChanges(): Boolean {
    // Adaptive content change detection with similarity checking
}

fun adjustCheckInterval(hadChange: Boolean) {
    // Smart interval adjustment: 200ms-2000ms based on activity
}
```

#### **Word Count Logic**
```kotlin
// In ClipboardMonitoringService.kt
fun calculateDisplayCharacter(text: String?): String {
    // A: 3 words, B: 4-6 words, C: 7-9 words, D: other
}
```

#### **Content Similarity Detection**
```kotlin
// In ClipboardMonitoringService.kt
private fun hasSignificantContentChange(oldContent: String?, newContent: String?): Boolean {
    // Prevents false triggers from minor clipboard fluctuations
}
```

#### **Persistence Mechanism**
```kotlin
// In ServiceWatchdog.kt
private fun checkAndRestartService() {
    // Auto-restart dead services every 30 seconds
}
```

## 🛠️ Super Easy Build & Development

### **🚀 Quick Start (Any Platform)**

#### **Option 1: Easy Button (Recommended)**
```bash
# Windows (PowerShell or Command Prompt)
askgpt-build.bat build

# Linux/macOS/Git Bash
./askgpt-build.sh build
```

#### **Option 2: Traditional Gradle**
```bash
# Windows
gradlew.bat assembleDebug

# Linux/macOS  
./gradlew assembleDebug
```

### **📱 Super Easy Installation**
```bash
# Windows - Easy way
askgpt-build.bat install

# Linux/macOS - Easy way
./askgpt-build.sh install

# Traditional way (any platform)
./gradlew installDebug
```

### **🔧 First Time Setup**
```bash
# Windows
askgpt-build.bat setup

# Linux/macOS
./askgpt-build.sh setup
```

### **✅ Verify Everything Works**
```bash
# Check installation, APK files, services
askgpt-build.bat verify    # Windows
./askgpt-build.sh verify   # Linux/macOS
```

### **Prerequisites (Don't Worry - Script Helps!)**
- **Android Studio** (script finds it automatically) 
- **Or just OpenJDK 11/17** (script detects this too)
- **That's it!** The scripts handle everything else

### **🎯 What Each Script Does**

#### **askgpt-build.sh** (Cross-Platform Master Script)
- 🔍 **Auto-detects**: Windows, Linux, macOS
- 🔧 **Finds Java**: Automatically locates Android Studio JDK  
- 🏗️ **Smart Building**: Handles Gradle setup and execution
- 📱 **Device Install**: ADB detection and APK installation
- ✅ **Verification**: Checks everything is working
- 🎨 **Pretty Output**: Colored terminal output with progress

#### **askgpt-build.bat** (Windows Native)
- 🪟 **Windows First**: Native batch file for Windows users
- 🔄 **Bash Fallback**: Uses cross-platform script if Git Bash available
- 🏗️ **Native Mode**: Pure Windows commands when bash not available
- 🎯 **Simple**: Just double-click or run from Command Prompt

### **🤔 Which Script Should I Use?**

| Platform | Recommended | Why |
|----------|-------------|-----|
| **Windows** | `askgpt-build.bat` | Native Windows, no bash needed |
| **Windows + Git Bash** | `./askgpt-build.sh` | Full cross-platform features |
| **Linux** | `./askgpt-build.sh` | Native bash support |
| **macOS** | `./askgpt-build.sh` | Native bash support |
| **Any Platform** | Gradle directly | Traditional approach |

### **⚡ Enhanced Detection System**

**Smart Interval Technology:**
- **Fast Mode**: 200ms intervals when clipboard activity detected
- **Normal Mode**: 750ms intervals for balanced performance  
- **Power Save**: 2000ms intervals during low activity
- **Business Ready**: Optimized for professional use cases

**Content Change Detection:**
- **Similarity Check**: Prevents false triggers from minor changes
- **Length Threshold**: Ignores insignificant character changes
- **Hash Comparison**: Efficient content change detection
- **Adaptive Monitoring**: Adjusts intervals based on clipboard activity

## 📱 Super Easy Installation & Setup

### **🎯 One-Command Install**
```bash
# Windows (choose one)
askgpt-build.bat install     # Native Windows
.\askgpt-build.sh install    # If you have Git Bash

# Linux/macOS
./askgpt-build.sh install
```

### **📋 Step-by-Step (If You Want Control)**

#### **1. Build the App**
```bash
# Easy way
askgpt-build.bat build       # Windows
./askgpt-build.sh build      # Linux/macOS

# Traditional way  
./gradlew assembleDebug      # Any platform
```

#### **2. Install to Your Device**
```bash
# Connect your Android device via USB
# Enable "Developer Options" and "USB Debugging"
# Then run:
askgpt-build.bat install     # Windows
./askgpt-build.sh install    # Linux/macOS
```

#### **3. First Launch Setup**
- 📱 App opens automatically after install
- ✅ **Grant notification permission** (required for visual feedback)
- ✅ **Accept battery optimization exemption** (for 24/7 operation)
- ✅ **Enable accessibility service if requested** (enhanced detection)

#### **4. Instant Verification**
- ✅ App opens without crashing
- ✅ Persistent notification appears in status bar  
- ✅ Copy 3 words → See "A" icon (⚠️)
- ✅ Copy 5 words → See "B" icon (ℹ️)
- ✅ Copy 8 words → See "C" icon (✖️)
- ✅ Copy 15 words → See "D" icon (🗑️)

### **🔍 Troubleshooting Made Easy**

#### **"Build Failed" or "Java Not Found"**
```bash
# Run setup first
askgpt-build.bat setup       # Windows  
./askgpt-build.sh setup      # Linux/macOS

# This will:
# - Find your Java/Android Studio installation
# - Set up environment variables
# - Verify Gradle wrapper
# - Tell you exactly what's missing
```

#### **"No Devices Found" During Install**
1. Connect Android device via USB cable
2. Enable "Developer Options" on device:
   - Go to Settings → About Phone
   - Tap "Build Number" 7 times
3. Enable "USB Debugging":
   - Settings → Developer Options → USB Debugging ✅
4. Accept the computer connection on your device
5. Try install again

#### **"Clipboard Not Working"**
1. Open AskGPT app
2. Check notification permission is granted
3. Disable battery optimization for AskGPT:
   - Settings → Battery → Battery Optimization → AskGPT → Don't Optimize
4. Copy some text in any app - notification should update immediately

#### **"App Crashes on Start"**
- This is fixed! ✅ The enhanced version has:
  - Defensive service startup
  - Comprehensive error handling  
  - Safe initialization sequences
  - Adaptive monitoring intervals

### **🚀 Cross-App Testing (The Fun Part!)**
After installation, test in different apps:
- **Chrome**: Copy URL or text from webpage
- **Messages**: Copy text from chat
- **Notes**: Copy written content
- **Any App**: Select and copy text

Watch the notification icon change instantly! No need to return to AskGPT.

## 🔧 Technical Specifications

### **Enhanced Clipboard Detection**
- **Base Interval**: 750ms (business-optimized balance)
- **Fast Mode**: 200ms (during active clipboard use)
- **Power Save**: 2000ms (during low activity periods)
- **Content Similarity**: 90% threshold to prevent false triggers
- **Change Detection**: Minimum 2-character difference required

### **Word Count Categories** 
- **Category A**: Exactly 3 words (2 < count < 4)
- **Category B**: 4-6 words (3 < count < 7)  
- **Category C**: 7-9 words (6 < count < 10)
- **Category D**: Empty, null, or other counts (1, 2, 10+)

### **Performance Characteristics**
- **Response Time**: < 200ms clipboard detection (fast mode)
- **Memory Usage**: ~ 15-25MB persistent
- **Battery Impact**: Minimal with adaptive intervals
- **CPU Usage**: < 0.5% during active monitoring

### **Compatibility**
- **Android**: API 28+ (Android 9.0+)
- **Devices**: All Android devices (phones, tablets)
- **ROMs**: Stock Android, HyperOS, MIUI, AOSP, LineageOS
- **Build Platforms**: Windows, Linux, macOS

## 🧪 Testing

### **Unit Tests**
```bash
./gradlew testDebugUnitTest
```
Tests cover word count logic, edge cases, and utility functions.

### **Manual Testing Checklist**
- [ ] App installs without crashes
- [ ] Notification permission granted
- [ ] Background service starts
- [ ] Clipboard monitoring active
- [ ] Word count categories correct (A/B/C/D)
- [ ] Cross-app functionality works
- [ ] Service survives app minimize/restore
- [ ] Auto-restart after device reboot

## 🚀 Production Deployment

### **📄 Build Scripts Reference**

The project includes unified build scripts that work across all platforms:

#### **askgpt-build.sh** (Master Cross-Platform Script)
```bash
./askgpt-build.sh setup      # Setup environment and find Java
./askgpt-build.sh build      # Clean and build debug APK
./askgpt-build.sh install    # Build and install to device
./askgpt-build.sh verify     # Verify installation and components
./askgpt-build.sh clean      # Clean build artifacts
./askgpt-build.sh release    # Build release APK
./askgpt-build.sh help       # Show detailed help
```

#### **askgpt-build.bat** (Windows Native)
```batch
askgpt-build.bat setup       # Setup Windows environment
askgpt-build.bat build       # Build using native Windows commands
askgpt-build.bat install     # Install to connected device
askgpt-build.bat verify      # Check installation status
askgpt-build.bat clean       # Clean build files
askgpt-build.bat help        # Show help information
```

#### **Features of Enhanced Build System**
- 🔍 **Auto-Detection**: Finds Java, Android Studio, ADB automatically
- 🌍 **Cross-Platform**: Works on Windows, Linux, macOS
- 🎨 **Colored Output**: Clear visual feedback during build process
- ⚡ **Smart Fallback**: Windows script uses bash if available, native otherwise
- 🛡️ **Error Handling**: Clear error messages with suggested solutions
- 📊 **Build Info**: Shows APK size, configuration details, enhanced features

#### **Legacy Files (Now Consolidated)**
The following individual batch/shell files were merged into the unified system:
- ~~build.bat~~ → `askgpt-build.bat build`
- ~~setup.bat~~ → `askgpt-build.bat setup`  
- ~~verify_hyperos.sh~~ → `askgpt-build.sh verify`
- ~~verify_mainactivity.sh~~ → `askgpt-build.sh verify`
- ~~install_hyperos.bat~~ → `askgpt-build.bat install`

#### **Removed Unused Services (Clean Architecture)**
The following service files were removed as they were not registered in AndroidManifest.xml:
- ~~TextMonitoringService.kt~~ (redundant with ClipboardMonitoringService)
- ~~TextOverlayService.kt~~ (unused overlay functionality)
- ~~OverlayService.kt~~ (duplicate overlay implementation)
- ~~TextSelectionAccessibilityService.kt~~ (replaced by AskGPTAccessibilityService)

**💡 Pro Tip**: Use the new unified scripts for better experience and focus on the core 3 services for understanding the app!

## 🔍 Troubleshooting

### **Common Issues**

#### **App Crashes on Install**
- **Solution**: Check emulator API level (use 28-34)
- **Solution**: Ensure proper permissions in manifest

#### **Clipboard Not Detected**
- **Solution**: Grant notification permission
- **Solution**: Disable battery optimization for app
- **Solution**: Enable accessibility service

#### **Service Stops Running**
- **Solution**: Check battery optimization settings
- **Solution**: Verify ServiceWatchdog is running
- **Solution**: Restart app to reinitialize services

### **Debug Information**

#### **Logcat Filters**
```bash
adb logcat | grep "ClipboardService\|AskGPT\|ServiceWatchdog"
```

#### **Service Status Check**
```bash
adb shell dumpsys activity services | grep -i askgpt
```

## 🤝 Contributing

### **Development Guidelines**
1. Follow existing code style and patterns
2. Add unit tests for new functionality  
3. Update documentation for API changes
4. Test on multiple Android versions
5. Verify battery optimization impact

### **Code Review Checklist**
- [ ] Code follows existing patterns
- [ ] Error handling implemented
- [ ] Logging added for debugging
- [ ] Unit tests updated
- [ ] Documentation updated
- [ ] Battery impact considered
- [ ] Cross-app compatibility verified

## 📄 License

This project is available under standard software licensing terms. See project documentation for specific licensing information.

## 🆘 Support

For technical support or questions:
1. Check troubleshooting section above
2. Review source code comments
3. Check logcat output for error messages
4. Test on different Android versions/devices

---

**AskGPT Clipboard Monitoring System** - Production-ready Android application for persistent clipboard monitoring with intelligent content analysis and visual feedback.
2. Click "Open an existing Android Studio project"
3. Navigate to and select the `AskGPT` folder
4. Wait for Gradle sync to complete
5. Click the "Run" button or press Shift+F10

#### Option B: Using Command Line
1. Ensure you have Android Studio installed (for the Java runtime)
2. Open PowerShell in the project directory
3. Run the build script: `.\build.bat` (automated build)
4. Or manually:
   ```
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
   .\gradlew.bat clean assembleDebug
   ```

#### Requirements
- Android Studio (recommended) or Android SDK
- Java 8 or higher (included with Android Studio)
- Android device with API level 24 (Android 7.0) or higher

### 2. Grant Permissions
The app requires two main permissions:

#### Notification Permission (Android 13+)
- The app will prompt for notification permission on first launch
- This allows the app to show notifications when bold text is detected

#### Accessibility Service Permission
- Go to **Settings > Accessibility**
- Find "AskGPT" in the downloaded apps section
- Toggle it ON
- Confirm the permission dialog

### 3. Usage

1. Open any app on your device (browser, social media, messaging, etc.)
2. Navigate to any content with text
3. **Select and copy any text** using the standard copy function
4. The app will:
   - Detect the copy action immediately
   - Get the exact copied text from clipboard
   - Show an overlay notification instantly on screen
   - Display the text in the app's main screen
   - Add it to the text history

### How Clipboard Monitoring Works
- **Copy Detection**: Monitors accessibility events to detect copy actions
- **Clipboard Access**: Gets exact copied text from system clipboard
- **Universal App Support**: Works with ALL apps that support text copying (not just browsers)
- **Instant Response**: Shows overlay immediately when copy is detected
- **Precise Text**: Gets exactly what you copied, character for character
- **Background Operation**: Continuously monitors in background without user interaction
- **Enhanced Stability**: Auto-restart capabilities for reliable connection

## Technical Details

### Components

1. **TextSelectionAccessibilityService**: Monitors accessibility events for text selection
2. **TextMonitoringService**: Background foreground service that keeps the app running
3. **NotificationHelper**: Manages system notifications
4. **SelectedTextManager**: Data management for selected text items
5. **MainActivity**: Main UI displaying text history and permissions

### **Bold Text Detection**
The app uses several heuristics to identify bold text:
- Short lines (likely headers)
- All caps text
- Text matching header patterns (numbered lists, bullet points)
- Markdown-style bold text (**text**)

❌ **OUTDATED** - Now captures ALL selected text

### **Clipboard Text Detection** ✅ **UPDATED v3.0**
The app now uses **clipboard monitoring** for the most accurate text capture:

**Primary Method: Clipboard Monitoring ✨ NEW**
- **100% accuracy**: Gets exactly what you copy via system clipboard
- **Universal app support**: Works in ALL apps that support copying (browsers, social media, messaging, etc.)
- **Instant display**: Shows overlay immediately when copy is detected
- **Copy action detection**: Monitors accessibility events to detect when copy occurs
- **Background monitoring**: Runs continuously without user interaction

**Enhanced Stability Features:**
- **Auto-restart**: Service automatically restarts if interrupted
- **Connection stability**: Improved error handling and reconnection
- **Robust clipboard access**: Multiple fallback methods for clipboard reading
- **Enhanced logging**: Better debugging and connection status tracking

**Benefits:**
- **Most accurate**: Direct clipboard access ensures exact text capture
- **Universal compatibility**: Works with any app that supports text copying
- **No false positives**: Only activates when you actually copy text
- **Immediate response**: No delays or hold requirements
- **Enhanced reliability**: Improved connection stability and auto-recovery

### Permissions Required
- `FOREGROUND_SERVICE`: For background operation
- `POST_NOTIFICATIONS`: For showing notifications
- `WAKE_LOCK`: To prevent device sleep during monitoring
- `SYSTEM_ALERT_WINDOW`: For overlay notifications
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: To prevent system from killing the service

## Troubleshooting

### Compilation Issues

#### "Try catch is not supported around composable function invocations" Error
✅ **FIXED**: Removed try-catch blocks around composable function calls
- Moved error handling outside of Compose scope
- Used proper Compose error handling patterns
- Maintained crash protection while following Compose best practices

#### "App crashes before permission grant" Error
✅ **FIXED**: Added comprehensive error handling and permission checks
- Service only starts after notification permission is granted
- Added try-catch blocks around all critical operations
- Removed problematic `foregroundServiceType="specialUse"`
- Added fallback error screens for graceful error handling

#### "Compose Compiler requires Kotlin version 1.9.20" Error
✅ **FIXED**: Updated Kotlin version from 1.9.10 to 1.9.20 to match Compose Compiler 1.5.4 requirements

#### "Unresolved reference: compose" Error
✅ **FIXED**: Removed kotlin.compose plugin reference from root build.gradle.kts

#### "JAVA_HOME is not set" Error
1. Install Android Studio if not already installed
2. Run the provided `build.bat` script (automated)
3. Or manually set JAVA_HOME to your Android Studio JDK:
   ```
   set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
   ```

#### Gradle Sync Failed
1. Open Android Studio
2. Go to File > Invalidate Caches and Restart
3. Clean and rebuild the project

#### Missing SDK Components
1. Open Android Studio
2. Go to Tools > SDK Manager
3. Ensure Android SDK Platform 34 is installed
4. Install any missing build tools

### App Not Detecting Text
1. Ensure accessibility service is enabled in Settings
2. Try selecting text in Chrome browser (not other browsers)
3. Select text that appears bold or looks like headers
4. Check if the app has necessary permissions

### Notifications Not Showing
1. Check notification permission is granted
2. Ensure notifications are not disabled for the app in system settings
3. Check Do Not Disturb settings

### Service Stops Working
1. Disable battery optimization for the app
2. Check if the foreground service notification is visible
3. Restart the app if needed

## Development Notes

- Built with Kotlin 1.9.20 and Jetpack Compose
- Uses StateFlow for reactive data management
- Implements proper service lifecycle management
- Follows Android accessibility best practices
- **Compose-Kotlin Compatibility**: Using Compose Compiler 1.5.4 with Kotlin 1.9.20 (compatible versions)

## Privacy
- The app only monitors text selection events
- No data is sent to external servers
- All text history is stored locally on the device
- The app only monitors Chrome browser activity
