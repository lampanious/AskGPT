@echo off
echo ================================================
echo AskGPT Installation Helper for HyperOS/MIUI
echo ================================================
echo.
echo This script helps with installing AskGPT on HyperOS/MIUI devices
echo.
echo STEPS TO INSTALL:
echo 1. Enable Developer Options (tap MIUI version 7 times in About Phone)
echo 2. Enable "Install via USB" in Developer Options
echo 3. Enable "USB Debugging" in Developer Options
echo 4. Allow installation from unknown sources for this PC
echo.
echo Building signed APK...
echo.

call gradlew.bat assembleRelease

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ================================================
    echo BUILD SUCCESSFUL!
    echo ================================================
    echo.
    echo APK Location: app\build\outputs\apk\release\app-release.apk
    echo.
    echo INSTALLATION INSTRUCTIONS:
    echo 1. Copy the APK to your phone
    echo 2. Install it using a file manager
    echo 3. Go to Settings > Accessibility
    echo 4. Find "AskGPT" in the accessibility services list
    echo 5. Enable the AskGPT accessibility service
    echo.
    echo The app will now appear in your accessibility settings!
    echo.
) else (
    echo.
    echo BUILD FAILED! Check the error messages above.
    echo.
)

pause
