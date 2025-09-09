#!/usr/bin/env bash
# AskGPT Cross-Platform Build & Setup Script
# Works on Windows (with Git Bash), Linux, and macOS
# Usage: ./askgpt-build.sh [command]

set -e  # Exit on error

# Script metadata
SCRIPT_NAME="AskGPT Build & Setup"
SCRIPT_VERSION="1.0.0"
PROJECT_NAME="AskGPT"

# Color codes for better output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Detect operating system
detect_os() {
    case "$(uname -s)" in
        Linux*)     OS=Linux;;
        Darwin*)    OS=Mac;;
        CYGWIN*)    OS=Windows;;
        MINGW*)     OS=Windows;;
        MSYS*)      OS=Windows;;
        *)          OS="UNKNOWN";;
    esac
    
    if [[ "$OS" == "UNKNOWN" ]] && [[ -n "$WINDIR" ]]; then
        OS=Windows
    fi
}

# Print colored output
print_color() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

print_header() {
    echo ""
    print_color $CYAN "=========================================="
    print_color $CYAN " $SCRIPT_NAME v$SCRIPT_VERSION"
    print_color $CYAN " Platform: $OS"
    print_color $CYAN "=========================================="
    echo ""
}

# Find Java/JDK paths based on OS
find_java() {
    local java_path=""
    
    case "$OS" in
        Windows)
            # Check common Windows Android Studio locations
            local paths=(
                "/c/Program Files/Android/Android Studio/jbr"
                "/c/Users/$USER/AppData/Local/Android/Sdk/jbr"
                "$PROGRAMFILES/Android/Android Studio/jbr"
                "$LOCALAPPDATA/Android/Sdk/jbr"
            )
            
            for path in "${paths[@]}"; do
                if [[ -f "$path/bin/java.exe" ]] || [[ -f "$path/bin/java" ]]; then
                    java_path="$path"
                    break
                fi
            done
            ;;
        Linux)
            # Check common Linux Android Studio locations
            local paths=(
                "$HOME/android-studio/jbr"
                "/opt/android-studio/jbr"
                "$HOME/Android/Sdk/jbr"
                "/usr/lib/jvm/java-17-openjdk-amd64"
                "/usr/lib/jvm/java-11-openjdk-amd64"
            )
            
            for path in "${paths[@]}"; do
                if [[ -f "$path/bin/java" ]]; then
                    java_path="$path"
                    break
                fi
            done
            ;;
        Mac)
            # Check common macOS Android Studio locations
            local paths=(
                "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
                "$HOME/Library/Android/sdk/jbr"
                "/Library/Java/JavaVirtualMachines/*/Contents/Home"
            )
            
            for path in "${paths[@]}"; do
                if [[ -f "$path/bin/java" ]]; then
                    java_path="$path"
                    break
                fi
            done
            ;;
    esac
    
    # Check if java is in PATH as fallback
    if [[ -z "$java_path" ]] && command -v java >/dev/null 2>&1; then
        java_path=$(dirname $(dirname $(readlink -f $(which java))))
    fi
    
    echo "$java_path"
}

# Setup environment
setup_environment() {
    print_color $BLUE "🔧 Setting up build environment..."
    
    local java_home=$(find_java)
    
    if [[ -z "$java_home" ]]; then
        print_color $RED "❌ Java/JDK not found!"
        print_color $YELLOW "📥 Please install Android Studio or OpenJDK 11/17"
        print_color $YELLOW "   Download: https://developer.android.com/studio"
        return 1
    fi
    
    export JAVA_HOME="$java_home"
    export PATH="$JAVA_HOME/bin:$PATH"
    
    print_color $GREEN "✅ Java found: $JAVA_HOME"
    
    # Verify Gradle wrapper
    if [[ ! -f "./gradlew" ]]; then
        print_color $RED "❌ Gradle wrapper not found!"
        print_color $YELLOW "   Make sure you're in the AskGPT project directory"
        return 1
    fi
    
    # Make gradlew executable on Unix systems
    if [[ "$OS" != "Windows" ]]; then
        chmod +x ./gradlew
    fi
    
    print_color $GREEN "✅ Environment setup complete"
    return 0
}

