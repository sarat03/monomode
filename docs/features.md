# Features: what was built, and why

One section per feature: what a user gets, where the code is, the decisions behind it, how it is tested,
and what is still open. This is the project's memory for people joining and for AI sessions: read the
section for the area you are about to touch before changing it. **Every change that adds or changes a
feature updates its section in the same PR** (see `CONTRIBUTING.md`). Terms are defined in `CONTEXT.md`.

Template for a new section:

```
## <Feature> (<first date>, last changed <date>)
**What:** what the user gets, in one or two sentences.
**Where:** files and modules.
**Decisions:** what was chosen and what was rejected, with ADR numbers.
**Tested by:** test classes, and what is checked by hand or on emulators only.
**Open:** known gaps, issue links.
```

---

## Own ADB client and pairing (2026-10-22)
**What:** MonoMode pairs with and talks to the phone's own adbd over Wireless debugging. No computer, no root.
**Where:** `adb/` (message codec, connection, `pairing/`: SPAKE2, AES-GCM peer info), `app/tls/` (Conscrypt TLS transports), `Session.kt`, `Pairing.kt`, `PairReceiver.kt` (code typed into a notification), `Mdns.kt`.
**Decisions:** own client, Conscrypt bundled for the TLS key exporter on Android 11, own Ed25519 point math, not constant-time (ADR 0001). Android 11 minimum (ADR 0003). The pairing handshake times out so a stalled one cannot hang the notification.
**Tested by:** `adb` unit tests against BoringSSL known-answer vectors and a scripted adbd; `RealAdbd*Test` against a real adbd on API 30-35 emulators (`.github/workflows/emulator.yml`).
**Open:** real pairing on an emulator needs a live dialog, so CI skips that one test.

## Network & access (2026-10-22)
**What:** a read-only report of what uses the internet and what can reach or control the phone.
**Where:** `engine/Network.kt` (`/proc/net`), `engine/Access.kt`, `engine/Findings.kt`; `DeviceChecks.kt` reads device admins, certificates and VPN in the app; `CheckTab.kt` draws.
**Decisions:** findings are `LOOK` or `INFO`, never a verdict. Built-in apps go under a drop-down: their access is usually how Android works.
**Tested by:** `NetworkTest`, `AccessTest`, `FindingsTest`; `DeviceChecksTest` and `RealAdbdExposureTest` on a device.

## App cleaner (2026-10-22)
**What:** disable or uninstall unused apps in checked batches, and restore any of them.
**Where:** `engine/Engine.kt` (`Debloat`, `apply`, `restore`, drift), `Records.kt`, `CleanupTab.kt`, lists in `examples/` and `lists/`; `cli.py`/`core.py` do the same over USB for Android 7-10.
**Decisions:** batches of five with a health check and rollback, user 0 only, Remove only for built-in apps, record written as each change applies (ADR 0008). Lists ship in the app (ADR 0002).
**Tested by:** `EngineTest`, `DebloatTest`, `RealAdbdDebloatTest`; `test_core.py` for the CLI.

## Modes: Kiosk and E-reader (2026-10-22, last changed 2026-11-14)
**What:** one switch per job that changes a few settings and puts the user's own back exactly when turned off, each with its own home screen.
**Where:** `engine/Profile.kt` (profiles, `Change`, the `PhoneSettings` port), `engine/Modes.kt`, `engine/Needs.kt`, `engine/Pin.kt`, `AppSettings.kt`, `ModePage.kt`, `HomeActivity.kt`.
**Decisions:** screen pinning, not device owner (ADR 0004). What a mode needs first is decided in the engine, not in the page (ADR 0006). Settings go through Android's own APIs where allowed, adb otherwise. Modes are installed from mode options; MonoMode starts as a launcher alone.
**Tested by:** `ProfileTest`, `ModesTest`, `NeedsTest`, `PhoneSettingsTest`, `PinTest`, `RealAdbdProfileTest`, `RealAdbdGrantTest`; mode home screens by the Compose tests in `HomeTest.kt`.
**Open:** realme gesture navigation lets a swipe leave Kiosk; Pin an app is the strict option.

## Phone module and session lifetime (2026-10-23, last changed 2026-10-24)
**What:** one place that owns the connection, every action, errors and when to offer Retry; Wireless debugging goes off whenever MonoMode leaves the screen.
**Where:** `engine/Phone.kt`; `MainActivity` (`onResume` refresh, `onStop` finish).
**Decisions:** screens draw only `PhoneState` (ADR 0006). Off on `onStop`, not `onDestroy` (ADR 0005). State is process-wide for now (ADR 0007).
**Tested by:** `PhoneTest`, `RealAdbdWirelessOffTest`.

## Tablet layout (2026-10-24)
**What:** a navigation rail on medium and wide windows, and a mode's page beside Home on wide ones.
**Where:** `MainScreen.kt`; plan in `docs/tablet-layout-plan.md`.
**Decisions:** one state holder, many layouts: the window size only picks how the same state is drawn.
**Tested by:** by hand on the Pixel Tablet emulator (tab34). No automated layout test.

## Everyday launcher (2026-11-05, #23)
**What:** MonoMode can be the phone's home screen with no mode on, showing all apps.
**Where:** `HomeActivity.kt`.
**Tested by:** `AllAppsTest` and `LauncherAccessibilityTest` (in `HomeTest.kt`) on a device.

## Built-in reader (2026-11-11 to 2026-11-13)
**What:** E-reader mode's home screen is a library; books open in place. EPUB, TXT, HTML, FB2, CBZ and PDF; bookmarks, highlights with notes, search, read aloud, reading lists and statistics.
**Where:** `Reader.kt`, `Pdf.kt`; `engine/BookFile.kt`, `Epub.kt`, `Catalog.kt`, `Shelf.kt`, `Tap.kt`, `KoReader.kt`.
**Decisions:** what the reader remembers lives in the engine behind a `Store`. Formats share one `BookFile` interface. Full history in `docs/agents/handoff-reader.md`.
**Tested by:** `EpubTest`, `BookFileTest`, `ShelfTest`, `CatalogTest`, `TapTest`; the page view and PDF by scripted emulator runs only.
**Open:** `Reader.kt` (about 1,100 lines) is to be split (`docs/architecture-plan.md` §4); review items at the end of the handoff.

## Release (2026-11-05, #15)
**What:** pushing a version tag builds a signed APK on GitHub Releases.
**Where:** `.github/workflows/release.yml`, `docs/release.md`.
**Open:** F-Droid metadata.
