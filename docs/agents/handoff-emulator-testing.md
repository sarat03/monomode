# Handoff: test MonoMode on every supported Android version, on emulators

You are testing MonoMode, an Android app, on one emulator per Android version it supports. You report what
works and what does not; you do not fix code unless the user asks. Read this whole file first.

## Safety rules (read before running anything)

1. **Never touch the user's real phone.** Connected (instrumented) tests **wipe MonoMode's app data**, and
   turning modes on changes real phone settings. Before every `adb` or Gradle device command, run
   `adb devices -l` and target an emulator explicitly: `adb -s emulator-5554 …`, and
   `ANDROID_SERIAL=emulator-5554 ./gradlew …`. Gradle's `connectedDebugAndroidTest` runs on **every**
   connected device otherwise. If a physical device is listed, ask the user to unplug it or confirm.
2. **Ask before downloading.** Each system image is about 1–1.5 GB. List what you will download (name and
   size) and wait for the user's yes.
3. **Do not push, merge, or file GitHub issues** without the user asking. Write findings to the report file
   (see the end).
4. **Do not change app code** to make a test pass. If something fails, record it with the exact steps.

## What MonoMode is (enough to test it)

- A **launcher** (`HomeActivity`: clock, pinned apps, All apps) plus **MonoMode** itself (`MainActivity`).
- **Modes**, installed on demand from Home: **E-reader** (grayscale, no animations, mute, battery saver; opens
  a built-in reader library) and **Kiosk** (pins one chosen app with screen pinning).
- **Wireless debugging only where needed.** MonoMode opens without pairing. Each option shows what it needs on
  a second line until it is met:
  - "Needs Wireless debugging once, to set up": settings changes; covered by the one-time pairing, which
    grants MonoMode `WRITE_SECURE_SETTINGS`.
  - "Needs Do Not Disturb access, once. Tap to allow.": Mute notifications.
  - "Uses Wireless debugging for a moment each time": Kiosk pinning, Network & access, App cleaner. MonoMode
    switches Wireless debugging on, does the action, and switches it off again.
- Code map: `engine/` (pure Kotlin, unit-tested: modes, profiles, `PhoneSettings` port, `Needs.kt`), `adb/`
  (own adb client and pairing), `app/` (screens, one file per page: `HomeTab.kt`, `ModePage.kt`, …).
  `docs/architecture-plan.md` explains the structure.

## Versions to cover

`minSdk = 30`, `targetSdk = compileSdk = 37` (`app/build.gradle.kts`). One phone emulator per API level:

| API | Android | Image installed now | AVD |
|----:|---------|---------------------|-----|
| 30 | 11 | no | create |
| 31 | 12 | no | create |
| 32 | 12L | no | create |
| 33 | 13 | no | create |
| 34 | 14 | **yes** (`google_apis;arm64-v8a`) | `pw34` (Pixel 6), `tab34` (Pixel Tablet) |
| 35 | 15 | no | create |
| 36 | 16 | no | create |
| 37 | 17 | no | create (check the image exists) |

Plus **two tablets**: `tab34` already exists. Add one more on the newest API, for the navigation rail and
two-pane layout.

The host is an Apple Silicon Mac: use `arm64-v8a` images. Use `google_apis` images, not `google_apis_playstore`:
those allow `adb root`, which the real-adbd tests need.

## Setup

```sh
S=~/Library/Android/sdk; export PATH=$S/platform-tools:$S/emulator:$S/cmdline-tools/latest/bin:$PATH
sdkmanager --list | grep -E 'system-images;android-3[0-7];google_apis;arm64'   # what exists; confirm 37's name
# after the user approves the downloads:
sdkmanager "system-images;android-30;google_apis;arm64-v8a"    # repeat per missing API
avdmanager create avd -n mm30 -k "system-images;android-30;google_apis;arm64-v8a" -d pixel_6
emulator -avd mm30 -no-snapshot-save -no-audio &                # run one or two at a time; RAM is the limit
adb -s emulator-5554 wait-for-device
until [ "$(adb -s emulator-5554 shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 3; done
```

Build once (from the repo root; `local.properties` must point at the SDK):

