# AskGPT - Selected Text Monitor

An Android app that runs in the background and monitors **any selected text** from Chrome browser, displaying it both in the app's main screen and as system notifications.

## Features

- **Background Monitoring**: Runs as a background service to continuously monitor text selection
- **Chrome Integration**: Monitors text selection in Chrome browser (any font, type, or format)
- **Universal Text Capture**: Captures **ANY selected/highlighted text**, regardless of formatting
- **System Notifications**: Shows notifications next to the clock when text is selected
- **Text History**: Keeps a history of all captured text with timestamps
- **Accessibility Service**: Uses Android's accessibility service for reliable text detection

## Setup Instructions

### 1. Build and Install

#### Option A: Using Android Studio (Recommended)
1. Open Android Studio
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
1. Open Chrome browser on your device
2. Navigate to any website 
3. **Select any text** by highlighting it with your finger or pointer
4. The app will:
   - Detect the selected text (any font, type, or formatting)
   - Show a notification immediately
   - Display the text in the app's main screen
   - Add it to the text history

### How Text Selection Works
- **Any Text**: Captures ALL selected text, regardless of font, size, color, or formatting
- **Universal Detection**: Works with normal text, bold text, italic text, headers, paragraphs, etc.
- **Real-time**: Immediate detection and notification when text is highlighted
- **Multiple Events**: Monitors various user interactions (selection, clicks, focus) for better detection

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

### **Text Selection Detection** ✅ **UPDATED**
The app now captures **any selected text** in Chrome:
- **Universal capture**: Any highlighted/selected text regardless of formatting
- **Multiple event types**: Text selection, clicks, focus events
- **Real-time detection**: Immediate response when text is highlighted
- **Enhanced logging**: Better debugging and detection accuracy

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
