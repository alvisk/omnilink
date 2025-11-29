#!/bin/bash
# =============================================================================
# NOMM Screenshot Capture Script
# =============================================================================
#
# Simple ADB-based screenshot capture with automatic naming.
# Use this for manual walkthrough or when Maestro isn't available.
#
# USAGE:
#   ./scripts/capture-screenshots.sh           # Interactive mode
#   ./scripts/capture-screenshots.sh --auto    # Auto-capture every 3 seconds
#
# =============================================================================

# Add Android SDK platform-tools to PATH
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"

set -e

# Configuration
OUTPUT_DIR="$(dirname "$0")/../screenshots/walkthrough_$(date +%Y%m%d_%H%M%S)"
DEVICE_PATH="/sdcard/nomm_screenshot.png"
COUNTER=1

# Colors for terminal output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Create output directory
mkdir -p "$OUTPUT_DIR"

echo -e "${CYAN}"
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║           NOMM Screenshot Capture Tool                        ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# Check ADB connection
if ! adb devices | grep -q "device$"; then
    echo -e "${RED}❌ No device connected. Please connect your Nothing Phone via USB.${NC}"
    echo ""
    echo "Troubleshooting:"
    echo "  1. Enable USB debugging in Developer Options"
    echo "  2. Trust this computer on your phone"
    echo "  3. Run: adb devices"
    exit 1
fi

DEVICE_MODEL=$(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r')
echo -e "${GREEN}✅ Connected to: ${DEVICE_MODEL}${NC}"
echo -e "${BLUE}📁 Screenshots will be saved to: ${OUTPUT_DIR}${NC}"
echo ""

# Function to capture screenshot
capture() {
    local name="${1:-screen}"
    local filename=$(printf "%02d_%s.png" "$COUNTER" "$name")

    echo -e "${YELLOW}📸 Capturing: ${filename}${NC}"

    # Take screenshot on device
    adb shell screencap -p "$DEVICE_PATH"

    # Pull to local machine
    adb pull "$DEVICE_PATH" "$OUTPUT_DIR/$filename" > /dev/null 2>&1

    # Clean up device
    adb shell rm "$DEVICE_PATH"

    echo -e "${GREEN}   ✓ Saved: $OUTPUT_DIR/$filename${NC}"

    ((COUNTER++))
}

# Auto-capture mode
if [[ "$1" == "--auto" ]]; then
    echo -e "${CYAN}🔄 Auto-capture mode: Taking screenshot every 3 seconds${NC}"
    echo -e "${YELLOW}   Navigate through the app. Press Ctrl+C to stop.${NC}"
    echo ""

    trap "echo -e '\n${GREEN}✅ Captured $((COUNTER-1)) screenshots to $OUTPUT_DIR${NC}'; exit 0" INT

    while true; do
        capture "auto"
        sleep 3
    done
fi

# Interactive mode
echo -e "${CYAN}📱 Interactive Mode - Suggested Walkthrough:${NC}"
echo ""
echo "  1. Onboarding - Welcome"
echo "  2. Onboarding - On-Device AI"
echo "  3. Onboarding - Screen-Aware"
echo "  4. Onboarding - Glyph Matrix"
echo "  5. Main Chat (empty)"
echo "  6. Permissions Sheet"
echo "  7. Model Settings"
echo "  8. Glyph Settings"
echo "  9. Chat with message"
echo "  10. Chat with response"
echo ""
echo -e "${YELLOW}Commands:${NC}"
echo "  [Enter]     - Capture with auto-name"
echo "  [name]      - Capture with custom name"
echo "  [q]         - Quit"
echo ""

# Suggested screen names
SCREENS=(
    "onboarding_welcome"
    "onboarding_ondevice_ai"
    "onboarding_screen_aware"
    "onboarding_glyph_matrix"
    "main_chat_empty"
    "permissions_sheet"
    "model_settings"
    "glyph_settings"
    "chat_with_input"
    "chat_with_response"
)

while true; do
    # Suggest next screen name
    SUGGESTED="${SCREENS[$((COUNTER-1))]:-screen}"

    echo -e -n "${BLUE}[$COUNTER] Capture '${SUGGESTED}'? (Enter/name/q): ${NC}"
    read -r input

    case "$input" in
        q|Q|quit|exit)
            echo ""
            echo -e "${GREEN}✅ Captured $((COUNTER-1)) screenshots${NC}"
            echo -e "${BLUE}📁 Location: $OUTPUT_DIR${NC}"

            # Open folder on macOS
            if [[ "$OSTYPE" == "darwin"* ]]; then
                open "$OUTPUT_DIR"
            fi
            exit 0
            ;;
        "")
            capture "$SUGGESTED"
            ;;
        *)
            capture "$input"
            ;;
    esac
    echo ""
done
