@echo off
echo Building AskGPT with Kotlin Daemon Fix...
echo.

REM Set Java environment
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Java Home: %JAVA_HOME%
echo.

REM Kill any existing Java processes
echo Stopping any running gradle/kotlin daemons...
taskkill /f /im java.exe /t >nul 2>&1

REM Clean gradle cache
echo Cleaning gradle cache...
if exist "%USERPROFILE%\.gradle\daemon" rmdir /s /q "%USERPROFILE%\.gradle\daemon" >nul 2>&1
if exist "%USERPROFILE%\.kotlin\daemon" rmdir /s /q "%USERPROFILE%\.kotlin\daemon" >nul 2>&1

echo.
echo Starting fresh build without daemon...
echo.

REM Build without daemon to avoid connection issues
gradlew.bat --no-daemon --max-workers=2 clean assembleDebug

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Build failed. Trying alternative approach...
    echo.
    
    REM Try with minimal settings
    gradlew.bat --no-daemon --no-parallel --max-workers=1 assembleDebug
)

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ✅ Build completed successfully!
    echo APK location: app\build\outputs\apk\debug\app-debug.apk
    echo.
    dir app\build\outputs\apk\debug\app-debug.apk
) else (
    echo.
    echo ❌ Build failed with error code %ERRORLEVEL%
    echo.
)

pause
