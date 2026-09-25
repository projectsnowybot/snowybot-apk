# SnowyBot APK

This repository contains the Android project for the SnowyBot app, including the Gradle sources and a packaged APK artifact.

## Project layout

- `SnowyBotApp/` – Android Studio / Gradle project
- `snowybot.apk` – packaged app artifact

## Build

From the project directory:

```bash
cd SnowyBotApp
./gradlew assembleDebug
```

The app is configured for Android SDK 34 and uses an appcompat-based UI.
