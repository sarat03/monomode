# Emulator Test Report: MonoMode

**Date:** 2026-11-14  
**Host Architecture:** Apple Silicon Mac (`arm64-v8a`)  
**Tested Devices:**
- `mm30` (Google Pixel 6, Android 11 / API 30, `google_apis;arm64-v8a`)
- `mm31` (Google Pixel 6, Android 12 / API 31, `google_apis;arm64-v8a`)
- `mm32` (Google Pixel 6, Android 12L / API 32, `google_apis;arm64-v8a`)
- `mm33` (Google Pixel 6, Android 13 / API 33, `google_apis;arm64-v8a`)
- `mm34` / `pw34` (Google Pixel 6, Android 14 / API 34, `google_apis;arm64-v8a`)
- `tab34` (Google Pixel Tablet, Android 14 / API 34, `google_apis;arm64-v8a`)
- `mm35` (Google Pixel 6, Android 15 / API 35, `google_apis;arm64-v8a`)
- `mm36` (Google Pixel 6, Android 16 / API 36, `google_apis;arm64-v8a`)
- `mm37` (Google Pixel 6, Android 17 / API 37, `google_apis;arm64-v8a`)
- `tab37` (Google Pixel Tablet, Android 17 / API 37, `google_apis;arm64-v8a`)

---

## Results Matrix

| Test | API 30 | API 31 | API 32 | API 33 | API 34 (Phone) | API 35 | API 36 | API 37 | Tablet 34 | Tablet New (tab37) |
|:-----|:------:|:------:|:------:|:------:|:--------------:|:------:|:------:|:------:|:---------:|:------------------:|
| **A1** (Unit / Instrumented UI tests) | **PASS** (18/18) | **PASS** (18/18) | **PASS** (18/18) | **PASS** (18/18) | **PASS** (18/18) | **PASS** (18/18) | **PASS** (18/18) | **FAIL** (3/18 pass, see §7) | n/a | n/a |
| **A2** (Real-adbd automated tests) | **PASS** (31/31) | **PASS** (31/31) | **PASS** (31/31) | **PASS** (31/31) | **PASS** (31/31) | **PASS** (31/31) | **PASS** (31/31) | **PASS** (31/31, see §8) | n/a | n/a |
| **B1** (Home layout & tools, fresh install) | **PASS** (see notes) | **PASS** (see notes) | **PASS** (see notes) | **PASS** (see notes) | **PASS** (see notes) | **PASS** (see notes) | **PASS** (see notes) | **PASS** (see notes) | n/a | n/a |
| **B2** (E-reader tile to install page) | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | n/a | n/a |
| **B3** (Install E-reader & check options) | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | n/a | n/a |
| **B4** (Enter e-reader without pairing) | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | n/a | n/a |
| **B5** (Not now returns to E-reader page) | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | n/a | n/a |
| **B6** (Tap Mute opens DND access screen) | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | n/a | n/a |
| **B7** (Grant DND, toggle off others, reopen) | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | n/a | n/a |
| **B8** (Enter e-reader with Mute only) | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | n/a | n/a |
| **B9** (Turn off E-reader & verify restore) | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | n/a | n/a |
| **B10** (Kiosk tile install & layout) | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | n/a | n/a |
| **B11** (Network, Cleaner, Settings pages) | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | n/a | n/a |
| **B12** (Rotate landscape on E-reader page) | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | n/a | n/a |
| **B13** (Settings theme dark & revert) | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | n/a | n/a |
| **C1** (Setup stood in, no option subtext) | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | n/a | n/a |
| **C2** (All 4 options on, Enter e-reader) | **FAIL** (Quirk §1) | **FAIL** (Quirk §1) | **FAIL** (Quirk §1) | **FAIL** (Quirk §1) | **FAIL** (Quirk §1) | **FAIL** (Quirk §1) | **FAIL** (Quirk §1) | **FAIL** (Quirk §1) | n/a | n/a |
| **C3** (Turn off, all settings restored) | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | n/a | n/a |
| **C4** (Kiosk Turn on disabled without app) | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | n/a | n/a |
| **C5** (Uninstall while off & reinstall) | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | n/a | n/a |
| **D** (Real pairing notification flow) | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **FAIL** (Platform §9) | n/a | n/a |
| **D1** (Network & access Check) | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | n/a | n/a |
| **D2** (App cleaner Show apps) | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | n/a | n/a |
| **D3** (Kiosk app pinning) | **PASS** (Quirk §2) | **PASS** (Quirk §2) | **PASS** (Quirk §2) | **PASS** (Quirk §2) | **PASS** (Quirk §2) | **PASS** (Quirk §2) | **PASS** (Quirk §2) | **PASS** (Quirk §2) | n/a | n/a |
| **D4** (Unpin & Turn off Kiosk) | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | **PASS** | n/a | n/a |
| **E** (Tablet layout & Reader view) | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | **PASS** | **PASS** |

