# OmniLink Patches for Nothing Phone 3

This directory contains patches and modules to enable OmniLink's Essential Button integration on Nothing Phone 3.

## Available Patches

### 1. Magisk Module (Rooted Phones)
**Location:** `magisk-essential-button/`

A Magisk module that:
- Disables Essential Space app
- Installs a custom key layout file
- Automatically configures on every boot

**Installation:**
```bash
cd magisk-essential-button
./build.sh
# Flash the generated zip via Magisk Manager
```

### 2. ADB Script (No Root Required)
**Location:** `adb-no-root.sh`

A simple ADB script that disables Essential Space without requiring root.

**Usage:**
```bash
chmod +x adb-no-root.sh
./adb-no-root.sh
```

**Note:** This method needs to be re-run after factory reset.

## How the Essential Button Integration Works

```
┌─────────────────────────────────────────────────────────┐
│                  Nothing Phone 3                         │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  Essential Button Press                                  │
│         │                                                │
│         ▼                                                │
│  ┌─────────────────┐                                    │
│  │ Hardware Event  │ Scancode 703 (0x2bf)               │
│  └────────┬────────┘                                    │
│           │                                              │
│           ▼                                              │
│  ┌─────────────────┐    ┌───────────────────────┐       │
│  │ Key Layout File │───▶│ KEYCODE_UNKNOWN (0)   │       │
│  └─────────────────┘    └───────────┬───────────┘       │
│                                     │                    │
│           ┌─────────────────────────┘                   │
│           │                                              │
│           ▼                                              │
│  ┌─────────────────────────────────────────────┐        │
│  │         Essential Space (DISABLED)          │        │
│  │         ❌ Cannot intercept                  │        │
│  └─────────────────────────────────────────────┘        │
│                                                          │
│           │                                              │
│           ▼                                              │
│  ┌─────────────────────────────────────────────┐        │
│  │      OmniLink Accessibility Service          │        │
│  │      ✅ onKeyEvent() captures button         │        │
│  │      ✅ Toggles overlay                      │        │
│  │      ✅ Flashes Glyph Matrix                 │        │
│  └─────────────────────────────────────────────┘        │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

## Troubleshooting

### Finding the Essential Button Scancode

If the default scancode doesn't work on your device:

```bash
# Enable event logging
adb shell getevent -l

# Press the Essential Button
# Look for output like:
# /dev/input/event2: EV_KEY       KEY_UNKNOWN          DOWN
# /dev/input/event2: EV_KEY       KEY_UNKNOWN          UP

# Or with hex codes:
# /dev/input/event2: 0001 02bf 00000001  (DOWN)
# /dev/input/event2: 0001 02bf 00000000  (UP)
```

The `02bf` (703 in decimal) is the scancode. Update the key layout file if different.

### Checking if Essential Space is Disabled

```bash
adb shell pm list packages -d | grep nothing
# Should show:
# package:com.nothing.ntessentialspace
# package:com.nothing.ntessentialrecorder
```

### Viewing OmniLink Logs

```bash
# All logs
adb logcat | grep -i "OmniService"

# Key event logs specifically
adb logcat | grep "Key event"
```

## Restoring Essential Space

### If using ADB method:
```bash
adb shell pm enable com.nothing.ntessentialspace
adb shell pm enable com.nothing.ntessentialrecorder
```

### If using Magisk module:
1. Open Magisk Manager
2. Go to Modules
3. Remove "OmniLink Essential Button Remap"
4. Reboot

## Legal Notice

This patch is provided for educational purposes. Use at your own risk. Modifying system behavior may void warranty or cause instability. Always backup your data before making system modifications.


