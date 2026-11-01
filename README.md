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

### v1.3.0 — no root, ever (works with normal & clone AirPods)

- 🚫 **Root is gone from the normal flow.** PodMate used to shell out to
  `su -c "settings get secure bluetooth_address"`, which popped a root prompt the
  moment AirPods connected (and did nothing on a phone without root). The local
  Bluetooth address is now resolved rootlessly (cached value → privileged
  permission → Shizuku → reflection → `Settings.Secure` → `bt-pan` interface),
  and if it can't be read the few packets that need it are simply skipped.
- 🔌 **Better rootless connection attempts.** The Apple control channel is opened
  with up to 3 timed attempts instead of one 5-second blocking `connect()`, so a
  channel that is busy right after pairing now succeeds, and a hanging attempt
  can never freeze the app. Every failure reason is recorded for
  *Settings → PodMate → Connection diagnostics*.
- 🔋 **Limited mode now actually engages.** It used to require an Apple BLE
  advertisement *and* a live status packet; earbuds that advertise nothing
  (most clones) left the app on "not connected" forever. Limited mode is now
  triggered by the normal A2DP/HEADSET connection (with a 7-second safety net
  after the control channel fails) and reads battery from the standard GATT
  Battery Service — no root, no Magisk, no LSPosed, no Shizuku.
- 🎛️ **Limited mode is a real screen now:** live battery, media controls
  (previous / play-pause / next), working battery-history link, "try the full
  AirPods connection again" button and a shortcut to Bluetooth settings.
- 🎧 **Non-Apple earbuds.** Detection recognises more names (AirPods, AirPods
  clones, "…Pods", TWS-style names) and there is a *Compatibility → Treat any
  earbuds as AirPods* switch for everything else, so battery, popups, widgets
  and history work for ordinary Bluetooth earbuds too.
- 🐞 **Fixed the "full mode vs limited mode" mix-up.** `AIRPODS_CONNECTED` and
  `AIRPODS_L2CAP_CONNECTED` were the *same* broadcast string, so any connection
  was treated as "full control mode" and the battery-only UI never appeared.
  They are distinct now.
- 🔓 **Onboarding no longer blocks you.** No "device not supported" wall and no
  typing `OK` to bypass it — a phone without the Xposed hook simply starts in
  limited mode.
- 📷 **Camera shutter without root:** it goes through Shizuku (`input keyevent
  27`) first; `su` is only used if you explicitly enable *Allow root helpers*.

The old root module, LSPosed hook and VendorID extras still exist for people who
want the last few Apple-only features — but nothing in the normal flow needs them.

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

## Root / Xposed — optional, never required

**PodMate does not need root to run.** Install the APK, grant the Nearby-devices permission,
connect your earbuds, done.

What you get with **no root at all**:

- automatic detection of the earbuds (real AirPods, clones and, with the compatibility switch,
  any Bluetooth earbuds),
- battery level per bud/case from Apple's BLE advertisements, or from the standard Bluetooth
  Battery Service when the earbuds don't send those,
- battery history graph, notification, home-screen widgets, Quick Settings tile and the
  connection popup,
- media controls, in-app update checker, settings backup/restore, equalizer presets.

What still needs a phone whose Bluetooth stack accepts Apple's private control channel
(Android 17, Pixel on 16 QPR3, ColorOS/OxygenOS 16, realme UI 7 — or the optional LSPosed hook
on older devices): listening modes (ANC/transparency/adaptive), ear detection, head gestures,
conversational awareness and the Apple-only audio tweaks. On other phones those simply show as
unavailable and PodMate stays in limited mode instead of failing silently.

Why: Android's L2CAP client negotiates a flow-control/ERTM mode that AirPods refuse, so the
channel is closed right after it opens ([Google issue 371713238](https://issuetracker.google.com/issues/371713238),
fixed in Android 17). Patching that inside the Bluetooth stack is the one thing an app can't do
without root — and PodMate no longer asks you for it. See the
[upstream readme](https://github.com/kavishdevar/librepods#readme) for the device list.

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
