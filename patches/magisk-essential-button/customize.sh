#!/system/bin/sh
# OmniLink Essential Button Magisk Module
# Customization script for Nothing Phone 3

SKIPUNZIP=0

# Print module info
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "   OmniLink Essential Button Remap"
ui_print "   For Nothing Phone 3"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print ""

# Check if device is Nothing Phone 3
DEVICE=$(getprop ro.product.device)
MODEL=$(getprop ro.product.model)

ui_print "- Device: $DEVICE"
ui_print "- Model: $MODEL"

if [[ "$DEVICE" != *"Pong"* ]] && [[ "$MODEL" != *"Phone (3)"* ]] && [[ "$MODEL" != *"A059"* ]]; then
    ui_print ""
    ui_print "⚠️  Warning: This module is designed for Nothing Phone 3"
    ui_print "   Your device may not be compatible"
    ui_print "   Proceeding anyway..."
    ui_print ""
fi

# Extract module files
ui_print "- Extracting module files..."
unzip -o "$ZIPFILE" -x 'META-INF/*' -d $MODPATH >&2

# Set permissions
ui_print "- Setting permissions..."
set_perm_recursive $MODPATH 0 0 0755 0644
set_perm $MODPATH/system/usr/keylayout/nothing_essential_key.kl 0 0 0644
set_perm $MODPATH/service.sh 0 0 0755
set_perm $MODPATH/post-fs-data.sh 0 0 0755

ui_print ""
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "   Installation Complete!"
ui_print ""
ui_print "   The Essential Button will now trigger"
ui_print "   the OmniLink overlay instead of"
ui_print "   Essential Space."
ui_print ""
ui_print "   Reboot to apply changes."
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"




