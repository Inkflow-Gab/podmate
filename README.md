# PodMate

[![Android CI](https://github.com/Inkflow-Gab/podmate/actions/workflows/ci-android.yml/badge.svg)](https://github.com/Inkflow-Gab/podmate/actions/workflows/ci-android.yml)

PodMate lets you use AirPods features that are normally exclusive to Apple devices, on Android:
changing listening modes, ear detection, accurate battery status, head gestures, conversational
awareness, custom equalizer, and more.

It is a fork of the excellent **[LibrePods](https://github.com/kavishdevar/librepods)** project by
[@kavishdevar](https://github.com/kavishdevar) and its contributors. PodMate is **not** affiliated
with the LibrePods project or with Apple. The name and logo are different on purpose, per the
upstream project's trademark notice.

## Releases (APK only)

Every push to `main` is built **automatically on GitHub Actions** and published on the
[Releases page](https://github.com/Inkflow-Gab/podmate/releases/latest) as a single signed APK:

> `PodMate-vX.Y.Z-<versionCode>-release.apk`

- The APK is signed with a release keystore that lives in the repo Actions secrets, so **updates
  install directly over previous releases** (no uninstall needed).
- Builds happen only on GitHub. Nothing runs on your phone, no local build required.

## New in this fork

- 🌗 **New app icon** (fresh PodMate branding).
- ⬆️ **In-app update checker** — *Settings → PodMate → App updates* tells you when a newer release
  is published on GitHub and links straight to the APK.
- 🪫 **Battery history** — AirPods battery levels are recorded in the background and drawn as a
  chart over the last 24 hours.
- 💾 **Settings backup & restore** — export all app settings to a file and restore them on any
  device.
- 🎚️ **Equalizer presets** — save your custom 3-band EQ as named presets, apply them with one tap,
  and export/import presets as JSON.

## Root / Xposed requirement

Same as upstream LibrePods: depending on your device and Android version, root + Xposed
(LSPosed) is needed to hook the Bluetooth stack. See the
[upstream readme](https://github.com/kavishdevar/librepods#readme) for the details on which
devices do/don't need it.

## Building from source

```bash
cd android
./gradlew assembleFossRelease
```

Requires JDK 21 and the Android SDK/NDK (NDK 30.0.14904198, CMake 3.22.1, compileSdk 37).
Signing configuration (optional, read from `local.properties`):

```
RELEASE_STORE_FILE=../release.keystore
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

## License

GPL-3.0-or-later — same as upstream LibrePods, which this fork is derived from. The original
project's source and licensing: https://github.com/kavishdevar/librepods

AirPods, AirPods Pro, and AirPods Max are trademarks of Apple Inc. PodMate is not affiliated with
or endorsed by Apple Inc. in any way.
