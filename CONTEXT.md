# MonoMode: domain language

The words this project uses, what each one means here, and where it lives in code. Use these words in
code, commits, issues and screen text. When a new term settles, add it here in the same change.

## The phone and the connection

| Term | Means | Code |
|---|---|---|
| **Wireless debugging** | Android 11+'s adb over Wi-Fi. MonoMode's only way to do what a normal app cannot. Always switched off after a session (ADR 0005). | `Session.kt` |
| **Pairing** | The one-time code exchange (SPAKE2 then TLS) that makes adbd trust MonoMode's key. | `adb/pairing/`, `Pairing.kt` |
| **Session** | One open connection to the phone's own adbd, from connect to switch-off. | `Session.kt` |
| **Link** | The engine's view of a session: the actions it can run. `Session` on the phone, a fake in tests. | `engine/Phone.kt` |
| **Shell** | One command run on the phone, returning its output. Every engine rule sits behind it. | `engine/Engine.kt` |
| **Phone** | The engine module that owns connection, actions, errors and the Retry rule. Screens call it and draw its `PhoneState`. | `engine/Phone.kt` |
| **PhoneState** | One snapshot of everything the screens show about the phone. | `engine/Phone.kt` |
| **Set up once** | Pairing plus MonoMode granting itself `WRITE_SECURE_SETTINGS`, so later changes need no Wireless debugging. | `Need.SETUP_ONCE` |
| **Only for modes** | The default way to use MonoMode: the user's own launcher stays; MonoMode only changes settings while a mode is on. | `Onboarding.kt` |
| **MonoMode as launcher** | Optional: MonoMode is the phone's home app, chosen by the user in Android's own picker. No mode sets it. | `HomeActivity.kt` |

## Tools

| Term | Means | Code |
|---|---|---|
| **Network & access** (was Exposure check, Safety check) | Read-only report: live connections, listening ports, accessibility, device admins, notification access, user certificates, VPN, apps from outside the Play Store. | `CheckTab.kt`, `engine/Network.kt`, `engine/Access.kt` |
| **Finding** | One line of that report. `LOOK` (worth a look) or `INFO`. Never a verdict: the app never says "hacked" or "safe". | `engine/Findings.kt` |
| **App cleaner** (was Clean up, Debloat) | Disable or uninstall apps in checked batches, with restore. | `CleanupTab.kt`, `engine/Engine.kt` |
| **List** | A per-brand file of packages, each marked `safe`, `caution` or `risky`. Bundled, never downloaded. | `examples/`, `lists/` |
| **Risk** | `SAFE` (pre-selected), `CAUTION`, `RISKY`. Packages in `lists/protected.txt` are never offered. | `engine/Engine.kt` |
| **Method** | `DISABLE` (`pm disable-user`) or `REMOVE` (`pm uninstall --user 0`). Both restorable. | `engine/Engine.kt` |
| **Batch** | A few packages changed at once, then the **health check** (Home, dialer, keyboard still work). A failed check undoes the batch. | `Debloat` |
| **History** | Everything MonoMode currently holds, apps and the mode that is on, with dates; Restore per item, Revert all. | `engine/History.kt`, `CleanupTab.kt` |
| **Restore record** | Every change, so it can be undone; copied to `Downloads/MonoMode`. | `Records.kt` |

## Modes

| Term | Means | Code |
|---|---|---|
| **Mode** (Profile in code) | One switch that changes a few settings for one job, and puts the user's own settings back when turned off. | `engine/Profile.kt`, `engine/Modes.kt` |
| **Kiosk** | Home screen with only chosen apps, or one app pinned (screen pinning, optional PIN to unpin). | `Profile.KIOSK` |
| **E-reader** | Grey screen, no animations, Do Not Disturb, battery saver; home screen is the built-in reader's library. | `Profile.E_READER` |
| **Option** | One switch inside a mode (for example Mute notifications). Each maps to a list of `Change`s. | `Profile.options` |
| **Change** | One setting a mode writes: namespace, key, value. `DND` stands for Do Not Disturb. | `engine/Profile.kt` |
| **PhoneSettings** | The port that reads and writes a `Change`. Adapters: `ShellSettings` (adb, every change), `AppSettings` (Android APIs, where allowed). | `engine/Profile.kt`, `AppSettings.kt` |
| **Need** | What the user must allow before a mode can do something itself: `SETUP_ONCE`, `DND_ACCESS`. | `engine/Needs.kt` |
| **Installed** | A mode the user added from mode options. Others show on Home with Install. | `Records.modes` |
| **Mode home screen** | The launcher shown while a mode is on. | `HomeActivity.kt` |
| **Pin** | Android screen pinning of one app. Not device-owner lock task (ADR 0004). | `engine/Pin.kt` |

## Reader

| Term | Means | Code |
|---|---|---|
| **Library** | E-reader mode's home screen: books, reading lists, collections. | `Reader.kt`, `engine/Catalog.kt` |
| **BookFile** | One interface over every format: EPUB, TXT, HTML, FB2, CBZ, PDF. | `engine/BookFile.kt` |
| **Shelf** | What the reader remembers per book: place, marks, quotes, look. Behind a `Store`. | `engine/Shelf.kt` |
| **Place / Mark / Quote** | Where a book was left / a bookmark / a highlight with a note. | `engine/Shelf.kt` |
| **Goal / Streak** | Minutes to read each day (0 = off) / days in a row, ending today, that met it; today unmet does not break it. | `Shelf.goalMinutes`, `streak()` in `engine/Shelf.kt` |
| **Update check** | The opt-in, once-a-day ask to GitHub for the latest release; shows "Update available" and opens its page. Never downloads. | `engine/Update.kt`, `update/UpdateCheck.kt` |
| **Tap** | What a tap zone does: `BACK`, `FORWARD`, `MENU`, `MARK`. | `engine/Tap.kt` |

## Words to avoid

- "Hacked", "safe", "secure" about the phone: findings are things to look at, not verdicts.
- "Root": MonoMode never needs it.
- "Debloat" on screen: say App cleaner. Fine in code and lists.
