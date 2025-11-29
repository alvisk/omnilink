# OmniLink Essential Button - Installation Guide

## 🚀 One-Click Install (Rooted Phones)

```bash
cd patches
chmod +x install-rooted.sh
./install-rooted.sh
```

That's it! The script will:
1. ✅ Check your device connection
2. ✅ Verify Magisk/root access
3. ✅ Build the module
4. ✅ Push & install automatically
5. ✅ Prompt to reboot

---

## 📱 Requirements

### For Rooted Install
- Nothing Phone 3 / 3a
- Magisk v20.4+ installed
- USB Debugging enabled
- ADB on computer

### For Non-Rooted Install
- Nothing Phone 3 / 3a
- USB Debugging enabled
- ADB on computer

---

## 🔧 Alternative Methods

### Method A: Manual Magisk Install
```bash
cd patches/magisk-essential-button
chmod +x build.sh
./build.sh
```
Then flash `OmniLink-Essential-Button-v1.0.0.zip` via Magisk Manager.

### Method B: ADB Only (No Root)
```bash
chmod +x patches/adb-no-root.sh
./adb-no-root.sh
```

---

## ❓ Troubleshooting

### "ADB not found"
Install Android Platform Tools:
- macOS: `brew install android-platform-tools`
- Linux: `sudo apt install adb`
- Windows: Download from Google

### "No device connected"
1. Enable USB Debugging: Settings → Developer Options → USB Debugging
2. Connect USB cable
3. Accept the authorization popup on phone

### "Root/Magisk not detected"
- Ensure Magisk is properly installed
- Try: `adb shell su -c 'id'` to test root
- Use the non-rooted ADB method instead

### Button still opens Essential Space
1. Check module is enabled in Magisk Manager
2. Try rebooting again
3. Check logs: `adb logcat | grep OmniLink`

---

## 🔄 Uninstall

### Rooted (Magisk)
1. Open Magisk Manager
2. Go to Modules
3. Remove "OmniLink Essential Button Remap"
4. Reboot

### Non-Rooted (ADB)
```bash
adb shell pm enable com.nothing.ntessentialspace
adb shell pm enable com.nothing.ntessentialrecorder
```


