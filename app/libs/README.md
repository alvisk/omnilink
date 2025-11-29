# Nothing Glyph Matrix SDK Setup

## Overview
This folder should contain the Nothing Glyph Matrix SDK AAR file for controlling the LED matrix on the Nothing Phone 3.

## Setup Instructions

### 1. Download the SDK
Download `glyph-matrix-sdk-1.0.aar` from the official Nothing Developer Programme GitHub repository:
- **Repository:** https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit
- **Direct link:** Click "Code" → "Download ZIP" or clone the repository

### 2. Place the AAR File
Copy `glyph-matrix-sdk-1.0.aar` to this `libs/` folder:
```
app/libs/glyph-matrix-sdk-1.0.aar
```

### 3. Get Your API Key
1. Visit the Nothing Developer Programme: https://nothing.tech/pages/glyph-developer-kit
2. Register as a developer
3. Request an API key for your app

### 4. Configure the API Key
Update `AndroidManifest.xml` and replace `YOUR_NOTHING_API_KEY` with your actual key:
```xml
<meta-data
    android:name="NothingKey"
    android:value="YOUR_ACTUAL_API_KEY_HERE" />
```

## Glyph Matrix Specifications

- **Resolution:** 25x25 LED grid
- **Device:** Nothing Phone (3) - Device ID: 23112
- **SDK Classes:**
  - `GlyphMatrixManager` - Main manager for initialization and display
  - `GlyphMatrixFrame` - Frame container for display content
  - `GlyphMatrixObject` - Object wrapper for bitmaps/images

## Implementation

The NOMM app automatically:
1. Initializes the Glyph Matrix when accessibility service is enabled
2. Displays a breathing NOMM logo on the LED matrix
3. Clears the display when accessibility is disabled

### Logo Design
The logo is a 25x25 pixel design featuring:
- A stylized "N" pattern (representing NOMM)
- A central dot (the "AI eye" - awareness/intelligence)
- Cardinal accent dots (compass-like, showing direction/purpose)

## Troubleshooting

### SDK Not Found
If you see "Glyph Matrix SDK not found" in logs:
- Ensure the AAR file is in `app/libs/`
- Clean and rebuild the project
- Sync Gradle files

### Initialization Failed
If initialization fails:
- Verify your API key is correct in AndroidManifest.xml
- Ensure you're running on a Nothing Phone 3 (model 23112)
- Check that the device system version is 20250801 or later

## Resources

- [GlyphMatrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit)
- [GlyphMatrix Example Project](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Example-Project)
- [Nothing Developer Programme](https://nothing.tech/pages/glyph-developer-kit)
