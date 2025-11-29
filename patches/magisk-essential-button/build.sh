#!/bin/bash
# Build script for OmniLink Essential Button Magisk Module
# Creates a flashable zip file

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VERSION="v1.0.0"
OUTPUT_NAME="OmniLink-Essential-Button-${VERSION}.zip"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Building OmniLink Essential Button Module"
echo "  Version: ${VERSION}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

cd "$SCRIPT_DIR"

# Remove old zip if exists
rm -f "$OUTPUT_NAME"

# Create the zip with proper structure
echo "Creating flashable zip..."
zip -r "$OUTPUT_NAME" \
    module.prop \
    customize.sh \
    service.sh \
    post-fs-data.sh \
    system/ \
    META-INF/ \
    -x "*.DS_Store" \
    -x "build.sh" \
    -x "README.md" \
    -x "*.log"

echo ""
echo "✅ Build complete: $OUTPUT_NAME"
echo ""
echo "To install:"
echo "  1. Copy $OUTPUT_NAME to your phone"
echo "  2. Open Magisk Manager → Modules → Install from storage"
echo "  3. Select the zip and reboot"
echo ""


