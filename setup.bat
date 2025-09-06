@echo off
echo Setting up Android project for compilation...
echo.

REM Check if Android Studio is installed and find Java
set "ANDROID_STUDIO_PATH="
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    set "ANDROID_STUDIO_PATH=C:\Program Files\Android\Android Studio"
) else if exist "C:\Users\%USERNAME%\AppData\Local\Android\Sdk\jbr\bin\java.exe" (
    set "JAVA_HOME=C:\Users\%USERNAME%\AppData\Local\Android\Sdk\jbr"
) else (
    echo Android Studio JDK not found in standard locations.
    echo Please install Android Studio or set JAVA_HOME manually.
    echo.
    echo You can download Android Studio from: https://developer.android.com/studio
    pause
    exit /b 1
)

echo Found Java at: %JAVA_HOME%
echo.

REM Set environment variable for current session
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Environment configured. You can now run:
echo   gradlew assembleDebug
echo   gradlew installDebug
echo.

REM Optionally run the build
set /p choice="Would you like to build the project now? (y/n): "
if /i "%choice%"=="y" (
    echo Building project...
    call gradlew assembleDebug
) else (
    echo Setup complete. Run 'gradlew assembleDebug' when ready to build.
)

pause
