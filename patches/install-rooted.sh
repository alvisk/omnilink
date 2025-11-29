#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════════════
# OmniLink Essential Button - One-Click Installer for Rooted Phones
# ═══════════════════════════════════════════════════════════════════════════════
#
# This script automatically:
# 1. Builds the Magisk module
# 2. Pushes it to your phone
# 3. Installs it via Magisk
# 4. Reboots (optional)
#
# Requirements:
# - Rooted Nothing Phone 3 with Magisk installed
# - USB Debugging enabled
# - ADB installed on computer
# ═══════════════════════════════════════════════════════════════════════════════

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$SCRIPT_DIR/magisk-essential-button"
VERSION="v1.0.0"
ZIP_NAME="OmniLink-Essential-Button-${VERSION}.zip"
PHONE_PATH="/sdcard/Download"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

print_banner() {
    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${CYAN}   ⚡ OmniLink Essential Button - One-Click Installer ⚡${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
}

check_adb() {
    echo -e "${YELLOW}[1/6]${NC} Checking ADB..."
    if ! command -v adb &> /dev/null; then
        echo -e "${RED}❌ ADB not found!${NC}"
        echo "   Please install Android SDK Platform Tools"
        echo "   Download: https://developer.android.com/tools/releases/platform-tools"
        exit 1
    fi
    echo -e "${GREEN}✅ ADB found${NC}"
}

check_device() {
    echo -e "${YELLOW}[2/6]${NC} Checking for connected device..."

    # Start ADB server
    adb start-server 2>/dev/null

    DEVICE=$(adb devices | grep -v "List" | grep -E "device$|recovery$" | head -1 | cut -f1)

    if [ -z "$DEVICE" ]; then
        echo -e "${RED}❌ No device connected!${NC}"
        echo ""
        echo "   Please ensure:"
        echo "   • USB Debugging is enabled"
        echo "   • Phone is connected via USB"
        echo "   • You've authorized this computer"
        exit 1
    fi

    MODEL=$(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r')
    echo -e "${GREEN}✅ Device: ${MODEL} (${DEVICE})${NC}"
}

check_root() {
    echo -e "${YELLOW}[3/6]${NC} Checking root access..."

    # Check if Magisk is installed
    MAGISK=$(adb shell "su -c 'magisk -v'" 2>/dev/null | tr -d '\r' || echo "")

    if [ -z "$MAGISK" ]; then
        echo -e "${RED}❌ Root/Magisk not detected!${NC}"
        echo ""
        echo "   This installer requires a rooted phone with Magisk."
        echo "   For non-rooted phones, use: ./adb-no-root.sh"
        exit 1
    fi

    echo -e "${GREEN}✅ Magisk ${MAGISK} detected${NC}"
}

build_module() {
    echo -e "${YELLOW}[4/6]${NC} Building Magisk module..."

    cd "$MODULE_DIR"

    # Remove old zip
    rm -f "$ZIP_NAME"

    # Create the zip
    zip -r "$ZIP_NAME" \
        module.prop \
        customize.sh \
        service.sh \
        post-fs-data.sh \
        system/ \
        META-INF/ \
        -x "*.DS_Store" \
        -x "build.sh" \
        -x "README.md" \
        -x "*.log" \
        > /dev/null 2>&1

    if [ ! -f "$ZIP_NAME" ]; then
        echo -e "${RED}❌ Failed to build module!${NC}"
        exit 1
    fi

    echo -e "${GREEN}✅ Module built: ${ZIP_NAME}${NC}"
}

push_and_install() {
    echo -e "${YELLOW}[5/6]${NC} Installing module..."

    # Push to phone
    echo "   Pushing to device..."
    adb push "$MODULE_DIR/$ZIP_NAME" "$PHONE_PATH/$ZIP_NAME" > /dev/null 2>&1

    # Install via Magisk
    echo "   Installing via Magisk..."
    adb shell "su -c 'magisk --install-module $PHONE_PATH/$ZIP_NAME'" 2>/dev/null

    INSTALL_RESULT=$?

    if [ $INSTALL_RESULT -ne 0 ]; then
        echo -e "${YELLOW}⚠️  Magisk CLI install may have issues, trying alternative...${NC}"
        # Alternative: Copy to Magisk modules directory
        adb shell "su -c 'mkdir -p /data/adb/modules/omnilink-essential-button'"
        adb shell "su -c 'unzip -o $PHONE_PATH/$ZIP_NAME -d /data/adb/modules/omnilink-essential-button/'"
    fi

    # Cleanup
    adb shell "rm -f $PHONE_PATH/$ZIP_NAME" 2>/dev/null

    echo -e "${GREEN}✅ Module installed${NC}"
}

prompt_reboot() {
    echo -e "${YELLOW}[6/6]${NC} Finalizing..."
    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${GREEN}   ✅ Installation Complete!${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo "   The Essential Button will now trigger OmniLink"
    echo "   instead of Essential Space."
    echo ""
    echo -e "${YELLOW}   ⚠️  A reboot is required to apply changes.${NC}"
    echo ""

    read -p "   Reboot now? (y/N): " -n 1 -r
    echo ""

    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "   Rebooting device..."
        adb reboot
        echo ""
        echo -e "${GREEN}   Device is rebooting. Please wait...${NC}"
    else
        echo ""
        echo "   Please reboot manually when ready."
    fi

    echo ""
}

# ═══════════════════════════════════════════════════════════════════════════════
# Main
# ═══════════════════════════════════════════════════════════════════════════════

print_banner
check_adb
check_device
check_root
build_module
push_and_install
prompt_reboot