```sh
./gradlew :engine:test :adb:test :app:assembleDebug :app:assembleDebugAndroidTest
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

Shut an emulator down with `adb -s emulator-5554 emu kill`. Boot each fresh (`-no-snapshot-save`), so one run's
settings never leak into the next.

## Tools for driving the screen

- `tools/reader-ui.sh`: source it (`. tools/reader-ui.sh`, set `ADB="adb -s emulator-XXXX"` first, as the script
  reads `$ADB`). Gives `ui` (all text on screen with bounds), `tap "<text>"` (taps the first line containing the
  text), `top` (front activity).
  - To tap a button rather than a heading that contains the same word, match the dump line exactly:
    `tap '  Install ['`.
  - `tap` fails if the screen has not settled; `sleep 1.5` after each navigation.
- `~/bin/adb-ui`: prints the screen's texts in one line (`ADB="adb -s …" ~/bin/adb-ui`).
- Prefer these text dumps to screenshots: they are exact and cheap. Take a screenshot only to check layout,
  colour or clipping.
- Settings on the emulator: `adb shell settings get global zen_mode` (Do Not Disturb: 0 off, 1 priority),
  `… secure accessibility_display_daltonizer_enabled` (grayscale), `… global window_animation_scale`,
  `… global low_power`, `… global adb_wifi_enabled` (Wireless debugging), `… global stay_on_while_plugged_in`,
  `… secure lock_to_app_enabled`.
- MonoMode's own state: `adb shell run-as io.github.sarat03.monomode ls files` (a `mode-<NAME>.txt` file means
  that mode is on and holds its restore record).
- Standing in for permissions on an emulator:
  - One-time setup: `adb shell pm grant io.github.sarat03.monomode android.permission.WRITE_SECURE_SETTINGS`
  - Do Not Disturb access: `adb shell cmd notification allow_dnd io.github.sarat03.monomode`
  - All files access (reader library): `adb shell appops set io.github.sarat03.monomode MANAGE_EXTERNAL_STORAGE allow`

  Undo each with `pm revoke`, `disallow_dnd`, or `appops … default`.

## Per version: what to run

Do these in order on each API level. Record pass / fail / not run, with notes, for every line.

### A. Automated tests

1. **UI and device tests, no adb:**
   `ANDROID_SERIAL=emulator-XXXX ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notPackage=io.github.sarat03.monomode.tls`
   (18 tests on API 34). Results: `app/build/outputs/androidTest-results/connected/debug/*.xml`.
2. **Real-adbd tests**, the way CI runs them (`.github/workflows/emulator.yml`; CI covers 30, 31, 33, 34, 35 on
   x86_64, so 32, 36 and 37 are new ground):
   ```sh
   adb -s emulator-XXXX root && adb -s emulator-XXXX wait-for-device
   adb -s emulator-XXXX shell setprop persist.adb.tls_server.enable 1 && sleep 5
   PORT=$(adb -s emulator-XXXX shell getprop service.adb.tls.port | tr -d '\r')
   ANDROID_SERIAL=emulator-XXXX ./gradlew :app:connectedDebugAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.connectPort=$PORT \
     -Pandroid.testInstrumentationRunnerArguments.notClass=io.github.sarat03.monomode.tls.RealAdbdWirelessOffTest
   ```
   These tests wipe app data: reinstall the APK afterwards before part B.

### B. Scripted checks without pairing (fresh install, nothing granted)

| # | Steps | Expected |
|---|-------|----------|
| B1 | Open MonoMode (`am start -n io.github.sarat03.monomode/.MainActivity`) | Home: "Modes", Kiosk and E-reader tiles reading **Install**, "Tools" with Network & access and App cleaner, each with "Uses Wireless debugging for a moment each time…". **No** pairing screen. |
| B2 | Tap E-reader | "Install E-reader", description, **Install** button only |
| B3 | Tap the Install button | "E-reader is off", Enter e-reader, Uninstall, What changes: Grayscale / No animations / Battery saver each with "Needs Wireless debugging once, to set up"; Mute with "Needs Do Not Disturb access, once. Tap to allow."; Apps → Reading app. **No** "Own home screen". |
| B4 | Tap Enter e-reader | "Set up once" screen with steps, Start pairing, **Not now**. No `mode-*.txt` file; grayscale unchanged. |
| B5 | Tap Not now | Back on the E-reader page |
| B6 | Tap the Mute line | Android's Do Not Disturb access screen opens; MonoMode listed |
| B7 | Back; switch off Grayscale, No animations, Battery saver; grant DND access (command above); leave and reopen MonoMode | Mute's second line is gone |
| B8 | Enter e-reader | `zen_mode` becomes 1, `adb_wifi_enabled` stays 0, the reader **Library** opens (`top` shows `HomeActivity`) |
| B9 | Reopen MonoMode → E-reader → Turn off | `zen_mode` back to 0, no `mode-*.txt` |
| B10 | Kiosk tile → Install | Kiosk page: option lines, "Kiosk app" with "Pinning uses Wireless debugging for a moment each time…", "App to pin" |
| B11 | Network & access; App cleaner; Settings (gear) | Pages open: "Not checked yet" + Check; "Show apps"; Settings sections Connection, Appearance, Home screen, Backups |
| B12 | Rotate (`adb shell settings put system user_rotation 1`, with auto-rotate off) on the E-reader page | No crash, layout reflows |
| B13 | Settings → Theme → Dark; back to Home | Dark colours everywhere; switch back |

### C. Scripted checks with the setup stood in (`pm grant … WRITE_SECURE_SETTINGS`, DND access allowed)

| # | Steps | Expected |
|---|-------|----------|
| C1 | Reopen MonoMode → E-reader | No second lines under any option |
| C2 | All four options on → Enter e-reader | Grayscale 1, animation scales 0, `zen_mode` 1, `low_power` 1; **`adb_wifi_enabled` stays 0** (all done in the app) |
| C3 | Turn off | Every value back to what it was before C2 (record them first) |
| C4 | Kiosk, all options on, no app chosen | Turn on is disabled until an app is chosen |
| C5 | Uninstall E-reader while off | Back to Home, E-reader tile reads Install; reinstall keeps the earlier option choices |

### D. Real pairing on the emulator (try it; record how far it gets)

The emulator's Settings has Developer options → **Wireless debugging** on API 30+, over its virtual Wi-Fi
("AndroidWifi"). MonoMode pairs through a notification: the user types the code shown by Android.

1. Turn on Developer options: `adb shell settings put global development_settings_enabled 1`.
2. MonoMode → Enter e-reader (with Grayscale on) → Start pairing. MonoMode posts its pairing notification and
   opens Developer options.
3. In Settings: Wireless debugging → on → "Pair device with pairing code". Read the 6-digit code with `ui`.
4. Open the notification shade (`adb shell cmd statusbar expand-notifications`), tap MonoMode's reply field,
   type the code (`adb shell input text 123456`), send.
5. Expected: "Paired". Back in MonoMode, the next action connects once and grants `WRITE_SECURE_SETTINGS`
   (`adb shell dumpsys package io.github.sarat03.monomode | grep WRITE_SECURE`).

If that works, also check:

| # | Steps | Expected |
|---|-------|----------|
| D1 | Network & access → Check | Results appear; afterwards `adb_wifi_enabled` is 0 again |
| D2 | App cleaner → Show apps | The list appears; Wireless debugging off again afterwards. **Do not remove** system apps unless testing restore, and then restore them. |
| D3 | Kiosk: choose an app (e.g. Settings or Clock) → Turn on | The app is pinned; Wireless debugging off; `lock_to_app_enabled` 1 |
| D4 | Unpin (`adb shell am task lock stop` stands in for the gesture), open MonoMode → Kiosk → Turn off | Settings restored; no Wireless debugging needed (nothing pinned) |

A helper for this, `tools/pair-emulator.sh`, is referenced by `RealAdbdPairingTest` but was never written. If
you get pairing working, write it (to pass `-e pairCode` and `-e pairPort` to that test) and say so in the report.

### E. Tablet (tab34 and one newest-API tablet)

- Home shows a **navigation rail** (Home, Network, Cleaner, Settings); Home's Tools rows are hidden.
- On wide landscape, tapping a mode opens its page **beside** Home ("Choose a mode to set it up." before).
- E-reader library on a tablet: a book-width column, the taskbar hidden while the reader shows.

## Known version quirks (from history)

- API 30: no default home on a stock emulator; restore must tolerate an already-deleted setting (fixed in CI,
  commits `c354ff4`, `a69f24f`). Android 11 crashed hiding system bars before `setContent` (`9adba07`).
- API 30/31: Java 9+ APIs crashed `adb`/`engine` (`d06bde0`). Watch for `NoSuchMethodError` in logcat.
- API 33+: MonoMode asks for notification permission before pairing (`PairScreen.kt`).
- API 34+: Do Not Disturb "on" silences the ringer; MonoMode uses "priority" for that reason (`Profile.kt`).
- Not on emulators at all: realme/Oppo/Xiaomi's extra "permission monitoring" switches, and realme's battery
  saver timeout key. Note them as "phone only".

Useful when something fails: `adb -s … logcat -d -b crash` and `adb -s … logcat -d | grep -i monomode`.

## Report

Write `docs/agents/emulator-test-report-<YYYY-MM-DD>.md` with:

- A table: rows A1–A2, B1–B13, C1–C5, D (pairing) and D1–D4, E; columns API 30 … 37 and tablets; cells
  pass / fail / n/a / not run.
- For each failure: API, exact steps, what you expected, what happened, the logcat crash if any.
- What you could not test and why (downloads declined, image missing, pairing not possible).

Then tell the user where the report is, summarise failures first, and ask before filing any issue.
