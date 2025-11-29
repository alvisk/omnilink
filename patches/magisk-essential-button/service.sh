#!/system/bin/sh
# OmniLink Essential Button - Service Script
# Runs after boot completes

MODDIR=${0%/*}

# Wait for boot to complete
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 1
done

# Additional delay to ensure all services are started
sleep 5

# Log function
log() {
    echo "[OmniLink] $1" >> /cache/omnilink_essential.log
}

log "Service script running..."

# Ensure Essential Space stays disabled
pm disable-user --user 0 com.nothing.ntessentialspace 2>/dev/null
pm disable-user --user 0 com.nothing.ntessentialrecorder 2>/dev/null

# Grant OmniLink necessary permissions if installed
OMNILINK_PKG="com.example.omni_link"
if pm list packages | grep -q "$OMNILINK_PKG"; then
    log "OmniLink found, ensuring permissions..."

    # Grant overlay permission
    appops set $OMNILINK_PKG SYSTEM_ALERT_WINDOW allow 2>/dev/null

    log "OmniLink permissions configured"
fi

log "Service script complete"


