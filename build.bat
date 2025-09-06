@echo off
echo Building AskGPT Android Project (Crash-Safe Version)...
echo.
echo Project Configuration:
echo - Kotlin: 1.9.20
echo - Compose Compiler: 1.5.4
echo - Android Gradle Plugin: 8.1.4
echo - Target SDK: 34
echo.
echo Recent Fixes:
echo - Added comprehensive error handling
echo - Fixed foreground service crashes
echo - Added permission checks before service start
echo - Enhanced notification error handling
echo.

REM Set Java Home
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"

REM Change to project directory
cd /d "C:\Users\demoM\AndroidStudioProjects\AskGPT"

REM Run gradle build
echo Running gradle clean and build...
gradlew.bat clean assembleDebug

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ✓ BUILD SUCCESSFUL!
    echo.
    echo APK location: app\build\outputs\apk\debug\app-debug.apk
    echo.
    echo ✅ CRASH FIXES APPLIED:
    echo - Service startup now checks permissions first
    echo - Added try-catch blocks for all critical operations
    echo - Removed problematic foregroundServiceType
    echo - Added fallback error screens
    echo.
) else (
    echo.
    echo ✗ BUILD FAILED!
    echo Check the error messages above.
    echo.
    echo Common issues and solutions:
    echo 1. Kotlin-Compose compatibility: Ensure Kotlin 1.9.20 + Compose 1.5.4
    echo 2. JAVA_HOME: Ensure Android Studio is installed
    echo 3. SDK: Ensure Android SDK 34 is installed
    echo.
)

pause
