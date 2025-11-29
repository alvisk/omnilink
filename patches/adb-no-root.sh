#!/bin/bash
# OmniLink Essential Button Setup (No Root Required)
# Disables Essential Space via ADB so OmniLink can capture the button
#
# Requirements:
# - USB Debugging enabled on phone
# - ADB installed on computer
# - Phone connected via USB

set -e

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  OmniLink Essential Button Setup"
echo "  (No Root Required)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Check if ADB is available
if ! command -v adb &> /dev/null; then
    echo "❌ ADB not found. Please install Android SDK Platform Tools."
    echo "   Download from: https://developer.android.com/tools/releases/platform-tools"
    exit 1
fi

# Check for connected device
echo "Checking for connected device..."
DEVICE=$(adb devices | grep -v "List" | grep "device$" | head -1 | cut -f1)

if [ -z "$DEVICE" ]; then
    echo "❌ No device connected or USB debugging not authorized."
    echo ""
    echo "Please ensure:"
    echo "  1. USB Debugging is enabled (Settings → Developer Options)"
    echo "  2. Phone is connected via USB"
    echo "  3. You've authorized this computer on the phone"
    exit 1
fi

echo "✅ Device found: $DEVICE"
echo ""

# Check if it's a Nothing Phone
MODEL=$(adb shell getprop ro.product.model 2>/dev/null)
echo "Device model: $MODEL"

if [[ "$MODEL" != *"Phone"* ]] && [[ "$MODEL" != *"Nothing"* ]] && [[ "$MODEL" != *"A059"* ]]; then
    echo ""
    echo "⚠️  Warning: This doesn't appear to be a Nothing Phone."
    echo "   Proceeding anyway..."
    echo ""
fi

echo ""
echo "Disabling Essential Space apps..."

# Disable Essential Space
if adb shell pm disable-user --user 0 com.nothing.ntessentialspace 2>/dev/null; then
    echo "✅ Disabled: com.nothing.ntessentialspace"
else
    echo "⚠️  Could not disable ntessentialspace (may already be disabled)"
fi

# Disable Essential Recorder
if adb shell pm disable-user --user 0 com.nothing.ntessentialrecorder 2>/dev/null; then
    echo "✅ Disabled: com.nothing.ntessentialrecorder"
else
    echo "⚠️  Could not disable ntessentialrecorder (may already be disabled)"
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Setup Complete!"
echo ""
echo "  The Essential Button will now send key"
echo "  events that OmniLink can intercept."
echo ""
echo "  Make sure OmniLink's Accessibility"
echo "  Service is enabled."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "To re-enable Essential Space later, run:"
echo "  adb shell pm enable com.nothing.ntessentialspace"
echo "  adb shell pm enable com.nothing.ntessentialrecorder"
echo ""




