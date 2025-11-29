#!/system/bin/sh
# OmniLink Essential Button - Post FS Data Script
# Runs early in boot to disable Essential Space before it starts

MODDIR=${0%/*}

# Log function
log() {
    echo "[OmniLink] $1" >> /cache/omnilink_essential.log
    log -t "OmniLink" "$1"
}

log "Post-fs-data script running..."

# Disable Essential Space and Essential Recorder packages
# This prevents them from intercepting the Essential Button
pm disable com.nothing.ntessentialspace 2>/dev/null && log "Disabled ntessentialspace"
pm disable com.nothing.ntessentialrecorder 2>/dev/null && log "Disabled ntessentialrecorder"

log "Post-fs-data complete"