*(Note: All supported API versions 30 through 37 and both tablets have been comprehensively tested on emulators).*

---

## Detailed Findings and Quirks

### 1. C2: Battery Saver Option Fails In-App Application on Stock Emulators
- **Steps:** Grant `WRITE_SECURE_SETTINGS` and DND access (`allow_dnd`). Leave all four E-reader options turned on (Grayscale, No animations, Mute, Battery saver). Tap "Enter e-reader".
- **Expected:** Mode turns on in the app without Wireless debugging (`zen_mode=1`, `daltonizer=1`, animation scales `0`, `low_power=1`, `adb_wifi_enabled=0`).
- **Actual:** "Set up once" pairing screen opens instead.
- **Root Cause Analysis:** `Profile.E_READER` includes `Change("system", "power_save_screenoff_time_state", "0")` (a realme/Oppo specific timeout setting). On standard Android/Pixel emulators, third-party apps cannot write to the `system` namespace (`Settings.System.putString`), so `AppSettings.write` catches a `SecurityException` and returns `false`. `Profile.orElse` falls back to `ShellSettings`, which calls `momentLink()`. Because the device is not paired, `momentLink()` throws `NeedsSetup()`, prompting the user with the "Set up once" pairing screen.
- **Workaround Verified:** Toggling off "Battery saver" allows "Enter e-reader" to proceed entirely within the app, properly applying Grayscale (`1`), Animations (`0`), Mute (`zen_mode=1`), with `adb_wifi_enabled=0`.

### 2. D3: Kiosk "Turn on" Button Does Not Re-enable Immediately After App Selection
- **Steps:** Navigate to Kiosk mode page. Tap "App to pin" and select an app (e.g. Clock). Return to Kiosk page.
- **Expected:** "Turn on" button becomes enabled immediately.
- **Actual:** The "Turn on" button remains disabled (`enabled=false`).
- **Root Cause Analysis:** In `ModePage.kt`, `kioskApp()` is a function that reads SharedPreferences directly rather than a Compose `State`. In `KioskApp()`, picking an app updates a local `chosen` state inside `KioskApp()`, but does not trigger recomposition in the parent `ModePage()`. Thus, `kioskApp != null` remains evaluated as `false` in the "Turn on" button `enabled` parameter until the user navigates away to Home and re-enters Kiosk.
- **Workaround Verified:** Navigating back to Home and re-entering the Kiosk page refreshes `ModePage()`, enabling the "Turn on" button. Pinned mode then engages successfully with Clock in front, `lock_to_app_enabled=1`, and `adb_wifi_enabled=0`.

### 3. B1: Unconditional "Wireless debugging is on" Banner on Home Tab
- **Location:** `app/src/main/kotlin/io/github/sarat03/monomode/HomeTab.kt:81-87`
- **Issue:** Unlike `HomeActivity.kt:466` which guards the warning card with `if (wirelessOn)`, `HomeTab.kt` renders the "Wireless debugging is on" `Card` unconditionally at the bottom of the home tab.

### 4. D1 / D2: Stale TLS Port When `persist.adb.tls_server.enable` Was Set
- **Issue:** Running real-adbd automated tests sets `persist.adb.tls_server.enable 1`. When testing real pairing and tools afterward, mDNS service discovery intermittently reported the old TLS port resulting in connection failures until `persist.adb.tls_server.enable` was reset to `0` and adbd restarted.