# Build project
build_project() {
    print_color $BLUE "🔨 Building $PROJECT_NAME..."
    
    print_color $YELLOW "📋 Project Configuration:"
    print_color $NC "   • Kotlin: 1.9.20"
    print_color $NC "   • Compose Compiler: 1.5.4" 
    print_color $NC "   • Android Gradle Plugin: 8.1.4"
    print_color $NC "   • Target SDK: 34"
    echo ""
    
    print_color $YELLOW "🔧 Recent Enhancements:"
    print_color $NC "   • Intelligent interval-based clipboard detection"
    print_color $NC "   • Business-ready adaptive monitoring (750ms base)"
    print_color $NC "   • Content change detection with similarity checking"
    print_color $NC "   • Enhanced error handling and auto-restart"
    echo ""
    
    # Clean and build
    print_color $BLUE "🧹 Cleaning previous build..."
    ./gradlew clean
    
    print_color $BLUE "🔨 Building debug APK..."
    ./gradlew assembleDebug
    
    if [[ $? -eq 0 ]]; then
        local apk_path="app/build/outputs/apk/debug/app-debug.apk"
        if [[ -f "$apk_path" ]]; then
            local apk_size=$(du -h "$apk_path" | cut -f1)
            print_color $GREEN "🎉 BUILD SUCCESSFUL!"
            print_color $GREEN "📱 APK: $apk_path ($apk_size)"
            echo ""
            print_color $CYAN "✨ Enhanced Features Applied:"
            print_color $NC "   • Smart clipboard detection intervals"
            print_color $NC "   • Adaptive monitoring (fast/normal/slow modes)"
            print_color $NC "   • Content similarity checking"
            print_color $NC "   • Business-optimized battery usage"
            print_color $NC "   • Cross-platform build system"
        else
            print_color $YELLOW "⚠️  Build completed but APK not found at expected location"
        fi
    else
        print_color $RED "❌ BUILD FAILED!"
        print_color $YELLOW "🔍 Common solutions:"
        print_color $NC "   1. Check internet connection (for dependencies)"
        print_color $NC "   2. Verify Android SDK is installed"
        print_color $NC "   3. Try: ./askgpt-build.sh clean"
        print_color $NC "   4. Check Java version compatibility"
        return 1
    fi
}

# Install APK to connected device
install_apk() {
    print_color $BLUE "📱 Installing APK to device..."
    
    if ! command -v adb >/dev/null 2>&1; then
        print_color $RED "❌ ADB not found in PATH"
        print_color $YELLOW "   Add Android SDK platform-tools to PATH"
        return 1
    fi
    
    # Check for connected devices
    local devices=$(adb devices | grep -v "List of devices" | grep -v "^$" | wc -l)
    if [[ $devices -eq 0 ]]; then
        print_color $RED "❌ No devices connected"
        print_color $YELLOW "   Connect your device and enable USB debugging"
        return 1
    fi
    
    local apk_path="app/build/outputs/apk/debug/app-debug.apk"
    if [[ ! -f "$apk_path" ]]; then
        print_color $YELLOW "⚠️  APK not found, building first..."
        build_project || return 1
    fi
    
    print_color $BLUE "📲 Installing to device..."
    ./gradlew installDebug
    
    if [[ $? -eq 0 ]]; then
        print_color $GREEN "✅ Installation complete!"
        print_color $CYAN "🚀 Next steps:"
        print_color $NC "   1. Open AskGPT app on your device"
        print_color $NC "   2. Grant notification permission"
        print_color $NC "   3. Disable battery optimization"
        print_color $NC "   4. Test clipboard monitoring"
    else
        print_color $RED "❌ Installation failed"
        return 1
    fi
}

