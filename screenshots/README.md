# 📸 NOMM Screenshot Automation

Automated screenshot capture for app walkthrough documentation.

## Quick Start

### Option 1: Maestro (Recommended - Fully Automated)

```bash
# Install Maestro (one-time)
curl -Ls "https://get.maestro.mobile.dev" | bash

# Connect your Nothing Phone via USB

# Run full walkthrough (captures all screens automatically)
maestro test .maestro/walkthrough.yaml

# Or just onboarding screens
maestro test .maestro/onboarding-only.yaml
```

Screenshots are saved to `~/.maestro/tests/{test_id}/`

### Option 2: Interactive Shell Script

```bash
# Navigate through app manually, press Enter to capture each screen
./scripts/capture-screenshots.sh

# Or auto-capture every 3 seconds while you navigate
./scripts/capture-screenshots.sh --auto
```

### Option 3: Quick Single Screenshot

```bash
./scripts/quick-screenshot.sh                    # Auto-named
./scripts/quick-screenshot.sh "main_chat"        # Custom name
```

## Screens Captured

| # | Screen | Description |
|---|--------|-------------|
| 1 | `onboarding_welcome` | NOMM intro with Möbius animation |
| 2 | `onboarding_ondevice_ai` | On-device AI explanation |
| 3 | `onboarding_screen_aware` | Screen context feature |
| 4 | `onboarding_glyph_matrix` | LED matrix feature |
| 5 | `main_chat_empty` | Empty chat screen |
| 6 | `permissions_sheet` | Accessibility permissions |
| 7 | `model_settings` | AI model configuration |
| 8 | `glyph_settings` | LED animation picker |
| 9 | `chat_with_input` | Chat with typed message |
| 10 | `chat_with_response` | Chat showing AI response |

## Requirements

- **ADB**: Android Debug Bridge (comes with Android Studio)
- **USB Debugging**: Enabled on your Nothing Phone
- **Maestro** (optional): For fully automated capture

## Tips

1. **Fresh install**: Maestro uses `clearState` to show onboarding
2. **Animations**: Scripts wait for animations to settle
3. **Device**: Works best on actual Nothing Phone for authentic Glyph previews
4. **Resolution**: Screenshots are captured at device native resolution

## Troubleshooting

```bash
# Check device connection
adb devices

# Restart ADB if needed
adb kill-server && adb start-server

# Manual screenshot fallback
adb shell screencap -p /sdcard/screen.png && adb pull /sdcard/screen.png
```

