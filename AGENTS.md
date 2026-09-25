# Codex Working Notes

## Project Context

This repository contains an Android/Kotlin/Jetpack Compose app for controlling Realme Buds, currently focused on `realme Buds Air 5 Pro` over the OPO RFCOMM channel.

The app is no longer just a diagnostic prototype. Treat it as a product moving toward a stable daily-use release:

- keep normal user flows separate from Lab/reverse-engineering tools
- prefer typed domain commands over raw packet calls in UI code
- preserve protocol safety; do not add firmware update support
- validate behavior on the connected phone whenever Bluetooth or UI behavior changes

## Communication

- The user prefers Polish. Keep final summaries concise and concrete.
- During execution, make reasonable product and engineering decisions locally.
- Ask only when a decision has major product consequences or cannot be discovered locally.
- If Spokenly MCP `ask_user_dictation` is available, use it for questions instead of plain text. If it is unavailable, avoid unnecessary questions and proceed with the safest assumption.

## Current Stable Direction

The stable release should focus on:

- reliable connection and reconnect behavior
- accurate battery display, including cached case battery with age
- confirmed ANC switching
- confirmed gesture configuration
- EQ preset read/write once packet behavior is validated
- a readable Material 3 / Material Expressive-style dashboard
- a separate Lab screen for raw HEX, BLE diagnostics and packet logs
- Android integrations that are genuinely useful: Quick Settings Tile, battery notification, and later a battery widget
- release-ready documentation, build steps, test checklist and known limitations

Do not count a feature as stable until it has:

- typed state and ViewModel command flow
- protocol parser/builder coverage when applicable
- visible status/error handling in UI
- log output useful for ADB debugging
- manual validation on the real device when hardware behavior is involved

## Implementation Style

- Use existing Kotlin and Compose patterns in the repo.
- Keep state in domain models such as `DeviceState`, `BatteryState`, `AncState`, `TouchConfig`, `EqState`, `LabState` and `AppSettingsState`.
- Keep UI actions flowing through `BudsCommand` or ViewModel methods rather than raw packet calls from composables.
- Keep `OpoProtocol` responsible for packet building/parsing.
- Keep `OpoBluetoothClient` responsible for Bluetooth transport, queues, reconnect, refresh and low-level diagnostics.
- Keep Lab-only functionality clearly labeled and isolated.
- Avoid broad refactors unless they directly reduce release risk.

## Safety Rules

- Firmware update stays out of scope.
- Unknown write packets belong in Lab only and must be documented before becoming normal UI.
- Do not remove existing user changes or reset the worktree.
- When adding Bluetooth features, handle missing permissions, no bonded device, missing OPO UUID, timeout and disconnect.
- If a feature depends on a packet that has not been confirmed by logs, mark it experimental.

## Local Build Environment

Known working environment on this machine:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
```

Preferred verification commands:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Install on connected phone:

```bash
/opt/homebrew/share/android-commandlinetools/platform-tools/adb install -r \
app/build/outputs/apk/debug/app-debug.apk
```

Useful ADB checks:

```bash
/opt/homebrew/share/android-commandlinetools/platform-tools/adb devices
/opt/homebrew/share/android-commandlinetools/platform-tools/adb logcat -s RealmeBuds
/opt/homebrew/share/android-commandlinetools/platform-tools/adb shell am start -n dev.vibe.realmebuds/.MainActivity
```

## Manual QA Priorities

Before calling a release stable, test on the phone with the real earbuds:

- grant permissions from a fresh install
- connect to paired `realme Buds Air 5 Pro`
- initial sync reads firmware, battery, ANC/EQ state where available
- battery L/R updates, case battery persists when case is unavailable and shows age
- ANC, transparency and normal modes switch correctly
- gestures apply and remain after reconnect
- EQ preset behavior is confirmed or clearly kept experimental
- disconnect/reconnect works without app restart
- auto-connect behaves correctly on app launch
- Lab log export contains useful TX/RX context
- Quick Settings Tile and notification behavior match their documented scope
- no crash appears in `adb logcat`

