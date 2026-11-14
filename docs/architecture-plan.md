# Architecture plan

Where MonoMode's code can get deeper: more behaviour behind smaller interfaces, at seams where something
really varies, tested through those interfaces. Vocabulary (module, interface, seam, adapter, depth) as in
Matt Pocock's codebase-design skill.

Today the `adb` and `engine` modules are deep and well tested. Most of the risk sits in `app/`:
`Reader.kt` (≈1,100 lines), `MainActivity.kt` (≈800) and `HomeActivity.kt` (≈580) mix drawing with
decisions that have no tests.

## 1. What a mode needs, in one engine module (done)

What each option needs (the one-time setup, Do Not Disturb access) and what **Turn on** asks for first
were decided in `ModePage`'s `if`s. They now live in `engine/Needs.kt` behind two functions,
`Profile.need(option, allowed)` and `Profile.firstAsk(off, allowed)`, tested in `NeedsTest`. The screen
only draws the answer.

## 2. A settings port instead of shell text (done)

`Profile` used to turn each `Change` into shell text and `Local.kt` parsed it back to run it in the app.
Now `engine/Profile.kt` has the port, `PhoneSettings` (`read(change)`, `write(change, value)`, each saying
"not here" with null/false), and `orElse` to fall back from one adapter to the next. Adapters:
`ShellSettings` (over Wireless debugging, every change), `AppSettings` in the app (settings and Do Not
Disturb through Android's own APIs, where it may), and `ProfileTest.FakeSettings` behind `ShellSettings`
in tests. `PhoneSettingsTest` covers the fallback; Kiosk's unpin is a step `Modes.turnOff` takes, run
only while something is pinned. Restore records keep their `ns key value` lines.

## 3. Screen state out of MainActivity's companion object (next)

Twelve `mutableStateOf` values in a `companion object` are shared by every page, and each page is a
private composable in one file. Move each page into its own file with an explicit state holder:

- `HomeTab`, `ModePage`, `CheckTab`, `CleanupTab`, `SettingsPage`, `PairScreen`.
- One `MainScreenState` (tab, open mode, cleaner filter, selections) passed down, so pages are
  previewable and Compose-testable (`KioskHomeTest`/`AllAppsTest` show the pattern).
- No behaviour change; check with the existing `androidTest` suite on the emulator only
  (it wipes app data — never on the user's phone).

## 4. Reader.kt

Split along what already varies: book formats behind `engine/BookFile.kt` (done), the page view, the
library, and the reading options. Do it with the reader's Phase 5 (see `docs/agents/handoff-reader.md`),
not before, so the pending work does not land on moved code.

## Habits that keep it this way

- New rules go in `engine` with a test first; `app` draws and calls.
- A seam needs two adapters (production + test); otherwise it is indirection.
- Test at the module's interface; when a deepening lands, delete the tests of the shallow parts.
- After code changes: `./gradlew :engine:test :adb:test :app:assembleDebug :app:compileDebugAndroidTestKotlin`,
  then `graphify update .`.
