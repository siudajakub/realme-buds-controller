# Realme Buds Controller Stable Release Plan

## Goal

Bring Realme Buds Controller from the current validated debug build to a stable daily-use release for `realme Buds Air 5 Pro`.

Stable means:

- the main dashboard covers the everyday controls without requiring Lab
- hardware behavior is validated on a real phone and earbuds
- unsupported or unconfirmed protocol behavior remains isolated in Lab
- connection, battery, ANC, gestures and EQ have clear state and error handling
- Android integrations are useful but do not make the app fragile
- the project can produce repeatable debug and release builds

Firmware update is explicitly out of scope.

## Current Status

Already implemented and at least partially validated:

- RFCOMM connection over OPO UUID
- initial sync after connection
- firmware display
- battery L/R and case display
- cached case battery with age when the case is unavailable
- ANC mode switching
- gesture setting flow
- Material 3 dashboard with Lab and Settings tabs
- Dynamic Color theme
- raw HEX and BLE diagnostics in Lab
- log export from Lab
- auto-connect setting
- periodic battery refresh setting
- basic Quick Settings Tile that opens the app
- unit tests for protocol helpers/parsers
- debug build and install through ADB

Needs completion before stable:

- full manual QA pass on the real earbuds
- Quick Settings Tile that can toggle ANC directly or a documented decision to keep it app-opening only
- optional battery notification implemented or removed from stable UI copy
- EQ behavior confirmed and moved out of Lab only when reliable
- release build/signing workflow
- final README and manual test checklist update

## Release Tracks

### Track A: Core Reliability

Owner profile: architecture, state, Bluetooth reliability.

Tasks:

- tighten connection state transitions: disconnected, connecting, syncing, ready, scanning, error
- keep last known battery values across reconnect without hiding stale data
- ensure disconnect clears volatile connection state but preserves useful last-known battery
- verify auto-connect after app restart and after Bluetooth reconnect
- add user-visible errors for missing permission, no bonded earbuds, missing OPO UUID, RFCOMM timeout and broken socket
- keep periodic battery refresh active only while connected and setting-enabled

Acceptance:

- no crash on denied permissions
- no crash when earbuds are not paired
- reconnect works without restarting the app
- case battery cache survives app restart
- `adb logcat -s RealmeBuds` gives enough context to diagnose failures

### Track B: Protocol and Feature Completeness

Owner profile: protocol parser and hardware validation.

Tasks:

- collect confirmed TX/RX logs for battery, firmware, ANC, gestures and EQ
- keep unconfirmed packets in Lab until validated
- add parser tests for every confirmed payload variant
- verify ANC set and query behavior across ANC, transparency and normal
- verify gesture combinations:
  - left/right double tap
  - left/right triple tap
  - left/right hold
  - both hold / game mode
- confirm whether EQ preset set/query is reliable
- decide whether custom EQ belongs in stable or remains future/Lab-only

Acceptance:

- stable UI exposes only confirmed protocol operations
- test suite covers packet builders/parsers used by stable UI
- README documents known unsupported combinations or firmware variants

### Track C: Android Integrations

Owner profile: Android services, notification, settings.

Tasks:

- decide final Quick Settings Tile behavior:
  - preferred: toggle between ANC and normal using last connected earbuds
  - fallback: open app and show current connection state
- if direct QS toggle is implemented:
  - handle missing permissions
  - avoid long-running work without proper service lifecycle
  - show tile unavailable when no paired/known earbuds exist
  - log tile actions with `RealmeBuds`
- implement battery notification while connected, or remove/disable the setting until implemented
- keep notification optional and off by default
- prepare battery widget only after battery state API is stable

Acceptance:

- QS Tile behavior is clear, tested and documented
- notification setting does not imply a missing feature
- Android integrations never crash when earbuds are absent or disconnected

### Track D: Material Expressive UI Polish

Owner profile: Compose UI and UX.

Tasks:

