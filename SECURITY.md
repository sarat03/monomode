# Security

## What MonoMode sends to the phone

The app talks only to the phone's own adbd, over Wireless debugging (ADR 0001, ADR 0002). Every command goes
through `commandLine` in `engine/Engine.kt`, which refuses anything outside this list before it leaves. The one
exception is the fixed string `settings put global adb_wifi_enabled 0` in `adb/AdbConnection.kt`, which switches
Wireless debugging off.

| Command | Used for |
|---|---|
| `pm list packages` | Every read of installed, disabled and system apps |
| `pm disable-user --user 0`, `pm enable` | App cleaner: Disable and its restore |
| `pm uninstall -k --user 0`, `cmd package install-existing` | App cleaner: Remove (built-in apps only) and its restore (ADR 0008) |
| `cmd package resolve-activity` | Health check (Home, dialer) and reading the current home app |
| `cmd package set-home-activity` | Modes that set their own home screen |
| `settings get / put / delete` | Modes, the keyboard health check, Network & access, Wireless debugging off (ADR 0005) |
| `cmd notification set_dnd` | Mute notifications in a mode |
| `am start`, `am stack list`, `am task lock` | Kiosk: pin one app |
| `pm grant / revoke` (MonoMode itself, `WRITE_SECURE_SETTINGS`) | Set up once, and undoing it in Settings |
| `cat /proc/net/tcp`, `cat /proc/net/tcp6` | Network & access: listening ports (read-only) |

Each argument must match `^[A-Za-z0-9._/:=,@+-]+$`, so nothing reaches `sh` that it could interpret. Tested in
`EngineTest` (`commandLine refuses…`), and every engine flow under test passes through the same check.

## Data and permissions

- **Lists are data.** Debloat lists ship inside the app and change only with a release. Package names must match
  `^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$`. Packages in `lists/protected.txt` are never offered.
- **No network beyond the phone.** `INTERNET` is declared only because Android needs it for the loopback adb
  sockets. Sockets are opened only in `Session.kt` and `app/tls/`; CI fails on a socket, URL or HTTP client
  anywhere else (`test.yml`). The one exception is the opt-in update check (off by default, ADR 0010): a single
  HTTPS GET of the latest release from `api.github.com`, in `update/UpdateCheck.kt` only. It sends no account or
  identifier and downloads nothing.
- **Broad permissions, used locally:** `QUERY_ALL_PACKAGES` (app names), `WRITE_SECURE_SETTINGS` (granted by
  MonoMode to itself, for modes and switching Wireless debugging), `MANAGE_EXTERNAL_STORAGE` (E-reader, off
  until allowed in Settings), `ACCESS_NOTIFICATION_POLICY` (Do Not Disturb). Nothing leaves the phone.
- **Keys:** the adb identity is an RSA-2048 key in AndroidKeyStore. The pairing port accepts any certificate
  (`TrustAll`), because SPAKE2 binds the code to this TLS session (ADR 0001). `allowBackup` is off.
- **Books are untrusted files.** The reader's WebView has no network, file or content access, and book scripts
  are blocked by a `script-src 'none'` header. Chapter and "Open with" reads are size-capped.

## Dependencies

Conscrypt (TLS 1.3 with key export on Android 11, ADR 0001) and AndroidX Compose. The Python CLI has none.
Dependabot keeps them and the CI actions up to date.

## Python CLI

Runs `adb` with an argv list, never a shell string; only `pm list|disable-user|enable`,
`cmd package resolve-activity` and `settings get`. Protected packages need an explicit per-package override.

Turn off USB and Wireless debugging when done. Report issues privately via GitHub security advisories.
