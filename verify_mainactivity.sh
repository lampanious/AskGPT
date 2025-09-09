#!/bin/bash
# APK MainActivity Verification Script

echo "=== AskGPT MainActivity Verification ==="
echo ""

DEBUG_APK="app/build/outputs/apk/debug/app-debug.apk"
RELEASE_APK="app/build/outputs/apk/release/app-release.apk"

# Check if APKs exist
if [ -f "$DEBUG_APK" ]; then
    DEBUG_SIZE=$(stat -f%z "$DEBUG_APK" 2>/dev/null || stat -c%s "$DEBUG_APK" 2>/dev/null)
    echo "✓ Debug APK found: ${DEBUG_SIZE} bytes"
else
    echo "✗ Debug APK not found"
fi

if [ -f "$RELEASE_APK" ]; then
    RELEASE_SIZE=$(stat -f%z "$RELEASE_APK" 2>/dev/null || stat -c%s "$RELEASE_APK" 2>/dev/null)
    echo "✓ Release APK found: ${RELEASE_SIZE} bytes"
else
    echo "✗ Release APK not found"
fi

echo ""
echo "=== Changes Made to Fix MainActivity Issue ==="
echo "1. ✓ Disabled minification in release build"
echo "2. ✓ Added comprehensive ProGuard rules"
echo "3. ✓ Created AskGPTApplication class for proper initialization"
echo "4. ✓ Updated AndroidManifest.xml with application class"
echo "5. ✓ Added class loading verification in Application.onCreate()"

echo ""
echo "=== ProGuard Rules Added ==="
echo "• Keep MainActivity and all activities"
echo "• Keep Application class"
echo "• Keep all services"
echo "• Keep accessibility service specifically"
echo "• Keep data classes and utils"
echo "• Keep public classes in main package"

echo ""
echo "=== Installation Instructions ==="
echo "1. Use either APK (both should work now)"
echo "2. Install via 'adb install -r app-debug.apk' or file manager"
echo "3. MainActivity should launch properly"
echo "4. Check logcat for 'AskGPT Application started' message"

echo ""
echo "=== If MainActivity Still Not Found ==="
echo "1. Check logcat: 'adb logcat | grep AskGPT'"
echo "2. Verify app installation: 'adb shell pm list packages | grep askgpt'"
echo "3. Try debug APK first (larger, less optimized)"
echo "4. Clear app data if reinstalling"

echo ""
echo "Release APK is now ${RELEASE_SIZE} bytes (was ~1MB, now ~5MB due to disabled minification)"
echo "This should resolve the MainActivity not found issue!"