- keep dashboard as the first screen, not a landing page
- preserve strong contrast in the top status panel
- keep battery tiles stable in size for empty, current and cached values
- make all touch targets comfortable on phone screens
- avoid burying stable controls in Lab
- ensure disabled states are readable but visually distinct
- review copy for consistency:
  - `Gotowe`
  - `Łączenie`
  - `Synchronizuję`
  - `z pamięci, X temu`
  - `bieżący odczyt`
- verify screenshots in light and dark themes

Acceptance:

- no clipped button text on Pixel 8-sized viewport
- dashboard remains scannable after connection and while disconnected
- Lab looks clearly experimental

### Track E: QA, Release and Documentation

Owner profile: test, docs, release engineering.

Tasks:

- add or update unit tests:
  - battery parser variants
  - firmware parser
  - ANC builder/parser
  - touch config builder and allowed-actions guard
  - EQ preset builder/parser if stable
  - raw HEX validation
- run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

- add release build command and signing notes
- update README with stable feature list, known limitations and manual QA results
- attach or save final screenshots from phone
- prepare changelog for the stable version

Acceptance:

- debug build passes
- unit tests pass
- release build path is documented
- manual QA checklist is completed
- known limitations are explicit

## Priority Order

### P0: Required for Stable

1. Full manual QA on connected phone and Realme Buds Air 5 Pro.
2. Confirm or constrain EQ behavior.
3. Confirm gesture matrix and document unsupported combinations.
4. Finish or remove battery notification setting from stable UX.
5. Decide and document final QS Tile behavior.
6. Add tests for all protocol behavior used by stable UI.
7. Update README and release checklist.
8. Produce repeatable release build instructions.

### P1: Strongly Recommended

1. Direct QS Tile ANC toggle.
2. Battery notification while connected.
3. More detailed reconnect diagnostics.
4. Capability model for future Realme/Oppo/OnePlus variants.
5. Better import/export for Lab logs.

### P2: Future After Stable

1. Battery widget.
2. Custom EQ editor.
3. Multi-model support.
4. More automated UI tests.
5. Companion notification actions for ANC/EQ.

## Manual QA Checklist

### Fresh Install

- install APK through ADB
- launch app
- grant all required Bluetooth permissions
- verify no crash in logcat

### Connection

- connect to paired `realme Buds Air 5 Pro`
- verify device name, model, UUID, transport and firmware
- disconnect
- reconnect without restarting app
- close app and reopen with auto-connect enabled

### Battery

- verify left and right battery
- verify case battery when earbuds/case expose it
- remove earbuds from case or create a state where case is unavailable
- refresh battery
- verify case value remains visible as cached with age
- restart app and verify cached case value still appears

### ANC

- switch to ANC
- switch to transparency
- switch to normal
- verify audible behavior or reliable device response
- verify no stuck syncing state

### Gestures

- set each supported side/type/action combination selected for stable
- physically test the gesture on earbuds
- reconnect and verify behavior persists
- document combinations ignored by firmware

### EQ

- query current EQ
- set each stable preset
- verify audible behavior or reliable device response
- if query/set cannot be trusted, keep EQ marked experimental or document limitation

### Android Integrations

- add Quick Settings Tile
- verify documented tile behavior
- enable notification setting if implemented
- verify notification appears only while connected
- verify disabling notification removes it

### Lab

- send invalid HEX and verify it is rejected
- send known safe HEX and verify log entry
- run BLE diagnostics
- export logs and verify report includes device, firmware, battery, ANC, EQ and TX/RX lines

## Stable Release Exit Criteria

The stable release can be tagged when all are true:

- `:app:testDebugUnitTest` passes
- `:app:assembleDebug` passes
- release build/signing path is documented
- phone install succeeds with `adb install -r`
- no crash in a full manual QA pass
- all stable features have confirmed hardware behavior
- Lab-only features are clearly separated
- README and this plan reflect the real app state

## Open Decisions

- Whether Quick Settings Tile should directly toggle ANC or remain an app launcher for v1.0.
- Whether battery notification is mandatory for v1.0 or can land in v1.1.
- Whether EQ preset set/query is reliable enough for stable UI.
- Whether custom EQ should be excluded from v1.0.
- Whether release distribution is debug APK sharing, signed APK, or Play Store-ready package.

