# 🚀 NOMM (Nothing On My Mind) Production Build Guide

## Prerequisites

- Android Studio (latest stable)
- JDK 17
- Nothing Phone 3 (or emulator with API 34+)

---

## 🔐 Step 1: Generate Release Keystore

Open a terminal and run:

```bash
keytool -genkey -v \
  -keystore nomm-release.keystore \
  -alias nomm \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

You'll be prompted for:
- Keystore password (remember this!)
- Your name, organization, location info
- Key password (can be same as keystore password)

Move the generated `nomm-release.keystore` to the project root.

---

## 🔑 Step 2: Configure Signing

Copy the template and fill in your credentials:

```bash
cp keystore.properties.template keystore.properties
```

Edit `keystore.properties`:

```properties
storeFile=nomm-release.keystore
storePassword=YOUR_KEYSTORE_PASSWORD
keyAlias=nomm
keyPassword=YOUR_KEY_PASSWORD
```

⚠️ **NEVER commit `keystore.properties` or your `.keystore` file!**

---

## 💫 Step 3: Configure Nothing API Key

Edit `app/src/main/AndroidManifest.xml` and replace `YOUR_NOTHING_API_KEY`:

```xml
<meta-data
    android:name="NothingKey"
    android:value="YOUR_ACTUAL_NOTHING_API_KEY" />
```

Get your key from: https://nothing.tech/pages/glyph-developer-kit

---

## 🏗️ Step 4: Build Release APK

### Option A: Using Android Studio

1. Open the project in Android Studio
2. Go to **Build → Select Build Variant**
3. Select `release` variant
4. Go to **Build → Build Bundle(s) / APK(s) → Build APK(s)**
5. APK will be at: `app/build/outputs/apk/release/app-release.apk`

### Option B: Using Command Line

```bash
# Clean and build release APK
./gradlew clean assembleRelease

# The signed APK will be at:
# app/build/outputs/apk/release/app-release.apk
```

---

## 📦 Step 5: Build App Bundle (for Play Store)

```bash
./gradlew bundleRelease

# The AAB will be at:
# app/build/outputs/bundle/release/app-release.aab
```

---

## ✅ Verify Your APK

Check that the APK is signed correctly:

```bash
# Verify signature
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk

# Check APK contents
aapt dump badging app/build/outputs/apk/release/app-release.apk | grep -E "package|versionCode|versionName"
```

---

## 📱 Install on Device

```bash
adb install app/build/outputs/apk/release/app-release.apk
```

---

## 🎯 Production Checklist

- [ ] Generated and secured release keystore
- [ ] Configured `keystore.properties` (not committed to git)
- [ ] Added Nothing API key to `AndroidManifest.xml`
- [ ] Updated `versionCode` and `versionName` in `build.gradle.kts`
- [ ] Tested on actual Nothing Phone 3
- [ ] Verified Glyph Matrix animations work
- [ ] Verified Accessibility Service permissions
- [ ] Tested with ProGuard/R8 minification enabled

---

## 🔧 Troubleshooting

### Build fails with signing error
Make sure `keystore.properties` exists and paths are correct.

### ProGuard removes too much
Check `app/proguard-rules.pro` and add keep rules for any missing classes.

### Glyph SDK not working
Ensure you have a valid Nothing API key and are testing on a Nothing Phone 3.

### APK size too large
The arm64-v8a filter is already applied. Consider using App Bundle for Play Store distribution.
