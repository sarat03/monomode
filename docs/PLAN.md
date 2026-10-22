# Plan

PhoneWarden: an Android app that keeps old phones safe and useful, for people who never open a terminal.

## Product
- Android 11+ app. Talks to its own phone through Wireless debugging with our own ADB client.
- No internet permission. Nothing about the phone leaves it.
- MIT, public. Debloat lists are our own work.
- Signed APK on GitHub Releases, then F-Droid, Play Store later.
- The Python CLI (`cli.py`, `core.py`) stays as an unpolished path for Android 7 to 10 phones over USB.

## Version 1 features
1. **Exposure check** (read-only). Live connections and the owning app, services listening to the network, ADB reachable over the network, accessibility services, device admins, notification access, user-installed certificates, active VPN, apps installed outside the Play Store. Plain language; never says "hacked" or "safe".
2. **Debloat.** Lists bundled per brand; only `safe` pre-selected; preview first; small batches with a Home/dialer/keyboard health check and auto-rollback; one-tap restore; restore record copied to Downloads. Packages the vendor locks get a caution-level "Remove for this user" (`pm uninstall --user 0`, restored with `cmd package install-existing`).
3. **Profiles.** Kiosk: screen pinning, stay awake while charging, notifications muted, debloat. E-reader: grayscale, animations off, notifications muted, battery saver, recommend KOReader.
4. Wireless debugging is switched off after every session and verified off.

## Milestones
- **M0** Realme XT over USB: `cat /proc/net/tcp`, disable and re-enable one harmless app.
- **M1** Own ADB client: message codec, SPAKE2 pairing, TLS, shell.
- **M2** Port the engine to Kotlin, tests first.
- **M3** Exposure check.
- **M4** Debloat.
- **M5** Kiosk and e-reader.

## Testing
- TDD on four seams: ADB message codec, pairing against AOSP test vectors, exposure parser, debloat engine (via a fake shell).
- Emulator tests for Android 11 to 15 in GitHub Actions.
- Manual checklist on the Realme XT before each release.
- Release gate: apply then restore must return the phone to its original state.

## Later
AI helper (version 2, scope to be defined), audio and local-AI profiles, device-owner kiosk.