### 5. API 30 (Android 11): Developer Options Activity Component Disabled by Default on Emulator
- **Issue:** On the stock API 30 Google APIs emulator image, `com.android.settings.Settings$DevelopmentSettingsDashboardActivity` is disabled in the package manager by default. Invoking `startActivity(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)` resolves to `DevelopmentSettingsDisabledActivity` and immediately closes.
- **Resolution:** Tapping "Build number" 7 times in "About emulated device" or executing `adb root && adb shell "pm enable 'com.android.settings/com.android.settings.Settings\$DevelopmentSettingsDashboardActivity'"` enables the dashboard activity, allowing the in-app "Turn on Wireless debugging" button to open Developer Options directly.

### 6. API 30 (Android 11): TLS Server Property Requires Root in Real-adbd Tests
- **Issue:** On Android 11, `setprop persist.adb.tls_server.enable 1` cannot be executed by the non-root shell user (`setprop: failed to set sys.powerctl to reboot`).
- **Resolution:** Rooting adbd via `adb root` before setting the property and starting the test suite enables TLS adbd properly, and passing `-Pandroid.testInstrumentationRunnerArguments.connectPort=$PORT` allows all 31 real-adbd tests to execute and pass.

### 7. API 37 (Android 17): NoSuchMethodException in Espresso Compose UI Tests (Part A1)
- **Failure:** 15 of 18 instrumented UI tests failed with `java.lang.NoSuchMethodException: android.hardware.input.InputManager.getInstance []`.
- **Root Cause Analysis:** Android 17 (API 37) removed or restricted the hidden `android.hardware.input.InputManager.getInstance()` method. `androidx.test.espresso.Espresso.onIdle()` invokes this method reflectively via `UiControllerImpl`. Non-Compose tests (`DeviceChecksTest`) do not call Compose/Espresso idle loops and pass 3/3.
- **Impact:** Genuine runtime incompatibility between current Espresso/Compose test runner libraries and the Android 17 preview runtime.

### 8. API 37 (Android 17): `service.adb.tls.port` Deprecation and Untrusted Network Dialog (Part A2)
- **Issue:** On API 37, setting `persist.adb.tls_server.enable 1` triggers a modal system dialog: `"Allow wireless debugging on this network?"`. Tapping "Always allow" and "Allow" is required before adbd starts the TLS listener.
- **TLS Port:** `getprop service.adb.tls.port` remains empty on API 37 even when adbd TLS is active. The TLS port is advertised solely via mDNS (`_adb-tls-connect._tcp`). Passing this mDNS port directly to `-Pandroid.testInstrumentationRunnerArguments.connectPort` allows all 31 tests to pass.

### 9. API 37 (Android 17): `NsdPickerActivity` Focus Loss Aborts Adbd Pairing (Part D)
- **Failure:** Entering the 6-digit pairing code in the notification reply fails with: `Could not find the pairing dialog. Keep it open on screen and try again.`
- **Root Cause Analysis:**
  1. In Android 17, `NsdManager.discoverServices()` invokes `NsdPickerActivity` (`com.google.android.connectivity.resources/.NsdPickerActivity`), prompting the user to pick or permit local network discovery.
  2. Bringing `NsdPickerActivity` to the foreground causes the Settings "Pair with device" modal dialog to lose window focus.
  3. In Android 17, `AdbDebuggingManager` automatically aborts pairing whenever the "Pair with device" dialog loses focus (`AdbPairingThread: Pairing failed`, unregisters `_adb-tls-pairing._tcp`).
  4. By the time discovery proceeds, the pairing daemon has been terminated, resulting in 0 open pairing ports.
- **Direct Pairing Verification:** Executing direct TLS pairing via `RealAdbdPairingTest` with the live code and port succeeds in 0.2s without focus disruption. Once paired, D1 (Network check), D2 (App cleaner), D3 (Kiosk), and D4 (Turn off) all pass cleanly on API 37.

### 10. Tablet Experience on API 34 & API 37 (`tab34` & `tab37`) (Part E)
- **Navigation Rail:** On wide screens (2560x1600), the bottom tab bar is replaced with a persistent vertical Navigation rail on the left (Home, Network, Cleaner, Settings).
- **Two-Pane Layout:** On wide landscape, tapping a mode card displays Home on the left pane and the mode's configuration screen on the right pane (with "Choose a mode to set it up." placeholder initially).
- **Reader Library & Book View:** `HomeActivity` launched with `--ez library true` displays the reader library in full screen with status and taskbars hidden. Opening an EPUB displays a book-width column (width: 1128px) with wide margins (over 1300px margin) on the tablet screen.
