@echo off
echo Starting Android Studio AskGPT project setup...
echo.

echo Step 1: Cleaning project...
call gradlew clean

echo Step 2: Building project...
call gradlew build

echo Step 3: Generating debug APK...
call gradlew assembleDebug

echo.
echo ====================================================
echo Android Studio Setup Instructions:
echo ====================================================
echo 1. Open Android Studio
echo 2. Click "Open an Existing Project"
echo 3. Navigate to: %~dp0
echo 4. Select this folder and click OK
echo 5. Wait for Gradle sync to complete
echo 6. If prompted, update Gradle or Android Gradle Plugin
echo 7. Connect an Android device or start an emulator
echo 8. Click the "Run" button (green play icon)
echo.
echo If you encounter issues:
echo - File > Invalidate Caches and Restart
echo - Build > Clean Project
echo - Build > Rebuild Project
echo.
echo Project is ready to run in Android Studio!
echo ====================================================
pause
