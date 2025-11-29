# OmniLink Essential Button Magisk Module

Remaps the Nothing Phone 3 **Essential Button** to trigger the OmniLink overlay instead of launching Essential Space.

## Requirements

- **Nothing Phone 3** (Phone 3/3a)
- **Rooted** with Magisk v20.4+
- **OmniLink** app installed

## Installation

### Method 1: Flash via Magisk Manager
1. Download `OmniLink-Essential-Button-v1.0.0.zip`
2. Open **Magisk Manager**
3. Go to **Modules** → **Install from storage**
4. Select the downloaded zip
5. **Reboot** your device

### Method 2: Flash via Recovery
1. Download `OmniLink-Essential-Button-v1.0.0.zip`
2. Boot into recovery (TWRP recommended)
3. Flash the zip
4. Reboot

## What This Module Does

1. **Disables Essential Space** - Prevents the stock app from intercepting the button
2. **Remaps the Essential Button** - Maps the button's scancode to a keycode OmniLink can detect
3. **Grants permissions** - Ensures OmniLink has overlay permissions

## How It Works

The Essential Button sends a hardware scancode when pressed. By default, Nothing's `Essential Space` app intercepts this. This module:

1. Disables `com.nothing.ntessentialspace` and `com.nothing.ntessentialrecorder`
2. Installs a custom key layout file (`nothing_essential_key.kl`)
3. Maps the button's scancode to `KEYCODE_UNKNOWN` (0)
4. OmniLink's accessibility service detects this keycode and toggles the overlay

## Troubleshooting

### Button not working?

1. **Check Magisk module is enabled**
   - Open Magisk Manager → Modules
   - Ensure "OmniLink Essential Button Remap" is checked

2. **Verify Essential Space is disabled**
   ```bash
   adb shell pm list packages -d | grep nothing
   ```
   Should show `com.nothing.ntessentialspace` as disabled

3. **Check the scancode**
   - Enable USB debugging
   - Run: `adb shell getevent -l`
   - Press the Essential Button
   - Note the scancode (e.g., `KEY_UNKNOWN` or a hex value)
   - Update the `.kl` file if needed

4. **Check logs**
   ```bash
   adb shell cat /cache/omnilink_essential.log
   adb logcat | grep -i "OmniService\|Essential"
   ```

### Finding the correct scancode

If the default scancode (703) doesn't work:

```bash
# Watch for key events
adb shell getevent -l

# Press the Essential Button and look for output like:
# /dev/input/eventX: EV_KEY KEY_UNKNOWN DOWN
# or
# /dev/input/eventX: EV_KEY 02bf DOWN (hex scancode)
```

Then edit `/system/usr/keylayout/nothing_essential_key.kl` with the correct scancode.

## Uninstallation

1. Open **Magisk Manager**
2. Go to **Modules**
3. Find "OmniLink Essential Button Remap"
4. Tap the **trash icon** to remove
5. **Reboot**

Essential Space will be re-enabled after uninstalling.

## Manual Alternative (No Root)

If you don't have root, you can disable Essential Space via ADB:

```bash
# Connect phone via USB with USB debugging enabled
adb shell pm disable-user --user 0 com.nothing.ntessentialspace
adb shell pm disable-user --user 0 com.nothing.ntessentialrecorder
```

To re-enable:
```bash
adb shell pm enable com.nothing.ntessentialspace
adb shell pm enable com.nothing.ntessentialrecorder
```

## Technical Details

### Key Layout File Location
```
/system/usr/keylayout/nothing_essential_key.kl
```

### Scancode Mapping
```
key 703   UNKNOWN    # Essential Button -> KEYCODE_UNKNOWN (0)
```

### Relevant Packages
- `com.nothing.ntessentialspace` - Essential Space app
- `com.nothing.ntessentialrecorder` - Essential Recorder
- `com.example.omni_link` - OmniLink app

## License

MIT License - Part of the OmniLink project

## Credits

- OmniLink Team
- Magisk by topjohnwu
- Nothing Community for scancode research




