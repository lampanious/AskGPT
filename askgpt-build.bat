@echo off
REM AskGPT Windows Build Script
REM This script attempts to run the cross-platform bash script
REM Falls back to native Windows commands if bash is not available

setlocal enabledelayedexpansion

REM Script metadata
set "SCRIPT_NAME=AskGPT Windows Build Helper"
set "SCRIPT_VERSION=1.0.0"

REM Color setup for Windows (basic)
set "RED=[91m"
set "GREEN=[92m"
set "YELLOW=[93m"
set "BLUE=[94m"
set "CYAN=[96m"
set "NC=[0m"

REM Print colored text (basic Windows version)
:print_color
echo %~2
goto :eof

REM Print header
:print_header
echo.
echo ==========================================
echo  %SCRIPT_NAME% v%SCRIPT_VERSION%
echo  Platform: Windows
echo ==========================================
echo.
goto :eof

REM Check if bash is available
:check_bash
where bash >nul 2>&1
if %errorlevel% equ 0 (
    set "BASH_AVAILABLE=1"
) else (
    set "BASH_AVAILABLE=0"
)
goto :eof

REM Try to run the cross-platform script
:run_cross_platform
if "%BASH_AVAILABLE%"=="1" (
    echo [96mUsing cross-platform bash script...[0m
    bash askgpt-build.sh %*
    goto :eof
) else (
    echo [93mBash not available, using Windows native commands...[0m
    goto :run_native
)

REM Native Windows implementation
:run_native
set "command=%~1"
if "%command%"=="" set "command=help"

if /i "%command%"=="setup" goto :setup_native
if /i "%command%"=="build" goto :build_native
if /i "%command%"=="install" goto :install_native
if /i "%command%"=="clean" goto :clean_native
if /i "%command%"=="verify" goto :verify_native
if /i "%command%"=="help" goto :help_native
goto :help_native

:setup_native
call :print_header
echo [94mSetting up build environment...[0m

REM Find Java
set "JAVA_HOME="
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
) else if exist "%LOCALAPPDATA%\Android\Sdk\jbr\bin\java.exe" (
    set "JAVA_HOME=%LOCALAPPDATA%\Android\Sdk\jbr"
) else (
    echo [91mJava not found in standard locations![0m
    echo [93mPlease install Android Studio or set JAVA_HOME manually[0m
    echo Download: https://developer.android.com/studio
    pause
    exit /b 1
)

echo [92mJava found: %JAVA_HOME%[0m
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo [92mEnvironment setup complete[0m
goto :eof

:build_native
call :print_header
call :setup_native
if %errorlevel% neq 0 exit /b %errorlevel%

echo [94mBuilding AskGPT...[0m
echo.
echo [93mProject Configuration:[0m
echo   • Kotlin: 1.9.20
echo   • Compose Compiler: 1.5.4
echo   • Android Gradle Plugin: 8.1.4
echo   • Target SDK: 34
echo.
echo [93mEnhanced Features:[0m
echo   • Intelligent clipboard monitoring intervals
echo   • Business-ready adaptive detection (750ms base)
echo   • Content change detection with similarity checking
echo   • Enhanced error handling and auto-restart
echo.

echo [94mCleaning previous build...[0m
gradlew.bat clean

echo [94mBuilding debug APK...[0m
gradlew.bat assembleDebug

if %errorlevel% equ 0 (
    echo.
    echo [92mBUILD SUCCESSFUL![0m
    echo [92mAPK: app\build\outputs\apk\debug\app-debug.apk[0m
    echo.
    echo [96mEnhanced Features Applied:[0m
    echo   • Smart clipboard detection intervals
    echo   • Adaptive monitoring (fast/normal/slow modes)
    echo   • Content similarity checking
    echo   • Business-optimized battery usage
) else (
    echo.
    echo [91mBUILD FAILED![0m
    echo [93mCommon solutions:[0m
    echo   1. Check internet connection
    echo   2. Verify Android SDK is installed
    echo   3. Run: askgpt-build.bat clean
    echo   4. Check Java version compatibility
)
goto :eof

:install_native
call :print_header
call :setup_native
if %errorlevel% neq 0 exit /b %errorlevel%

echo [94mInstalling APK to device...[0m
gradlew.bat installDebug

if %errorlevel% equ 0 (
    echo [92mInstallation complete![0m
    echo [96mNext steps:[0m
    echo   1. Open AskGPT app on your device
    echo   2. Grant notification permission
    echo   3. Disable battery optimization
    echo   4. Test clipboard monitoring
) else (
    echo [91mInstallation failed[0m
)
goto :eof

:clean_native
call :print_header
call :setup_native
if %errorlevel% neq 0 exit /b %errorlevel%

echo [94mCleaning build artifacts...[0m
gradlew.bat clean
echo [92mClean complete[0m
goto :eof

:verify_native
call :print_header
echo [94mVerifying installation...[0m

REM Check APK files
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    for %%A in ("app\build\outputs\apk\debug\app-debug.apk") do echo [92mDebug APK found: %%~zA bytes[0m
) else (
    echo [93mDebug APK not found[0m
)

if exist "app\build\outputs\apk\release\app-release.apk" (
    for %%A in ("app\build\outputs\apk\release\app-release.apk") do echo [92mRelease APK found: %%~zA bytes[0m
) else (
    echo [93mRelease APK not found[0m
)

REM Check manifest
findstr /c:"ClipboardMonitoringService" "app\src\main\AndroidManifest.xml" >nul 2>&1
if %errorlevel% equ 0 (
    echo [92mClipboard service configured[0m
) else (
    echo [91mClipboard service missing[0m
)

echo [96mVerification complete![0m
goto :eof

:help_native
call :print_header
echo [96mUsage: askgpt-build.bat [command][0m
echo.
echo [93mCommands:[0m
echo   setup     - Setup build environment
echo   build     - Build the Android APK
echo   install   - Install APK to connected device
echo   verify    - Verify installation and configuration
echo   clean     - Clean build artifacts
echo   help      - Show this help
echo.
echo [93mExamples:[0m
echo   askgpt-build.bat setup     # First time setup
echo   askgpt-build.bat build     # Build debug APK
echo   askgpt-build.bat install   # Build and install
echo   askgpt-build.bat verify    # Check everything works
echo.
echo [93mEnhanced Features:[0m
echo   • Cross-platform compatibility
echo   • Intelligent clipboard monitoring
echo   • Business-ready adaptive detection
echo   • Automatic environment detection
echo.
echo [93mNote:[0m
echo   For full cross-platform features, install Git Bash:
echo   https://git-scm.com/download/win
echo.
goto :eof

REM Main execution
:main
call :check_bash
call :run_cross_platform %*
goto :eof

REM Entry point
call :main %*
