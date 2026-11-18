# MonoMode

One phone, one job at a time. MonoMode is an Android app that turns an old or spare phone into a simple, single-purpose device, and checks that it is safe to use. It runs on the phone itself: no computer, no root.

It talks to the phone's own `adbd` over **Wireless debugging** (Android 11+), so it can change settings a normal app cannot. You pair once; after that it connects by itself and switches Wireless debugging off when you finish.

## What it does

- **Network & access**: which apps use the internet and what can reach the phone (open network ports, apps with accessibility, notification or device-admin access, user certificates, VPN, apps from outside the Play Store). Read-only.
- **App cleaner**: uninstall bloatware or disable apps you don't use, in small batches. Listing your apps needs no Wireless debugging; removing or disabling does. After each batch it checks that Home, calls and the keyboard still work, and undoes the batch if not. Every removal can be restored, and a copy of the restore list is saved in `Downloads/MonoMode`.
- **Modes**: one switch changes a few settings for one job, and turning it off puts your own settings back exactly as they were.
  - **Kiosk**: a home screen with only the apps you choose, or one app pinned (PIN to unpin). Pinning an app uses Wireless debugging each time, and MonoMode turns it off again.
  - **E-reader**: grey screen, no animations, Do not disturb, battery saver, and a library as the home screen. Once it is on, *Enter e-reader* takes you back to it.
- **Built-in reader**: a library and reader for EPUB, TXT, HTML, FB2, CBZ and PDF books, with bookmarks, highlights with notes, search, read aloud, reading lists and statistics. Pages are centred columns (two on wide screens), with your choice of font, size, spacing and margins, in day, sepia or night themes. Books are treated as untrusted: size is capped, and scripts inside a book are blocked.
- **Everyday launcher**: with no mode on, MonoMode can be the phone's home screen, showing all your apps.
- **Tablets**: a navigation rail and a two-pane layout on wide screens.

## Install

Download the APK and open it on the phone (Android will ask you to allow installs from your browser or file manager):

- **[monomode-v0.1.0.apk](https://github.com/sarat03/monomode/releases/download/v0.1.0/monomode-v0.1.0.apk)**, direct download of the latest release
- [All releases](https://github.com/sarat03/monomode/releases), for newer and older versions

Every release is signed with the same key, so it installs over the previous one. To check a download, its signing certificate SHA-256 should be `c9f22ce5758f5e2efe3d2946d08babb3dd9572cb09cb5e2c38dfbf4dd6e48bb7` (`apksigner verify --print-certs monomode-v0.1.0.apk`).

Or build it yourself (below) and install `app/build/outputs/apk/release/app-release.apk`. Then open MonoMode and follow **Set up once**: turn on Developer options and Wireless debugging, tap *Pair device with pairing code*, and type the code into the MonoMode notification.

To list the books on your phone, the library asks for *All files access*. It only reads them; nothing leaves the phone, and the app talks to nothing but the phone's own `adbd`.

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

Design decisions are in `docs/adr/`, what each feature is and why in `docs/features.md`, the project's words in `CONTEXT.md`, and how to work on it in `CONTRIBUTING.md`.

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
