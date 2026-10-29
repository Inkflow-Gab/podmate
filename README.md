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

### v1.2.2 — rootless battery via the standard GATT Battery Service

- 🔋 **GATT Battery Service fallback (new).** When no Apple BLE status ads are seen at all
  (many clones send *nothing* on Apple's channel), PodMate now also connects to the earbuds'
  **standard Bluetooth Battery Service** (0x180F/0x2A19) — the exact mechanism Android's own
  quick settings uses. Works on any phone with the normal Nearby-connect permission:
  **no root, no Magisk, no LSPosed, no Shizuku needed.**
- 🛠️ The battery-only fallback also now triggers reliably even when clone AirPods never
  broadcast Apple's status message, and hands off cleanly to full mode if a partly-compatible
  clone *does* let the control channel connect later.

### v1.2.1 — battery-only mode for clone AirPods & non-rooted phones

- 🔋 **Battery-only ("limited") mode.** If Apple's control channel can't be established — the
  normal case with **clone/"fake" AirPods bought online**, and on unrooted phones whose Bluetooth
  stack needs the root workaround — the app now **falls back to battery-only mode** instead of
  staying on "not connected" forever: device detected, live battery, battery history, widgets and
  the connection popup all work using the BLE broadcasts that clones actually do implement.
  Advanced controls (ANC etc.) are honestly marked as unavailable rather than silently broken.
- ⚠️ Please note: **"AirPods" bought online are very often clones** that skip most of Apple's
  proprietary protocol on purpose; software can't make them support ANC control, ear detection etc.

### v1.2.0 — works on every phone, with or without root

- 🔍 **Reliable connection detection.** The original app only detected AirPods when Android's
  Bluetooth SDP cache already contained AirPods' UUID at connect time — which often isn't the
  case on many phones, so the app "didn't detect anything" without Xposed. Detection now also
  uses device-name recognition ("AirPods" contains-check), previously-seen MAC addresses, the
  follow-up UUID broadcast after SDP refresh, and the A2DP/HEADSET profile connection broadcasts
  (the most reliable signal on every Android version). Disconnects are detected the same way,
  so the UI no longer gets stuck in "connected" state on phones without the Xposed hook.
- 🐫 **Shizuku support** — *Settings → PodMate → Extended Bluetooth access*. If you don't have
  root but have [Shizuku](https://shizuku.rikka.app) running, PodMate can:
  - read the local Bluetooth MAC without root (needed for some features),
  - grant the Nearby-connect / Notifications permissions via `pm grant` when system dialogs
    misbehave on some OEM ROMs,
  - enable the screen-overlay permission through `appops` so the AirPods popup can show over
    other apps.
- 🩺 **Connection diagnostics** — *Settings → PodMate → Troubleshooting* shows exactly what the
  app can see: Bluetooth on/off, whether AirPods are bonded, permission state, whether the
  data channel has ever worked, and which privilege path (Xposed / Shizuku / none) is active.
- Works **fully without root** wherever the Android Bluetooth stack already cooperates
  (Pixel on Android 16 QPR3+, ColorOS/OxygenOS 16, realme UI 7 — see the upstream readme).
  On phones where the stack bug applies, detection + battery + popups now work without root,
  while ANC/advanced control still needs the known workarounds (root + Xposed, or Android 17).

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