# Verify installation and functionality
verify_installation() {
    print_color $BLUE "🔍 Verifying installation..."
    
    if ! command -v adb >/dev/null 2>&1; then
        print_color $YELLOW "⚠️  ADB not available, skipping device verification"
        return 0
    fi
    
    # Check if app is installed
    local package="com.example.askgpt"
    if adb shell pm list packages | grep -q "$package"; then
        print_color $GREEN "✅ App installed on device"
        
        # Check if app is running
        if adb shell ps | grep -q "$package"; then
            print_color $GREEN "✅ App is running"
        else
            print_color $YELLOW "⚠️  App installed but not running"
        fi
    else
        print_color $RED "❌ App not found on device"
        return 1
    fi
    
    # Check APK files
    print_color $BLUE "📁 Checking build outputs..."
    local debug_apk="app/build/outputs/apk/debug/app-debug.apk"
    local release_apk="app/build/outputs/apk/release/app-release.apk"
    
    if [[ -f "$debug_apk" ]]; then
        local size=$(du -h "$debug_apk" | cut -f1)
        print_color $GREEN "✅ Debug APK: $size"
    else
        print_color $YELLOW "⚠️  Debug APK not found"
    fi
    
    if [[ -f "$release_apk" ]]; then
        local size=$(du -h "$release_apk" | cut -f1)
        print_color $GREEN "✅ Release APK: $size"
    else
        print_color $YELLOW "⚠️  Release APK not found"
    fi
    
    # Check manifest for key components
    print_color $BLUE "📋 Checking manifest configuration..."
    local manifest="app/src/main/AndroidManifest.xml"
    
    if grep -q "ClipboardMonitoringService" "$manifest"; then
        print_color $GREEN "✅ Clipboard service configured"
    else
        print_color $RED "❌ Clipboard service missing"
    fi
    
    if grep -q "AskGPTAccessibilityService" "$manifest"; then
        print_color $GREEN "✅ Accessibility service configured"
    else
        print_color $YELLOW "⚠️  Accessibility service not found"
    fi
    
    print_color $CYAN "🎯 Verification complete!"
}

# Show help
show_help() {
    print_header
    print_color $CYAN "📖 Usage: ./askgpt-build.sh [command]"
    echo ""
    print_color $YELLOW "Commands:"
    print_color $NC "  setup     - Setup build environment and dependencies"
    print_color $NC "  build     - Build the Android APK"
    print_color $NC "  install   - Install APK to connected device"
    print_color $NC "  verify    - Verify installation and configuration"
    print_color $NC "  clean     - Clean build artifacts"
    print_color $NC "  release   - Build release APK"
    print_color $NC "  help      - Show this help message"
    echo ""
    print_color $YELLOW "Examples:"
    print_color $NC "  ./askgpt-build.sh setup     # First time setup"
    print_color $NC "  ./askgpt-build.sh build     # Build debug APK"
    print_color $NC "  ./askgpt-build.sh install   # Build and install"
    print_color $NC "  ./askgpt-build.sh verify    # Check everything works"
    echo ""
    print_color $YELLOW "🔧 Enhanced Features:"
    print_color $NC "  • Cross-platform compatibility (Windows/Linux/macOS)"
    print_color $NC "  • Intelligent clipboard monitoring intervals"
    print_color $NC "  • Business-ready adaptive detection"
    print_color $NC "  • Automatic environment detection"
    print_color $NC "  • Enhanced error handling and logging"
    echo ""
}

# Clean build artifacts
clean_project() {
    print_color $BLUE "🧹 Cleaning build artifacts..."
    ./gradlew clean
    print_color $GREEN "✅ Clean complete"
}

# Build release APK
build_release() {
    print_color $BLUE "🚀 Building release APK..."
    print_color $YELLOW "⚠️  Note: Release APK requires signing configuration"
    
    ./gradlew assembleRelease
    
    if [[ $? -eq 0 ]]; then
        local apk_path="app/build/outputs/apk/release/app-release-unsigned.apk"
        if [[ -f "$apk_path" ]]; then
            local apk_size=$(du -h "$apk_path" | cut -f1)
            print_color $GREEN "✅ Release APK built: $apk_size"
            print_color $YELLOW "⚠️  APK is unsigned - sign before distribution"
        fi
    else
        print_color $RED "❌ Release build failed"
        return 1
    fi
}

# Main execution
main() {
    detect_os
    
    # Change to script directory
    cd "$(dirname "$0")"
    
    local command="${1:-help}"
    
    case "$command" in
        setup)
            print_header
            setup_environment
            ;;
        build)
            print_header
            setup_environment && build_project
            ;;
        install)
            print_header
            setup_environment && install_apk
            ;;
        verify)
            print_header
            verify_installation
            ;;
        clean)
            print_header
            setup_environment && clean_project
            ;;
        release)
            print_header
            setup_environment && build_release
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            print_color $RED "❌ Unknown command: $command"
            echo ""
            show_help
            exit 1
            ;;
    esac
}

# Run main function with all arguments
main "$@"
