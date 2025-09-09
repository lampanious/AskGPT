#!/bin/bash
# HyperOS Installation Verification Script

echo "=== AskGPT HyperOS Installation Check ==="
echo ""

# Check if APK files exist
echo "1. Checking APK files..."
if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    DEBUG_SIZE=$(stat -f%z "app/build/outputs/apk/debug/app-debug.apk" 2>/dev/null || stat -c%s "app/build/outputs/apk/debug/app-debug.apk" 2>/dev/null)
    echo "   ✓ Debug APK found (${DEBUG_SIZE} bytes)"
else
    echo "   ✗ Debug APK missing"
fi

if [ -f "app/build/outputs/apk/release/app-release.apk" ]; then
    RELEASE_SIZE=$(stat -f%z "app/build/outputs/apk/release/app-release.apk" 2>/dev/null || stat -c%s "app/build/outputs/apk/release/app-release.apk" 2>/dev/null)
    echo "   ✓ Release APK found (${RELEASE_SIZE} bytes)"
else
    echo "   ✗ Release APK missing"
fi

echo ""
echo "2. Checking AndroidManifest.xml..."
if grep -q "AskGPTAccessibilityService" app/src/main/AndroidManifest.xml; then
    echo "   ✓ Accessibility service defined"
else
    echo "   ✗ Accessibility service missing"
fi

if grep -q "android.permission.BIND_ACCESSIBILITY_SERVICE" app/src/main/AndroidManifest.xml; then
    echo "   ✓ Accessibility permission declared"
else
    echo "   ✗ Accessibility permission missing"
fi

echo ""
echo "3. Checking accessibility service configuration..."
if [ -f "app/src/main/res/xml/accessibility_service_config.xml" ]; then
    echo "   ✓ Accessibility service config found"
else
    echo "   ✗ Accessibility service config missing"
fi

echo ""
echo "4. Checking service classes..."
if [ -f "app/src/main/java/com/example/askgpt/services/AskGPTAccessibilityService.kt" ]; then
    echo "   ✓ Accessibility service class found"
else
    echo "   ✗ Accessibility service class missing"
fi

if [ -f "app/src/main/java/com/example/askgpt/services/ClipboardMonitoringService.kt" ]; then
    echo "   ✓ Clipboard monitoring service found"
else
    echo "   ✗ Clipboard monitoring service missing"
fi

echo ""
echo "=== Installation Recommendations ==="
echo "• For HyperOS: Use app-release.apk (smaller, more trusted)"
echo "• Enable 'Install unknown apps' for your file manager"
echo "• After install: Settings > Accessibility > AskGPT > Enable"
echo "• Disable battery optimization: Settings > Battery > AskGPT > No restrictions"
echo ""
echo "=== Usage ==="
echo "• Copy any text from any app"
echo "• Check notification: 'AskGPT [A/B/C/D]'"
echo "  - A: >2 words, B: >7 words, C: 0 words, D: null"
