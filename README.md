# MonoMode

One phone, one job at a time. MonoMode is an Android app that turns an old or spare phone into a simple, single-purpose device, and checks that it is safe to use. It runs on the phone itself: no computer, no root.

It talks to the phone's own `adbd` over **Wireless debugging** (Android 11+), so it can change settings a normal app cannot. You pair once; after that it connects by itself and switches Wireless debugging off when you finish.

## What it does

- **Network & access**: which apps use the internet and what can reach the phone (open network ports, apps with accessibility, notification or device-admin access, user certificates, VPN, apps from outside the Play Store). Read-only.
- **App cleaner**: uninstall bloatware or disable apps you don't use, in small batches. After each batch it checks that Home, calls and the keyboard still work, and undoes the batch if not. Every removal can be restored, and a copy of the restore list is saved in `Downloads/MonoMode`.
- **Modes**: one switch changes a few settings for one job, and turning it off puts your own settings back exactly as they were.
  - **Kiosk**: a home screen with only the apps you choose, or one app pinned (PIN to unpin).
  - **E-reader**: grey screen, no animations, Do not disturb, battery saver, and a paper-like home screen that opens your reading app.

## Install

Build it (below) and install `app/build/outputs/apk/release/app-release.apk`. Then open MonoMode and follow **Set up once**: turn on Developer options and Wireless debugging, tap *Pair device with pairing code*, and type the code into the MonoMode notification.

Some phones (realme, Oppo, Xiaomi) block settings changes from Wireless debugging until you turn on *Disable permission monitoring* or *USB debugging (Security settings)* in Developer options; MonoMode tells you when that happens.

## Build and test

```
gradle :adb:test :engine:test          # unit tests, no phone needed
gradle :app:assembleRelease            # the app
gradle :app:connectedDebugAndroidTest  # against a real adbd; see .github/workflows/emulator.yml
```

Needs JDK 21 and the Android SDK (`ANDROID_HOME`).

## Layout

| Module | What it holds |
|---|---|
| `adb` | ADB protocol and Wireless-debugging pairing (SPAKE2, TLS), pure Kotlin |
| `engine` | Checks, debloat, modes and `Phone` (the app's state and actions), all behind one small `Shell` interface, so they are tested without a phone |
| `app` | The Android app: Compose screens, the TLS connection, the mode home screens |

Design decisions are in `docs/adr/`.

## Python CLI

`cli.py` does the debloat part from a computer over USB `adb`, for phones older than Android 11:

```
python3 cli.py disable examples/generic.txt            # dry run
python3 cli.py disable examples/generic.txt --apply
python3 cli.py drift                                   # after an OTA
python3 cli.py restore
```

Needs `adb` and Python 3.10+. Lists are `com.vendor.app|caution   # comment` (risk: safe, caution, risky). Test with `python3 -m unittest -v`.

## What this is not

Not a security update. Old phones stop getting patches; the real fix is a maintained custom ROM for your model. MonoMode reduces what can reach the phone and what runs on it, nothing more.

## Licence

MIT
