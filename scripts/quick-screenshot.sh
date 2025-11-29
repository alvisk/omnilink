#!/bin/bash
# =============================================================================
# Quick Screenshot - One-liner for instant capture
# =============================================================================
# Usage: ./scripts/quick-screenshot.sh [optional_name]
# =============================================================================

# Add Android SDK platform-tools to PATH
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"

NAME="${1:-screenshot_$(date +%H%M%S)}"
OUTPUT="screenshots/${NAME}.png"

mkdir -p screenshots
adb shell screencap -p /sdcard/temp_ss.png
adb pull /sdcard/temp_ss.png "$OUTPUT" 2>/dev/null
adb shell rm /sdcard/temp_ss.png

echo "📸 Saved: $OUTPUT"

# Open on macOS
[[ "$OSTYPE" == "darwin"* ]] && open "$OUTPUT"
